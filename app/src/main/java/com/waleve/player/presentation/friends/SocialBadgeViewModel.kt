package com.waleve.player.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.domain.model.InboxNotificationType
import com.waleve.player.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-level badge for the Friends tab: unread inbox items + friend requests waiting for me. */
@HiltViewModel
class SocialBadgeViewModel @Inject constructor(repo: FriendRepository) : ViewModel() {
    val badge: StateFlow<Int> = combine(repo.observeInbox(), repo.observeIncomingRequests()) { inbox, reqs ->
        inbox.count { !it.isRead && it.type != InboxNotificationType.FRIEND_REQUEST } + reqs.size
    }.catch { emit(0) }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}
