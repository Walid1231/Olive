package com.waleve.player.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.waleve.player.domain.model.UserProfile
import com.waleve.player.domain.repository.AuthRepository
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
                    .addOnFailureListener { trySend(null) }
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
        return try {
            val credentialManager = CredentialManager.create(activityContext)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            // Authenticate with Firebase
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: return Result.failure(Exception("Sign-in returned null user"))

            // Create or update user profile in Firestore
            val profile = ensureUserProfile(
                uid = user.uid,
                displayName = user.displayName ?: "Unknown",
                email = user.email ?: "",
                avatarUrl = user.photoUrl?.toString(),
            )

            Result.success(profile)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Google Sign-In failed", e)
            Result.failure(e)
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
            // Update avatar and display name if changed
            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName,
                "displayNameLower" to displayName.lowercase(),
                "email" to email,
            )
            if (avatarUrl != null) updates["avatarUrl"] = avatarUrl
            usersCol.document(uid).set(updates, SetOptions.merge()).await()

            return docToProfile(usersCol.document(uid).get().await())
        }

        // First time — generate unique Friend Code
        val friendCode = generateUniqueFriendCode()

        val data = mapOf(
            "uid" to uid,
            "displayName" to displayName,
            "displayNameLower" to displayName.lowercase(),
            "email" to email,
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
            val code = "WLEV-" + (1..4).map { chars.random() }.joinToString("")
            val existing = usersCol.whereEqualTo("friendCode", code).limit(1).get().await()
            if (existing.isEmpty) return code
        }
        // Extremely unlikely fallback — use longer code
        val code = "WLEV-" + (1..6).map { chars.random() }.joinToString("")
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
            avatarUrl = doc.getString("avatarUrl"),
            friendCode = doc.getString("friendCode") ?: "",
            bio = doc.getString("bio"),
            favGenre = doc.getString("favGenre"),
            friendCount = friendCount,
            joinedAt = doc.getLong("joinedAt") ?: 0L,
        )
    }
}
