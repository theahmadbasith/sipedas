package com.sipedas.ponorogo.ui.aduan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sipedas.ponorogo.viewmodel.SipedasViewModel
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

object ReportCopyTimer {
    var copiedTimeMillis: Long = 0L
}

@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f): Brush {
    return if (showShimmer) {
        val baseColor = MaterialTheme.colorScheme.onSurface
        val shimmerColors = listOf(
            baseColor.copy(alpha = 0.08f),
            baseColor.copy(alpha = 0.02f),
            baseColor.copy(alpha = 0.08f),
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_translation"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

@Composable
fun AduanSkeletonItem(shimmerBrush: Brush) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header parts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    // Avatar skeleton
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(shimmerBrush, RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        // Name skeleton
                        Box(
                            modifier = Modifier
                                .size(width = 120.dp, height = 16.dp)
                                .background(shimmerBrush, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Location skeleton
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(shimmerBrush, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 12.dp)
                                    .background(shimmerBrush, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
                // Timestamp skeleton
                Box(
                    modifier = Modifier
                        .size(width = 50.dp, height = 12.dp)
                        .background(shimmerBrush, RoundedCornerShape(4.dp))
                )
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Text line 1
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(14.dp)
                    .background(shimmerBrush, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Text line 2
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .background(shimmerBrush, RoundedCornerShape(4.dp))
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Photo row skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .background(shimmerBrush, RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

fun isWithinLast30Days(rawTimestamp: String): Boolean {
    val trimmed = rawTimestamp.trim()
    if (trimmed.isEmpty() || trimmed == "—") return true
    
    val now = System.currentTimeMillis()
    val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000
    
    val formats = listOf(
        "dd-MM-yyyy HH:mm:ss",
        "dd-MM-yyyy HH:mm",
        "dd-MM-yyyy",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    
    for (fmt in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
            if (fmt.contains("'Z'")) {
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val parsedDate = sdf.parse(trimmed)
            if (parsedDate != null) {
                val diff = now - parsedDate.time
                // Allow up to 2 days in the future (timezone difference) up to 30 days in the past
                return diff in -172800000L..thirtyDaysMillis
            }
        } catch (e: Exception) {
            // continue
        }
    }
    
    // parse raw date space separator dd-MM-yyyy
    try {
        val datePart = trimmed.substringBefore(" ")
        val dateParts = datePart.split("-")
        if (dateParts.size == 3) {
            val day = dateParts[0].toIntOrNull()
            val month = dateParts[1].toIntOrNull()
            val year = dateParts[2].toIntOrNull()
            if (day != null && month != null && year != null) {
                val cal = java.util.Calendar.getInstance()
                cal.set(year, month - 1, day, 12, 0, 0)
                val diff = now - cal.timeInMillis
                return diff in -172800000L..thirtyDaysMillis
            }
        }
    } catch (e: Exception) {
    }
    
    return true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AduanScreen(
    viewModel: SipedasViewModel,
    modifier: Modifier = Modifier
) {
    val onlineAduanJsonStr by viewModel.onlineAduanJson.collectAsState()
    val isLoadingAduan by viewModel.isLoadingAduan.collectAsState()
    
    val onlineAduan: List<JSONObject> = remember(onlineAduanJsonStr) {
        val list = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(onlineAduanJsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val rawTimestamp = listOf(
                        obj.optString("timestamp"),
                        obj.optString("waktu"),
                        obj.optString("tanggal"),
                        obj.optString("createdAt")
                    ).firstOrNull { it.isNotEmpty() } ?: ""
                    
                    if (isWithinLast30Days(rawTimestamp)) {
                        list.add(obj)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SIPEDAS", "Error parsing online aduan JSON in AduanScreen: ${e.message}")
        }
        list.sortedByDescending { it.optString("timestamp", "") }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchOnlineAduan(force = false)
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Sticky Header without Refresh Button (moved to TopBar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Daftar Aduan",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Laporan dan aduan masyarakat dari Web Sapapedestrian",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isLoadingAduan && onlineAduan.isEmpty()) {
            val brush = shimmerBrush()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(3) {
                    AduanSkeletonItem(shimmerBrush = brush)
                }
            }
        } else if (onlineAduan.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Belum ada aduan masuk.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Menunggu data dimuat dari server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(onlineAduan) { aduanObj ->
                    AduanCard(aduanObj)
                }
            }
        }
    }
}

@Composable
fun AduanCard(aduanObj: JSONObject) {
    val id = aduanObj.optString("id", "DRAFT")
    
    // Fallback for timestamp
    val rawTimestamp = listOf(
        aduanObj.optString("timestamp"),
        aduanObj.optString("waktu"),
        aduanObj.optString("tanggal"),
        aduanObj.optString("createdAt")
    ).firstOrNull { it.isNotEmpty() } ?: "—"

    // Fallback for text
    val text = listOf(
        aduanObj.optString("aduan"),
        aduanObj.optString("laporan"),
        aduanObj.optString("deskripsi"),
        aduanObj.optString("keterangan"),
        aduanObj.optString("pesan")
    ).firstOrNull { it.isNotEmpty() } ?: "Tidak ada teks aduan"

    // Check if there is parsed data (from older implementation or similar)
    val parsedObj = aduanObj.optJSONObject("parsed")

    // Fallback for names
    val pelaporInfo = if (parsedObj != null && parsedObj.optString("namaDanru").isNotEmpty()) {
        parsedObj.optString("namaDanru")
    } else {
        listOf(
            aduanObj.optString("nama"),
            aduanObj.optString("pelapor"),
            aduanObj.optString("danru"),
            aduanObj.optString("pengirim")
        ).firstOrNull { it.isNotEmpty() } ?: "Anonim"
    }

    // Fallback for location
    val lokasi = if (parsedObj != null && parsedObj.optString("lokasi").isNotEmpty()) {
        parsedObj.optString("lokasi")
    } else {
        listOf(
            aduanObj.optString("lokasi"),
            aduanObj.optString("alamat"),
            aduanObj.optString("tempat")
        ).firstOrNull { it.isNotEmpty() } ?: "Lokasi tidak diketahui"
    }

    // Collect photos
    val cloudinaryUrls = mutableListOf<String>()
    
    // First try "photos" array
    val photosArray = aduanObj.optJSONArray("photos")
    if (photosArray != null) {
        for (i in 0 until photosArray.length()) {
            val pObj = photosArray.optJSONObject(i)
            if (pObj != null) {
                val url = pObj.optString("cloudinaryUrl").takeIf { it.isNotEmpty() } ?: pObj.optString("url")
                if (url.isNotEmpty() && url.startsWith("http")) {
                    cloudinaryUrls.add(url)
                }
            } else {
                val pStr = photosArray.optString(i)
                if (pStr.startsWith("http")) {
                    cloudinaryUrls.add(pStr)
                }
            }
        }
    }

    // Next try "foto", "photo", "image", "imageUrl" flat string fields (e.g., if array doesn't exist)
    if (cloudinaryUrls.isEmpty()) {
        listOf("foto", "photo", "image", "imageUrl", "cloudinaryUrl").forEach { key ->
            val flatUrl = aduanObj.optString(key)
            if (flatUrl.startsWith("http")) {
                cloudinaryUrls.add(flatUrl)
            }
        }
    }
    
    val totalPhotos = cloudinaryUrls.size

    var showActionDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showActionDialog = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = pelaporInfo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lokasi,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Text(
                    text = rawTimestamp,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (cloudinaryUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                // Diplay up to 3 thumbnails side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayCount = minOf(3, cloudinaryUrls.size)
                    for (i in 0 until displayCount) {
                        val isLastAndMore = i == 2 && cloudinaryUrls.size > 3
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = cloudinaryUrls[i],
                                contentDescription = "Foto Aduan",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Overlay indicator if there are more photos
                            if (isLastAndMore) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${cloudinaryUrls.size - 3}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (totalPhotos > 0) {
                 Spacer(modifier = Modifier.height(12.dp))
                 Text(
                     text = "($totalPhotos foto terlampir - namun file foto tidak ditemukan)",
                     fontSize = 11.sp,
                     color = MaterialTheme.colorScheme.error,
                     fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                 )
            }
        }
    }

    if (showActionDialog) {
        AduanDetailDialog(
            pelaporInfo = pelaporInfo,
            lokasi = lokasi,
            rawTimestamp = rawTimestamp,
            text = text,
            cloudinaryUrls = cloudinaryUrls,
            onDismiss = { showActionDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AduanDetailDialog(
    pelaporInfo: String,
    lokasi: String,
    rawTimestamp: String,
    text: String,
    cloudinaryUrls: List<String>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        val anim = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) {
            anim.animateTo(1f, animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .graphicsLayer {
                    alpha = anim.value
                    scaleX = 0.95f + (0.05f * anim.value)
                    scaleY = 0.95f + (0.05f * anim.value)
                }
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(20.dp)
            ) {
                // Header of Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Detail Laporan Aduan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Info Panel
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Pelapor Name Row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Pelapor / Danru",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = pelaporInfo,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Waktu Laporan Row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Waktu Laporan",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = rawTimestamp,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Lokasi Row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Lokasi Laporan",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = lokasi,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Laporan Isi Konten
                    Text(
                        text = "Isi Laporan / Aduan:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    // Display Photos
                    if (cloudinaryUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Foto Lampiran (${cloudinaryUrls.size}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            cloudinaryUrls.forEachIndexed { index, url ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = "Foto Lampiran ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Badge index
                                        Box(
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "Foto ${index + 1}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom button close
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "Tutup Detail",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
