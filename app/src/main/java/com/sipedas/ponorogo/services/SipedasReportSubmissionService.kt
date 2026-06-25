package com.sipedas.ponorogo.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.sipedas.ponorogo.data.SipedasDatabase
import com.sipedas.ponorogo.utils.GoogleDriveSheetHelper
import com.sipedas.ponorogo.parser.SipedasParser
import com.sipedas.ponorogo.widget.SipedasReportWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SipedasReportSubmissionService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val NOTIFICATION_ID = 2024
        private const val CHANNEL_ID = "sipedas_background_submission"
        private const val CHANNEL_NAME = "SIPEDAS Background Submission"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Mengirim Laporan")
            .setContentText("Menghubungi server...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            try {
                performReportSubmission()
            } catch (e: Exception) {
                Log.e("SIPEDAS_SERVICE", "Submission service run error: ${e.message}")
                showResultNotification(false, "Terjadi kesalahan: ${e.message}")
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun performReportSubmission() {
        val context = applicationContext
        val database = SipedasDatabase.getDatabase(context)
        val sipedasDao = database.sipedasDao()

        // 1. Fetch draft report
        val draft = sipedasDao.getDraftReportById("ACTIVE_SESSION_DRAFT")
        if (draft == null || draft.laporan.trim().isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Laporan kosong! Silakan tulis draf terlebih dahulu.", Toast.LENGTH_LONG).show()
            }
            showResultNotification(false, "Gagal mengirim: Draf laporan kosong.")
            return
        }

        // 2. Fetch photos
        val photos = sipedasDao.getPhotosForDraft("ACTIVE_SESSION_DRAFT")
        if (photos.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Gagal mengirim: Lampirkan minimal 1 foto dokumentasi.", Toast.LENGTH_LONG).show()
            }
            showResultNotification(false, "Gagal mengirim: Minimal 1 foto dokumentasi diperlukan.")
            return
        }

        // 3. Get preferences
        val prefs = context.getSharedPreferences("sipedas_prefs", Context.MODE_PRIVATE)
        val targetUrl = prefs.getString("server_url", com.sipedas.ponorogo.BuildConfig.APPS_SCRIPT_URL) ?: com.sipedas.ponorogo.BuildConfig.APPS_SCRIPT_URL
        val folderId = prefs.getString("drive_folder_id", com.sipedas.ponorogo.BuildConfig.GOOGLE_DRIVE_FOLDER_ID) ?: com.sipedas.ponorogo.BuildConfig.GOOGLE_DRIVE_FOLDER_ID
        val sheetId = prefs.getString("sheet_id", com.sipedas.ponorogo.BuildConfig.GOOGLE_SHEET_ID) ?: com.sipedas.ponorogo.BuildConfig.GOOGLE_SHEET_ID

        if (targetUrl.trim().isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Gagal mengirim: Server URL belum disetting.", Toast.LENGTH_LONG).show()
            }
            showResultNotification(false, "Gagal mengirim: Server URL belum dikonfigurasi.")
            return
        }

        // Parse report to get dynamic date/month label for fullFileName
        val parsedReport = SipedasParser.parseLaporan(draft.laporan)
        val rawDate = parsedReport.tanggal.trim()
        val dateLabel = if (rawDate.isNotEmpty()) rawDate else {
            try {
                SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date())
            } catch (e: Exception) {
                "Dokumentasi"
            }
        }

        updateNotificationProgress("Mengunggah foto...", 0, photos.size)

        val linkFotoArray = JSONArray()
        var remoteFolderUrl = ""
        var hasUploadError = false

        for (i in photos.indices) {
            val photo = photos[i]
            val file = File(photo.filePath)
            if (!file.exists()) {
                hasUploadError = true
                break
            }

            try {
                updateNotificationProgress("Mengunggah foto ${i + 1} dari ${photos.size}...", i, photos.size)

                val prefix = if (photo.source.equals("camera", ignoreCase = true)) "[KAMERA]" else "[GALERI]"
                val danruClean = parsedReport.namaDanru.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
                val danruSlug = if (danruClean.isNotEmpty()) "_$danruClean" else ""
                val suffix = if (photos.size <= 1) "" else "_${i + 1}"
                val ext = if (photo.mimeType.contains("png", ignoreCase = true)) ".png" else ".jpg"
                val fullFileName = "${prefix}_${dateLabel}${danruSlug}${suffix}${ext}"

                // Read bytes & Base64 encode
                val bytes = file.readBytes()
                val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mime = photo.mimeType.ifEmpty { "image/jpeg" }
                val base64Payload = "data:$mime;base64,$base64Str"

                val uploadResult = GoogleDriveSheetHelper.uploadFoto(
                    targetUrl = targetUrl,
                    base64Data = base64Payload,
                    mimeType = mime,
                    source = photo.source,
                    lat = photo.lat,
                    lng = photo.lng,
                    timestamp = photo.timestamp ?: "",
                    address = photo.address ?: "",
                    noFoto = i + 1,
                    jumlahTotal = photos.size,
                    reportText = draft.laporan,
                    folderId = folderId.trim()
                )

                val driveUrl = uploadResult.optString("linkFile") ?: uploadResult.optString("url") ?: ""
                if (driveUrl.isEmpty()) {
                    throw Exception("Gagal mengunggah foto ke-${i + 1}")
                }

                if (remoteFolderUrl.isEmpty()) {
                    remoteFolderUrl = uploadResult.optString("folderUrl") ?: ""
                }

                val uploadedPhotoObj = JSONObject().apply {
                    put("link", driveUrl)
                    put("namaFile", fullFileName)
                    put("source", photo.source)
                }
                linkFotoArray.put(uploadedPhotoObj)
            } catch (e: Exception) {
                Log.e("SIPEDAS_SERVICE", "Background upload failed for photo index $i: ${e.message}")
                hasUploadError = true
                break
            }
        }

        if (hasUploadError) {
            showResultNotification(false, "Gagal mengunggah foto dokumentasi.")
            return
        }

        if (remoteFolderUrl.isEmpty()) {
            remoteFolderUrl = "https://drive.google.com/drive/folders/${folderId.trim()}"
        }

        updateNotificationProgress("Menyimpan ke Spreadsheet...", photos.size, photos.size)

        try {
            val submitJson = GoogleDriveSheetHelper.submitLaporan(
                targetUrl = targetUrl,
                reportText = draft.laporan,
                linkFotoArray = linkFotoArray,
                remoteFolderUrl = remoteFolderUrl,
                draftId = "ACTIVE_SESSION_DRAFT",
                sheetId = sheetId.trim()
            )

            // Delete database and photos since it is successfully uploaded!
            photos.forEach { entity ->
                try {
                    val file = File(entity.filePath)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    Log.e("SIPEDAS_SERVICE", "Error deleting file: ${e.message}")
                }
            }
            sipedasDao.deleteDraftReportById("ACTIVE_SESSION_DRAFT")
            sipedasDao.deletePhotosByDraftId("ACTIVE_SESSION_DRAFT")

            // Broadcast widget update
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val reportComponentName = ComponentName(context, SipedasReportWidget::class.java)
            val reportIds = appWidgetManager.getAppWidgetIds(reportComponentName)
            if (reportIds.isNotEmpty()) {
                val widgetIntent = Intent(context, SipedasReportWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, reportIds)
                }
                context.sendBroadcast(widgetIntent)
            }

            showResultNotification(true, "Laporan patroli draf dari widget berhasil terkirim!")
        } catch (e: Exception) {
            Log.e("SIPEDAS_SERVICE", "Spreadsheet save failure: ${e.message}")
            showResultNotification(false, "Gagal menyimpan ke Google Spreadsheet.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun updateNotificationProgress(statusText: String, progress: Int, max: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Mengirim Laporan")
            .setContentText(statusText)
            .setProgress(max, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun showResultNotification(isSuccess: Boolean, message: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(NOTIFICATION_ID)

        val title = if (isSuccess) "Laporan Terkirim!" else "Pengiriman Gagal"
        val icon = if (isSuccess) android.R.drawable.ic_dialog_info else android.R.drawable.ic_dialog_alert

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
