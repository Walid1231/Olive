package com.waleve.player.data.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A link shared to Olive from another app ("Share → Olive"), waiting for the Download page. */
object SharedLinkInbox {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    private val URL = Regex("""https?://[^\s<>"']+""")

    /** Pull the first link out of shared text ("Check this out https://youtu.be/…"). */
    fun offer(sharedText: String?) {
        val url = sharedText?.let { URL.find(it)?.value }?.trimEnd('.', ',', ')', ']') ?: return
        _pending.value = url
    }

    fun take(): String? = _pending.value.also { _pending.value = null }

    fun firstUrlIn(text: String?): String? = text?.let { URL.find(it)?.value }?.trimEnd('.', ',', ')', ']')
}
