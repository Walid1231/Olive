package com.waleve.player.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.domain.model.UserProfile
import com.waleve.player.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiEvent {
    data class Error(val message: String) : AuthUiEvent()
    data object SignInSuccess : AuthUiEvent()
    data object SignOutSuccess : AuthUiEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** Current signed-in profile (null = not signed in). */
    val profile: StateFlow<UserProfile?> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _events = MutableSharedFlow<AuthUiEvent>()
    val events: SharedFlow<AuthUiEvent> = _events.asSharedFlow()

    val isSignedIn: Boolean get() = authRepository.isSignedIn()

    /**
     * Initiates Google Sign-In. Requires Activity context for Credential Manager.
     */
    fun signInWithGoogle(activityContext: Context) {
        _isSigningIn.value = true
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(activityContext)
            result.onSuccess {
                _events.emit(AuthUiEvent.SignInSuccess)
            }.onFailure { e ->
                _events.emit(AuthUiEvent.Error(e.message ?: "Sign-in failed"))
            }
            _isSigningIn.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _events.emit(AuthUiEvent.SignOutSuccess)
        }
    }
}
