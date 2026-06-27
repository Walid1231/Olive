package com.waleve.player.domain.repository

import com.waleve.player.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository for authentication operations.
 */
interface AuthRepository {

    /** Observe current auth state — emits UserProfile when signed in, null when signed out. */
    fun observeAuthState(): Flow<UserProfile?>

    /** Returns the current UserProfile if signed in, null otherwise. */
    suspend fun getCurrentProfile(): UserProfile?

    /** Returns true if the user is currently signed in. */
    fun isSignedIn(): Boolean

    /** Sign in with Google using Credential Manager. Returns the UserProfile on success. */
    suspend fun signInWithGoogle(activityContext: android.content.Context): Result<UserProfile>

    /** Sign out the current user. */
    suspend fun signOut()

    /** Update the current user's profile fields (displayName, bio, favGenre). */
    suspend fun updateProfile(displayName: String? = null, bio: String? = null, favGenre: String? = null): Result<UserProfile>
}
