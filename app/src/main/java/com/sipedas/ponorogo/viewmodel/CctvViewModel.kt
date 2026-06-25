package com.sipedas.ponorogo.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class CctvViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("cctv_prefs", Context.MODE_PRIVATE)

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    fun setShowSettings(show: Boolean) {
        _showSettings.value = show
    }

    private fun getInitialMapUrl(): String {
        val configUrl = com.sipedas.ponorogo.BuildConfig.CCTV_URL
        val fallbackUrl = if (configUrl.isBlank() || configUrl == "dummy") "https://gasta.ponorogo.go.id/" else configUrl
        return prefs.getString("map_url", fallbackUrl) ?: fallbackUrl
    }

    private val _mapUrl = MutableStateFlow(getInitialMapUrl())
    val mapUrl: StateFlow<String> = _mapUrl.asStateFlow()

    private val _refreshMapEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshMapEvent = _refreshMapEvent.asSharedFlow()

    fun triggerMapRefresh() {
        _refreshMapEvent.tryEmit(Unit)
    }

    fun updateMapUrl(url: String) {
        _mapUrl.value = url
        prefs.edit().putString("map_url", url).apply()
    }
}
