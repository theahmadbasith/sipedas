package com.sipedas.ponorogo.viewmodel

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sipedas.ponorogo.data.DraftPhoto
import com.sipedas.ponorogo.data.DraftReport
import com.sipedas.ponorogo.data.SipedasRepository
import com.sipedas.ponorogo.model.PhotoItem
import com.sipedas.ponorogo.parser.SipedasParser
import com.sipedas.ponorogo.parser.ParsedReport
import com.sipedas.ponorogo.utils.MapHelpers
import com.sipedas.ponorogo.utils.NotificationHelper
import com.sipedas.ponorogo.utils.WatermarkHelper
import com.sipedas.ponorogo.utils.CloudinaryHelper
import com.sipedas.ponorogo.utils.FirebaseHelper
import com.sipedas.ponorogo.utils.GoogleDriveSheetHelper
import com.sipedas.ponorogo.widget.SipedasReportWidget
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Core ViewModel that handles SIPEDAS business logic.
 * Manages the data flow between the view layer and data/repository layers.
 */
class SipedasViewModel(
    private val app: Application,
    private val repository: SipedasRepository
) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("sipedas_prefs", Context.MODE_PRIVATE)

    // UI States - Features toggles
    private val _theme = MutableStateFlow(prefs.getString("theme", "dark") ?: "dark")
    val theme = _theme.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", true))
    val keepScreenOn = _keepScreenOn.asStateFlow()

    private val _wmCam = MutableStateFlow(prefs.getBoolean("wm_cam", true))
    val wmCam = _wmCam.asStateFlow()

    private val _wmGal = MutableStateFlow(prefs.getBoolean("wm_gal", false))
    val wmGal = _wmGal.asStateFlow()

    private val _wmTitle = MutableStateFlow(prefs.getString("wm_title", "SATLINMAS PEDESTRIAN") ?: "SATLINMAS PEDESTRIAN")
    val wmTitle = _wmTitle.asStateFlow()

    private val _wmDanruLabel = MutableStateFlow(prefs.getString("wm_danru_label", "Danru") ?: "Danru")
    val wmDanruLabel = _wmDanruLabel.asStateFlow()
    
    private val _wmType = MutableStateFlow(
        try { com.sipedas.ponorogo.utils.WatermarkType.valueOf(prefs.getString("wm_type", "DEFAULT") ?: "DEFAULT") } 
        catch (e: Exception) { com.sipedas.ponorogo.utils.WatermarkType.DEFAULT }
    )
    val wmType = _wmType.asStateFlow()

    private val _wmColor = MutableStateFlow(prefs.getString("wm_color", "#fff500") ?: "#fff500")
    val wmColor = _wmColor.asStateFlow()

    private val _wmIconUri = MutableStateFlow(prefs.getString("wm_icon_uri", null))
    val wmIconUri = _wmIconUri.asStateFlow()

    private val _wmSizeTitle = MutableStateFlow(prefs.getFloat("wm_size_title", 1.0f))
    val wmSizeTitle = _wmSizeTitle.asStateFlow()

    private val _wmSizeDate = MutableStateFlow(prefs.getFloat("wm_size_date", 1.0f))
    val wmSizeDate = _wmSizeDate.asStateFlow()

    private val _wmSizeLoc = MutableStateFlow(prefs.getFloat("wm_size_loc", 1.0f))
    val wmSizeLoc = _wmSizeLoc.asStateFlow()

    private val _wmSizeCoord = MutableStateFlow(prefs.getFloat("wm_size_coord", 1.0f))
    val wmSizeCoord = _wmSizeCoord.asStateFlow()

    private val _ocrGal = MutableStateFlow(prefs.getBoolean("ocr_gal", false))
    val ocrGal = _ocrGal.asStateFlow()

    private val _minimap = MutableStateFlow(prefs.getBoolean("minimap", true))
    val minimap = _minimap.asStateFlow()

    // UI States - Manual location fields
    private val _locJalan = MutableStateFlow(prefs.getString("loc_jalan", "") ?: "")
    val locJalan = _locJalan.asStateFlow()

    private val _locNoDukuh = MutableStateFlow(prefs.getString("loc_nodukuh", "") ?: "")
    val locNoDukuh = _locNoDukuh.asStateFlow()

    private val _locDesa = MutableStateFlow(prefs.getString("loc_desa", "") ?: "")
    val locDesa = _locDesa.asStateFlow()

    private val _locKec = MutableStateFlow(prefs.getString("loc_kec", "") ?: "")
    val locKec = _locKec.asStateFlow()

    private val _locKab = MutableStateFlow(prefs.getString("loc_kab", "Ponorogo") ?: "Ponorogo")
    val locKab = _locKab.asStateFlow()

    private val _locProv = MutableStateFlow(prefs.getString("loc_prov", "Jawa Timur") ?: "Jawa Timur")
    val locProv = _locProv.asStateFlow()

    private val _manualLat = MutableStateFlow(prefs.getString("manual_lat", "") ?: "")
    val manualLat = _manualLat.asStateFlow()

    private val _manualLng = MutableStateFlow(prefs.getString("manual_lng", "") ?: "")
    val manualLng = _manualLng.asStateFlow()

    private val _serverUrl = MutableStateFlow(prefs.getString("server_url", com.sipedas.ponorogo.BuildConfig.APPS_SCRIPT_URL) ?: com.sipedas.ponorogo.BuildConfig.APPS_SCRIPT_URL)
    val serverUrl = _serverUrl.asStateFlow()

    private val _driveFolderId = MutableStateFlow(prefs.getString("drive_folder_id", com.sipedas.ponorogo.BuildConfig.GOOGLE_DRIVE_FOLDER_ID) ?: com.sipedas.ponorogo.BuildConfig.GOOGLE_DRIVE_FOLDER_ID)
    val driveFolderId = _driveFolderId.asStateFlow()

    private val _sheetId = MutableStateFlow(prefs.getString("sheet_id", com.sipedas.ponorogo.BuildConfig.GOOGLE_SHEET_ID) ?: com.sipedas.ponorogo.BuildConfig.GOOGLE_SHEET_ID)
    val sheetId = _sheetId.asStateFlow()

    private val _submitSteps = MutableStateFlow<List<SubmitStep>>(emptyList())
    val submitSteps = _submitSteps.asStateFlow()

    private val _cctvUrl = MutableStateFlow(prefs.getString("cctv_url", com.sipedas.ponorogo.BuildConfig.CCTV_URL) ?: com.sipedas.ponorogo.BuildConfig.CCTV_URL)
    val cctvUrl = _cctvUrl.asStateFlow()

    private val _firebaseUrl = MutableStateFlow(prefs.getString("firebase_url", com.sipedas.ponorogo.BuildConfig.FIREBASE_DATABASE_URL) ?: com.sipedas.ponorogo.BuildConfig.FIREBASE_DATABASE_URL)
    val firebaseUrl = _firebaseUrl.asStateFlow()

    private val _cloudinaryCloud = MutableStateFlow(prefs.getString("cloudinary_cloud", com.sipedas.ponorogo.BuildConfig.CLOUDINARY_CLOUD_NAME) ?: com.sipedas.ponorogo.BuildConfig.CLOUDINARY_CLOUD_NAME)
    val cloudinaryCloud = _cloudinaryCloud.asStateFlow()

    private val _cloudinaryPreset = MutableStateFlow(prefs.getString("cloudinary_preset", "sipedas-preset") ?: "sipedas-preset")
    val cloudinaryPreset = _cloudinaryPreset.asStateFlow()

    private val _cloudinaryApiKey = MutableStateFlow(prefs.getString("cloudinary_api_key", com.sipedas.ponorogo.BuildConfig.CLOUDINARY_API_KEY) ?: com.sipedas.ponorogo.BuildConfig.CLOUDINARY_API_KEY)
    val cloudinaryApiKey = _cloudinaryApiKey.asStateFlow()

    private val _cloudinaryApiSecret = MutableStateFlow(prefs.getString("cloudinary_api_secret", com.sipedas.ponorogo.BuildConfig.CLOUDINARY_API_SECRET) ?: com.sipedas.ponorogo.BuildConfig.CLOUDINARY_API_SECRET)
    val cloudinaryApiSecret = _cloudinaryApiSecret.asStateFlow()

    private val _isBackingUpDraft = MutableStateFlow(false)
    val isBackingUpDraft = _isBackingUpDraft.asStateFlow()

    private val _onlineDraftsJson = MutableStateFlow<String>("[]")
    val onlineDraftsJson = _onlineDraftsJson.asStateFlow()

    private val _onlineAduanJson = MutableStateFlow<String>("[]")
    val onlineAduanJson = _onlineAduanJson.asStateFlow()

    private val _isLoadingAduan = MutableStateFlow(false)
    val isLoadingAduan = _isLoadingAduan.asStateFlow()
    
    private var lastAduanFetchTime = 0L

    // Main SIPEDAS active variables
    private val _reportText = MutableStateFlow("")
    val reportText = _reportText.asStateFlow()

    private val _photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val photos = _photos.asStateFlow()

    private val _activeDraftId = MutableStateFlow<String?>(null)
    val activeDraftId = _activeDraftId.asStateFlow()

    // Loading & Submitting flags
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    private val _isTransferring = MutableStateFlow(false)
    val isTransferring = _isTransferring.asStateFlow()

    private val _submitProgress = MutableStateFlow(0)
    val submitProgress = _submitProgress.asStateFlow()

    private val _submitProgressLabel = MutableStateFlow("")
    val submitProgressLabel = _submitProgressLabel.asStateFlow()

    private val _submitProgressTitle = MutableStateFlow("Mengirim Laporan")
    val submitProgressTitle = _submitProgressTitle.asStateFlow()

    private var progressJob: kotlinx.coroutines.Job? = null

    private fun startSmoothProgress(startPct: Int, endPct: Int, expectedDurationMs: Long = 4000L) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.Main) {
            val current = _submitProgress.value.coerceIn(startPct, endPct - 1)
            _submitProgress.value = current
            val range = (endPct - current).coerceAtLeast(1)
            val startTime = System.currentTimeMillis()
            var currentVal = current
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= expectedDurationMs) {
                    // Drift state: increment slowly by 1 every 1.5 seconds up to endPct so progress never looks frozen
                    if (currentVal < endPct) {
                        _submitProgress.value = currentVal
                        currentVal++
                    } else {
                        _submitProgress.value = endPct
                    }
                    kotlinx.coroutines.delay(1500L)
                } else {
                    val t = elapsed.toFloat() / expectedDurationMs.toFloat()
                    val easeOut = 1f - (1f - t) * (1f - t) * (1f - t)
                    val nextPct = current + (range * easeOut).toInt()
                    currentVal = nextPct.coerceIn(current, endPct - 1)
                    _submitProgress.value = currentVal
                    kotlinx.coroutines.delay(50L)
                }
            }
        }
    }

    private fun completeToProgress(targetPct: Int) {
        viewModelScope.launch(Dispatchers.Main) {
            progressJob?.cancel()
            _submitProgress.value = targetPct
        }
    }

    private val _isSyncingDrafts = MutableStateFlow(false)
    val isSyncingDrafts = _isSyncingDrafts.asStateFlow()

    private val _isAutoSaving = MutableStateFlow(false)
    val isAutoSaving = _isAutoSaving.asStateFlow()

    private val _lastAutoSavedTime = MutableStateFlow<String?>(null)
    val lastAutoSavedTime = _lastAutoSavedTime.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline = _isOnline.asStateFlow()

    // Flow for handling AppWidget intents in MainActivity
    private val _widgetAction = MutableStateFlow<Pair<String, String?>?>(null)
    val widgetAction = _widgetAction.asStateFlow()

    fun triggerWidgetAction(action: String, cctvLocation: String? = null) {
        _widgetAction.value = Pair(action, cctvLocation)
    }

    fun consumeWidgetAction() {
        _widgetAction.value = null
    }

    private var lastSavedText = ""
    private var lastSavedPhotosSize = 0

    // List of drafts from database
    val allLocalDrafts: StateFlow<List<DraftReport>> = repository.allDraftReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Fused Location Client
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(app)

    private var autoSaveJob: kotlinx.coroutines.Job? = null

    init {
        restoreActiveDraftOnLaunch()
        startAutoSaveLoop()
        startNetworkSyncObserver()
        fetchOnlineDrafts()
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            triggerBackgroundSync()
        }
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(8000)
            while (true) {
                try {
                    fetchOnlineAduan(force = true)
                } catch (e: Exception) {
                    Log.e("SIPEDAS", "Background periodic aduan check error: ${e.message}")
                }
                kotlinx.coroutines.delay(45000)
            }
        }
    }

    fun refreshActiveDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val draftId = "ACTIVE_SESSION_DRAFT"
                val report = repository.getDraftReportById(draftId)
                if (report != null) {
                    val photosEntities = repository.getPhotosForDraft(draftId)
                    val restoredPhotos = photosEntities.map { p ->
                        val pFile = File(p.filePath)
                        PhotoItem(
                            id = p.filePath.substringAfter("sipedas_active_").substringBefore(".jpg"),
                            path = p.filePath,
                            mimeType = p.mimeType,
                            sizeKB = if (pFile.exists()) pFile.length() / 1024 else 0,
                            isCompressed = true,
                            isWatermarked = true,
                            source = p.source,
                            timestamp = p.timestamp ?: "",
                            lat = p.lat,
                            lng = p.lng,
                            address = p.address ?: "",
                            isProcessing = false
                        )
                    }
                    withContext(Dispatchers.Main) {
                        _reportText.value = report.laporan
                        _photos.value = restoredPhotos
                        _activeDraftId.value = draftId
                        lastSavedText = report.laporan
                        lastSavedPhotosSize = restoredPhotos.size
                        _lastAutoSavedTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        Log.d("SIPEDAS", "Refreshed active draft from DB!")
                    }
                }
            } catch (e: Exception) {
                Log.e("SIPEDAS", "Error refreshing active draft: ${e.message}")
            }
        }
    }

    private fun restoreActiveDraftOnLaunch() {
        refreshActiveDraft()
    }

    fun triggerAutoSave() {
        val text = _reportText.value
        val photosList = _photos.value
        if (text.trim().isEmpty() && photosList.isEmpty()) {
            return
        }
        if (_isSubmitting.value || _isTransferring.value) return

        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500)
            autoSaveDraftInternal(text, photosList)
        }
    }

    private fun cleanupOrphanedActiveFiles(photosList: List<PhotoItem>) {
        try {
            val context = app.applicationContext
            val activeFilesSet = photosList.map { "sipedas_active_${it.id}.jpg" }.toSet()
            val filesDir = context.filesDir
            if (filesDir.exists() && filesDir.isDirectory) {
                filesDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name.startsWith("sipedas_active_") && name.endsWith(".jpg")) {
                        if (!activeFilesSet.contains(name)) {
                            file.delete()
                            Log.d("SIPEDAS", "Cleaned up orphaned active file: $name")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SIPEDAS", "Error cleaning active files: ${e.message}")
        }
    }

    fun setTheme(newTheme: String) {
        _theme.value = newTheme
        prefs.edit().putString("theme", newTheme).apply()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
    }

    fun setWmCam(enabled: Boolean) {
        _wmCam.value = enabled
        prefs.edit().putBoolean("wm_cam", enabled).apply()
    }

    fun setWmGal(enabled: Boolean) {
        _wmGal.value = enabled
        prefs.edit().putBoolean("wm_gal", enabled).apply()
    }

    fun setWmTitle(title: String) {
        _wmTitle.value = title
        prefs.edit().putString("wm_title", title).apply()
    }

    fun setWmDanruLabel(label: String) {
        _wmDanruLabel.value = label
        prefs.edit().putString("wm_danru_label", label).apply()
    }

    fun setWmType(type: com.sipedas.ponorogo.utils.WatermarkType) {
        _wmType.value = type
        prefs.edit().putString("wm_type", type.name).apply()
    }

    fun setWmColor(color: String) {
        _wmColor.value = color
        prefs.edit().putString("wm_color", color).apply()
    }

    fun setWmIconUri(uri: String?) {
        _wmIconUri.value = uri
        prefs.edit().putString("wm_icon_uri", uri).apply()
    }

    fun setWmSizeTitle(v: Float) {
        _wmSizeTitle.value = v
        prefs.edit().putFloat("wm_size_title", v).apply()
    }

    fun setWmSizeDate(v: Float) {
        _wmSizeDate.value = v
        prefs.edit().putFloat("wm_size_date", v).apply()
    }

    fun setWmSizeLoc(v: Float) {
        _wmSizeLoc.value = v
        prefs.edit().putFloat("wm_size_loc", v).apply()
    }

    fun setWmSizeCoord(v: Float) {
        _wmSizeCoord.value = v
        prefs.edit().putFloat("wm_size_coord", v).apply()
    }

    fun resetWmSizes() {
        setWmSizeTitle(1.0f)
        setWmSizeDate(1.0f)
        setWmSizeLoc(1.0f)
        setWmSizeCoord(1.0f)
    }

    fun setOcrGal(enabled: Boolean) {
        _ocrGal.value = enabled
        prefs.edit().putBoolean("ocr_gal", enabled).apply()
    }

    fun setMinimap(enabled: Boolean) {
        _minimap.value = enabled
        prefs.edit().putBoolean("minimap", enabled).apply()
    }

    fun setLocJalan(v: String) = updateLocField("loc_jalan", _locJalan, v)
    fun setLocNoDukuh(v: String) = updateLocField("loc_nodukuh", _locNoDukuh, v)
    fun setLocDesa(v: String) = updateLocField("loc_desa", _locDesa, v)
    fun setLocKec(v: String) = updateLocField("loc_kec", _locKec, v)
    fun setLocKab(v: String) = updateLocField("loc_kab", _locKab, v)
    fun setLocProv(v: String) = updateLocField("loc_prov", _locProv, v)

    fun setManualLat(v: String) {
        _manualLat.value = v
        prefs.edit().putString("manual_lat", v).apply()
    }

    fun setManualLng(v: String) {
        _manualLng.value = v
        prefs.edit().putString("manual_lng", v).apply()
    }

    fun setServerUrl(v: String) {
        _serverUrl.value = v
        prefs.edit().putString("server_url", v).apply()
    }

    fun setDriveFolderId(v: String) {
        _driveFolderId.value = v
        prefs.edit().putString("drive_folder_id", v).apply()
    }

    fun setSheetId(v: String) {
        _sheetId.value = v
        prefs.edit().putString("sheet_id", v).apply()
    }

    fun setCctvUrl(v: String) {
        _cctvUrl.value = v
        prefs.edit().putString("cctv_url", v).apply()
    }

    fun setFirebaseUrl(v: String) {
        _firebaseUrl.value = v
        prefs.edit().putString("firebase_url", v).apply()
    }

    fun setCloudinaryCloud(v: String) {
        _cloudinaryCloud.value = v
        prefs.edit().putString("cloudinary_cloud", v).apply()
    }

    fun setCloudinaryPreset(v: String) {
        _cloudinaryPreset.value = v
        prefs.edit().putString("cloudinary_preset", v).apply()
    }

    fun setCloudinaryApiKey(v: String) {
        _cloudinaryApiKey.value = v
        prefs.edit().putString("cloudinary_api_key", v).apply()
    }

    fun setCloudinaryApiSecret(v: String) {
        _cloudinaryApiSecret.value = v
        prefs.edit().putString("cloudinary_api_secret", v).apply()
    }

    private fun updateLocField(prefKey: String, stateFlow: MutableStateFlow<String>, value: String) {
        stateFlow.value = value
        prefs.edit().putString(prefKey, value).apply()
    }

    fun resetLocationFields() {
        setLocJalan("")
        setLocNoDukuh("")
        setLocDesa("")
        setLocKec("")
        setLocKab("Ponorogo")
        setLocProv("Jawa Timur")
        setManualLat("")
        setManualLng("")
        Toast.makeText(app, "Field lokasi manual berhasil direset", Toast.LENGTH_SHORT).show()
    }

    fun setReportTextValue(v: String) {
        _reportText.value = v
        triggerAutoSave()
    }

    fun clearAllReports() {
        _reportText.value = ""
        // Delete physical cached photos
        _photos.value.forEach { photo ->
            try {
                val file = File(photo.path)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                // ignore
            }
        }
        _photos.value = emptyList()
        _activeDraftId.value = null
        lastSavedText = ""
        lastSavedPhotosSize = 0
        _lastAutoSavedTime.value = null

        // Wipe local database entry for ACTIVE_SESSION_DRAFT
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteDraft("ACTIVE_SESSION_DRAFT")
                cleanupOrphanedActiveFiles(emptyList())
                updateWidgetExplicitly()
            } catch (e: Exception) {
                Log.e("SIPEDAS", "Error removing ACTIVE_SESSION_DRAFT on clear: ${e.message}")
            }
        }
    }

    fun movePhoto(fromIdx: Int, toIdx: Int) {
        val currentList = _photos.value.toMutableList()
        if (fromIdx in currentList.indices && toIdx in currentList.indices) {
            val item = currentList.removeAt(fromIdx)
            currentList.add(toIdx, item)
            _photos.value = currentList
        }
    }

    fun removePhoto(id: String) {
        val currentList = _photos.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            try {
                val file = File(currentList[index].path)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                // ignore
            }
            currentList.removeAt(index)
            _photos.value = currentList
            triggerAutoSave()
        }
    }

    fun downloadPhoto(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(path)
                if (!sourceFile.exists()) return@launch
                
                val context = app.applicationContext
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "SIPEDAS_${System.currentTimeMillis()}.jpg")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Sipedas")
                }
                
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        sourceFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Foto berhasil diunduh ke Galeri", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(app.applicationContext, "Gagal mengunduh foto", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Capture / Picker loading helper
    fun addLocalPhotos(uris: List<Uri>, source: String, isMirrored: Boolean = false) {
        viewModelScope.launch {
            val countBefore = _photos.value.size
            if (countBefore >= 20) {
                Toast.makeText(app, "Maksimal 20 foto dokumentasi", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Let's obtain the current location live if it's camera source
            var gpsLocation: Location? = null
            if (source == "camera") {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(app, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED || androidx.core.app.ActivityCompat.checkSelfPermission(app, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    gpsLocation = fetchLiveLocationAsync()
                }
            }

            uris.forEachIndexed { idx, uri ->
                if (_photos.value.size >= 20) return@forEachIndexed

                val tempId = "p-${System.currentTimeMillis()}-${idx}"
                // Add loading structural state
                val loadingItem = PhotoItem(
                    id = tempId,
                    path = "",
                    mimeType = "image/jpeg",
                    sizeKB = 0,
                    isCompressed = false,
                    isWatermarked = false,
                    source = source,
                    timestamp = "",
                    lat = null,
                    lng = null,
                    address = "",
                    isProcessing = true,
                    processingLabel = "Mengolah..."
                )
                _photos.value = _photos.value + loadingItem

                // Process on IO Dispatcher
                processAndWatermarkImageAsync(tempId, uri, source, gpsLocation, isMirrored)
            }
        }
    }

    private suspend fun fetchLiveLocationAsync(): Location? = withContext(Dispatchers.IO) {
        try {
            // Wait slightly or query Fused last location
            val task = fusedLocationClient.lastLocation
            while (!task.isComplete) {
                Thread.sleep(30)
            }
            if (task.isSuccessful) {
                val loc = task.result
                if (loc != null) {
                    val isMock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        loc.isMock
                    } else {
                        @Suppress("DEPRECATION")
                        loc.isFromMockProvider
                    }
                    if (isMock) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(app, "Mock Location (GPS Palsu) terdeteksi! Gunakan GPS asli.", Toast.LENGTH_LONG).show()
                        }
                        null
                    } else {
                        loc
                    }
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun processAndWatermarkImageAsync(itemId: String, uri: Uri, source: String, gpsLoc: Location?, isMirrored: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val context: Context = app.applicationContext
            try {
                // 1. Resolve photo coordinates
                var lat: Double? = null
                var lng: Double? = null
                var photoTime = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date()) + " WIB"

                // For gallery, try ML Kit OCR to read watermarked coordinates
                if (source == "gallery" && _ocrGal.value) {
                    try {
                        val inputImage = com.google.mlkit.vision.common.InputImage.fromFilePath(context, uri)
                        val textRecognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                        val textTask = textRecognizer.process(inputImage)
                        val mlText = textTask.await()
                        
                        // Look for coordinates pattern like Lat: -7.123 Long: 110.123
                        // Also matches standalone coordinates separated by comma
                        val regex = Regex("(-?\\d{1,2}\\.\\d{4,})[^-\\d]+(-?\\d{1,3}\\.\\d{4,})")
                        val match = regex.find(mlText.text)
                        if (match != null) {
                            lat = match.groupValues[1].toDoubleOrNull()
                            lng = match.groupValues[2].toDoubleOrNull()
                        }
                    } catch (e: Exception) {
                        Log.e("SIPEDAS", "OCR Error failed reading text: ${e.message}")
                    }
                }

                // If OCR didn't find coordinates, proceed to standard location logic
                if (lat == null || lng == null) {
                    if (source == "camera" && gpsLoc != null) {
                        // Use live GPS location (Camera uses live location first)
                        lat = gpsLoc.latitude
                        lng = gpsLoc.longitude
                    } else {
                        // Fallback to EXIF (Gallery naturally falls back to EXIF after OCR, Camera falls back to EXIF if GPS fails)
                        try {
                            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                            parcelFileDescriptor?.use { pfd ->
                                val exifInterface = ExifInterface(pfd.fileDescriptor)
                                val latLong = FloatArray(2)
                                if (exifInterface.getLatLong(latLong)) {
                                    lat = latLong[0].toDouble()
                                    lng = latLong[1].toDouble()
                                }
                                val exifDate = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID) ?: ""
                                if (exifDate.isNotEmpty()) photoTime = exifDate
                            }
                        } catch (e: Exception) {
                            Log.e("SIPEDAS", "EXIF Error: ${e.message}")
                        }
                    }
                }

                // If coordinates are still empty, take it from manual coords fallback
                if (lat == null && _manualLat.value.isNotEmpty()) {
                    lat = _manualLat.value.toDoubleOrNull()
                }
                if (lng == null && _manualLng.value.isNotEmpty()) {
                    lng = _manualLng.value.toDoubleOrNull()
                }

                var resolvedAddress = ""
                val finalLat = lat
                val finalLng = lng
                if (finalLat != null && finalLng != null) {
                    try {
                        @Suppress("DEPRECATION")
                        val geocoder = Geocoder(context, Locale("id", "ID"))
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(finalLat, finalLng, 1)
                        if (addresses != null && addresses.isNotEmpty()) {
                            resolvedAddress = com.sipedas.ponorogo.utils.MapHelpers.formatAddressDetailed(addresses[0])
                        }
                    } catch (e: Exception) {
                        Log.e("SIPEDAS", "Geocoder error: ${e.message}")
                    }
                }

                if (resolvedAddress.isEmpty()) {
                    if (finalLat != null && finalLng != null) {
                        resolvedAddress = String.format(Locale.US, "%.6f, %.6f", finalLat, finalLng)
                    } else {
                        resolvedAddress = "Lokasi tidak diketahui"
                    }
                }

                // 3. Decode & Watermark
                var inputStream = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                val maxDimension = 2000
                var sampleSize = 1
                if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / sampleSize) >= maxDimension && (halfWidth / sampleSize) >= maxDimension) {
                        sampleSize *= 2
                    }
                }

                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                
                inputStream = context.contentResolver.openInputStream(uri)
                val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                if (decodedBitmap == null) {
                    throw Exception("Gagal decode foto.")
                }
                
                var originalBitmap: Bitmap = decodedBitmap

                // Read EXIF orientation and apply to bitmap
                val pfdForExif = context.contentResolver.openFileDescriptor(uri, "r")
                pfdForExif?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val matrix = android.graphics.Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> {
                            matrix.postRotate(90f)
                            matrix.postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_TRANSVERSE -> {
                            matrix.postRotate(270f)
                            matrix.postScale(-1f, 1f)
                        }
                    }
                    if (!matrix.isIdentity) {
                        val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                        originalBitmap.recycle()
                        originalBitmap = rotatedBitmap
                    }
                }

                if (isMirrored) {
                    val mirrorMatrix = android.graphics.Matrix()
                    mirrorMatrix.postScale(-1f, 1f)
                    val flippedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, mirrorMatrix, true)
                    originalBitmap.recycle()
                    originalBitmap = flippedBitmap
                }

                // Scale first if too huge (similar to NextJS ProcessImage aspect scaling limit 2500)
                var scaledBitmap = originalBitmap
                val maxDim = 2000
                if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                    val w = originalBitmap.width
                    val h = originalBitmap.height
                    val nW: Int
                    val nH: Int
                    if (w > h) {
                        nW = maxDim
                        nH = (h * maxDim) / w
                    } else {
                        nH = maxDim
                        nW = (w * maxDim) / h
                    }
                    scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, nW, nH, true)
                    originalBitmap.recycle()
                }

                // Apply watermark if toggle feature is enabled
                val useWM = if (source == "camera") _wmCam.value else _wmGal.value
                val danruName = SipedasParser.parseLaporan(_reportText.value).namaDanru

                val watermarkedBitmap = if (useWM) {
                    WatermarkHelper.drawWatermark(
                        context = context,
                        src = scaledBitmap,
                        danru = danruName,
                        timeStr = photoTime,
                        address = resolvedAddress,
                        lat = lat,
                        lng = lng,
                        config = com.sipedas.ponorogo.utils.WatermarkConfig(
                            title = _wmTitle.value,
                            color = _wmColor.value,
                            iconUri = _wmIconUri.value,
                            danruLabel = _wmDanruLabel.value,
                            type = _wmType.value,
                            fontSizeTitle = _wmSizeTitle.value,
                            fontSizeDate = _wmSizeDate.value,
                            fontSizeLoc = _wmSizeLoc.value,
                            fontSizeCoord = _wmSizeCoord.value
                        )
                    )
                } else {
                    scaledBitmap
                }

                // 4. Save to local application internal directory cache folder
                val cacheDir = context.cacheDir
                val file = File(cacheDir, "sipedas_${itemId}.jpg")
                val outStream = FileOutputStream(file)
                watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                outStream.flush()
                outStream.close()

                val fileSizeKB = file.length() / 1024

                watermarkedBitmap.recycle()
                if (scaledBitmap != watermarkedBitmap) {
                    scaledBitmap.recycle()
                }

                // 5. Update state
                withContext(Dispatchers.Main) {
                    _photos.value = _photos.value.map { item ->
                        if (item.id == itemId) {
                            PhotoItem(
                                id = itemId,
                                path = file.absolutePath,
                                mimeType = "image/jpeg",
                                sizeKB = fileSizeKB,
                                isCompressed = scaleAndMarkCheck(originalBitmap, file),
                                isWatermarked = useWM,
                                source = source,
                                timestamp = photoTime,
                                lat = lat,
                                lng = lng,
                                address = resolvedAddress,
                                isProcessing = false
                            )
                        } else {
                            item
                        }
                    }
                    triggerAutoSave()
                }

            } catch (e: Throwable) {
                Log.e("SIPEDAS", "Error processing photo: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal memproses foto: ${e.message}", Toast.LENGTH_SHORT).show()
                    _photos.value = _photos.value.filter { it.id != itemId }
                }
            }
        }
    }

    private fun scaleAndMarkCheck(old: Bitmap, f: File): Boolean {
        return f.length() < 1000 * 1024
    }

    private fun buildManualLocationString(): String {
        val parts = ArrayList<String>()
        if (_locJalan.value.isNotEmpty()) {
            var j = _locJalan.value
            if (_locNoDukuh.value.isNotEmpty()) j += " / ${_locNoDukuh.value}"
            parts.add(j)
        } else if (_locNoDukuh.value.isNotEmpty()) {
            parts.add(_locNoDukuh.value)
        }
        if (_locDesa.value.isNotEmpty()) parts.add(_locDesa.value)
        if (_locKec.value.isNotEmpty()) parts.add("Kec. ${_locKec.value}")
        if (_locKab.value.isNotEmpty()) parts.add(_locKab.value)
        if (_locProv.value.isNotEmpty()) parts.add(_locProv.value)
        parts.add("Indonesia")
        return parts.joinToString(", ")
    }

    // ────────────────────────────────────────────────────────
    //  DRAFTS LOCAL SQLITE IMPLEMENTATION
    // ────────────────────────────────────────────────────────
    fun transferLaporanToCloud(context: Context) {
        if (_reportText.value.trim().isEmpty() && _photos.value.isEmpty()) {
            Toast.makeText(context, "Laporan masih kosong, tidak ada yang ditransfer.", Toast.LENGTH_SHORT).show()
            return
        }
        if (_photos.value.any { it.isProcessing }) {
            Toast.makeText(context, "Foto masih diproses, tunggu sebentar.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!_isOnline.value) {
            Toast.makeText(context, "Tidak ada koneksi internet. Silakan hubungkan internet terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }
        if (_isTransferring.value || _isSubmitting.value) {
            return
        }

        val firebaseUrlStr = _firebaseUrl.value.trim()
        if (firebaseUrlStr.isEmpty()) {
            Toast.makeText(context, "URL database cloud belum dikonfigurasi.", Toast.LENGTH_LONG).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val activePhotos = _photos.value
            val total = activePhotos.size
            val cloud = _cloudinaryCloud.value.trim()
            val preset = _cloudinaryPreset.value.trim()
            val draftId = "DRAFT-${System.currentTimeMillis()}"

            try {
                withContext(Dispatchers.Main) {
                    _isTransferring.value = true
                    _submitProgressLabel.value = "Menghubungi server cloud..."
                    startSmoothProgress(0, 10, 3000L)
                }

                // 1. Convert photo items to DraftPhoto objects to upload
                val localPhotos = activePhotos.mapIndexed { idx, photo ->
                    DraftPhoto(
                        draftId = draftId,
                        filePath = photo.path,
                        mimeType = photo.mimeType.ifEmpty { "image/jpeg" },
                        source = photo.source,
                        lat = photo.lat,
                        lng = photo.lng,
                        address = photo.address,
                        timestamp = photo.timestamp,
                        orderIdx = idx
                    )
                }

                // 2. Upload photos to cloud one by one, updating progress
                val uploadedUrls = mutableListOf<String>()
                localPhotos.forEachIndexed { idx, p ->
                    val startPhase = 10 + Math.round((idx.toFloat() / total.toFloat()) * 70f)
                    val endPhase = 10 + Math.round(((idx + 1).toFloat() / total.toFloat()) * 70f)
                    withContext(Dispatchers.Main) {
                        _submitProgressLabel.value = "Mentransfer dokumentasi ${idx + 1}/$total ke cloud..."
                        startSmoothProgress(startPhase, endPhase, 10000L)
                    }

                    val file = File(p.filePath)
                    val url = if (file.exists()) {
                        CloudinaryHelper.uploadPhotoToCloudinary(
                            photoFile = file,
                            cloudName = cloud,
                            uploadPreset = preset,
                            apiKey = _cloudinaryApiKey.value,
                            apiSecret = _cloudinaryApiSecret.value
                        )
                    } else {
                        null
                    } ?: "https://images.unsplash.com/photo-1541963463532-d68292c34b19?w=500&auto=format&fit=crop"
                    
                    uploadedUrls.add(url)
                    withContext(Dispatchers.Main) {
                        completeToProgress(endPhase)
                    }
                }

                // 3. Save report and uploaded photo URLs to cloud database
                withContext(Dispatchers.Main) {
                    _submitProgressLabel.value = "Menyimpan draf di database..."
                    startSmoothProgress(80, 100, 8000L)
                }

                val trimmed = firebaseUrlStr.trim()
                val qIdx = trimmed.indexOf('?')
                val urlWithoutQuery = if (qIdx != -1) trimmed.substring(0, qIdx) else trimmed
                val queryStr = if (qIdx != -1) trimmed.substring(qIdx) else ""
                val cleanUrl = if (urlWithoutQuery.endsWith("/")) urlWithoutQuery else "$urlWithoutQuery/"
                val targetUrl = "${cleanUrl}drafts/$draftId.json$queryStr"

                val jsonPhotos = JSONArray()
                localPhotos.forEachIndexed { idx, p ->
                    val cloudUrl = if (idx < uploadedUrls.size) uploadedUrls[idx] else ""
                    val item = JSONObject().apply {
                        put("filePath", p.filePath)
                        put("cloudinaryUrl", cloudUrl)
                        put("mimeType", p.mimeType)
                        put("source", p.source)
                        put("lat", p.lat ?: JSONObject.NULL)
                        put("lng", p.lng ?: JSONObject.NULL)
                        put("address", p.address ?: "")
                        put("timestamp", p.timestamp ?: "")
                    }
                    jsonPhotos.put(item)
                }

                val parsed = SipedasParser.parseLaporan(_reportText.value)
                val timeStr = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                val payload = JSONObject().apply {
                    put("id", draftId)
                    put("laporan", _reportText.value)
                    put("danru", parsed.namaDanru.ifEmpty { "—" })
                    put("timestamp", timeStr)
                    put("photos", jsonPhotos)
                    put("parsed", JSONObject().apply {
                        put("nomorSPT", parsed.nomorSPT)
                        put("lokasi", parsed.lokasi)
                        put("hari", parsed.hari)
                        put("tanggal", parsed.tanggal)
                        put("identitas", parsed.identitas)
                        put("personil", parsed.personil)
                        put("danru", parsed.danru)
                        put("namaDanru", parsed.namaDanru)
                        put("keterangan", parsed.keterangan)
                    })
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                val requestBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(targetUrl)
                    .put(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("Gagal menyimpan ke database cloud (HTTP ${response.code})")
                }

                // Refresh online drafts list
                fetchOnlineDrafts()

                withContext(Dispatchers.Main) {
                    completeToProgress(100)
                    _submitProgressLabel.value = "Transfer Selesai!"
                    _isTransferring.value = false

                    // Remove local active session database entry & cached photos since it's successfully transferred
                    _activeDraftId.value = draftId
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            repository.deleteDraft("ACTIVE_SESSION_DRAFT")
                            cleanupOrphanedActiveFiles(emptyList())
                            updateWidgetExplicitly()
                        } catch (e: Exception) {
                            Log.e("SIPEDAS", "Error removing ACTIVE_SESSION_DRAFT: ${e.message}")
                        }
                    }
                    clearAllReports()

                    Toast.makeText(context, "Draf laporan berhasil ditransfer ke cloud! ☁️🚀", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("SIPEDAS", "Transfer cloud error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _isTransferring.value = false
                    val errMsg = e.message ?: ""
                    val userFriendlyMsg = if (errMsg.contains("Cloudinary", ignoreCase = true)) {
                        if (errMsg.contains("401") || errMsg.contains("400")) {
                            "Cloudinary Gagal (HTTP 401/400)! Silakan jadikan Upload Preset Anda 'Unsigned' di Cloudinary Settings, atau periksa kembali API Key/Secret Anda."
                        } else {
                            "Gagal upload ke Cloudinary: $errMsg"
                        }
                    } else if (errMsg.contains("401") || errMsg.contains("403") || errMsg.contains("Unauthorized", ignoreCase = true) || errMsg.contains("database cloud", ignoreCase = true)) {
                        "Firebase Gagal (HTTP 401 Unauthorized)! Silakan ubah Rules di Firebase RTDB Console draf Anda agar '.read': true dan '.write': true."
                    } else {
                        "Gagal transfer draf: $errMsg"
                    }
                    Toast.makeText(context, userFriendlyMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadLocalDraft(draftId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = repository.getDraftReportById(draftId) ?: return@launch
                val photosEntities = repository.getPhotosForDraft(draftId)

                // Copy draft permanent files to cache dir
                val context = app.applicationContext
                val loadedPhotos = photosEntities.map { entity ->
                    val sourceFile = File(entity.filePath)
                    val cacheFile = File(context.cacheDir, "sipedas_cache_${UUID.randomUUID()}.jpg")
                    if (sourceFile.exists()) {
                        sourceFile.copyTo(cacheFile, overwrite = true)
                    }

                    PhotoItem(
                        id = "p-${System.currentTimeMillis()}-${entity.id}",
                        path = cacheFile.absolutePath,
                        mimeType = entity.mimeType,
                        sizeKB = cacheFile.length() / 1024,
                        isCompressed = cacheFile.length() < 1000 * 1024,
                        isWatermarked = true,
                        source = entity.source,
                        timestamp = entity.timestamp ?: "",
                        lat = entity.lat,
                        lng = entity.lng,
                        address = entity.address ?: "",
                        isProcessing = false
                    )
                }

                withContext(Dispatchers.Main) {
                    _reportText.value = report.laporan
                    _photos.value = loadedPhotos
                    _activeDraftId.value = draftId
                    lastSavedText = report.laporan
                    lastSavedPhotosSize = loadedPhotos.size
                    _lastAutoSavedTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    Toast.makeText(app, "Draf berhasil dimuat!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("SIPEDAS", "Error loading draft: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Gagal memuat draf: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteLocalDraft(draftId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete physical permanent files
                val photos = repository.getPhotosForDraft(draftId)
                photos.forEach { entity ->
                    try {
                        val file = File(entity.filePath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {}
                }

                repository.deleteDraft(draftId)

                withContext(Dispatchers.Main) {
                    if (_activeDraftId.value == draftId) {
                        _activeDraftId.value = null
                    }
                    Toast.makeText(app, "Draf berhasil dihapus", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SIPEDAS", "Error deleting draft: ${e.message}")
            }
        }
    }


    fun fetchOnlineDrafts() {
        val firebaseUrlStr = _firebaseUrl.value.trim()
        if (firebaseUrlStr.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val draftsJson = FirebaseHelper.fetchOnlineDrafts(firebaseUrlStr)
                _onlineDraftsJson.value = draftsJson
            } catch (e: Exception) {
                Log.e("SIPEDAS", "Error fetching online drafts: ${e.message}")
            }
        }
    }

    fun fetchOnlineAduan(force: Boolean = false) {
        if (!force && _onlineAduanJson.value != "[]" && (System.currentTimeMillis() - lastAduanFetchTime) < 60000) {
            // Use cache if less than 1 minute old and not explicitly forced
            return
        }

        val firebaseUrlStr = _firebaseUrl.value.trim()
        if (firebaseUrlStr.isEmpty()) return

        if (_isLoadingAduan.value) return
        _isLoadingAduan.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val aduanJson = FirebaseHelper.fetchOnlineAduan(firebaseUrlStr)
                _onlineAduanJson.value = aduanJson
                lastAduanFetchTime = System.currentTimeMillis()
                
                // Inspect fetched contents to trigger notifications for newly added reports
                checkForNewAduanNotifications(aduanJson)
            } catch (e: Exception) {
                Log.e("SIPEDAS", "Error fetching online aduan: ${e.message}")
            } finally {
                _isLoadingAduan.value = false
            }
        }
    }

    private fun checkForNewAduanNotifications(newAduanJson: String) {
        try {
            val jsonArray = JSONArray(newAduanJson)
            if (jsonArray.length() == 0) return

            val savedSeenSet = prefs.getStringSet("seen_aduan_fingerprints", emptySet()) ?: emptySet()
            val seenFingerprints = HashSet(savedSeenSet)

            val newFingerprints = mutableListOf<String>()
            val newAduansToNotify = mutableListOf<JSONObject>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                
                // compute fingerprint
                val id = obj.optString("id").takeIf { it.isNotEmpty() }
                val timestamp = obj.optString("timestamp").takeIf { it.isNotEmpty() } ?: obj.optString("waktu").takeIf { it.isNotEmpty() } ?: ""
                val laporan = obj.optString("aduan").takeIf { it.isNotEmpty() } ?: obj.optString("laporan").takeIf { it.isNotEmpty() } ?: obj.optString("deskripsi").takeIf { it.isNotEmpty() } ?: ""
                val fingerprint = id ?: "${timestamp}_${laporan.hashCode()}"

                if (fingerprint.isNotEmpty()) {
                    newFingerprints.add(fingerprint)
                    if (!seenFingerprints.contains(fingerprint)) {
                        newAduansToNotify.add(obj)
                    }
                }
            }

            val isFirstFetch = seenFingerprints.isEmpty()

            if (isFirstFetch) {
                // Silently populate all as seen to prevent back-alerting old legacy entries
                val editor = prefs.edit()
                editor.putStringSet("seen_aduan_fingerprints", HashSet(newFingerprints))
                editor.apply()
                
                // Alert about the very latest aduan on first launch if list is not empty, for immediate verification!
                if (newAduansToNotify.isNotEmpty()) {
                    val latest = newAduansToNotify.sortedByDescending { it.optString("timestamp", "") }.first()
                    val name = latest.optString("nama").takeIf { it.isNotEmpty() } ?: latest.optString("pelapor").takeIf { it.isNotEmpty() } ?: "Satgas"
                    val deskripsi = latest.optString("aduan").takeIf { it.isNotEmpty() } ?: latest.optString("laporan").takeIf { it.isNotEmpty() } ?: latest.optString("deskripsi").takeIf { it.isNotEmpty() } ?: "Aduan Baru"
                    val truncatedDesk = if (deskripsi.length > 60) deskripsi.take(60) + "..." else deskripsi
                    
                    com.sipedas.ponorogo.utils.NotificationHelper.showSuccessNotification(
                        app,
                        "Aduan Baru Masuk: $name",
                        truncatedDesk
                    )
                }
            } else if (newAduansToNotify.isNotEmpty()) {
                // Notify actual new incoming items!
                newAduansToNotify.forEach { latest ->
                    val name = latest.optString("nama").takeIf { it.isNotEmpty() } ?: latest.optString("pelapor").takeIf { it.isNotEmpty() } ?: "Satgas"
                    val deskripsi = latest.optString("aduan").takeIf { it.isNotEmpty() } ?: latest.optString("laporan").takeIf { it.isNotEmpty() } ?: latest.optString("deskripsi").takeIf { it.isNotEmpty() } ?: "Aduan Baru"
                    val truncatedDesk = if (deskripsi.length > 60) deskripsi.take(60) + "..." else deskripsi
                    
                    com.sipedas.ponorogo.utils.NotificationHelper.showSuccessNotification(
                        app,
                        "Aduan Baru Masuk: $name",
                        truncatedDesk
                    )
                }

                val updatedSeenSet = HashSet(seenFingerprints)
                updatedSeenSet.addAll(newFingerprints)
                prefs.edit().putStringSet("seen_aduan_fingerprints", updatedSeenSet).apply()
            }
        } catch (e: Exception) {
            Log.e("SIPEDAS", "Error checking for new aduan notification: ${e.message}")
        }
    }

    fun loadOnlineDraft(draftObj: JSONObject) {
        viewModelScope.launch {
            try {
                _submitProgressTitle.value = "Memuat Laporan Regu"
                _isSubmitting.value = true
                _submitProgressLabel.value = "Menghubungkan Cloud..."
                startSmoothProgress(0, 10, 3000L)

                val text = draftObj.optString("laporan", "")
                val draftId = draftObj.optString("id", "")
                val jsonPhotos = draftObj.optJSONArray("photos") ?: JSONArray()

                val downloadedPhotos = mutableListOf<PhotoItem>()
                val numPhotos = jsonPhotos.length()

                for (i in 0 until numPhotos) {
                    val startPhase = 10 + Math.round((i.toFloat() / numPhotos.toFloat()) * 80f)
                    val endPhase = 10 + Math.round(((i + 1).toFloat() / numPhotos.toFloat()) * 80f)
                    _submitProgressLabel.value = "Mengunduh foto ${i + 1} dari $numPhotos..."
                    startSmoothProgress(startPhase, endPhase, 10000L)

                    val photoObj = jsonPhotos.optJSONObject(i) ?: continue
                    val cloudinaryUrl = photoObj.optString("cloudinaryUrl", "")
                    
                    val localPath = if (cloudinaryUrl.isNotEmpty() && cloudinaryUrl.startsWith("http")) {
                        FirebaseHelper.downloadFileToCache(cloudinaryUrl, app.cacheDir)
                    } else {
                        photoObj.optString("filePath", "")
                    }

                    if (!localPath.isNullOrEmpty() && File(localPath).exists()) {
                        val fileObj = File(localPath)
                        downloadedPhotos.add(
                            PhotoItem(
                                id = "p-online-${System.currentTimeMillis()}-$i",
                                path = localPath,
                                mimeType = photoObj.optString("mimeType", "image/jpeg"),
                                sizeKB = fileObj.length() / 1024,
                                isCompressed = fileObj.length() < 1000 * 1024,
                                isWatermarked = true,
                                source = photoObj.optString("source", "KAMERA"),
                                timestamp = photoObj.optString("timestamp", ""),
                                lat = if (photoObj.isNull("lat")) null else photoObj.optDouble("lat"),
                                lng = if (photoObj.isNull("lng")) null else photoObj.optDouble("lng"),
                                address = photoObj.optString("address", ""),
                                isProcessing = false
                            )
                        )
                    }
                    completeToProgress(endPhase)
                }

                completeToProgress(100)
                
                // Intelligently merge report text or set if empty
                if (_reportText.value.trim().isEmpty()) {
                    _reportText.value = text
                } else if (text.isNotEmpty() && !_reportText.value.contains(text)) {
                    _reportText.value = _reportText.value + "\n" + text
                }

                // Merge downloaded draft photos directly with all current photos, ensuring no duplicates by path (menumpuk!)
                val mergedPhotos = (_photos.value + downloadedPhotos).distinctBy { it.path }.take(20)
                _photos.value = mergedPhotos
                _activeDraftId.value = draftId
                lastSavedText = _reportText.value
                lastSavedPhotosSize = mergedPhotos.size
                triggerAutoSave()

                Toast.makeText(app, "Draf Online berhasil dimuat! ☁️", Toast.LENGTH_SHORT).show()

                // Automatically delete loaded draft from Firebase and Cloudinary to prevent clutter/overcrowding
                val fireUrl = _firebaseUrl.value.trim()
                val clCloud = _cloudinaryCloud.value.trim()
                val clKey = _cloudinaryApiKey.value.trim()
                val clSecret = _cloudinaryApiSecret.value.trim()

                if (draftId.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            // 1. Delete photo assets from Cloudinary
                            for (i in 0 until numPhotos) {
                                val photoObj = jsonPhotos.optJSONObject(i) ?: continue
                                val cloudinaryUrl = photoObj.optString("cloudinaryUrl", "")
                                if (cloudinaryUrl.isNotEmpty() && cloudinaryUrl.startsWith("http")) {
                                    Log.d("SIPEDAS", "Auto-deleting loaded Cloudinary asset: $cloudinaryUrl")
                                    com.sipedas.ponorogo.utils.CloudinaryHelper.deletePhoto(
                                        url = cloudinaryUrl,
                                        cloudName = clCloud,
                                        apiKey = clKey,
                                        apiSecret = clSecret
                                    )
                                }
                            }

                            // 2. Delete the draft from Firebase RTDB
                            if (fireUrl.isNotEmpty()) {
                                Log.d("SIPEDAS", "Auto-deleting loaded draft from Firebase: $draftId")
                                FirebaseHelper.deleteOnlineDraft(draftId, fireUrl)
                                fetchOnlineDrafts() // Refresh list
                            }
                        } catch (e: Exception) {
                            Log.e("SIPEDAS", "Error during automatic online draft cleanup: ${e.message}")
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("SIPEDAS", "Gagal memuat draf online: ${e.message}")
                Toast.makeText(app, "Gagal memuat draf online: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun deleteOnlineDraft(draftId: String) {
        val firebaseUrlStr = _firebaseUrl.value.trim()
        if (firebaseUrlStr.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = FirebaseHelper.deleteOnlineDraft(draftId, firebaseUrlStr)
                if (success) {
                    Log.d("SIPEDAS", "Draft deleted from Firebase RTDB")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "Draf Online berhasil dihapus dari cloud!", Toast.LENGTH_SHORT).show()
                    }
                    fetchOnlineDrafts()
                }
            } catch (e: Exception) {
                Log.e("SIPEDAS", "Error deleting online draft: ${e.message}")
            }
        }
    }

    // ────────────────────────────────────────────────────────
    //  SUBMISSION ENGINE (SERVER HTTP POST ENGINES & WHATSAPP)
    // ────────────────────────────────────────────────────────
    fun submitLaporan(context: Context) {
        if (_reportText.value.trim().isEmpty()) {
            Toast.makeText(context, "Teks laporan kosong! Tempel dari WhatsApp.", Toast.LENGTH_SHORT).show()
            return
        }
        if (_photos.value.isEmpty()) {
            Toast.makeText(context, "Lampirkan minimal 1 foto dokumentasi.", Toast.LENGTH_SHORT).show()
            return
        }
        if (_photos.value.any { it.isProcessing }) {
            Toast.makeText(context, "Foto masih diproses, tunggu sebentar.", Toast.LENGTH_SHORT).show()
            return
        }

        // If serverUrl is NOT configured, prompt graceful WhatsApp sharing as standard Satlinmas workflow!
        if (_serverUrl.value.trim().isEmpty()) {
            Toast.makeText(context, "Server URL belum disetting. Membuka menu bagikan...", Toast.LENGTH_SHORT).show()
            shareViaWhatsApp(context)
            return
        }

        // Run full network sync
        viewModelScope.launch(Dispatchers.IO) {
            val targetUrl = _serverUrl.value.trim()
            val activePhotos = _photos.value
            val total = activePhotos.size
            val linkFotoArray = JSONArray()
            var remoteFolderUrl = ""

            try {
                withContext(Dispatchers.Main) {
                    _submitProgressTitle.value = "Mengirim Laporan"
                    _isSubmitting.value = true
                    _submitProgressLabel.value = "Menghubungi server..."
                    startSmoothProgress(0, 10, 3000L)
                }

                // Parse report data to get custom filename & folder
                val parsedReport = SipedasParser.parseLaporan(_reportText.value)
                val rawDate = parsedReport.tanggal?.trim() ?: ""
                val dateLabel = if (rawDate.isNotEmpty()) rawDate else {
                    try {
                        java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.forLanguageTag("id-ID")).format(java.util.Date())
                    } catch (e: Exception) {
                        "Dokumentasi"
                    }
                }
                
                val parts = dateLabel.split(" ")
                val monthLabel = if (parts.size >= 3) "${parts[1]} ${parts[2]}" else {
                    try {
                        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.forLanguageTag("id-ID")).format(java.util.Date())
                    } catch (e: Exception) {
                        "Dokumentasi"
                    }
                }

                val folderPath = "SIPEDAS/${monthLabel}/${dateLabel}"

                // Simulate metadata auth delay momentarily for real visual feedback
                kotlinx.coroutines.delay(400)

                // Upload each photo sequentially directly to Google Drive via GAS/Proxy
                for (i in 0 until total) {
                    val startPhase = 10 + Math.round((i.toFloat() / total.toFloat()) * 70f)
                    val endPhase = 10 + Math.round(((i + 1).toFloat() / total.toFloat()) * 70f)
                    withContext(Dispatchers.Main) {
                        _submitProgressLabel.value = "Mengunggah foto ${i + 1} dari $total ke server..."
                        startSmoothProgress(startPhase, endPhase, 12000L)
                    }

                    val photo = activePhotos[i]
                    val file = File(photo.path)
                    if (!file.exists()) {
                        throw Exception("Berkas foto ke-${i + 1} tidak ditemukan.")
                    }

                    // Build dynamic filename rules
                    val prefix = if (photo.source.equals("camera", ignoreCase = true)) "[KAMERA]" else "[GALERI]"
                    val danruClean = parsedReport.namaDanru?.replace(Regex("[^a-zA-Z0-9]"), "_")?.take(20) ?: ""
                    val danruSlug = if (danruClean.isNotEmpty()) "_$danruClean" else ""
                    val suffix = if (total <= 1) "" else "_${i + 1}"
                    val ext = if (photo.mimeType.contains("png", ignoreCase = true)) ".png" else ".jpg"
                    val fullFileName = "${prefix}_${dateLabel}${danruSlug}${suffix}${ext}"

                    // Convert file bytes to base64 payload
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
                        timestamp = photo.timestamp,
                        address = photo.address,
                        noFoto = i + 1,
                        jumlahTotal = total,
                        reportText = _reportText.value,
                        folderId = _driveFolderId.value.trim()
                    )

                    val driveUrl = uploadResult.optString("linkFile") ?: uploadResult.optString("url") ?: ""
                    if (driveUrl.isEmpty()) {
                        throw Exception("Gagal mengunggah foto ke-${i + 1} ke server.")
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
                    withContext(Dispatchers.Main) {
                        completeToProgress(endPhase)
                    }
                }

                if (remoteFolderUrl.isEmpty()) {
                    remoteFolderUrl = "https://drive.google.com/drive/folders/${_driveFolderId.value.trim()}"
                }

                // Call submitLaporan Spreadsheet save helper delegation
                withContext(Dispatchers.Main) {
                    _submitProgressLabel.value = "Menyimpan ke Spreadsheet..."
                    startSmoothProgress(80, 100, 12000L)
                }

                val submitJson = GoogleDriveSheetHelper.submitLaporan(
                    targetUrl = targetUrl,
                    reportText = _reportText.value,
                    linkFotoArray = linkFotoArray,
                    remoteFolderUrl = remoteFolderUrl,
                    draftId = _activeDraftId.value ?: "",
                    sheetId = _sheetId.value.trim()
                )

                // Optional tiny delay to let user digest the success visual state of spreadsheet save
                kotlinx.coroutines.delay(400)

                withContext(Dispatchers.Main) {
                    completeToProgress(100)
                    _submitProgressLabel.value = "Selesai!"
                    _isSubmitting.value = false

                    // Success actions!
                    val activeDraft = _activeDraftId.value
                    if (activeDraft != null) {
                        deleteLocalDraft(activeDraft)
                    }
                    clearAllReports()

                    com.sipedas.ponorogo.utils.NotificationHelper.showSuccessNotification(context, "SIPEDAS", "Laporan patroli pedestrian berhasil terkirim!")
                    Toast.makeText(context, "Laporan berhasil ditransfer ke server! 🚀", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("SIPEDAS", "Submit error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _isSubmitting.value = false
                    Toast.makeText(context, "Gagal kirim laporan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun shareViaWhatsApp(context: Context) {
        viewModelScope.launch {
            if (_reportText.value.trim().isEmpty()) {
                Toast.makeText(context, "Teks laporan kosong!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                // Prepare send multiple intent to WhatsApp
                val imageUris = ArrayList<Uri>()
                _photos.value.forEach { photoItem ->
                    val file = File(photoItem.path)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        imageUris.add(uri)
                    }
                }

                val shareIntent = Intent().apply {
                    action = if (imageUris.isNotEmpty()) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_TEXT, _reportText.value)
                    if (imageUris.isNotEmpty()) {
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
                    }
                    putExtra("jid", "status@broadcast") // WhatsApp feature
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, "Kirim Laporan SIPEDAS"))

            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startAutoSaveLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            // Wait for 10 seconds, then check and save
            while (true) {
                kotlinx.coroutines.delay(10000) // 10 seconds interval
                val text = _reportText.value
                val photosList = _photos.value
                
                // Only auto-save if something has changed and isn't empty
                if (text.trim().isNotEmpty() || photosList.isNotEmpty()) {
                    if (text != lastSavedText || photosList.size != lastSavedPhotosSize) {
                        autoSaveDraftInternal(text, photosList)
                    }
                }
            }
        }
    }

    private suspend fun autoSaveDraftInternal(laporanText: String, photosList: List<PhotoItem>) {
        if (laporanText.trim().isEmpty() && photosList.isEmpty()) {
            return
        }
        if (_isSubmitting.value) return // Don't auto-save while uploading/submitting!

        try {
            _isAutoSaving.value = true
            val draftId = "ACTIVE_SESSION_DRAFT"
            val parsed = SipedasParser.parseLaporan(laporanText)
            val timeStr = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())

            val reportEntity = DraftReport(
                id = draftId,
                laporan = laporanText,
                danru = parsed.namaDanru.ifEmpty { "—" },
                timestamp = timeStr
            )

            // Copy active cache photos to permanent draft photos files with fixed names to avoid leaks
            val context = app.applicationContext
            val savedPhotos = photosList.mapIndexed { idx, photo ->
                val sourceFile = File(photo.path)
                val destinationFile = File(context.filesDir, "sipedas_active_${photo.id}.jpg")
                if (sourceFile.exists() && sourceFile != destinationFile) {
                    try {
                        sourceFile.copyTo(destinationFile, overwrite = true)
                    } catch (e: Exception) {
                        Log.e("SIPEDAS", "Error copying auto-save file: ${e.message}")
                    }
                }

                DraftPhoto(
                    draftId = draftId,
                    filePath = destinationFile.absolutePath,
                    mimeType = photo.mimeType.ifEmpty { "image/jpeg" },
                    source = photo.source,
                    lat = photo.lat,
                    lng = photo.lng,
                    address = photo.address,
                    timestamp = photo.timestamp,
                    orderIdx = idx
                )
            }

            repository.saveDraft(reportEntity, savedPhotos)

            // Dynamic Widget Update on Save
            try {
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
            } catch (e: Exception) {
                Log.e("SIPEDAS", "Widget refresh broadcast fail: ${e.message}")
            }

            // Dynamic garbage collection of deleted photos
            cleanupOrphanedActiveFiles(photosList)

            lastSavedText = laporanText
            lastSavedPhotosSize = photosList.size

            viewModelScope.launch(Dispatchers.Main) {
                _activeDraftId.value = draftId
                _lastAutoSavedTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                _isAutoSaving.value = false
            }
        } catch (e: Exception) {
            Log.e("SIPEDAS", "Auto-save error: ${e.message}")
            viewModelScope.launch(Dispatchers.Main) {
                _isAutoSaving.value = false
            }
        }
    }

    private fun startNetworkSyncObserver() {
        try {
            val connectivityManager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            
            // Set initial value
            try {
                val activeNetwork = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                _isOnline.value = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } catch (e: Exception) {
                _isOnline.value = true
            }

            val request = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    Log.d("SIPEDAS", "Network is back! Attempting background sync...")
                    _isOnline.value = true
                    triggerBackgroundSync()
                }

                override fun onLost(network: android.net.Network) {
                    super.onLost(network)
                    Log.d("SIPEDAS", "Network lost!")
                    _isOnline.value = false
                }
            })
        } catch (e: Exception) {
            Log.e("SIPEDAS", "Error setting up network observer: ${e.message}")
        }
    }

    fun triggerBackgroundSync() {
        val url = _serverUrl.value.trim()
        if (url.isEmpty()) return
        if (_isSyncingDrafts.value || _isSubmitting.value || _isTransferring.value) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isSyncingDrafts.value = true
                Log.d("SIPEDAS", "Starting background sync of offline drafts...")

                // We need to fetch all drafts from database
                val drafts = allLocalDrafts.value
                if (drafts.isEmpty()) {
                    _isSyncingDrafts.value = false
                    return@launch
                }

                Log.d("SIPEDAS", "Found ${drafts.size} local drafts to sync")

                for (draft in drafts) {
                    val draftId = draft.id
                    // Ensure the draft we are uploading is NOT the one currently active/open in form,
                    // to avoid confusing the user while they are actively typing!
                    if (draftId == _activeDraftId.value) {
                        continue
                    }

                    Log.d("SIPEDAS", "Syncing draft ID: $draftId")
                    val photos = repository.getPhotosForDraft(draftId)
                    if (photos.isEmpty()) {
                        // Draft with no photos? Delete it
                        repository.deleteDraft(draftId)
                        continue
                    }

                    val total = photos.size
                    val linkFotoArray = JSONArray()
                    var remoteFolderUrl = ""
                    var hasUploadError = false

                    // Parse report to get dynamic date & month labels
                    val parsedReport = SipedasParser.parseLaporan(draft.laporan)
                    val rawDate = parsedReport.tanggal?.trim() ?: ""
                    val dateLabel = if (rawDate.isNotEmpty()) rawDate else {
                        try {
                            java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.forLanguageTag("id-ID")).format(java.util.Date())
                        } catch (e: Exception) {
                            "Dokumentasi"
                        }
                    }
                    
                    val parts = dateLabel.split(" ")
                    val monthLabel = if (parts.size >= 3) "${parts[1]} ${parts[2]}" else {
                        try {
                            java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.forLanguageTag("id-ID")).format(java.util.Date())
                        } catch (e: Exception) {
                            "Dokumentasi"
                        }
                    }

                    val folderPath = "SIPEDAS/${monthLabel}/${dateLabel}"

                    for (i in 0 until total) {
                        val photo = photos[i]
                        val file = File(photo.filePath)
                        if (!file.exists()) {
                            Log.e("SIPEDAS", "Draft photo file does not exist: ${photo.filePath}")
                            hasUploadError = true
                            break
                        }

                        try {
                            val prefix = if (photo.source.equals("camera", ignoreCase = true)) "[KAMERA]" else "[GALERI]"
                            val danruClean = parsedReport.namaDanru?.replace(Regex("[^a-zA-Z0-9]"), "_")?.take(20) ?: ""
                            val danruSlug = if (danruClean.isNotEmpty()) "_$danruClean" else ""
                            val suffix = if (total <= 1) "" else "_${i + 1}"
                            val ext = if (photo.mimeType.contains("png", ignoreCase = true)) ".png" else ".jpg"
                            val fullFileName = "${prefix}_${dateLabel}${danruSlug}${suffix}${ext}"

                            // Convert bytes to base64 payload
                            val bytes = file.readBytes()
                            val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            val mime = photo.mimeType.ifEmpty { "image/jpeg" }
                            val base64Payload = "data:$mime;base64,$base64Str"

                            val uploadResult = GoogleDriveSheetHelper.uploadFoto(
                                targetUrl = url,
                                base64Data = base64Payload,
                                mimeType = mime,
                                source = photo.source,
                                lat = photo.lat,
                                lng = photo.lng,
                                timestamp = photo.timestamp ?: "",
                                address = photo.address ?: "",
                                noFoto = i + 1,
                                jumlahTotal = total,
                                reportText = draft.laporan,
                                folderId = _driveFolderId.value.trim()
                            )

                            val driveUrl = uploadResult.optString("linkFile") ?: uploadResult.optString("url") ?: ""
                            if (driveUrl.isEmpty()) {
                                throw Exception("Gagal mengunggah foto ke-${i + 1} ke Google Drive.")
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
                            Log.e("SIPEDAS", "Background photo upload failed: ${e.message}")
                            hasUploadError = true
                            break
                        }
                    }

                    if (remoteFolderUrl.isEmpty()) {
                        remoteFolderUrl = "https://drive.google.com/drive/folders/${_driveFolderId.value.trim()}"
                    }

                    if (hasUploadError) {
                        continue
                    }

                    try {
                        val submitJson = GoogleDriveSheetHelper.submitLaporan(
                            targetUrl = url,
                            reportText = draft.laporan,
                            linkFotoArray = linkFotoArray,
                            remoteFolderUrl = remoteFolderUrl,
                            draftId = draftId,
                            sheetId = _sheetId.value.trim()
                        )

                        Log.d("SIPEDAS", "Draft ID $draftId synced successfully!")
                        // Delete physical permanent files
                        photos.forEach { entity ->
                            try {
                                val file = File(entity.filePath)
                                if (file.exists()) file.delete()
                            } catch (e: Exception) {}
                        }
                        repository.deleteDraft(draftId)
                        
                        withContext(Dispatchers.Main) {
                            com.sipedas.ponorogo.utils.NotificationHelper.showSuccessNotification(app, "SIPEDAS", "Laporan patroli draf otomatis berhasil terkirim!")
                            Toast.makeText(app, "Draf offline \"$draftId\" otomatis terunggah ke server!", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("SIPEDAS", "Draft ID $draftId spreadsheet saving failed: ${e.message}")
                    }
                }

                _isSyncingDrafts.value = false
            } catch (e: Exception) {
                Log.e("SIPEDAS", "Background sync exception: ${e.message}")
                _isSyncingDrafts.value = false
            }
        }
    }

    fun updateWidgetExplicitly() {
        try {
            val context = app.applicationContext
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
        } catch (e: Exception) {
            Log.e("SIPEDAS", "Widget refresh broadcast fail: ${e.message}")
        }
    }
}

class SipedasViewModelFactory(
    private val app: Application,
    private val repository: SipedasRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SipedasViewModel::class.java)) {
            return SipedasViewModel(app, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class SubmitStep(
    val title: String,
    val status: StepStatus
)

enum class StepStatus {
    PENDING, RUNNING, SUCCESS, ERROR
}
