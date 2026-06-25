package com.sipedas.ponorogo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sipedas.ponorogo.MainActivity
import com.sipedas.ponorogo.R
import com.sipedas.ponorogo.data.SipedasDatabase
import com.sipedas.ponorogo.services.SipedasReportSubmissionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SipedasReportWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Use Handler to delay the update slightly on package replaced
            // to allow the launcher's AppWidgetHostView to finish its re-indexing of the package
            // and avoid "Failed to open APK ... I/O error"
            val pendingResult = goAsync()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SipedasReportWidget::class.java))
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult?.finish()
                }
            }, 5000)
        } else if (intent.action == "com.sipedas.ponorogo.action.WIDGET_SEND") {
            val serviceIntent = Intent(context, SipedasReportSubmissionService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val database = SipedasDatabase.getDatabase(context.applicationContext)
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val draft = database.sipedasDao().getDraftReportById("ACTIVE_SESSION_DRAFT")
                val draftText = draft?.laporan ?: ""
                
                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_report)

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            views.setViewPadding(R.id.widget_report_container, 0, 0, 0, 0)
                        }

                        setupWidgetViews(context, views, draftText)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_report)
                        views.setTextViewText(R.id.txt_widget_draft_content, "Error: ${e.message}")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val database = SipedasDatabase.getDatabase(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val draft = database.sipedasDao().getDraftReportById("ACTIVE_SESSION_DRAFT")
                    val draftText = draft?.laporan ?: ""
                    
                    withContext(Dispatchers.Main) {
                        val views = RemoteViews(context.packageName, R.layout.widget_report)

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            // Android 12+ automatically applies padding and rounded corners
                            views.setViewPadding(R.id.widget_report_container, 0, 0, 0, 0)
                        }

                        setupWidgetViews(context, views, draftText)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        val views = RemoteViews(context.packageName, R.layout.widget_report)
                        views.setTextViewText(R.id.txt_widget_draft_content, "Error: ${e.message}")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            }
        }

        private fun setupWidgetViews(context: Context, views: RemoteViews, draftText: String) {
            if (draftText.trim().isNotEmpty()) {
                views.setTextViewText(R.id.txt_widget_draft_content, draftText)
                views.setTextViewText(R.id.widget_report_status, "Siap")
                views.setTextColor(R.id.widget_report_status, android.graphics.Color.parseColor("#10B981"))
                views.setTextColor(R.id.widget_report_status_dot, android.graphics.Color.parseColor("#10B981"))
            } else {
                views.setTextViewText(R.id.txt_widget_draft_content, "PATROLI SIPEDAS: Situasi aman, kondusif, dan terkendali di wilayah Ponorogo.")
                views.setTextViewText(R.id.widget_report_status, "Siap")
                views.setTextColor(R.id.widget_report_status, android.graphics.Color.parseColor("#10B981"))
                views.setTextColor(R.id.widget_report_status_dot, android.graphics.Color.parseColor("#10B981"))
            }

            // 1. Camera Click Intent
            val cameraIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("WIDGET_ACTION", "camera")
            }
            val cameraPendingIntent = PendingIntent.getActivity(
                context, 101, cameraIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_camera, cameraPendingIntent)

            // 2. Gallery Click Intent
            val galleryIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("WIDGET_ACTION", "gallery")
            }
            val galleryPendingIntent = PendingIntent.getActivity(
                context, 102, galleryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_gallery, galleryPendingIntent)

            // 3. Draft Container (Direct Typing) Click Intent
            val draftIntent = Intent(context, QuickDraftActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("AUTO_PASTE", false)
            }
            val draftPendingIntent = PendingIntent.getActivity(
                context, 105, draftIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_draft_container, draftPendingIntent)

            // 4. Paste/Tempel Click Intent
            val pasteIntent = Intent(context, QuickDraftActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("AUTO_PASTE", true)
            }
            val pastePendingIntent = PendingIntent.getActivity(
                context, 103, pasteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_paste, pastePendingIntent)
        }
    }
}
