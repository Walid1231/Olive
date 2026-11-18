package com.waleve.player.presentation.together

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.data.social.TogetherRepository
import com.waleve.player.domain.model.Friend
import com.waleve.player.domain.model.ListenSession
import com.waleve.player.domain.model.NowPlaying
import com.waleve.player.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TogetherViewModel @Inject constructor(
    private val together: TogetherRepository,
    friendRepository: FriendRepository,
) : ViewModel() {
    val friends: StateFlow<List<Friend>> = friendRepository.observeFriends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activity: StateFlow<Map<String, NowPlaying>> = together.observeFriendActivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val invites: StateFlow<List<ListenSession>> = together.observeInvites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hosting: StateFlow<ListenSession?> = together.hosting
    val guest: StateFlow<ListenSession?> = together.guest
    val hostUpload: StateFlow<Float?> = together.hostUpload
    val shareActivity: StateFlow<Boolean> = together.shareActivity
    val events: SharedFlow<String> = together.events

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun clearError() { _error.value = null }

    private fun run(block: suspend () -> Result<Unit>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            block().onFailure { _error.value = it.message ?: "Something went wrong" }
            _busy.value = false
        }
    }

    fun start(friendUids: List<String>) = run { together.startSession(friendUids) }
    fun inviteMore(friendUids: List<String>) = run { together.inviteMore(friendUids) }
    fun join(hostUid: String) = run { together.joinSession(hostUid) }
    fun end() = run { together.endSession(); Result.success(Unit) }
    fun leave() = run { together.leaveSession(); Result.success(Unit) }
    fun setShareActivity(on: Boolean) = together.setShareActivity(on)
    fun playFriend(np: NowPlaying) = run {
        if (together.playFriendTrack(np)) Result.success(Unit)
        else Result.failure(Exception("“${np.track.title}” is only on your friend's phone"))
    }
}
