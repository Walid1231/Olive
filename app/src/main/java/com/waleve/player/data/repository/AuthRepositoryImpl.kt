package com.waleve.player.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.waleve.player.domain.model.UserProfile
import com.waleve.player.domain.repository.AuthRepository
import com.waleve.player.domain.repository.SignInFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val supabaseStorage: com.waleve.player.data.remote.SupabaseStorage,
) : AuthRepository {

    companion object {
        // Web client ID from google-services.json (client_type 3)
        private const val WEB_CLIENT_ID =
            "338773262708-asfdmmuq3u533ib0uicspkgpi70gahk1.apps.googleusercontent.com"
    }

    private val usersCol get() = firestore.collection("users")

    // ─── Auth state ──────────────────────────────────────────────────────────

    override fun observeAuthState(): Flow<UserProfile?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(null)
            } else {
                // Fetch profile from Firestore
                usersCol.document(user.uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val profile = docToProfile(doc)
                            // If the profile exists but has no friend code (created via
                            // FriendRepository.ensureUserProfile which doesn't write one),
                            // generate and persist a code now, then re-emit the updated profile.
                            if (profile.friendCode.isEmpty()) {
                                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val newCode = generateUniqueFriendCode()
                                        usersCol.document(user.uid)
                                            .update("friendCode", newCode)
                                            .await()
                                        trySend(profile.copy(friendCode = newCode))
                                    } catch (e: Exception) {
                                        // Emit as-is; will retry next auth state change
                                        trySend(profile)
                                    }
                                }
                            } else {
                                trySend(profile)
                            }
                        } else {
                            // Profile not yet created — return minimal (no friend code yet)
                            trySend(
                                UserProfile(
                                    uid = user.uid,
                                    displayName = user.displayName ?: "Unknown",
                                    email = user.email ?: "",
                                    avatarUrl = user.photoUrl?.toString(),
                                )
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        // Firestore unreachable / blocked: the user IS signed in — don't pretend otherwise
                        android.util.Log.e("AuthRepository", "Profile read failed", e)
                        trySend(
                            UserProfile(
                                uid = user.uid,
                                displayName = user.displayName ?: "Unknown",
                                email = user.email ?: "",
                                avatarUrl = user.photoUrl?.toString(),
                            )
                        )
                    }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun getCurrentProfile(): UserProfile? {
        val user = auth.currentUser ?: return null
        return try {
            val doc = usersCol.document(user.uid).get().await()
            if (doc.exists()) docToProfile(doc) else null
        } catch (e: Exception) {
            null
        }
    }

    override fun isSignedIn(): Boolean = auth.currentUser != null

    // ─── Google Sign-In ──────────────────────────────────────────────────────

    override suspend fun signInWithGoogle(activityContext: Context): Result<UserProfile> {
        var step = "Google account"
        return try {
            val idToken = googleIdToken(activityContext)

            // Authenticate with Firebase. A first connection over a half-working network often
            // fails, so try a couple more times before giving up.
            step = "Firebase"
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = retryOnNetwork { auth.signInWithCredential(firebaseCredential).await() }
            val user = authResult.user ?: return Result.failure(SignInFailure("Sign-in returned no user", step))

            // Create the Firestore profile on first sign-in (keeps a custom name/photo on later sign-ins)
            step = "profile"
            val profile = try {
                ensureUserProfile(
                    uid = user.uid,
                    displayName = user.displayName ?: "Unknown",
                    email = user.email ?: "",
                    avatarUrl = user.photoUrl?.toString(),
                )
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Signed in, but profile save failed", e)
                return Result.failure(signInFailure(e, step, activityContext))
            }

            Result.success(profile)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Google Sign-In failed at $step", e)
            Result.failure(signInFailure(e, step, activityContext))
        }
    }

    /** Google ID token from the account picker. */
    private suspend fun googleIdToken(activityContext: Context): String {
        val credentialManager = CredentialManager.create(activityContext)
        // 1st choice: the explicit "Sign in with Google" sheet — always shows every Gmail account on the phone.
        suspend fun signInSheet(): String {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build())
                .build()
            return tokenFrom(credentialManager.getCredential(activityContext, request).credential)
        }
        // Fallback: One Tap account chooser
        suspend fun oneTap(): String {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            return tokenFrom(credentialManager.getCredential(activityContext, request).credential)
        }
        return try {
            try {
                signInSheet()
            } catch (e: androidx.credentials.exceptions.GetCredentialInterruptedException) {
                // Google Play services lost the connection for a moment: once more
                kotlinx.coroutines.delay(1_200)
                signInSheet()
            }
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "SignInWithGoogle option failed, trying One Tap", e)
            oneTap()
        }
    }

    private suspend fun <T> retryOnNetwork(times: Int = 3, block: suspend () -> T): T {
        var last: Exception? = null
        repeat(times) { attempt ->
            try {
                return block()
            } catch (e: com.google.firebase.FirebaseNetworkException) {
                last = e
                android.util.Log.w("AuthRepository", "Network error, attempt ${attempt + 1}", e)
                if (attempt < times - 1) kotlinx.coroutines.delay(1_000L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException("retry failed")
    }

    private fun isNetworkError(e: Throwable): Boolean {
        val msg = ((e.message ?: "") + " " + (e.cause?.message ?: "")).lowercase()
        return e is com.google.firebase.FirebaseNetworkException ||
            e is androidx.credentials.exceptions.GetCredentialInterruptedException ||
            e is java.io.IOException || "network" in msg || "timeout" in msg || "unreachable" in msg
    }

    /**
     * Message + detail for a failed sign-in. For network errors it checks the connection to
     * Google's sign-in server itself, so it doesn't claim "no internet" when the internet works.
     */
    private suspend fun signInFailure(e: Throwable, step: String, context: Context): SignInFailure {
        val what = "$step · ${e.javaClass.simpleName}"
        if (!isNetworkError(e)) {
            return SignInFailure(friendlyError(e), "$what: ${(e.message ?: "").take(160)}")
        }
        val probe = probeGoogle(context)
        val message = when {
            !probe.online -> "No internet connection."
            probe.ipv6Broken -> "Google sign-in can't connect on this network (its IPv6 isn't working). Switch between Wi-Fi and mobile data, then try again."
            !probe.ipv4Ok && !probe.ipv6Ok -> "This network is blocking Google sign-in. Try mobile data or another Wi-Fi."
            else -> "Couldn't reach Google sign-in just now. Tap Continue with Google again."
        }
        return SignInFailure(message, "$what · ${probe.summary}")
    }

    private class NetProbe(val online: Boolean, val ipv4Ok: Boolean, val ipv6Ok: Boolean, val hasIpv6: Boolean, val summary: String) {
        val ipv6Broken get() = hasIpv6 && !ipv6Ok && ipv4Ok
    }

    /** Is the phone online, and can it open a connection to Google's sign-in server over IPv4 / IPv6? */
    private suspend fun probeGoogle(context: Context): NetProbe = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val online = runCatching {
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
            cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }.getOrDefault(true)
        val addresses = runCatching { java.net.InetAddress.getAllByName("identitytoolkit.googleapis.com").toList() }.getOrDefault(emptyList())
        val v4 = addresses.firstOrNull { it is java.net.Inet4Address }
        val v6 = addresses.firstOrNull { it is java.net.Inet6Address }
        fun connectMs(a: java.net.InetAddress?): Long? {
            if (a == null) return null
            val t0 = android.os.SystemClock.elapsedRealtime()
            return runCatching {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(a, 443), 3_000) }
                android.os.SystemClock.elapsedRealtime() - t0
            }.getOrNull()
        }
        val t4 = async { connectMs(v4) }
        val t6 = async { connectMs(v6) }
        val ms4 = t4.await()
        val ms6 = t6.await()
        fun show(ms: Long?, addr: java.net.InetAddress?) = when {
            addr == null -> "none"
            ms == null -> "fail"
            else -> "${ms}ms"
        }
        val summary = (if (online) "online" else "offline") +
            " · dns ${addresses.count { it is java.net.Inet4Address }}v4/${addresses.count { it is java.net.Inet6Address }}v6" +
            " · v4 ${show(ms4, v4)} · v6 ${show(ms6, v6)}"
        NetProbe(online, ms4 != null, ms6 != null, v6 != null, summary)
    }

    private fun tokenFrom(credential: androidx.credentials.Credential): String {
        if (credential is androidx.credentials.CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type: ${credential.type}")
    }

    /** Turns raw Google / Firebase errors into something a person can act on. */
    private fun friendlyError(e: Throwable): String {
        val msg = (e.message ?: "") + " " + (e.cause?.message ?: "")
        return when {
            e is androidx.credentials.exceptions.GetCredentialCancellationException -> "Sign-in cancelled."
            e is androidx.credentials.exceptions.NoCredentialException ->
                "No Google account found on this phone. Add one in Settings → Accounts, then try again."
            "[10]" in msg || "DEVELOPER_ERROR" in msg || "Developer console" in msg || "[16]" in msg ->
                "Google rejected this app's signature. Add this APK's SHA-1 fingerprint in Firebase → Project settings → Your apps."
            e is com.google.firebase.firestore.FirebaseFirestoreException &&
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Signed in, but the database blocked access. Update the Firestore security rules (see docs/firestore.rules)."
            e is com.google.firebase.FirebaseNetworkException || "network" in msg.lowercase() ->
                "No internet connection."
            else -> "Sign-in failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    // ─── Profile management ──────────────────────────────────────────────────

    override suspend fun updateProfile(
        displayName: String?,
        bio: String?,
        favGenre: String?,
    ): Result<UserProfile> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
            val updates = mutableMapOf<String, Any?>()
            if (displayName != null) {
                updates["displayName"] = displayName
                updates["displayNameLower"] = displayName.lowercase()
            }
            if (bio != null) updates["bio"] = bio
            if (favGenre != null) updates["favGenre"] = favGenre

            if (updates.isNotEmpty()) {
                usersCol.document(uid).update(updates.mapValues { it.value as Any }).await()
            }

            val doc = usersCol.document(uid).get().await()
            Result.success(docToProfile(doc))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateAvatar(imageUri: android.net.Uri): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
            val url = supabaseStorage.uploadAvatar(imageUri, uid).getOrElse { return Result.failure(it) }
            usersCol.document(uid).set(mapOf("avatarUrl" to url, "customAvatar" to true), SetOptions.merge()).await()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Ensures a user profile exists in Firestore. Generates a unique Friend Code on first creation.
     */
    private suspend fun ensureUserProfile(
        uid: String,
        displayName: String,
        email: String,
        avatarUrl: String?,
    ): UserProfile {
        val doc = usersCol.document(uid).get().await()

        if (doc.exists()) {
            // Keep a name / photo the user set themselves; only fill in what's missing
            val updates = mutableMapOf<String, Any>(
                "uid" to uid,
                "email" to email.lowercase(),
            )
            if (doc.getString("displayName").isNullOrBlank()) {
                updates["displayName"] = displayName
                updates["displayNameLower"] = displayName.lowercase()
            }
            if (avatarUrl != null && doc.getBoolean("customAvatar") != true && doc.getString("avatarUrl").isNullOrBlank()) {
                updates["avatarUrl"] = avatarUrl
            }
            if (doc.getString("friendCode").isNullOrBlank()) updates["friendCode"] = generateUniqueFriendCode()
            usersCol.document(uid).set(updates, SetOptions.merge()).await()

            return docToProfile(usersCol.document(uid).get().await())
        }

        // First time — generate unique Friend Code
        val friendCode = generateUniqueFriendCode()

        val data = mapOf(
            "uid" to uid,
            "displayName" to displayName,
            "displayNameLower" to displayName.lowercase(),
            "email" to email.lowercase(),
            "avatarUrl" to (avatarUrl ?: ""),
            "friendCode" to friendCode,
            "bio" to "",
            "favGenre" to "",
            "friendIds" to emptyList<String>(),
            "friendCount" to 0,
            "joinedAt" to System.currentTimeMillis(),
        )
        usersCol.document(uid).set(data).await()

        return UserProfile(
            uid = uid,
            displayName = displayName,
            email = email,
            avatarUrl = avatarUrl,
            friendCode = friendCode,
            joinedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Generates a unique "WLEV-XXXX" friend code. Retries if collision is found.
     */
    private suspend fun generateUniqueFriendCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I,O,0,1 to avoid confusion
        repeat(10) { // max 10 attempts
            val code = "OLV-" + (1..4).map { chars.random() }.joinToString("")
            val existing = usersCol.whereEqualTo("friendCode", code).limit(1).get().await()
            if (existing.isEmpty) return code
        }
        // Extremely unlikely fallback — use longer code
        val code = "OLV-" + (1..6).map { chars.random() }.joinToString("")
        return code
    }

    private fun docToProfile(doc: com.google.firebase.firestore.DocumentSnapshot): UserProfile {
        @Suppress("UNCHECKED_CAST")
        val friendIds = doc.get("friendIds") as? List<String> ?: emptyList()
        val storedCount = doc.getLong("friendCount")?.toInt() ?: 0
        // Use whichever is larger — friendIds is always accurate, stored count may lag
        val friendCount = maxOf(storedCount, friendIds.size)
        return UserProfile(
            uid = doc.getString("uid") ?: doc.id,
            displayName = doc.getString("displayName") ?: "Unknown",
            email = doc.getString("email") ?: "",
            avatarUrl = doc.getString("avatarUrl")?.takeIf { it.isNotBlank() },
            friendCode = doc.getString("friendCode") ?: "",
            bio = doc.getString("bio"),
            favGenre = doc.getString("favGenre"),
            friendCount = friendCount,
            joinedAt = doc.getLong("joinedAt") ?: 0L,
        )
    }
}
