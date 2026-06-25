package com.sipedas.ponorogo.ui.cctv

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rawAzimuth by remember { mutableStateOf(0f) }
    var animatedAzimuth by remember { mutableStateOf(0f) }
    var showCalibrationGuide by remember { mutableStateOf(false) }

    // Smooth orientation wrap handling
    LaunchedEffect(rawAzimuth) {
        val diff = (rawAzimuth - animatedAzimuth) % 360
        val shortestDiff = if (diff < -180) diff + 360 else if (diff > 180) diff - 360 else diff
        animatedAzimuth += shortestDiff
    }

    val rotationAnim by animateFloatAsState(
        targetValue = animatedAzimuth,
        animationSpec = tween(durationMillis = 150),
        label = "compass_rotation"
    )

    var sensorAccuracy by remember { mutableStateOf(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) }
    var sensorStatusText by remember { mutableStateOf("Menginisialisasi...") }
    var hasSensors by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var useRotationVector = rotationVectorSensor != null

        if (!useRotationVector && (magnetometer == null || accelerometer == null)) {
            hasSensors = false
            sensorStatusText = "Sensor Magnetik Tidak Tersedia"
        } else {
            hasSensors = true
            sensorStatusText = "Sensor Aktif"
        }

        // Low-pass filtered arrays to prevent trembling needle jittering
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sensorAccuracy = event.accuracy
                if (useRotationVector && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rMat = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rMat, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rMat, orientation)
                    val azimuthRad = orientation[0]
                    var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                    if (azimuthDeg < 0) azimuthDeg += 360f

                    // Apply standard exponential low-pass smoothing
                    rawAzimuth = if (rawAzimuth == 0f) azimuthDeg else {
                        var diff = azimuthDeg - rawAzimuth
                        if (diff > 180) diff -= 360
                        else if (diff < -180) diff += 360
                        rawAzimuth + 0.18f * diff
                    }
                } else {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        for (i in 0..2) {
                            gravity[i] = gravity[i] + 0.15f * (event.values[i] - gravity[i])
                        }
                        hasGravity = true
                    } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        for (i in 0..2) {
                            geomagnetic[i] = geomagnetic[i] + 0.15f * (event.values[i] - geomagnetic[i])
                        }
                        hasGeomagnetic = true
                    }

                    if (hasGravity && hasGeomagnetic) {
                        val rMat = FloatArray(9)
                        val rI = FloatArray(9)
                        if (SensorManager.getRotationMatrix(rMat, rI, gravity, geomagnetic)) {
                            val orientation = FloatArray(3)
                            SensorManager.getOrientation(rMat, orientation)
                            val azimuthRad = orientation[0]
                            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                            if (azimuthDeg < 0) azimuthDeg += 360f

                            rawAzimuth = if (rawAzimuth == 0f) azimuthDeg else {
                                var diff = azimuthDeg - rawAzimuth
                                if (diff > 180) diff -= 360
                                else if (diff < -180) diff += 360
                                rawAzimuth + 0.18f * diff
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                sensorAccuracy = accuracy
            }
        }

        if (hasSensors) {
            if (useRotationVector) {
                sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
            }
        }

        onDispose {
            if (hasSensors) {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val displayAzimuth = (rawAzimuth % 360).let { if (it < 0) it + 360 else it }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kompas Bantuan Arah",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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

                Spacer(modifier = Modifier.height(6.dp))
                
                // Sensor Accuracy Badge Row
                AccuracyBadge(accuracy = sensorAccuracy, hasSensors = hasSensors)

                Spacer(modifier = Modifier.height(16.dp))

                if (!showCalibrationGuide) {
                    // MAIN COMPASS VIEW
                    Text(
                        text = "Gunakan kompas untuk menentukan arah hadap yang akurat untuk membantu pemantauan patroli kawasan pedestrian.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Compass Widget Area
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Interactive Compass Canvas
                        CompassCanvas(
                            rotation = rotationAnim,
                            accentColor = MaterialTheme.colorScheme.primary,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )

                        // Stationary Center Needle pointer representing current heading direction (true top)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 4.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val w = size.width
                                val h = size.height
                                val path = android.graphics.Path().apply {
                                    moveTo(w / 2f, 0f)
                                    lineTo(w, h)
                                    lineTo(w / 2f, h * 0.75f)
                                    lineTo(0f, h)
                                    close()
                                }
                                drawIntoCanvas { canvas ->
                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.RED
                                        style = android.graphics.Paint.Style.FILL
                                        isAntiAlias = true
                                    }
                                    canvas.nativeCanvas.drawPath(path, paint)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Heading text display
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${displayAzimuth.roundToInt()}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "°",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = getDirectionAbbr(displayAzimuth),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Text(
                        text = getDirectionName(displayAzimuth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Calibration Action Button
                    Button(
                        onClick = { showCalibrationGuide = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kalibrasi Kompas", fontWeight = FontWeight.Bold)
                    }

                } else {
                    // CALIBRATION GUIDE VIEW WITH GORGEOUS FIGURE-8 ANIMATION
                    Text(
                        text = "Cara Kalibrasi Sensor agar Lebih Akurat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Gerakkan handphone Anda membentuk pola angka 8 di udara secara perlahan seperti panduan animasi di bawah ini untuk kalibrasi optimal sensor geomagnetik.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Figure 8 Canvas Animation
                    FigureEightAnimation(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(130.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Accuracy tips block
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Akurasi berubah real-time saat HP diputar. Jauhkan dari benda logam, besi, atau speaker bermagnet besar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showCalibrationGuide = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Selesai Kalibrasi", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AccuracyBadge(accuracy: Int, hasSensors: Boolean) {
    if (!hasSensors) {
        Row(
            modifier = Modifier
                .background(Color(0xFFFEE2E2), RoundedCornerShape(50.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFFEF4444), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Sensor Tidak Didukung",
                color = Color(0xFF991B1B),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    val (badgeBg, badgeText, statusLabel, bulletColor) = when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> {
            Quadruple(Color(0xFFDCFCE7), Color(0xFF166534), "Akurasi Tinggi (Optimal)", Color(0xFF22C55E))
        }
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> {
            Quadruple(Color(0xFFFEF9C3), Color(0xFF854D0E), "Akurasi Sedang", Color(0xFFEAB308))
        }
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> {
            Quadruple(Color(0xFFFFEDD5), Color(0xFF9A3412), "Akurasi Rendah (Goyang HP)", Color(0xFFF97316))
        }
        else -> {
            Quadruple(Color(0xFFFEE2E2), Color(0xFF991B1B), "Butuh Kalibrasi", Color(0xFFEF4444))
        }
    }

    Row(
        modifier = Modifier
            .background(badgeBg, RoundedCornerShape(50.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(bulletColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusLabel,
            color = badgeText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun FigureEightAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "figure_8_anim")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "figure_8_progress"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val rx = w * 0.32f
        val ry = h * 0.22f

        // Build mathematical Lemniscate path (Figure 8)
        val pointsList = mutableListOf<Offset>()
        for (i in 0..120) {
            val t = (i / 120f) * 2.0 * Math.PI
            // Lemniscate of Gerono formula:
            // x = sin(t), y = sin(t)*cos(t)
            val x = (rx * Math.sin(t)).toFloat() + cx
            val y = (ry * Math.sin(t) * Math.cos(t)).toFloat() + cy
            pointsList.add(Offset(x, y))
        }

        // Draw dotted Lemniscate track
        for (i in 0 until pointsList.size - 1) {
            if (i % 3 == 0) {
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.4f),
                    radius = 2.5f,
                    center = pointsList[i]
                )
            }
        }

        // Current particle position
        val tParticle = progress * 2.0 * Math.PI
        val px = (rx * Math.sin(tParticle)).toFloat() + cx
        val py = (ry * Math.sin(tParticle) * Math.cos(tParticle)).toFloat() + cy

        // Draw moving indicator dot with pulse glow
        drawCircle(
            color = Color.Red.copy(alpha = 0.22f),
            radius = 18f,
            center = Offset(px, py)
        )
        drawCircle(
            color = Color.Red,
            radius = 6f,
            center = Offset(px, py)
        )
    }
}

@Composable
fun CompassCanvas(
    rotation: Float,
    accentColor: Color,
    textColor: Color,
    tickColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxSize().padding(18.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            nativeCanvas.save()

            // Rotate entire dial canvas backwards against orientation changes
            nativeCanvas.rotate(-rotation, center.x, center.y)

            // Paint configurations
            val textPaintPrimary = android.graphics.Paint().apply {
                color = textColor.toArgb()
                textSize = 42f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            val textPaintSecondary = android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.6f).toArgb()
                textSize = 24f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            val paintNorth = android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                textSize = 46f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            val tickPaint = android.graphics.Paint().apply {
                color = tickColor.toArgb()
                strokeWidth = 2f
                isAntiAlias = true
            }

            val tickPaintMajor = android.graphics.Paint().apply {
                color = accentColor.toArgb()
                strokeWidth = 4.5f
                isAntiAlias = true
            }

            // Draw ticks around the dial
            for (angle in 0 until 360 step 10) {
                val isMajor = angle % 30 == 0
                val tickLength = if (isMajor) 18f else 10f
                val startX = center.x
                val startY = center.y - radius
                val endX = center.x
                val endY = center.y - radius + tickLength

                nativeCanvas.save()
                nativeCanvas.rotate(angle.toFloat(), center.x, center.y)
                nativeCanvas.drawLine(
                    startX, startY, endX, endY,
                    if (isMajor) tickPaintMajor else tickPaint
                )
                nativeCanvas.restore()
            }

            // Draw Headings
            // North
            nativeCanvas.drawText("U", center.x, center.y - radius + 55f, paintNorth)

            // East
            nativeCanvas.save()
            nativeCanvas.rotate(90f, center.x, center.y)
            nativeCanvas.drawText("T", center.x, center.y - radius + 55f, textPaintPrimary)
            nativeCanvas.restore()

            // South
            nativeCanvas.save()
            nativeCanvas.rotate(180f, center.x, center.y)
            nativeCanvas.drawText("S", center.x, center.y - radius + 55f, textPaintPrimary)
            nativeCanvas.restore()

            // West
            nativeCanvas.save()
            nativeCanvas.rotate(270f, center.x, center.y)
            nativeCanvas.drawText("B", center.x, center.y - radius + 55f, textPaintPrimary)
            nativeCanvas.restore()

            // Draw diagonals
            // Northeast
            nativeCanvas.save()
            nativeCanvas.rotate(45f, center.x, center.y)
            nativeCanvas.drawText("TL", center.x, center.y - radius + 50f, textPaintSecondary)
            nativeCanvas.restore()

            // Southeast
            nativeCanvas.save()
            nativeCanvas.rotate(135f, center.x, center.y)
            nativeCanvas.drawText("TG", center.x, center.y - radius + 50f, textPaintSecondary)
            nativeCanvas.restore()

            // Southwest
            nativeCanvas.save()
            nativeCanvas.rotate(225f, center.x, center.y)
            nativeCanvas.drawText("BD", center.x, center.y - radius + 50f, textPaintSecondary)
            nativeCanvas.restore()

            // Northwest
            nativeCanvas.save()
            nativeCanvas.rotate(315f, center.x, center.y)
            nativeCanvas.drawText("BL", center.x, center.y - radius + 50f, textPaintSecondary)
            nativeCanvas.restore()

            nativeCanvas.restore()
        }
    }
}

private fun getDirectionName(azimuth: Float): String {
    val deg = (azimuth + 22.5f) % 360
    return when {
        deg < 45 -> "Utara"
        deg < 90 -> "Timur Laut"
        deg < 135 -> "Timur"
        deg < 180 -> "Tenggara"
        deg < 225 -> "Selatan"
        deg < 270 -> "Barat Daya"
        deg < 315 -> "Barat"
        else -> "Barat Laut"
    }
}

private fun getDirectionAbbr(azimuth: Float): String {
    val deg = (azimuth + 22.5f) % 360
    return when {
        deg < 45 -> "U"
        deg < 90 -> "TL"
        deg < 135 -> "T"
        deg < 180 -> "TG"
        deg < 225 -> "S"
        deg < 270 -> "BD"
        deg < 315 -> "B"
        else -> "BL"
    }
}
