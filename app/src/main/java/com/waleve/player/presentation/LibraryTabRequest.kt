package com.waleve.player.presentation

import kotlinx.coroutines.flow.MutableStateFlow

/** Lets other screens (e.g. Home chips) open the Library on a specific tab: 0 Songs, 1 Audiobooks. */
object LibraryTabRequest {
    val tab = MutableStateFlow<Int?>(null)
}
