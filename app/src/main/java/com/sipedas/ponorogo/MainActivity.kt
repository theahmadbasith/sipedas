package com.sipedas.ponorogo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import org.json.JSONObject
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import coil.compose.rememberAsyncImagePainter
import com.sipedas.ponorogo.data.SipedasDatabase
import com.sipedas.ponorogo.data.SipedasRepository
import com.sipedas.ponorogo.model.PhotoItem
import com.sipedas.ponorogo.parser.SipedasParser
import com.sipedas.ponorogo.parser.ParsedReport
import com.sipedas.ponorogo.utils.MapHelpers
import com.sipedas.ponorogo.ui.cctv.CompassDialog
import com.sipedas.ponorogo.viewmodel.SipedasViewModel
import com.sipedas.ponorogo.viewmodel.SipedasViewModelFactory
import com.sipedas.ponorogo.viewmodel.SubmitStep
import com.sipedas.ponorogo.viewmodel.StepStatus
import org.json.JSONArray
import java.util.UUID
import java.io.File
import java.util.*

/**
 * Toggles the device flashlight.
 * @param context Android context
 * @param enabled True to turn on, false to turn off.
 * @return True if successful, false otherwise.
 */
fun toggleTorch(context: Context, enabled: Boolean): Boolean {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
    try {
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: cameraManager.cameraIdList.getOrNull(0) ?: return false
        cameraManager.setTorchMode(cameraId, enabled)
        return true
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}

/**
 * Creates a generic haptic feedback vibration.
 */
fun triggerHapticFeedback(context: Context) {
    val vibrator = androidx.core.content.ContextCompat.getSystemService(context, Vibrator::class.java)
    if (vibrator != null && vibrator.hasVibrator()) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 35, 60, 35), -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private const val REPORT_TEMPLATE_TEXT = """Kepada. 
Yth. Kepala Bidang SDA dan LINMAS Satpol PP Kabupaten Ponorogo
di -
     Ponorogo

*Mohon ijin Melaporkan Hasil  Pelaksanaan Kegiatan :*
Patroli Linmas Pedestrian di Jl. Jenderal Sudirman, Jl. Diponegoro, Jl Urip Sumoharjo, Jl. HOS Cokro Aminoto.

*Sebagai Berikut :*

*Nomor SPT Bulan Juni*
300.1.4/ARH/191/405.14/2026

*Hari Pelaksanaan*
Hari         : Sabtu
Tanggal  : 20 Juni 2026

*Identitas / Nama Pelangggaran*

NIHIL    

*Personil yang terlibat :* 
( Basith) 

*Keterangan :*
Pelaksanaan Kegiatan berjalan aman dan lancar. kondisi cuaca cerah, Arus Lalu Lintas Rame Lancar. Titik keramaian berada di Alun" dan beberapa pengunjung di cafe jalan Urip Sumoharjo dan jalan Hos Tjokroaminoto. Lampu jalan berfungsi baik, Parkir kendaraan tertib. Tidak ditemukan pelanggaran ketertiban. 

Demikian Yang Dapat Kami Laporkan Untuk  Menjadikan Periksa.                              

*Danru 2*

( Basith)"""

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!cameraGranted || !locGranted) {
            Toast.makeText(this, "Izin kamera dan lokasi direkomendasikan untuk CCTV & Watermark", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val widgetAction = intent.getStringExtra("WIDGET_ACTION")
        val cctvLocation = intent.getStringExtra("CCTV_LOCATION")
        val database = SipedasDatabase.getDatabase(applicationContext)
        val repository = SipedasRepository(database.sipedasDao())
        val viewModel: SipedasViewModel by viewModels {
            SipedasViewModelFactory(application, repository)
        }
        if (widgetAction != null) {
            viewModel.triggerWidgetAction(widgetAction, cctvLocation)
        }
    }

    override fun onResume() {
        super.onResume()
        val database = SipedasDatabase.getDatabase(applicationContext)
        val repository = SipedasRepository(database.sipedasDao())
        val viewModel: SipedasViewModel by viewModels {
            SipedasViewModelFactory(application, repository)
        }
        viewModel.refreshActiveDraft()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Database and Repository
        val database = SipedasDatabase.getDatabase(applicationContext)
        val repository = SipedasRepository(database.sipedasDao())
        val viewModel: SipedasViewModel by viewModels {
            SipedasViewModelFactory(application, repository)
        }

        // Handle startup AppWidget Intent Action
        val widgetAction = intent?.getStringExtra("WIDGET_ACTION")
        val cctvLocation = intent?.getStringExtra("CCTV_LOCATION")
        
        if (widgetAction == null) {
            // Request runtime permissions only if not launching from widget to avoid dialogs over widget actions
            requestRequiredPermissions()
        } else {
            viewModel.triggerWidgetAction(widgetAction, cctvLocation)
        }

        setContent {
            val currentTheme by viewModel.theme.collectAsState()
            var showSplash by remember { mutableStateOf(widgetAction == null) }
            
            // Centralized theme wrapper supporting custom configurations
            val colorScheme = if (currentTheme == "light") lightColorScheme(
                primary = Color(0xFF1a65d6),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE3F2FD),
                onPrimaryContainer = Color(0xFF001B3E),
                secondary = Color(0xFF1a4fba),
                background = Color(0xFFF4F6F9),
                surface = Color.White
            ) else darkColorScheme(
                primary = Color(0xFF4ea2ff),
                onPrimary = Color(0xFF003258),
                primaryContainer = Color(0xFF1565C0),
                onPrimaryContainer = Color(0xFFD1E4FF),
                secondary = Color(0xFF2673d3),
                background = Color(0xFF0F172A),
                surface = Color(0xFF1E293B)
            )

            val view = androidx.compose.ui.platform.LocalView.current
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            if (!view.isInEditMode) {
                androidx.compose.runtime.DisposableEffect(lifecycleOwner, currentTheme) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            val activity = view.context as? androidx.activity.ComponentActivity
                            if (activity != null) {
                                val navBarColor = colorScheme.background.toArgb()
                                activity.enableEdgeToEdge(
                                    statusBarStyle = androidx.activity.SystemBarStyle.auto(
                                        android.graphics.Color.TRANSPARENT,
                                        android.graphics.Color.TRANSPARENT
                                    ),
                                    navigationBarStyle = if (currentTheme == "light") {
                                        androidx.activity.SystemBarStyle.light(
                                            navBarColor,
                                            navBarColor
                                        )
                                    } else {
                                        androidx.activity.SystemBarStyle.dark(
                                            navBarColor
                                        )
                                    }
                                )
                                val window = activity.window
                                @Suppress("DEPRECATION")
                                window.statusBarColor = android.graphics.Color.TRANSPARENT
                                @Suppress("DEPRECATION")
                                window.navigationBarColor = navBarColor
                                val isLight = currentTheme == "light"
                                androidx.core.view.WindowCompat.getInsetsController(window, view).let { controller ->
                                    controller.isAppearanceLightStatusBars = isLight
                                    controller.isAppearanceLightNavigationBars = isLight
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                androidx.compose.runtime.SideEffect {
                    val activity = view.context as? androidx.activity.ComponentActivity
                    if (activity != null) {
                        val navBarColor = colorScheme.background.toArgb()
                        activity.enableEdgeToEdge(
                            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                            ),
                            navigationBarStyle = if (currentTheme == "light") {
                                androidx.activity.SystemBarStyle.light(
                                    navBarColor,
                                    navBarColor
                                )
                            } else {
                                androidx.activity.SystemBarStyle.dark(
                                    navBarColor
                                )
                            }
                        )
                        val window = activity.window
                        @Suppress("DEPRECATION")
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        @Suppress("DEPRECATION")
                        window.navigationBarColor = navBarColor
                        val isLight = currentTheme == "light"
                        androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
                        androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
                    }
                }
            }

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var targetLogoRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                    var targetTextRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SipedasApp(
                            viewModel = viewModel,
                            onTitleLogoPositioned = { targetLogoRect = it },
                            onTitleTextPositioned = { targetTextRect = it }
                        )
                        if (showSplash) {
                            SipedasSplashScreen(
                                onFinish = { showSplash = false },
                                targetLogoRect = targetLogoRect,
                                targetTextRect = targetTextRect,
                                isDark = currentTheme == "dark"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val permissionsArray = permissions.toTypedArray()
        val needsRequest = permissionsArray.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            permissionLauncher.launch(permissionsArray)
        }
    }
}

@Composable
fun RowScope.AnimatedPillTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "tab_color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tab_content_color"
    )
    val weight by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.5f else 1f, 
        label = "weight"
    )
    
    Box(
        modifier = Modifier
            .weight(weight)
            .height(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            androidx.compose.animation.AnimatedVisibility(visible = selected) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SipedasApp(
    viewModel: SipedasViewModel,
    onTitleLogoPositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onTitleTextPositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    
    DisposableEffect(keepScreenOn) {
        val window = (context as? android.app.Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(keepScreenOn) {
        if (keepScreenOn) {
            // Keep screen on for maximum 30 minutes to conserve device battery
            kotlinx.coroutines.delay(30 * 60 * 1000L)
            viewModel.setKeepScreenOn(false)
            android.widget.Toast.makeText(
                context,
                "Fitur 'Layar Selalu Menyala' otomatis dinonaktifkan setelah batas 30 menit",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    var activeTab by remember { mutableIntStateOf(1) } // 0 = CCTV, 1 = Laporan, 2 = Aduan, 3 = Dashboard
    var refreshDashboardTrigger by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCopyConfirmDialog by remember { mutableStateOf(false) }
    var isCopied by remember {
        val duration = System.currentTimeMillis() - com.sipedas.ponorogo.ui.aduan.ReportCopyTimer.copiedTimeMillis
        mutableStateOf(duration in 0L..(5 * 60 * 1000L))
    }
    LaunchedEffect(isCopied) {
        if (isCopied) {
            val elapsed = System.currentTimeMillis() - com.sipedas.ponorogo.ui.aduan.ReportCopyTimer.copiedTimeMillis
            val remaining = (5 * 60 * 1000L) - elapsed
            if (remaining > 0) {
                kotlinx.coroutines.delay(remaining)
            }
            isCopied = false
        }
    }

    LaunchedEffect(activeTab) {
        focusManager.clearFocus()
    }

    val cctvViewModel: com.sipedas.ponorogo.viewmodel.CctvViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val isCctvFullscreen = false
    val showMap = true

    // Draft State Dialogs
    var showLoadDraftDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var viewPhotoDetail by remember { mutableStateOf<PhotoItem?>(null) }
    var showInAppCamera by remember { mutableStateOf(false) }
    var showCompassDialog by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showInAppCamera = true
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto langsung", Toast.LENGTH_LONG).show()
        }
    }

    val topGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addLocalPhotos(uris, "gallery")
        }
    }

    // AppWidget Intent actions listener
    LaunchedEffect(viewModel) {
        viewModel.widgetAction.collect { actionPair ->
            if (actionPair != null) {
                val (action, cctvLoc) = actionPair
                viewModel.consumeWidgetAction()
                when (action) {
                    "camera" -> {
                        activeTab = 1
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) {
                            showInAppCamera = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    "gallery" -> {
                        activeTab = 1
                        topGalleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    "paste" -> {
                        activeTab = 1
                        kotlinx.coroutines.delay(600)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clipData = clipboard?.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val pastedText = clipData.getItemAt(0).text?.toString() ?: ""
                            if (pastedText.isNotEmpty()) {
                                viewModel.setReportTextValue(pastedText)
                                Toast.makeText(context, "Draf laporan ditempel dari clipboard", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Clipboard kosong atau tidak didukung", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "send" -> {
                        activeTab = 1
                        viewModel.submitLaporan(context)
                    }
                    "compass" -> {
                        showCompassDialog = true
                    }
                    "cctv" -> {
                        activeTab = 0
                        if (cctvLoc != null) {
                            Toast.makeText(context, "Membuka monitor CCTV: $cctvLoc", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            if (!isCctvFullscreen) {
                SipedasTopAppBar(
                    viewModel = viewModel,
                    onTitleLogoPositioned = onTitleLogoPositioned,
                    onTitleTextPositioned = onTitleTextPositioned,
                    onAboutClick = { showAboutDialog = true },
                    onSettingsClick = {
                        if (activeTab == 0) {
                            cctvViewModel.setShowSettings(true)
                        } else {
                            showSettings = true
                        }
                    },
                    showThemeToggle = activeTab != 3 && activeTab != 0,
                    showSettingsButton = activeTab != 3 && activeTab != 2,
                    customSubtitle = when (activeTab) {
                        0 -> "Kanal Monitoring CCTV Pedestrian"
                        1 -> "Kanal Pelaporan Satlinmas Pedestrian"
                        2 -> "Daftar Aduan Masyarakat"
                        else -> "Dashboard Admin SIPEDAS"
                    },
                    extraActions = {
                        if (activeTab == 0) {
                            IconButton(
                                onClick = { cctvViewModel.triggerMapRefresh() },
                                modifier = Modifier.testTag("refresh_map_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Web",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { showCompassDialog = true },
                                modifier = Modifier.testTag("compass_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = "Kompas",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else if (activeTab == 1) {
                            IconButton(
                                onClick = { showCopyConfirmDialog = true },
                                modifier = Modifier.testTag("copy_report_format_button")
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = if (isCopied) "Format laporan telah disalin" else "Salin format laporan",
                                    tint = if (isCopied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else if (activeTab == 2) {
                            // Extra actions for Aduan
                            val isLoadingAduan by viewModel.isLoadingAduan.collectAsState()
                            if (isLoadingAduan) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.padding(8.dp).size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { viewModel.fetchOnlineAduan(force = true) },
                                    modifier = Modifier.testTag("refresh_aduan_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Muat Ulang Aduan",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else if (activeTab == 3) {
                            IconButton(
                                onClick = { refreshDashboardTrigger++ },
                                modifier = Modifier.testTag("refresh_dashboard_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Dashboard",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            val keybdHeight = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
            if (!isCctvFullscreen && keybdHeight == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            AnimatedPillTab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                icon = Icons.Default.Videocam,
                                label = "CCTV"
                            )
                            AnimatedPillTab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                icon = Icons.AutoMirrored.Filled.Assignment,
                                label = "Laporan"
                            )
                            AnimatedPillTab(
                                selected = activeTab == 2,
                                onClick = { activeTab = 2 },
                                icon = Icons.Default.Forum,
                                label = "Aduan"
                            )
                            AnimatedPillTab(
                                selected = activeTab == 3,
                                onClick = { activeTab = 3 },
                                icon = Icons.Default.SpaceDashboard,
                                label = "Dashboard"
                            )
                        }
                    }
                }
            }
        }
    }
    ) { innerPadding ->
        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
        
        val cctvOffsetMultiplier by animateFloatAsState(
            targetValue = (0 - activeTab).toFloat(),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "cctvOffset"
        )
        val laporanOffsetMultiplier by animateFloatAsState(
            targetValue = (1 - activeTab).toFloat(),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "laporanOffset"
        )
        val aduanOffsetMultiplier by animateFloatAsState(
            targetValue = (2 - activeTab).toFloat(),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "aduanOffset"
        )
        val dashboardOffsetMultiplier by animateFloatAsState(
            targetValue = (3 - activeTab).toFloat(),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "dashboardOffset"
        )

        val contentModifier = if (isCctvFullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                )
                .fillMaxSize()
        }
        Box(
            modifier = contentModifier
        ) {
            // Tab 0: CCTV Screen (Keep Alive)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = screenWidth * cctvOffsetMultiplier)
                    .graphicsLayer {
                        alpha = if (kotlin.math.abs(cctvOffsetMultiplier) >= 0.99f) 0f else (1f - kotlin.math.abs(cctvOffsetMultiplier)).coerceIn(0f, 1f)
                    }
            ) {
                com.sipedas.ponorogo.ui.cctv.CctvScreen(
                    cctvViewModel = cctvViewModel,
                    mainViewModel = viewModel,
                    isActive = activeTab == 0,
                    onTitleLogoPositioned = onTitleLogoPositioned,
                    onTitleTextPositioned = onTitleTextPositioned,
                    onAboutClick = { showAboutDialog = true }
                )
            }

            // Tab 1: Form Laporan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = screenWidth * laporanOffsetMultiplier)
                    .graphicsLayer {
                        alpha = if (kotlin.math.abs(laporanOffsetMultiplier) >= 0.99f) 0f else (1f - kotlin.math.abs(laporanOffsetMultiplier)).coerceIn(0f, 1f)
                    }
            ) {
                FormLaporanView(
                    viewModel = viewModel,
                    onLoadDraftClick = { showLoadDraftDialog = true },
                    onResetFormClick = { showResetConfirmDialog = true },
                    onPhotoClick = { viewPhotoDetail = it },
                    onLaunchCamera = {
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) {
                            showInAppCamera = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onTitleLogoPositioned = onTitleLogoPositioned,
                    onTitleTextPositioned = onTitleTextPositioned,
                    onAboutClick = { showAboutDialog = true },
                    onSettingsClick = { showSettings = true }
                )
            }

            // Tab 2: Aduan Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = screenWidth * aduanOffsetMultiplier)
                    .graphicsLayer {
                        alpha = if (kotlin.math.abs(aduanOffsetMultiplier) >= 0.99f) 0f else (1f - kotlin.math.abs(aduanOffsetMultiplier)).coerceIn(0f, 1f)
                    }
            ) {
                com.sipedas.ponorogo.ui.aduan.AduanScreen(viewModel = viewModel)
            }

            // Tab 3: Sipedas Dashboard View (Keep Alive)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = screenWidth * dashboardOffsetMultiplier)
                    .graphicsLayer {
                        alpha = if (kotlin.math.abs(dashboardOffsetMultiplier) >= 0.99f) 0f else (1f - kotlin.math.abs(dashboardOffsetMultiplier)).coerceIn(0f, 1f)
                    }
            ) {
                SipedasDashboardView(
                    viewModel = viewModel,
                    refreshTrigger = refreshDashboardTrigger,
                    isActive = activeTab == 3
                )
            }

            // Submitting Network Progress Overlay Card
            val isSubmitting by viewModel.isSubmitting.collectAsState()
            val isTransferring by viewModel.isTransferring.collectAsState()
            val progressVal by viewModel.submitProgress.collectAsState()
            val progressLabel by viewModel.submitProgressLabel.collectAsState()
            val progressTitle by viewModel.submitProgressTitle.collectAsState()

            if (isTransferring) {
                TransferProgressModal(
                    progress = progressVal,
                    label = progressLabel
                )
            }

            if (isSubmitting) {
                val animatedSubmitProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = progressVal / 100f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "smooth_submit_progress"
                )
                val displaySubmitProgressPercent = (animatedSubmitProgress * 100f).toInt()

                androidx.compose.ui.window.Dialog(
                    onDismissRequest = {},
                    properties = androidx.compose.ui.window.DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.56f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // Circular Stage Indicator with Center Percentage
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { animatedSubmitProgress },
                                        modifier = Modifier.size(80.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 6.dp,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    )
                                    Text(
                                        text = "$displaySubmitProgressPercent%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = progressTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = progressLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Settings sheet
    if (showSettings) {
        SettingsSheetDialog(
            viewModel = viewModel,
            onDismiss = { showSettings = false }
        )
    }

    // Restores past draf state sheets
    if (showLoadDraftDialog) {
        LoadDraftDialog(
            viewModel = viewModel,
            onDismiss = { showLoadDraftDialog = false }
        )
    }

    // Safe full form reset confirmations
    if (showResetConfirmDialog) {
        ProfessionalConfirmDialog(
            title = "Kosongkan Form?",
            message = "Teks laporan dan seluruh foto terlampir akan dihapus permanen dari memori. Lanjutkan?",
            icon = Icons.Default.Delete,
            iconColor = MaterialTheme.colorScheme.error,
            confirmText = "Kosongkan",
            cancelText = "Batal",
            onConfirm = {
                viewModel.clearAllReports()
                showResetConfirmDialog = false
            },
            onDismiss = { showResetConfirmDialog = false }
        )
    }

    // Modal Dialog untuk Tentang Aplikasi
    if (showAboutDialog) {
        TentangAplikasiDialog(
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showCompassDialog) {
        CompassDialog(
            onDismiss = { showCompassDialog = false }
        )
    }

    if (showCopyConfirmDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCopyConfirmDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Salin Format Laporan?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Apakah Anda ingin menyalin draf template laporan patroli pedestrian resmi di bawah ini ke clipboard?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = REPORT_TEMPLATE_TEXT,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCopyConfirmDialog = false },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text("Batal", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(REPORT_TEMPLATE_TEXT))
                                com.sipedas.ponorogo.ui.aduan.ReportCopyTimer.copiedTimeMillis = System.currentTimeMillis()
                                isCopied = true
                                showCopyConfirmDialog = false
                            },
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Salin Format", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }

    // Photo details high fidelity popup card
    viewPhotoDetail?.let { photo ->
        PhotoDetailDialog(
            photo = photo,
            onDismiss = { viewPhotoDetail = null },
            onDelete = {
                viewModel.removePhoto(photo.id)
                viewPhotoDetail = null
            }
        )
    }

    if (showInAppCamera) {
        SipedasCameraView(
            viewModel = viewModel,
            onPhotoCaptured = { uri, isMirrored ->
                showInAppCamera = false
                viewModel.addLocalPhotos(listOf(uri), "camera", isMirrored = isMirrored)
            },
            onDismiss = {
                showInAppCamera = false
            }
        )
    }
}

@Composable
fun PhotoDetailDialog(photo: PhotoItem, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var isImageZoomed by remember { mutableStateOf(false) }
    var showModalMap by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        ProfessionalConfirmDialog(
            title = "Hapus Foto Dokumentasi?",
            message = "Apakah Anda yakin ingin menghapus foto dokumentasi ini?",
            icon = Icons.Default.Delete,
            iconColor = MaterialTheme.colorScheme.error,
            confirmText = "Hapus",
            cancelText = "Batal",
            onConfirm = {
                showDeleteConfirmDialog = false
                onDelete()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    if (showModalMap && photo.lat != null && photo.lng != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showModalMap = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Lokasi Peta",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
                        AndroidView(
                            factory = { ctx ->
                                android.webkit.WebView(ctx).apply {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = android.webkit.WebViewClient()
                                    val html = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                            <meta charset="utf-8" />
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css" />
                                            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
                                            <style>
                                                body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; }
                                                .leaflet-control-attribution { display: none !important; }
                                                .custom-pin {
                                                    display: flex;
                                                    align-items: center;
                                                    justify-content: center;
                                                }
                                            </style>
                                        </head>
                                        <body>
                                            <div id="map"></div>
                                            <script>
                                                var map = L.map('map', {
                                                    center: [${photo.lat}, ${photo.lng}],
                                                    zoom: 17,
                                                    zoomControl: false
                                                });
                                                
                                                L.TileLayer.Cached = L.TileLayer.extend({
                                                    createTile: function (coords, done) {
                                                        var tile = document.createElement('img');
                                                        var url = this.getTileUrl(coords);
                                                        tile.crossOrigin = 'Anonymous';
                                                        
                                                        if ('caches' in window) {
                                                            caches.open('map-tiles-cache').then(function(cache) {
                                                                cache.match(url).then(function(response) {
                                                                    if (response) {
                                                                        response.blob().then(function(blob) {
                                                                            tile.src = URL.createObjectURL(blob);
                                                                            done(null, tile);
                                                                        });
                                                                    } else {
                                                                        fetch(url)
                                                                        .then(function(res) {
                                                                            cache.put(url, res.clone());
                                                                            return res.blob();
                                                                        })
                                                                        .then(function(blob) {
                                                                            tile.src = URL.createObjectURL(blob);
                                                                            done(null, tile);
                                                                        })
                                                                        .catch(function(err) {
                                                                            tile.src = url; // Fallback
                                                                            done(err, tile);
                                                                        });
                                                                    }
                                                                });
                                                            });
                                                        } else {
                                                            tile.src = url;
                                                            done(null, tile);
                                                        }
                                                        
                                                        return tile;
                                                    }
                                                });
                                                
                                                L.tileLayer.cached = function (url, options) {
                                                    return new L.TileLayer.Cached(url, options);
                                                };
                                                
                                                L.tileLayer.cached('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', {
                                                    maxZoom: 20
                                                }).addTo(map);
                                                
                                                var svgIcon = L.divIcon({
                                                    html: `
                                                        <svg width="24" height="32" viewBox="0 0 32 40" style="display: block; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.25))">
                                                            <path d="M16 0 C9.37 0 4 5.37 4 12 C4 21.5 16 40 16 40 C16 40 28 21.5 28 12 C28 5.37 22.63 0 16 0 Z" fill="#EF4444" />
                                                            <circle cx="16" cy="12" r="7.5" fill="#ffffff" />
                                                        </svg>
                                                    `,
                                                    className: 'custom-pin',
                                                    iconSize: [24, 32],
                                                    iconAnchor: [12, 32]
                                                });
                                                
                                                L.marker([${photo.lat}, ${photo.lng}], { icon: svgIcon }).addTo(map);
                                                
                                                L.circle([${photo.lat}, ${photo.lng}], {
                                                    color: '#EF4444',
                                                    fillColor: '#EF4444',
                                                    fillOpacity: 0.15,
                                                    radius: 30
                                                }).addTo(map);
                                            </script>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    loadDataWithBaseURL("https://appassets.androidplatform.net", html, "text/html", "UTF-8", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(photo.address ?: "Lokasi tidak diketahui", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showModalMap = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Tutup Kembali")
                    }
                }
            }
        }
    }

    if (isImageZoomed) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { isImageZoomed = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                Image(
                    painter = rememberAsyncImagePainter(model = File(photo.path)),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
                
                IconButton(
                    onClick = { isImageZoomed = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .statusBarsPadding()
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                ) {
                    Text(
                        text = "Detail Foto",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            val painter = rememberAsyncImagePainter(model = File(photo.path))
                            val intrinsicSize = painter.intrinsicSize
                            val isSpecified = intrinsicSize != androidx.compose.ui.geometry.Size.Unspecified && intrinsicSize.width > 0 && intrinsicSize.height > 0
                            val dynamicModifier = if (isSpecified) {
                                val ratio = intrinsicSize.width / intrinsicSize.height
                                Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                            } else {
                                Modifier
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp)
                                    .then(dynamicModifier)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { isImageZoomed = true }
                            ) {
                                Image(
                                    painter = painter,
                                    contentDescription = "Dokumentasi",
                                    contentScale = ContentScale.Crop, // Safe to crop because aspect ratio matches image bounds perfectly
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Text("Zoom", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilledTonalIconButton(
                                    onClick = {
                                        val resolver = context.contentResolver
                                        val contentValues = android.content.ContentValues().apply {
                                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "sipedas_${System.currentTimeMillis()}.jpg")
                                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Sipedas")
                                        }
                                        try {
                                            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                                            uri?.let {
                                                resolver.openOutputStream(it)?.use { outMsg ->
                                                    File(photo.path).inputStream().use { inMsg ->
                                                        inMsg.copyTo(outMsg)
                                                    }
                                                }
                                                Toast.makeText(context, "Selesai diunduh ke Galeri", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Unduh", style = MaterialTheme.typography.labelSmall)
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilledTonalIconButton(
                                    onClick = {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            File(photo.path)
                                        )
                                        val shareIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            type = "image/jpeg"
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan Foto Dokumentasi"))
                                    },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Bagikan", style = MaterialTheme.typography.labelSmall)
                            }

                            if (photo.lat != null && photo.lng != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    FilledTonalIconButton(
                                        onClick = { showModalMap = true },
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = "View Map", modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Peta", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilledTonalIconButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Hapus", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CompactMetaItem(modifier = Modifier.weight(1f), label = "Sumber Berkas", value = if (photo.source == "camera") "Kamera Langsung" else "Galeri Berkas", icon = Icons.Default.CenterFocusStrong)
                                    CompactMetaItem(modifier = Modifier.weight(1f), label = "Ukuran Dok", value = "${photo.sizeKB} KB", icon = Icons.Default.DataUsage)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CompactMetaItem(modifier = Modifier.weight(1f), label = "Waktu Pemotretan", value = photo.timestamp.ifEmpty { "—" }, icon = Icons.Default.Schedule)
                                    CompactMetaItem(modifier = Modifier.weight(1f), label = "Koordinat Lokasi", value = if (photo.lat != null && photo.lng != null) String.format("%.6f , %.6f", photo.lat, photo.lng) else "Tidak Terdeteksi", icon = Icons.Default.PinDrop)
                                }
                                CompactMetaItem(modifier = Modifier.fillMaxWidth(), label = "Nama Jalan / Geocode", value = photo.address?.ifEmpty { "—" } ?: "—", icon = Icons.Default.Place)
                            }
                        }
                    }
                }
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CompactMetaItem(modifier: Modifier = Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SipedasTopAppBar(
    viewModel: SipedasViewModel,
    onTitleLogoPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onTitleTextPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onAboutClick: () -> Unit,
    onSettingsClick: () -> Unit,
    customSubtitle: String? = null,
    showOnlySingleAction: Boolean = false,
    showThemeToggle: Boolean = true,
    showSettingsButton: Boolean = true,
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBarHeight = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
    var savedStatusBarHeightDp by remember { mutableStateOf(24.dp) }
    LaunchedEffect(statusBarHeight) {
        if (statusBarHeight > 0) {
            savedStatusBarHeightDp = with(density) { statusBarHeight.toDp() }
        }
    }

    TopAppBar(
        windowInsets = WindowInsets(
            left = 0.dp,
            top = savedStatusBarHeightDp,
            right = 0.dp,
            bottom = 0.dp
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onAboutClick
                )
            ) {
                Card(
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, Color(0xFFFF9100)),
                    modifier = Modifier
                        .size(38.dp)
                        .onGloballyPositioned { coordinates ->
                            onTitleLogoPositioned(coordinates.boundsInRoot())
                        }
                ) {
                    Image(
                        painter = coil.compose.rememberAsyncImagePainter(model = R.drawable.sipedas_logo),
                        contentDescription = "SIPEDAS Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                val isSyncingDrafts by viewModel.isSyncingDrafts.collectAsState()
                Column {
                    Text(
                        text = "SIPEDAS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFEA00), // Pure Yellow
                                    Color(0xFFFF9100), // Orange
                                    Color(0xFFFF3D00)  // Deep Orange
                                )
                            )
                        ),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            onTitleTextPositioned(coordinates.boundsInRoot())
                        }
                    )
                    if (isSyncingDrafts) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(8.dp),
                                strokeWidth = 1.dp,
                                color = Color(0xFFFF5722)
                            )
                            Text(
                                text = customSubtitle ?: "Kanal Pelaporan Satgas Linmas Pedestrian",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF5722),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = customSubtitle ?: "Kanal Pelaporan Satgas Linmas Pedestrian",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        actions = {
            extraActions()
            if (!showOnlySingleAction) {
                if (showThemeToggle) {
                    val currentTheme by viewModel.theme.collectAsState()
                    IconButton(
                        onClick = { viewModel.setTheme(if (currentTheme == "light") "dark" else "light") },
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (currentTheme == "light") Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = if (currentTheme == "light") "Ganti ke Mode Gelap" else "Ganti ke Mode Terang",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (showSettingsButton) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun FormLaporanView(
    viewModel: SipedasViewModel,
    onLoadDraftClick: () -> Unit,
    onResetFormClick: () -> Unit,
    onPhotoClick: (PhotoItem) -> Unit,
    onLaunchCamera: () -> Unit,
    onTitleLogoPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onTitleTextPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onAboutClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val textState by viewModel.reportText.collectAsState()
    val photosState by viewModel.photos.collectAsState()
    val activeDraftId by viewModel.activeDraftId.collectAsState()
    var torchEnabled by remember { mutableStateOf(false) }
    var photoToDelete by remember { mutableStateOf<String?>(null) }
    var showArrangeModal by remember { mutableStateOf(false) }
    var showDownloadConfDialog by remember { mutableStateOf<PhotoItem?>(null) }

    photoToDelete?.let { id ->
        ProfessionalConfirmDialog(
            title = "Hapus Foto?",
            message = "Foto dokumentasi ini akan dihapus dari laporan. Lanjutkan?",
            icon = Icons.Default.Delete,
            iconColor = MaterialTheme.colorScheme.error,
            confirmText = "Hapus",
            cancelText = "Batal",
            onConfirm = {
                viewModel.removePhoto(id)
                photoToDelete = null
            },
            onDismiss = { photoToDelete = null }
        )
    }

    if (showArrangeModal) {
        ArrangePhotosDialog(
            photos = photosState,
            onDismiss = { showArrangeModal = false },
            onSwap = { from, to -> viewModel.movePhoto(from, to) }
        )
    }
    
    showDownloadConfDialog?.let { photo ->
        ProfessionalConfirmDialog(
            title = "Download Foto",
            message = "Apakah Anda ingin mendownload foto dokumentasi ini ke penyimpanan perangkat?",
            icon = Icons.Default.Download,
            iconColor = MaterialTheme.colorScheme.primary,
            confirmText = "Download",
            cancelText = "Batal",
            onConfirm = {
                viewModel.downloadPhoto(photo.path) // assuming download method exists
                showDownloadConfDialog = null
            },
            onDismiss = { showDownloadConfDialog = null }
        )
    }

    // Real-time parsed details for officer feedbacks
    val parsedData = remember(textState) {
        SipedasParser.parseLaporan(textState)
    }

    // Set up standard Android media capture triggers
    val cameraPhotoUri = remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraPhotoUri.value?.let { uri ->
                viewModel.addLocalPhotos(listOf(uri), "camera")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addLocalPhotos(uris, "gallery")
        }
    }

    val isOnline by viewModel.isOnline.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = !isOnline,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Offline Mode",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Mode Offline (Sinyal Terputus)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Setiap draf laporan otomatis dicatat dan disimpan aman secara lokal.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Report text Area Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Input Teks Laporan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        val isAutoSaving by viewModel.isAutoSaving.collectAsState()
                        val lastAutoSavedTime by viewModel.lastAutoSavedTime.collectAsState()

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${textState.length} kar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = textState,
                        onValueChange = { viewModel.setReportTextValue(it) },
                        placeholder = { Text("Tempel teks laporan sesuai format di sini...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                            .testTag("report_text_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    // Expandable real-time parsing widget
                    if (textState.trim().isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Parsed",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Danru Terdeteksi: ${parsedData.namaDanru.ifEmpty { "Belum terdeteksi" }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        // Attached photos Grid Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Dokumentasi Foto (${photosState.size}/20)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        if (photosState.isNotEmpty()) {
                            TextButton(
                                onClick = { showArrangeModal = true },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tata", fontSize = 12.sp)
                            }
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val availableWidth = maxWidth
                        val itemW = (availableWidth - 20.dp - 2.dp) / 3f
                        val itemH = itemW * 1.33f

                        if (photosState.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemH)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Kosong. Silakan ambil foto dari kamera atau galeri.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(itemH),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                lazyItemsIndexed(
                                    items = photosState,
                                    key = { _, photo -> photo.id }
                                ) { index, photo ->
                                    var isVisible by remember { mutableStateOf(false) }
                                    LaunchedEffect(photo.id) {
                                        isVisible = true
                                    }
                                    val animScaleAlpha = androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isVisible) 1f else 0.5f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                                        label = "graphicsLayerAnim"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .animateItem()
                                            .graphicsLayer {
                                                alpha = if (isVisible) animScaleAlpha.value else 0f
                                                scaleX = animScaleAlpha.value
                                                scaleY = animScaleAlpha.value
                                            }
                                            .size(itemW, itemH)
                                            .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            if (photo.isProcessing) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(photo.processingLabel, style = MaterialTheme.typography.labelSmall)
                                                }
                                            } else {
                                                Image(
                                                    painter = rememberAsyncImagePainter(model = File(photo.path)),
                                                    contentDescription = "Thumbnail",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clickable { onPhotoClick(photo) }
                                                )

                                                // Top Row Badges: index & source badge
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "${index + 1}",
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                if (photo.source == "camera") Color(0xFFFFB300) else Color(0xFF2196F3),
                                                                RoundedCornerShape(12.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (photo.source == "camera") Icons.Default.CameraAlt else Icons.Default.PhotoLibrary,
                                                            contentDescription = "Sumber",
                                                            tint = if (photo.source == "camera") Color.Black else Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }

                                                // Bottom sorting bar + overlay
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .fillMaxWidth()
                                                        .background(Color.Black.copy(alpha = 0.6f))
                                                        .padding(4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Download,
                                                            contentDescription = "Download",
                                                            tint = Color.White,
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clickable { showDownloadConfDialog = photo }
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Hapus",
                                                            tint = Color(0xFFef4444),
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clickable {
                                                                    photoToDelete = photo.id
                                                                }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    // Removed AnimatedVisibility wrapping block brace
                                }
                            }
                        }
                    }

                    // Camera/Gallery trigger buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                triggerHapticFeedback(context)
                                onLaunchCamera()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFffd200), contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("camera_picker_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kamera", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                triggerHapticFeedback(context)
                                onResetFormClick()
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFef4444)),
                            border = BorderStroke(1.2.dp, Color(0xFFef4444).copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("reset_form_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Reset",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                triggerHapticFeedback(context)
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("gallery_picker_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Galeri", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Draft Controls - Cloud Transfer and Retrieval
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    if (activeDraftId != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            val displayId = if (activeDraftId == "ACTIVE_SESSION_DRAFT") "Aktif" else activeDraftId
                            Text(
                                text = "Sesi disimpan lokal: $displayId",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cloud Transfer
                        OutlinedButton(
                            onClick = { viewModel.transferLaporanToCloud(context) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_draft_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Transfer", fontSize = 12.sp)
                        }

                        // Cloud Retrieval
                        OutlinedButton(
                            onClick = onLoadDraftClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("load_draft_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Muat", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Standard wide Primary trigger submission layout
        item {
            Button(
                onClick = { viewModel.submitLaporan(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_report_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "KIRIM LAPORAN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
}





@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SipedasDashboardView(viewModel: SipedasViewModel, refreshTrigger: Int = 0, isActive: Boolean = true) {
    val dashboardUrl = "https://sipedasadmin.vercel.app"
    var webLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isError by remember { mutableStateOf(false) }
    val isOnline by viewModel.isOnline.collectAsState()
    val themeBgColor = MaterialTheme.colorScheme.background.toArgb()

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            isError = false
            webLoading = true
            webViewRef?.reload() ?: webViewRef?.loadUrl(dashboardUrl)
        }
    }
    
    Box(
        modifier = if (isActive) {
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
        } else {
            Modifier.size(0.dp)
        }
    ) {
        if (!isOnline || isError) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (!isOnline) Icons.Default.WifiOff else Icons.Default.CloudOff,
                                    contentDescription = "Offline / Gagal Memuat",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text(
                                text = if (!isOnline) "Koneksi Terputus" else "Gagal Memuat Dashboard",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                text = if (!isOnline) 
                                    "Perangkat Anda sedang offline. Mohon aktifkan koneksi internet Anda untuk menggunakan layanan Dashboard SIPEDAS." 
                                    else "Terjadi kesalahan saat berkomunikasi dengan server dashboard. Silakan coba kembali beberapa saat lagi.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 20.sp
                            )
                            
                            Spacer(modifier = Modifier.height(28.dp))
                            
                            Button(
                                onClick = {
                                    isError = false
                                    webLoading = true
                                    webViewRef?.reload() ?: webViewRef?.loadUrl(dashboardUrl)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Coba Lagi",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Coba Lagi",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                webLoading = true
                                isError = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                webLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isError = true
                                    webLoading = false
                                }
                            }
                            
                            @Suppress("DEPRECATION")
                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                @Suppress("DEPRECATION")
                                super.onReceivedError(view, errorCode, description, failingUrl)
                                isError = true
                                webLoading = false
                            }
                            
                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                    isError = true
                                    webLoading = false
                                }
                            }
                        }
                        // Ensure background color from theme
                        setBackgroundColor(themeBgColor)
                        visibility = if (isActive) android.view.View.VISIBLE else android.view.View.GONE
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = false
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        loadUrl(dashboardUrl)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    webView.setBackgroundColor(themeBgColor)
                    webView.visibility = if (isActive) android.view.View.VISIBLE else android.view.View.GONE
                },
                modifier = if (isActive) Modifier.fillMaxSize() else Modifier.size(0.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheetDialog(
    viewModel: SipedasViewModel,
    onDismiss: () -> Unit
) {
    val currentTheme by viewModel.theme.collectAsState()
    val wmCamState by viewModel.wmCam.collectAsState()
    val wmGalState by viewModel.wmGal.collectAsState()
    val ocrGalState by viewModel.ocrGal.collectAsState()
    val minimapState by viewModel.minimap.collectAsState()
    val keepScreenOnState by viewModel.keepScreenOn.collectAsState()

    // Location fallbacks states
    val jln by viewModel.locJalan.collectAsState()
    val dukuh by viewModel.locNoDukuh.collectAsState()
    val ds by viewModel.locDesa.collectAsState()
    val kc by viewModel.locKec.collectAsState()
    val kb by viewModel.locKab.collectAsState()
    val pv by viewModel.locProv.collectAsState()
    val manualL by viewModel.manualLat.collectAsState()
    val manualG by viewModel.manualLng.collectAsState()

    // Config entries states
    val proxySrv by viewModel.serverUrl.collectAsState()
    val driveFolderId by viewModel.driveFolderId.collectAsState()
    val sheetId by viewModel.sheetId.collectAsState()
    val camCctv by viewModel.cctvUrl.collectAsState()
    val fbUrl by viewModel.firebaseUrl.collectAsState()
    val cldCloud by viewModel.cloudinaryCloud.collectAsState()
    val cldPreset by viewModel.cloudinaryPreset.collectAsState()
    val cldApiKey by viewModel.cloudinaryApiKey.collectAsState()
    val cldApiSecret by viewModel.cloudinaryApiSecret.collectAsState()

    var activeCollSection by remember { mutableStateOf<String?>("features") }

    fun toggleSection(section: String) {
        activeCollSection = if (activeCollSection == section) null else section
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f), // Fixed height to prevent jarring animation
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Vibrant Blue/Dark Header with subtle info subtitle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = if (currentTheme == "light") {
                                    listOf(Color(0xFF1a65d6), Color(0xFF1a4fba))
                                } else {
                                    listOf(Color(0xFF1e293b), Color(0xFF0f172a))
                                }
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Pengaturan Aplikasi",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Konfigurasi Sistem ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Changed from weight(1f, fill = false) to fill available space
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section 1: Watermark Features, styled inside card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            CollapsibleHeaderCompact(
                                title = "Setelan Umum",
                                subtitle = "Watermark foto dan Sistem",
                                icon = Icons.Default.CameraAlt,
                                isOpen = activeCollSection == "features",
                                onClick = { toggleSection("features") }
                            )

                            AnimatedVisibility(visible = activeCollSection == "features") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ToggleOptionRowStyled(
                                        title = "Watermark Foto Kamera",
                                        desc = "Cap + Logo Linmas pada foto kamera",
                                        checked = wmCamState,
                                        onCheckedChange = { viewModel.setWmCam(it) }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                    ToggleOptionRowStyled(
                                        title = "Watermark Foto Galeri",
                                        desc = "Cap pada foto dari galeri",
                                        checked = wmGalState,
                                        onCheckedChange = { viewModel.setWmGal(it) }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                    ToggleOptionRowStyled(
                                        title = "Deteksi Lokasi (OCR) Galeri",
                                        desc = "Ekstrak kordinat dari teks pada foto galeri",
                                        checked = ocrGalState,
                                        onCheckedChange = { viewModel.setOcrGal(it) }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                    ToggleOptionRowStyled(
                                        title = "Mini Map Lokasi",
                                        desc = "Tampilkan peta mini visual dari koordinat foto",
                                        checked = minimapState,
                                        onCheckedChange = { viewModel.setMinimap(it) }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                    ToggleOptionRowStyled(
                                        title = "Layar Selalu Menyala",
                                        desc = "Cegah layar mati otomatis saat aplikasi dibuka (30 Menit)",
                                        checked = keepScreenOnState,
                                        onCheckedChange = { viewModel.setKeepScreenOn(it) }
                                    )
                                }
                            }
                        }
                    }

                    // Section 1.5: Kostumisasi Watermark
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            CollapsibleHeaderCompact(
                                title = "Kostumisasi Watermark",
                                subtitle = "Warna, judul, dan ikon khusus",
                                icon = Icons.Default.Palette,
                                isOpen = activeCollSection == "watermark_custom",
                                onClick = { toggleSection("watermark_custom") }
                            )

                            AnimatedVisibility(visible = activeCollSection == "watermark_custom") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Live WYSIWYG watermark preview
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Preview Cap Air Langsung (Live WYSIWYG)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        WatermarkLivePreview(viewModel = viewModel)
                                    }

                                    val wmTitle by viewModel.wmTitle.collectAsState()
                                    val wmDanruLabel by viewModel.wmDanruLabel.collectAsState()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = wmTitle,
                                            onValueChange = { viewModel.setWmTitle(it) },
                                            label = { Text("Judul Cap Air") },
                                            modifier = Modifier.weight(1.1f),
                                            shape = RoundedCornerShape(11.dp),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = wmDanruLabel,
                                            onValueChange = { viewModel.setWmDanruLabel(it) },
                                            label = { Text("Label Jabatan") },
                                            placeholder = { Text("Contoh: Danru") },
                                            modifier = Modifier.weight(0.9f),
                                            shape = RoundedCornerShape(11.dp),
                                            singleLine = true
                                        )
                                    }
                                    
                                    val wmType by viewModel.wmType.collectAsState()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("Tema", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(60.dp))
                                        
                                        val segmentBg = if (currentTheme == "dark") Color(0xFF2E3A4E) else Color(0xFFF1F5F9)
                                        Row(
                                            modifier = Modifier.weight(1f)
                                                .background(segmentBg, RoundedCornerShape(14.dp))
                                                .padding(3.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            com.sipedas.ponorogo.utils.WatermarkType.values().forEach { type ->
                                                val isSelected = type == wmType
                                                val label = when(type) {
                                                    com.sipedas.ponorogo.utils.WatermarkType.DEFAULT -> "Default"
                                                    com.sipedas.ponorogo.utils.WatermarkType.FLOATING -> "Terapung"
                                                    com.sipedas.ponorogo.utils.WatermarkType.SIMPLE -> "Klasik"
                                                }
                                                val icon = when(type) {
                                                    com.sipedas.ponorogo.utils.WatermarkType.DEFAULT -> Icons.Default.ViewAgenda
                                                    com.sipedas.ponorogo.utils.WatermarkType.FLOATING -> Icons.Default.Layers
                                                    com.sipedas.ponorogo.utils.WatermarkType.SIMPLE -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                                }
                                                
                                                val animBgColor by androidx.compose.animation.animateColorAsState(
                                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                                )
                                                val animTextColor by androidx.compose.animation.animateColorAsState(
                                                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (currentTheme == "dark") Color(0xFFCBD5E1) else Color(0xFF64748B)
                                                )
                                                
                                                Button(
                                                    onClick = { viewModel.setWmType(type) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = animBgColor,
                                                        contentColor = animTextColor
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp).height(34.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    elevation = if (isSelected) ButtonDefaults.buttonElevation(1.dp) else ButtonDefaults.buttonElevation(0.dp)
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    val wmColor by viewModel.wmColor.collectAsState()
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Warna Teks", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            TextButton(
                                                onClick = { viewModel.setWmColor("#fff500") },
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Reset Default", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                            }
                                        }
                                        HsvColorPicker(
                                            colorHex = wmColor,
                                            onColorChanged = { viewModel.setWmColor(it) }
                                        )
                                    }
                                    
                                    val wmIconUri by viewModel.wmIconUri.collectAsState()
                                    val context = LocalContext.current
                                    val imagePickerLauncher = rememberLauncherForActivityResult(
                                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                                    ) { uri ->
                                        if (uri != null) {
                                            val mimeType = context.contentResolver.getType(uri)
                                            if (mimeType == "image/png" || mimeType == "image/x-png") {
                                                viewModel.setWmIconUri(uri.toString())
                                            } else {
                                                Toast.makeText(context, "Ikon wajib berformat PNG", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("Ikon", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(60.dp))
                                        
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (wmIconUri != null) {
                                                    androidx.compose.foundation.Image(
                                                        painter = coil.compose.rememberAsyncImagePainter(model = wmIconUri),
                                                        contentDescription = "Selected Icon",
                                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Image, contentDescription = "Default Icon", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            
                                            Button(
                                                onClick = { imagePickerLauncher.launch("image/png") },
                                                modifier = Modifier.weight(1f).height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(if (wmIconUri != null) "Ganti PNG" else "Pilih PNG", fontSize = 11.sp, maxLines = 1)
                                            }
                                            
                                            if (wmIconUri != null) {
                                                TextButton(
                                                    onClick = { viewModel.setWmIconUri(null) },
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Text("Hapus", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                    
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    
                                    val wmSizeTitle by viewModel.wmSizeTitle.collectAsState()
                                    val wmSizeDate by viewModel.wmSizeDate.collectAsState()
                                    val wmSizeLoc by viewModel.wmSizeLoc.collectAsState()
                                    val wmSizeCoord by viewModel.wmSizeCoord.collectAsState()

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Sizing & Skala Font Cap Air", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                            TextButton(
                                                onClick = { viewModel.resetWmSizes() },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Reset Ukuran", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // 1. Judul
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Judul", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    Text(String.format(java.util.Locale.US, "%.1fx", wmSizeTitle), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(
                                                    value = wmSizeTitle,
                                                    onValueChange = { viewModel.setWmSizeTitle(it) },
                                                    valueRange = 0.6f..1.8f,
                                                    steps = 12,
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                            // 2. Tanggal
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Waktu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    Text(String.format(java.util.Locale.US, "%.1fx", wmSizeDate), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(
                                                    value = wmSizeDate,
                                                    onValueChange = { viewModel.setWmSizeDate(it) },
                                                    valueRange = 0.6f..1.8f,
                                                    steps = 12,
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // 3. Lokasi
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Alamat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    Text(String.format(java.util.Locale.US, "%.1fx", wmSizeLoc), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(
                                                    value = wmSizeLoc,
                                                    onValueChange = { viewModel.setWmSizeLoc(it) },
                                                    valueRange = 0.6f..1.8f,
                                                    steps = 12,
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                            // 4. Koordinat
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Koordinat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    Text(String.format(java.util.Locale.US, "%.1fx", wmSizeCoord), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Slider(
                                                    value = wmSizeCoord,
                                                    onValueChange = { viewModel.setWmSizeCoord(it) },
                                                    valueRange = 0.6f..1.8f,
                                                    steps = 12,
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }





                    // Section 3: Guides Circular info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            CollapsibleHeaderCompact(
                                title = "Panduan Penggunaan",
                                subtitle = "Alur operasional di lapangan",
                                icon = Icons.Default.Info,
                                isOpen = activeCollSection == "guide",
                                onClick = { toggleSection("guide") }
                            )

                            AnimatedVisibility(visible = activeCollSection == "guide") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    
                                    GuideItem(
                                        step = 1,
                                        title = "Isi Teks Laporan",
                                        desc = "Salin & tempel teks laporan dari WhatsApp. Memuat Detail SPT, Tanggal, Personil, dan Danru secara otomatis."
                                    )
                                    GuideItem(
                                        step = 2,
                                        title = "Abadikan Foto Kamera",
                                        desc = "Gunakan tombol kamera, sensor akan otomatis membubuhkan koordinat GPS, segmen jalan, dan QR Map visual di cap air."
                                    )
                                    GuideItem(
                                        step = 3,
                                        title = "Simpan local Draf",
                                        desc = "Gunakan tombol draf jika hendak menunda pekerjaan untuk di-load kembali sewaktu-waktu."
                                    )
                                    GuideItem(
                                        step = 4,
                                        title = "Kirim / Transfer Laporan",
                                        desc = "Tekan Kirim Laporan untuk otomatis mengunggah semua foto ke Google Drive dan baris data ke Google Spreadsheet jika API terkonfigurasi."
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTheme == "light") Color(0xFF1a65d6) else Color(0xFF2673d3)
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan & Tutup", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ToggleOptionRowStyled(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun CollapsibleHeaderCompact(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isOpen: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            imageVector = if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}



@Composable
fun GuideItem(
    step: Int,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "$step", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadDraftDialog(
    viewModel: SipedasViewModel,
    onDismiss: () -> Unit
) {
    val onlineDraftsJsonStr by viewModel.onlineDraftsJson.collectAsState()
    val currentTheme by viewModel.theme.collectAsState()
    
    var draftToDelete by remember { mutableStateOf<String?>(null) }

    val onlineDrafts: List<JSONObject> = remember(onlineDraftsJsonStr) {
        val list = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(onlineDraftsJsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) list.add(obj)
            }
        } catch (e: Exception) {
            android.util.Log.e("SIPEDAS", "Error parsing online drafts JSON: ${e.message}")
        }
        list.sortedByDescending { it.optString("timestamp", "") }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchOnlineDrafts()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = if (currentTheme == "light") {
                                    listOf(Color(0xFF1a4fba), Color(0xFF1a65d6))
                                } else {
                                    listOf(Color(0xFF1e293b), Color(0xFF0f172a))
                                }
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("Draf Cloud", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Sub-header description
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Wifi, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Mengambil draf dari server cloud", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onlineDrafts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(42.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tidak ada draf cloud di database.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Draf akan muncul setelah ditransfer ke cloud.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(onlineDrafts) { draftObj: JSONObject ->
                                val id = draftObj.optString("id", "DRAFT-ONLINE")
                                val danru = draftObj.optString("danru", "—")
                                val timestamp = draftObj.optString("timestamp", "—")
                                val text = draftObj.optString("laporan", "")

                                val photoUrls = remember(draftObj) {
                                    val urls = mutableListOf<String>()
                                    val photosArr = draftObj.optJSONArray("photos")
                                    if (photosArr != null) {
                                        for (i in 0 until photosArr.length()) {
                                            val pObj = photosArr.optJSONObject(i)
                                            if (pObj != null) {
                                                val cloudUrl = pObj.optString("cloudinaryUrl", "")
                                                if (cloudUrl.isNotEmpty()) {
                                                    urls.add(cloudUrl)
                                                } else {
                                                    val localPath = pObj.optString("filePath", "")
                                                    if (localPath.isNotEmpty()) {
                                                        urls.add(localPath)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    urls
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.loadOnlineDraft(draftObj)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                Text(
                                                    text = id,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Danru: $danru • $timestamp",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )

                                            if (photoUrls.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    photoUrls.take(6).forEach { url ->
                                                        Card(
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier.size(36.dp),
                                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                                        ) {
                                                            androidx.compose.foundation.Image(
                                                                painter = rememberAsyncImagePainter(
                                                                    model = if (url.startsWith("http")) url else File(url)
                                                                ),
                                                                contentDescription = null,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                    }
                                                    if (photoUrls.size > 6) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "+${photoUrls.size - 6}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = { 
                                                draftToDelete = id
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus",
                                                tint = Color(0xFFef4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        draftToDelete?.let { draftId ->
            ProfessionalConfirmDialog(
                title = "Hapus Draf?",
                message = "Draf laporan ini akan dihapus permanen dari server. Lanjutkan?",
                icon = Icons.Default.Delete,
                iconColor = MaterialTheme.colorScheme.error,
                confirmText = "Hapus",
                cancelText = "Batal",
                onConfirm = {
                    viewModel.deleteOnlineDraft(draftId)
                    draftToDelete = null
                },
                onDismiss = { draftToDelete = null }
            )
        }
    }
}

@Composable
fun TentangAplikasiDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .shadow(16.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Card(
                    shape = CircleShape,
                    border = BorderStroke(2.dp, Color(0xFFFF9100)),
                    modifier = Modifier.size(80.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = R.drawable.sipedas_logo),
                        contentDescription = "SIPEDAS Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Text(
                    text = "SIPEDAS",
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFFEA00),
                                Color(0xFFFF9100),
                                Color(0xFFFF3D00)
                            )
                        )
                    ),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                
                Text(
                    text = "SISTEM INFORMASI PEDESTRIAN\nDAN AKSI SATGAS LINMAS",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = R.drawable.basith),
                        contentDescription = "Ahmad Abdul Basith",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFFFF9100), CircleShape)
                    )
                    Column {
                        Text(
                            text = "Ahmad Abdul Basith, S.Tr.I.P",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pengembang Sistem",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "ASN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "SIPEDAS adalah aplikasi yang dikembangkan dengan bahasa pemrograman Kotlin guna meningkatkan performa, efisiensi, dan optimalisasi layanan dalam mendukung kegiatan Satgas Linmas Pedestrian Kabupaten Ponorogo.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Justify,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SipedasCameraView(
    viewModel: SipedasViewModel,
    onPhotoCaptured: (Uri, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // States
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var flashEnabled by remember { mutableStateOf(false) }
    var captureLoading by remember { mutableStateOf(false) }
    
    // Timer & Flash states
    var timerValue by remember { mutableStateOf(0) }
    var showTimerOptions by remember { mutableStateOf(false) }
    var countdownRemaining by remember { mutableStateOf(0) }
    var isSimulatingFlash by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) {
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }
    
    DisposableEffect(isSimulatingFlash) {
        val window = activity?.window
        if (isSimulatingFlash && window != null) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = layoutParams
        }
        onDispose {
            if (window != null) {
                val layoutParams = window.attributes
                layoutParams.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = layoutParams
            }
        }
    }
    
    // Live watermark text details
    val textState by viewModel.reportText.collectAsState()
    val parsedData = remember(textState) { SipedasParser.parseLaporan(textState) }
    val danruName = parsedData.namaDanru.ifEmpty { "—" }
    
    val manualLat by viewModel.manualLat.collectAsState()
    val manualLng by viewModel.manualLng.collectAsState()
    
    val wmTitle by viewModel.wmTitle.collectAsState()
    val wmDanruLabel by viewModel.wmDanruLabel.collectAsState()
    val wmColor by viewModel.wmColor.collectAsState()
    val wmIconUri by viewModel.wmIconUri.collectAsState()
    val wmType by viewModel.wmType.collectAsState()
    
    var isLandscape by remember { mutableStateOf(false) }
    var gridEnabled by remember { mutableStateOf(false) }
    var isMirrorEnabled by remember { mutableStateOf(false) }

    // Format live time string
    var liveTimeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            liveTimeStr = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date()) + " WIB"
            kotlinx.coroutines.delay(1000)
        }
    }
    
    // CameraX objects
    @Suppress("DEPRECATION")
    val preview = remember { Preview.Builder().setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3).build() }
    val imageCapture = remember { 
        @Suppress("DEPRECATION")
        val builder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
        builder.build()
    }
    val cameraSelector = remember(lensFacing) {
        CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
    }
    val previewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    
    // Bind camera to lifecycle when lensFacing changes or lifecycle updates
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    
    LaunchedEffect(lensFacing) {
        try {
            val cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        continuation.resume(future.get())
                    } catch (e: Exception) {
                        // fallback or direct resumption
                    }
                }, ContextCompat.getMainExecutor(context))
            }
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        future.get().unbindAll()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Toggle torch mode when flashEnabled changes
    LaunchedEffect(flashEnabled, camera) {
        try {
            camera?.cameraControl?.enableTorch(flashEnabled)
            imageCapture.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    LaunchedEffect(isLandscape) {
        imageCapture.targetRotation = if (isLandscape) android.view.Surface.ROTATION_90 else android.view.Surface.ROTATION_0
    }

    // Intercept back button events to close the camera view cleanly
    androidx.activity.compose.BackHandler {
        onDismiss()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {} // Capture and completely absorb clicks to prevent background leakage
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner box 3:4 constraint
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f/4f)
        ) {
            val containerWidth = constraints.maxWidth
            val containerHeight = constraints.maxHeight
            // 1. Camera Live Preview
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Reverse the default CameraX preview mirror if user wants normal output
                        if (lensFacing == CameraSelector.LENS_FACING_FRONT && !isMirrorEnabled) {
                            scaleX = -1f
                        } else {
                            scaleX = 1f
                        }
                    }
            )
            
            // Grid Overlay
            if (gridEnabled && !showTimerOptions) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 1.dp.toPx()
                    val wp = size.width / 3f
                    val hp = size.height / 3f
                    drawLine(color = Color.White.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(wp, 0f), end = androidx.compose.ui.geometry.Offset(wp, size.height), strokeWidth = strokeWidth)
                    drawLine(color = Color.White.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(wp*2, 0f), end = androidx.compose.ui.geometry.Offset(wp*2, size.height), strokeWidth = strokeWidth)
                    drawLine(color = Color.White.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(0f, hp), end = androidx.compose.ui.geometry.Offset(size.width, hp), strokeWidth = strokeWidth)
                    drawLine(color = Color.White.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(0f, hp*2), end = androidx.compose.ui.geometry.Offset(size.width, hp*2), strokeWidth = strokeWidth)
                }
            }
            
            // 2. Real-time Watermark Overlay
            var previewOverlay by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            val previewWidth = context.resources.displayMetrics.widthPixels
            val previewHeight = context.resources.displayMetrics.heightPixels
            
            var liveLat by remember { mutableStateOf(manualLat.toDoubleOrNull()) }
            var liveLng by remember { mutableStateOf(manualLng.toDoubleOrNull()) }
            
            DisposableEffect(Unit) {
                val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        result.lastLocation?.let { loc ->
                            val isMock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                loc.isMock
                            } else {
                                @Suppress("DEPRECATION")
                                loc.isFromMockProvider
                            }
                            if (isMock) {
                                android.widget.Toast.makeText(context, "Mock Location (GPS Palsu) terdeteksi! Mohon gunakan GPS asli.", android.widget.Toast.LENGTH_LONG).show()
                                liveLat = null
                                liveLng = null
                            } else {
                                liveLat = loc.latitude
                                liveLng = loc.longitude
                            }
                        }
                    }
                }
                if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val locationRequest = com.google.android.gms.location.LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
                    fusedClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper())
                    fusedClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            val isMock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                loc.isMock
                            } else {
                                @Suppress("DEPRECATION")
                                loc.isFromMockProvider
                            }
                            if (isMock) {
                                android.widget.Toast.makeText(context, "Mock Location (GPS Palsu) terdeteksi! Mohon gunakan GPS asli.", android.widget.Toast.LENGTH_LONG).show()
                                liveLat = null
                                liveLng = null
                            } else {
                                liveLat = loc.latitude
                                liveLng = loc.longitude
                            }
                        }
                    }
                }
                onDispose {
                    fusedClient.removeLocationUpdates(locationCallback)
                }
            }
            
            val wmSizeTitle by viewModel.wmSizeTitle.collectAsState()
            val wmSizeDate by viewModel.wmSizeDate.collectAsState()
            val wmSizeLoc by viewModel.wmSizeLoc.collectAsState()
            val wmSizeCoord by viewModel.wmSizeCoord.collectAsState()
            
            LaunchedEffect(danruName, liveTimeStr, parsedData.lokasi, liveLat, liveLng, manualLat, manualLng, isLandscape, wmTitle, wmColor, wmIconUri, wmDanruLabel, wmType, containerWidth, containerHeight, wmSizeTitle, wmSizeDate, wmSizeLoc, wmSizeCoord) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val finalLat = liveLat ?: manualLat.toDoubleOrNull()
                        val finalLng = liveLng ?: manualLng.toDoubleOrNull()
                        
                        var realTimeAddress = parsedData.lokasi
                        if (finalLat != null && finalLng != null) {
                            try {
                                @Suppress("DEPRECATION")
                                val geocoder = android.location.Geocoder(context, java.util.Locale("id", "ID"))
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(finalLat, finalLng, 1)
                                if (addresses?.isNotEmpty() == true) {
                                    val resolved = com.sipedas.ponorogo.utils.MapHelpers.formatAddressDetailed(addresses[0])
                                    if (resolved.isNotEmpty()) realTimeAddress = resolved
                                }
                            } catch (e: Exception) {}
                            
                            if (realTimeAddress.isEmpty() || realTimeAddress == parsedData.lokasi) {
                                realTimeAddress = String.format(java.util.Locale.US, "%.6f, %.6f", finalLat, finalLng)
                            }
                        }

                        val w = if (isLandscape) containerHeight else containerWidth
                        val fullH = if (isLandscape) containerWidth else containerHeight
                        val wmConfig = com.sipedas.ponorogo.utils.WatermarkConfig(
                            title = wmTitle, 
                            color = wmColor, 
                            iconUri = wmIconUri, 
                            danruLabel = wmDanruLabel,
                            type = wmType,
                            fontSizeTitle = wmSizeTitle,
                            fontSizeDate = wmSizeDate,
                            fontSizeLoc = wmSizeLoc,
                            fontSizeCoord = wmSizeCoord
                        )
                        val h = com.sipedas.ponorogo.utils.WatermarkHelper.calculateRequiredHeight(context, w, fullH, realTimeAddress, wmConfig)
                        val overlay = com.sipedas.ponorogo.utils.WatermarkHelper.createWatermarkOverlay(
                            context = context,
                            overlayWidth = w,
                            overlayHeight = h,
                            danru = danruName,
                            timeStr = liveTimeStr,
                            address = realTimeAddress, 
                            lat = finalLat,
                            lng = finalLng,
                            config = wmConfig
                        )
                        previewOverlay = overlay.asImageBitmap()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                previewOverlay?.let { overlay ->
                    if (isLandscape) {
                        // For landscape, place watermark on the left side (which is rotated bottom)
                        androidx.compose.ui.layout.Layout(
                            modifier = Modifier.fillMaxSize(),
                            content = {
                                androidx.compose.foundation.Image(
                                    bitmap = overlay,
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                                )
                            }
                        ) { measurables, constraints ->
                            val screenW = constraints.maxWidth
                            val screenH = constraints.maxHeight
                            val wLayer = containerHeight
                            val hLayer = overlay.height
                            val placeable = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(wLayer, hLayer))
                            
                            layout(screenW, screenH) {
                                // To place it exactly on the left edge (rotated, flowing left-to-right) matching the 3:4 container bounds
                                placeable.placeWithLayer(
                                    x = hLayer,
                                    y = 0
                                ) {
                                    rotationZ = 90f
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                }
                            }
                        }
                    } else {
                        androidx.compose.foundation.Image(
                            bitmap = overlay,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        
        // 3. Top Toolbar buttons (Grid, Timer options, Flip, Rotate)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3a. Grid Toggle
                IconButton(
                    onClick = {
                        triggerHapticFeedback(context)
                        gridEnabled = !gridEnabled
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (gridEnabled) Icons.Default.GridOn else Icons.Default.GridOff,
                        contentDescription = "Grid",
                        tint = if (gridEnabled) Color(0xFFffd200) else Color.White,
                        modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
                    )
                }

                // 3b. Timer Toggle and options (Directly next to Grid)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { 
                            triggerHapticFeedback(context)
                            showTimerOptions = !showTimerOptions 
                        }, 
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (timerValue > 0) Color(0xFFffd200) else Color.Transparent,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer, 
                            contentDescription = "Timer", 
                            tint = if (timerValue > 0) Color.Black else Color.White, 
                            modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
                        )
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTimerOptions,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            listOf(0, 3, 5, 10).forEach { t ->
                                val isSelected = timerValue == t
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFFffd200).copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            triggerHapticFeedback(context)
                                            timerValue = t
                                            showTimerOptions = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (t == 0) "Off" else "${t}s", 
                                        color = if (isSelected) Color(0xFFffd200) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, 
                                        modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Small modern visual divider
                Box(
                    modifier = Modifier
                        .size(1.dp, 20.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )

                // 3c. Mirror Camera Toggle
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    IconButton(
                        onClick = {
                            triggerHapticFeedback(context)
                            isMirrorEnabled = !isMirrorEnabled
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flip,
                            contentDescription = "Mirror Photo",
                            tint = if (isMirrorEnabled) Color(0xFFffd200) else Color.White,
                            modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
                        )
                    }
                }

                // 3d. Screen Orientation Rotate button
                IconButton(
                    onClick = {
                        triggerHapticFeedback(context)
                        isLandscape = !isLandscape
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isLandscape) Color(0xFFffd200) else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Rotate Portrait/Landscape",
                        tint = if (isLandscape) Color.Black else Color.White,
                        modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
                    )
                }

                // 3d. Flip Camera Button
                IconButton(
                    onClick = {
                        triggerHapticFeedback(context)
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            CameraSelector.LENS_FACING_BACK
                        } else {
                            CameraSelector.LENS_FACING_FRONT
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
                    )
                }
            }
        }
        
        // 4. Bottom Capture controls (Trigger Button, Flash, Dismiss)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Dismiss (Left)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Keluar",
                        tint = Color.White,
                        modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
                    )
                }
            }

            // Capture Button (Center)
            if (captureLoading || countdownRemaining > 0) {
                if (countdownRemaining > 0) {
                    Text(
                        text = countdownRemaining.toString(),
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 16.dp) // shift up slightly above grid
                            .rotate(if (isLandscape) 90f else 0f)
                    )
                } else {
                    CircularProgressIndicator(
                        color = Color(0xFFffd200),
                        modifier = Modifier.size(44.dp)
                    )
                }
            } else {
                Surface(
                    onClick = {
                        if (captureLoading || countdownRemaining > 0) return@Surface
                        triggerHapticFeedback(context)
                        
                        coroutineScope.launch {
                            if (timerValue > 0) {
                                for (i in timerValue downTo 1) {
                                    countdownRemaining = i
                                    triggerHapticFeedback(context)
                                    kotlinx.coroutines.delay(1000)
                                }
                                countdownRemaining = 0
                            }
                            
                            val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
                            if (isFront && flashEnabled) {
                                isSimulatingFlash = true
                                kotlinx.coroutines.delay(300) // Allow brightness to ramp up
                            }
                            
                            captureLoading = true
                            val outputFile = File(context.cacheDir, "temp_sipedas_shoot_${System.currentTimeMillis()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                            
                            val mirrorNow = isFront && isMirrorEnabled

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        captureLoading = false
                                        isSimulatingFlash = false
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            outputFile
                                        )
                                        // Trigger haptic after capturing
                                        triggerHapticFeedback(context)
                                        onPhotoCaptured(uri, mirrorNow)
                                    }
                                    
                                    override fun onError(exception: ImageCaptureException) {
                                        captureLoading = false
                                        isSimulatingFlash = false
                                        exception.printStackTrace()
                                        Toast.makeText(context, "Gagal mengambil foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    },
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(4.dp, Color(0xFFffd200)),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            // Flash (Right)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (flashEnabled) Color(0xFFFF9800).copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f)
                ),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                IconButton(
                    onClick = {
                        triggerHapticFeedback(context)
                        flashEnabled = !flashEnabled
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch Toggle",
                        tint = Color.White,
                        modifier = Modifier.rotate(if (isLandscape) 90f else 0f)
                    )
                }
            }
        }
        
        if (isSimulatingFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun HsvColorPicker(
    colorHex: String,
    onColorChanged: (String) -> Unit
) {
    val initialColor = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color(0xFFFFF500) }
    var hsv by remember { 
        val hsvArr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsvArr)
        mutableStateOf(hsvArr) 
    }
    
    LaunchedEffect(colorHex) {
        val currentHex = String.format("#%06X", (0xFFFFFF and android.graphics.Color.HSVToColor(hsv)))
        val incomingHex = try {
            val color = Color(android.graphics.Color.parseColor(colorHex))
            String.format("#%06X", (0xFFFFFF and color.toArgb()))
        } catch (e: Exception) {
            colorHex
        }
        if (currentHex.uppercase() != incomingHex.uppercase()) {
            val hsvArr = FloatArray(3)
            android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(colorHex), hsvArr)
            hsv = hsvArr
        }
    }
    
    val pickedColor = Color(android.graphics.Color.HSVToColor(hsv))
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(pickedColor, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text("Hex: ${String.format("#%06X", (0xFFFFFF and pickedColor.toArgb()))}", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Warna Utama", style = MaterialTheme.typography.bodySmall)
        Box(modifier = Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(16.dp))) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 1f, 1f))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(60f, 1f, 1f))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(120f, 1f, 1f))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(180f, 1f, 1f))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(240f, 1f, 1f))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(300f, 1f, 1f))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(360f, 1f, 1f)))
                    )
                )
                drawRect(brush)
            }
            androidx.compose.material3.Slider(
                value = hsv[0],
                onValueChange = { 
                    hsv = floatArrayOf(it, hsv[1], hsv[2])
                    onColorChanged(String.format("#%06X", (0xFFFFFF and android.graphics.Color.HSVToColor(hsv))))
                },
                valueRange = 0f..360f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Text("Kepekatan Warna", style = MaterialTheme.typography.bodySmall)
        androidx.compose.material3.Slider(
            value = hsv[1],
            onValueChange = { 
                hsv = floatArrayOf(hsv[0], it, hsv[2])
                onColorChanged(String.format("#%06X", (0xFFFFFF and android.graphics.Color.HSVToColor(hsv))))
            },
            valueRange = 0f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = pickedColor, activeTrackColor = pickedColor)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        Text("Kecerahan", style = MaterialTheme.typography.bodySmall)
        androidx.compose.material3.Slider(
            value = hsv[2],
            onValueChange = { 
                hsv = floatArrayOf(hsv[0], hsv[1], it)
                onColorChanged(String.format("#%06X", (0xFFFFFF and android.graphics.Color.HSVToColor(hsv))))
            },
            valueRange = 0f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.DarkGray, activeTrackColor = Color.DarkGray)
        )
    }
}

@Composable
fun ProfessionalConfirmDialog(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    confirmText: String = "Ya",
    cancelText: String = "Batal",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDestructive = confirmText == "Hapus" || confirmText == "Kosongkan" || iconColor == MaterialTheme.colorScheme.error
    val finalIconColor = if (isDestructive) Color(0xFFD32F2F) else iconColor

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Header with circle background
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(finalIconColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = finalIconColor, modifier = Modifier.size(32.dp))
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(28.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(cancelText, fontWeight = FontWeight.SemiBold)
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = finalIconColor)
                    ) {
                        Text(confirmText, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ArrangePhotosDialog(photos: List<PhotoItem>, onDismiss: () -> Unit, onSwap: (Int, Int) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Tata Urutan Foto (${photos.size}/20)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tahan dan geser foto untuk mengatur posisi. (5 kolom)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    val configWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                    val itemWidth = ((configWidth * 0.95f - 32f) / 5f).dp
                    val itemWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { itemWidth.toPx() }
                    val itemHeightPx = itemWidthPx * 1.33f
                    val itemHeight = itemWidth * 1.33f

                    photos.forEachIndexed { index, photo ->
                        androidx.compose.runtime.key(photo.id) {
                            var dragOffset by remember { mutableStateOf(Offset.Zero) }
                            var isDragging by remember { mutableStateOf(false) }
    
                            val targetX = (index % 5) * itemWidthPx
                            val targetY = (index / 5) * itemHeightPx
                            
                            val animTargetX by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = targetX, 
                                label = "tx",
                                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                            )
                            val animTargetY by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = targetY, 
                                label = "ty",
                                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                            )
    
                            Box(
                                modifier = Modifier
                                    .offset { 
                                        if (isDragging) {
                                            IntOffset((targetX + dragOffset.x).roundToInt(), (targetY + dragOffset.y).roundToInt())
                                        } else {
                                            IntOffset(animTargetX.roundToInt(), animTargetY.roundToInt())
                                        }
                                    }
                                    .size(itemWidth, itemHeight)
                                    .padding(2.dp)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .pointerInput(photo.id) {
                                        detectDragGestures(
                                            onDragStart = { isDragging = true },
                                            onDragEnd = { isDragging = false; dragOffset = Offset.Zero },
                                            onDragCancel = { isDragging = false; dragOffset = Offset.Zero },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                                
                                                val currentCenterX = targetX + dragOffset.x + itemWidthPx / 2
                                                val currentCenterY = targetY + dragOffset.y + itemHeightPx / 2
                                                
                                                val hitCol = (currentCenterX / itemWidthPx).toInt().coerceIn(0, 4)
                                                val hitRow = (currentCenterY / itemHeightPx).toInt()
                                                val hitIndex = hitRow * 5 + hitCol
                                                
                                                if (hitIndex != index && hitIndex in photos.indices) {
                                                    onSwap(index, hitIndex)
                                                    // Inverse adjust offset smoothly
                                                    val newTargetX = (hitIndex % 5) * itemWidthPx
                                                    val newTargetY = (hitIndex / 5) * itemHeightPx
                                                    dragOffset += Offset(targetX - newTargetX, targetY - newTargetY)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = File(photo.path)),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .align(Alignment.TopStart)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Selesai")
                }
            }
        }
    }
}

@Composable
fun TransferProgressModal(
    progress: Int,
    label: String,
    onDismiss: () -> Unit = {}
) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "smooth_transfer_progress"
    )
    val displayProgressPercent = (animatedProgress * 100f).toInt()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp)
                    .testTag("transfer_progress_modal")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(90.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
                        )
                        
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(86.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                        
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Cloud Uploading logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Transfer Draf ke Cloud",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "$displayProgressPercent% Selesai",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun WatermarkLivePreview(
    viewModel: com.sipedas.ponorogo.viewmodel.SipedasViewModel,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wmTitle by viewModel.wmTitle.collectAsState()
    val wmColor by viewModel.wmColor.collectAsState()
    val wmIconUri by viewModel.wmIconUri.collectAsState()
    val wmDanruLabel by viewModel.wmDanruLabel.collectAsState()
    val wmType by viewModel.wmType.collectAsState()
    val wmSizeTitle by viewModel.wmSizeTitle.collectAsState()
    val wmSizeDate by viewModel.wmSizeDate.collectAsState()
    val wmSizeLoc by viewModel.wmSizeLoc.collectAsState()
    val wmSizeCoord by viewModel.wmSizeCoord.collectAsState()

    var previewBitmap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    androidx.compose.runtime.LaunchedEffect(
        wmTitle, wmColor, wmIconUri, wmDanruLabel, wmType,
        wmSizeTitle, wmSizeDate, wmSizeLoc, wmSizeCoord
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val w = 800
                val h = 400
                val dummyBmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(dummyBmp)

                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val gradient = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(),
                    android.graphics.Color.parseColor("#0F172A"), android.graphics.Color.parseColor("#1E293B"), android.graphics.Shader.TileMode.CLAMP)
                paint.shader = gradient
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

                val gridPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#334155")
                    strokeWidth = 1f
                }
                canvas.drawLine(w / 3f, 0f, w / 3f, h.toFloat(), gridPaint)
                canvas.drawLine(w * 2 / 3f, 0f, w * 2 / 3f, h.toFloat(), gridPaint)
                canvas.drawLine(0f, h / 3f, w.toFloat(), h / 3f, gridPaint)
                canvas.drawLine(0f, h * 2 / 3f, w.toFloat(), h * 2 / 3f, gridPaint)

                val wmConfig = com.sipedas.ponorogo.utils.WatermarkConfig(
                    title = wmTitle,
                    color = wmColor,
                    iconUri = wmIconUri,
                    danruLabel = wmDanruLabel,
                    type = wmType,
                    fontSizeTitle = wmSizeTitle,
                    fontSizeDate = wmSizeDate,
                    fontSizeLoc = wmSizeLoc,
                    fontSizeCoord = wmSizeCoord
                )

                val sampleAddress = "Jl. Alun-Alun Selatan, Ponorogo, Jawa Timur, Indonesia"
                val strpH = com.sipedas.ponorogo.utils.WatermarkHelper.calculateRequiredHeight(
                    context, w, h, sampleAddress, wmConfig
                )
                val sy = h - strpH

                com.sipedas.ponorogo.utils.WatermarkHelper.drawSipedasWatermark(
                    context = context,
                    canvas = canvas,
                    w = w,
                    h = h,
                    strpH = strpH,
                    sy = sy,
                    danru = "Satgas Linmas",
                    timeStr = "23 Jun 2026, 08:30:15 WIB",
                    address = sampleAddress,
                    lat = -7.872722,
                    lng = 111.462639,
                    config = wmConfig,
                    allowNetwork = false
                )

                previewBitmap = dummyBmp.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        if (previewBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = previewBitmap!!,
                contentDescription = "Live Watermark Preview",
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
            )
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = androidx.compose.ui.Modifier.size(36.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}


