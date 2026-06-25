package com.sipedas.ponorogo

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import com.sipedas.ponorogo.widget.SipedasReportWidget

class SipedasApplication : Application() {

    private var currentUiMode: Int = -1

    override fun onCreate() {
        super.onCreate()
        currentUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        
        // Delay the initial widget update on startup by 5000ms to allow the Android system 
        // and Launcher's AppWidgetHostView to fully complete package re-indexing and APK 
        // path caching on package replaced/reinstallation. This prevents "Failed to open APK" 
        // and "Package name not found" I/O errors during app updates.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateAllWidgets()
        }, 5000)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (currentUiMode != newUiMode) {
            currentUiMode = newUiMode
            updateAllWidgets()
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)

        // Update Report Widget directly
        val reportIds = appWidgetManager.getAppWidgetIds(ComponentName(this, SipedasReportWidget::class.java))
        for (id in reportIds) {
            SipedasReportWidget.updateAppWidget(this, appWidgetManager, id)
        }
    }
}
