package com.waleve.player.presentation.profile

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

sealed class ProfileUiEvent {
    data class Success(val message: String) : ProfileUiEvent()
    data class Error(val message: String) : ProfileUiEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val profile: StateFlow<UserProfile?> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<ProfileUiEvent>()
    val events: SharedFlow<ProfileUiEvent> = _events.asSharedFlow()

    fun toggleEditing() {
        _isEditing.value = !_isEditing.value
    }

    fun saveProfile(displayName: String, bio: String, favGenre: String) {
        _isSaving.value = true
        viewModelScope.launch {
            val result = authRepository.updateProfile(
                displayName = displayName.takeIf { it.isNotBlank() },
                bio = bio,
                favGenre = favGenre,
            )
            result.onSuccess {
                _events.emit(ProfileUiEvent.Success("Profile updated!"))
                _isEditing.value = false
            }.onFailure { e ->
                _events.emit(ProfileUiEvent.Error(e.message ?: "Failed to update profile"))
            }
            _isSaving.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
