package com.waleve.player.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.waleve.player.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val isNightMode: StateFlow<Boolean> = userPreferencesRepository.isNightMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** 0 = follow the phone, 1 = light, 2 = dark. */
    val mode: StateFlow<Int> = AppearancePrefs.mode

    fun setMode(m: Int) = AppearancePrefs.set(m)

    fun toggleTheme() {
        viewModelScope.launch {
            userPreferencesRepository.setNightMode(!isNightMode.value)
        }
    }
}

/** Light / Dark / follow-the-phone, saved on the phone. */
object AppearancePrefs {
    private var prefs: android.content.SharedPreferences? = null
    private val _mode = MutableStateFlow(0)
    val mode: StateFlow<Int> = _mode.asStateFlow()

    fun init(context: android.content.Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
        _mode.value = prefs!!.getInt("mode", 0)
    }

    fun set(m: Int) {
        _mode.value = m
        prefs?.edit()?.putInt("mode", m)?.apply()
    }
}
