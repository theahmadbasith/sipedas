package com.sipedas.ponorogo

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay

@Composable
fun SipedasSplashScreen(
    onFinish: () -> Unit,
    targetLogoRect: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero,
    targetTextRect: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero,
    isDark: Boolean = true
) {
    var scale by remember { mutableStateOf(0.5f) }
    var opacity by remember { mutableStateOf(0f) }
    var subtitleOpacity by remember { mutableStateOf(0f) }
    var isTransitioning by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_logo")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(Unit) {
        animate(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) { value, _ ->
            scale = value
        }
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(600, easing = LinearOutSlowInEasing)
        ) { value, _ ->
            opacity = value
        }
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(800, delayMillis = 300, easing = LinearOutSlowInEasing)
        ) { value, _ ->
            subtitleOpacity = value
        }
        
        // Duration of visible splash screen
        delay(1800)
        
        isTransitioning = true
        delay(650) // Transition duration matching the longest animation (550ms + possible UI delay)
        onFinish()
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    
    var statusBarHeightPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
    if (statusBarHeightPx == 0f) {
        statusBarHeightPx = with(density) { 32.dp.toPx() } // fallback
    }
    
    val topAppBarHeightPx = with(density) { 64.dp.toPx() }

    // Initial positions
    val initialLogoX = screenWidthPx / 2f
    var initialLogoRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var initialTextRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    var textWidthPx by remember { mutableFloatStateOf(0f) }
    var textHeightPx by remember { mutableFloatStateOf(0f) }

    val targetLogoScale = if (targetLogoRect.width > 0f && initialLogoRect.width > 0f) targetLogoRect.width / initialLogoRect.width else 38f / 150f
    val targetTextScale = if (targetTextRect.width > 0f && initialTextRect.width > 0f) targetTextRect.width / initialTextRect.width else 22f / 42f

    val finalTranslateY by animateFloatAsState(
        targetValue = if (isTransitioning && targetLogoRect.height > 0f && initialLogoRect.height > 0f) {
            targetLogoRect.center.y - initialLogoRect.center.y
        } else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "final_y"
    )
    val finalTranslateX by animateFloatAsState(
        targetValue = if (isTransitioning && targetLogoRect.width > 0f && initialLogoRect.width > 0f) {
            targetLogoRect.center.x - initialLogoRect.center.x
        } else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "final_x"
    )

    val finalTextTranslateY by animateFloatAsState(
        targetValue = if (isTransitioning && targetTextRect.height > 0f && initialTextRect.height > 0f) {
            targetTextRect.center.y - initialTextRect.center.y
        } else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "final_text_y"
    )
    val finalTextTranslateX by animateFloatAsState(
        targetValue = if (isTransitioning && targetTextRect.width > 0f && initialTextRect.width > 0f) {
            targetTextRect.center.x - initialTextRect.center.x
        } else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "final_text_x"
    )

    val finalScale by animateFloatAsState(
        targetValue = if (isTransitioning) targetLogoScale else scale,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "final_scale"
    )
    
    val finalTextScale by animateFloatAsState(
        targetValue = if (isTransitioning) targetTextScale else scale,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "final_text_scale"
    )

    val subtitleOffset by animateDpAsState(
        targetValue = if (subtitleOpacity > 0.01f) 0.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "subtitle_offset"
    )

    val backgroundOpacity by animateFloatAsState(
        targetValue = if (isTransitioning) 0f else 1f,
        animationSpec = tween(650, easing = LinearOutSlowInEasing),
        label = "bg_opacity"
    )

    val finalOpacity by animateFloatAsState(
        targetValue = if (isTransitioning) 0f else opacity,
        animationSpec = tween(500, easing = LinearOutSlowInEasing), // Fades out slightly faster to reveal underlying UI clearly
        label = "final_opacity"
    )

    val bgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF4F6F9)
    val gridColor = if (isDark) {
        Color(0xFF1E3A8A).copy(alpha = 0.3f * backgroundOpacity)
    } else {
        Color(0xFF3B82F6).copy(alpha = 0.15f * backgroundOpacity)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(bgColor.copy(alpha = backgroundOpacity))
                
                val gridSize = 40.dp.toPx()
                val strokeWidth = 1.dp.toPx()
                
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth)
                    x += gridSize
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth)
                    y += gridSize
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Glowing Background Core
        Box(
            modifier = Modifier
                .size(420.dp)
                .alpha(finalOpacity)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF9100).copy(alpha = 0.38f),
                            Color(0xFFFFEA00).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .onGloballyPositioned { 
                        if (initialLogoRect == androidx.compose.ui.geometry.Rect.Zero) {
                            initialLogoRect = it.boundsInRoot()
                        }
                    }
                    .graphicsLayer {
                        translationX = finalTranslateX
                        translationY = finalTranslateY
                        scaleX = finalScale * glowScale
                        scaleY = finalScale * glowScale
                    },
                contentAlignment = Alignment.Center
            ) {
                // Rotating glowing circle
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotation)
                        .border(
                            width = 3.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFFFFEA00),
                                    Color(0xFFFF5722),
                                    Color(0xFFFF3D00),
                                    Color(0xFFFFEA00)
                                )
                            ),
                            shape = CircleShape
                        )
                        .shadow(16.dp, CircleShape, ambientColor = Color(0xFFFF9100), spotColor = Color(0xFFFF3D00))
                        .alpha(finalOpacity) 
                )
                
                // Logo Image
                Image(
                    painter = rememberAsyncImagePainter(model = R.drawable.sipedas_logo),
                    contentDescription = "SIPEDAS Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp) 
                        .alpha(finalOpacity) // Fade out during transition for a premium feel
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "SIPEDAS",
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFFEA00), // Pure Yellow
                            Color(0xFFFF9100), // Orange
                            Color(0xFFFF3D00)  // Deep Orange
                        )
                    )
                ),
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp, // Changed from 4.sp to 1.sp to exactly match TopAppBar tracking
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .onGloballyPositioned { 
                        textWidthPx = it.size.width.toFloat()
                        textHeightPx = it.size.height.toFloat()
                        if (initialTextRect == androidx.compose.ui.geometry.Rect.Zero) {
                            initialTextRect = it.boundsInRoot()
                        }
                    }
                    .graphicsLayer {
                        translationX = finalTextTranslateX
                        translationY = finalTextTranslateY
                        scaleX = finalTextScale
                        scaleY = finalTextScale
                    }
                    .alpha(finalOpacity)
            )

            val chipBgColor = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.25f) else Color(0xFFDBEAFE)
            val chipBorderColor = if (isDark) Color(0xFF3B82F6).copy(alpha = 0.5f) else Color(0xFF93C5FD)
            val chipTextColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)

            Box(
                modifier = Modifier
                    .offset(y = subtitleOffset)
                    .alpha(subtitleOpacity * finalOpacity)
                    .background(chipBgColor, RoundedCornerShape(12.dp))
                    .border(1.dp, chipBorderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "MOBILE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = chipTextColor,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val descTextColor = if (isDark) Color.White.copy(alpha = 0.95f) else Color(0xFF1E293B).copy(alpha = 0.95f)

            Text(
                text = "SISTEM INFORMASI PEDESTRIAN\nDAN AKSI SATGAS LINMAS",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 20.sp,
                color = descTextColor,
                textAlign = TextAlign.Center,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .alpha(subtitleOpacity * finalOpacity)
                    .offset(y = subtitleOffset)
            )
        }
    }
}
