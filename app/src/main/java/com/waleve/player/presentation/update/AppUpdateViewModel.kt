package com.waleve.player.presentation.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.BuildConfig
import com.waleve.player.service.AppUpdateChecker
import com.waleve.player.service.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.updateDataStore by preferencesDataStore(name = "update_preferences")

/**
 * In-app updates from GitHub Releases: checks at start (and every time the app comes back
 * after 6 h), downloads the APK with progress and opens Android's installer. Same signing key
 * every release, so it installs over the old version and keeps all your data.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    val updateChecker: AppUpdateChecker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Shown as a dialog by the app shell. */
    val updateInfo: StateFlow<UpdateInfo?> = updateChecker.available

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    companion object {
        private val DISMISSED_VERSION_KEY = stringPreferencesKey("dismissed_update_version")
        @Volatile private var lastAutoCheck = 0L
    }

    /** Automatic check (skipped versions stay quiet). At most every 6 hours. */
    fun checkForUpdates() {
        val now = System.currentTimeMillis()
        if (now - lastAutoCheck < 6 * 3_600_000L) return
        lastAutoCheck = now
        viewModelScope.launch {
            val info = updateChecker.checkForUpdate() ?: return@launch
            val skipped = context.updateDataStore.data.map { it[DISMISSED_VERSION_KEY] }.first()
            if (skipped != info.version) updateChecker.available.value = info
        }
    }

    /** "Check for updates" button: always shows the result. */
    fun checkNow() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            val info = updateChecker.checkForUpdate()
            _checking.value = false
            if (info != null) updateChecker.available.value = info
            else _messages.tryEmit("You're on the latest version (${BuildConfig.VERSION_NAME})")
        }
    }

    /** "Later": hide for now, ask again next time. */
    fun later() { updateChecker.available.value = null }

    /** "Skip this version": don't ask again until a newer one comes out. */
    fun dismissUpdate() {
        val version = updateChecker.available.value?.version
        updateChecker.available.value = null
        if (version != null) {
            viewModelScope.launch {
                context.updateDataStore.edit { it[DISMISSED_VERSION_KEY] = version }
            }
        }
    }
}
