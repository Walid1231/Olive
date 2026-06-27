package com.waleve.player.presentation.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waleve.player.service.AppUpdateChecker
import com.waleve.player.service.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.updateDataStore by preferencesDataStore(name = "update_preferences")

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    val updateChecker: AppUpdateChecker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private var hasChecked = false

    companion object {
        private val DISMISSED_VERSION_KEY = stringPreferencesKey("dismissed_update_version")
    }

    fun checkForUpdates() {
        if (hasChecked) return
        hasChecked = true
        
        viewModelScope.launch {
            val info = updateChecker.checkForUpdate()
            if (info != null) {
                // Check if user previously dismissed this specific version
                val dismissedVersion = context.updateDataStore.data
                    .map { it[DISMISSED_VERSION_KEY] }
                    .first()
                
                if (dismissedVersion != info.version) {
                    _updateInfo.value = info
                }
            }
        }
    }

    fun dismissUpdate() {
        val version = _updateInfo.value?.version
        _updateInfo.value = null
        // Persist the dismissed version so it doesn't show again until a newer version
        if (version != null) {
            viewModelScope.launch {
                context.updateDataStore.edit { preferences ->
                    preferences[DISMISSED_VERSION_KEY] = version
                }
            }
        }
    }
}
