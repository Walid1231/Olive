package com.waleve.player.data.cover

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Library options: hide junk audio (voice notes, call recordings, ringtones) and fetch missing covers. */
@Singleton
class LibraryPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    private val _hideJunk = MutableStateFlow(prefs.getBoolean("hide_junk", true))
    val hideJunk: StateFlow<Boolean> = _hideJunk.asStateFlow()
    fun setHideJunk(on: Boolean) { _hideJunk.value = on; prefs.edit().putBoolean("hide_junk", on).apply() }

    private val _autoCovers = MutableStateFlow(prefs.getBoolean("auto_covers", true))
    val autoCovers: StateFlow<Boolean> = _autoCovers.asStateFlow()
    fun setAutoCovers(on: Boolean) { _autoCovers.value = on; prefs.edit().putBoolean("auto_covers", on).apply() }

    companion object {
        private val JUNK_PATHS = listOf(
            "whatsapp voice notes", "whatsapp business voice notes", "/ptt-", "/aud-ptt",
            "/call/", "/calls/", "call_rec", "callrec", "call recording", "call_recording", "/recordings/call",
            "sound_recorder/call", "/notifications/", "/ringtones/", "/alarms/", "/ui/", "/android/media/com.google.android.gm/",
        )

        /** Voice notes, call recordings, ringtones and tiny clips aren't music. */
        fun isJunk(path: String, durationMs: Long): Boolean {
            if (durationMs in 1..19_999) return true
            val p = path.lowercase()
            return JUNK_PATHS.any { it in p }
        }
    }
}
