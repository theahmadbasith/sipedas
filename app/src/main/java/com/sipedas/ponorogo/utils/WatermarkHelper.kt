package com.sipedas.ponorogo.utils

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import com.sipedas.ponorogo.R
import java.net.URL
import android.graphics.BitmapFactory

enum class WatermarkType {
    DEFAULT,
    FLOATING,
    SIMPLE
}

data class WatermarkConfig(
    val title: String = "SATLINMAS PEDESTRIAN",
    val color: String = "#fff500", // Yellow default
    val iconResId: Int = R.drawable.icon_full,
    val iconUri: String? = null,
    val danruLabel: String = "Danru",
    val type: WatermarkType = WatermarkType.DEFAULT,
    val fontSizeTitle: Float = 1.0f,
    val fontSizeDate: Float = 1.0f,
    val fontSizeLoc: Float = 1.0f,
    val fontSizeCoord: Float = 1.0f
)

object WatermarkHelper {
    private val tileCache = android.util.LruCache<String, Bitmap>(20)

    fun wrapText(paint: Paint, text: String, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList()
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var cur = ""
        for (word in words) {
            val next = if (cur.isEmpty()) word else "$cur $word"
            if (paint.measureText(next) > maxWidth && cur.isNotEmpty()) {
                lines.add(cur)
                cur = word
            } else {
                cur = next
            }
        }
        if (cur.isNotEmpty()) {
            lines.add(cur)
        }
        return lines.take(5)
    }

    fun drawSipedasWatermark(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        strpH: Int,
        sy: Int,
        danru: String,
        timeStr: String,
        address: String,
        lat: Double?,
        lng: Double?,
        config: WatermarkConfig,
        allowNetwork: Boolean = false
    ) {
        if (config.type == WatermarkType.SIMPLE) {
            val padV = 4
            val baseFB = Math.max(9, Math.round((w * 0.75f) * 0.10f * 0.30f).toInt())
            val fT = (baseFB * config.fontSizeTitle).toInt()
            val fD = (baseFB * config.fontSizeDate).toInt()
            val fL = (baseFB * config.fontSizeLoc).toInt()
            val fC = (baseFB * config.fontSizeCoord).toInt()
            
            val tempPaint = Paint().apply { textSize = fL.toFloat() }
            val tw = w - 32
            val addrLines = wrapText(tempPaint, address, tw.toFloat())
            
            // Draw gradient at bottom
            val grPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, sy.toFloat(), 0f, h.toFloat(),
                    Color.TRANSPARENT, Color.argb(195, 0, 0, 0), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, sy.toFloat(), w.toFloat(), h.toFloat(), grPaint)
            
            val danruPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = fT.toFloat()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(2.5f, 1f, 1f, Color.BLACK)
            }
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = fD.toFloat()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                setShadowLayer(2.5f, 1f, 1f, Color.BLACK)
            }
            val addrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = fL.toFloat()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                setShadowLayer(2.5f, 1f, 1f, Color.BLACK)
            }
            val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = fC.toFloat()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                setShadowLayer(2.5f, 1f, 1f, Color.BLACK)
            }
            
            val coordStr = if (lat != null && lng != null) "${String.format(java.util.Locale.US, "%.6f", lat)}, ${String.format(java.util.Locale.US, "%.6f", lng)}" else "Koordinat tidak tersedia"
            val label = if (config.danruLabel.isBlank()) "Danru" else config.danruLabel
            val danruStr = if (danru.isEmpty()) "—" else danru

            // Draw text lines tightly
            var ty = sy + padV + fT
            canvas.drawText("$label: $danruStr", 16f, ty.toFloat(), danruPaint)
            ty += Math.round(fD * 1.35f).toInt()
            canvas.drawText(timeStr, 16f, ty.toFloat(), timePaint)
            ty += Math.round(fL * 1.35f).toInt()
            
            addrLines.forEach { ln ->
                canvas.drawText(ln, 16f, ty.toFloat(), addrPaint)
                ty += Math.round(fL * 1.35f).toInt()
            }
            canvas.drawText(coordStr, 16f, ty.toFloat(), coordPaint)

            return
        }

        var actualW = w
        var actualH = h
        var actualSy = sy
        
        if (config.type == WatermarkType.FLOATING) {
            val margin = Math.round(w * 0.03f).toInt()
            actualW = w - margin * 2
            actualSy = sy + margin
            canvas.save()
            canvas.translate(margin.toFloat(), 0f)
            val clipPath = Path().apply {
                val radius = margin.toFloat() * 0.5f
                addRoundRect(RectF(0f, actualSy.toFloat(), actualW.toFloat(), actualH.toFloat() - margin), radius, radius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
        }

        val bar = Math.max(3, Math.round(actualW * 0.006f).toInt())
        val pad = Math.round(actualW * 0.022f).toInt()
        val padV = 5

        // Use consistent ratio of width for elements to ensure identical sizing proportions in both preview and final photos
        val minDim = (actualW * 0.75f).toInt()
        val logoSize = Math.round(minDim * 0.10f).toInt()

        val qrSize = Math.max(80, Math.min(340, Math.round(minDim * 0.125f).toInt()))
        val qrPad = Math.round(pad * 0.4f).toInt()

        val fT = (Math.max(11, Math.round(logoSize * 0.35f).toInt()) * config.fontSizeTitle).toInt()
        val fB = (Math.max(9, Math.round(logoSize * 0.30f).toInt()) * config.fontSizeDate).toInt()
        val fAddr = (Math.max(9, Math.round(logoSize * 0.30f).toInt()) * config.fontSizeLoc).toInt()
        val fS = (Math.max(7, Math.round(fB * 0.70f).toInt()) * config.fontSizeCoord).toInt()
        val lh = Math.round(fB * 1.25f).toInt()
        val lhAddr = Math.round(fAddr * 1.22f).toInt()

        val tx = bar + Math.round(pad * 0.35f).toInt() + logoSize + Math.round(pad * 0.45f).toInt()
        val tw = actualW - tx - pad - (qrSize + qrPad * 2)

        val tempPaintForWrap = Paint().apply {
            textSize = fAddr.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val addrLines = wrapText(tempPaintForWrap, address, tw.toFloat())

        // 1. Draw Gradient Background
        val grPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, actualSy.toFloat(), 0f, actualH.toFloat(),
                intArrayOf(
                    Color.argb((0.42f * 255).toInt(), 2, 6, 18),
                    Color.argb((0.65f * 255).toInt(), 2, 6, 18),
                    Color.argb((0.80f * 255).toInt(), 2, 6, 18)
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, actualSy.toFloat(), actualW.toFloat(), actualH.toFloat(), grPaint)

        // 2. Draw Solid Side Colorful Bar
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, actualSy.toFloat(), 0f, actualH.toFloat(),
                intArrayOf(
                    Color.argb((0.65f * 255).toInt(), 26, 101, 214), // rgba(26,101,214,0.65)
                    Color.argb((0.90f * 255).toInt(), 26, 80, 184)   // rgba(26,80,184,0.90)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, actualSy.toFloat(), bar.toFloat(), actualH.toFloat(), bgPaint)

        // 3. Draw Logo Image
        val lx = bar + Math.round(pad * 0.35f).toInt()
        
        // Ensure strpH covers the block properly 
        val blockH = if (config.type == WatermarkType.FLOATING) strpH - Math.round(w * 0.03f).toInt() * 2 else strpH
        val ly = actualSy + Math.round((blockH - logoSize) / 2.0).toInt()

        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 255
        }

        var customIconDrawn = false
        if (config.iconUri != null) {
            try {
                val uri = android.net.Uri.parse(config.iconUri)
                val isStr = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(isStr, null, options)
                isStr?.close()
                
                var sampleSize = 1
                if (options.outWidth > logoSize * 2 || options.outHeight > logoSize * 2) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / sampleSize) >= (logoSize * 2) && (halfWidth / sampleSize) >= (logoSize * 2)) {
                        sampleSize *= 2
                    }
                }
                
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val isStrActual = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(isStrActual, null, decodeOptions)
                isStrActual?.close()
                if (bmp != null) {
                    val scaled = Bitmap.createScaledBitmap(bmp, logoSize, logoSize, true)
                    canvas.drawBitmap(scaled, lx.toFloat(), ly.toFloat(), logoPaint)
                    customIconDrawn = true
                    bmp.recycle()
                }
                isStr?.close()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        
        if (!customIconDrawn) {
            val drawable = ContextCompat.getDrawable(context, config.iconResId)
            if (drawable != null) {
                drawable.setBounds(lx, ly, lx + logoSize, ly + logoSize)
                drawable.setAlpha(255)
                drawable.draw(canvas)
            } else {
                drawStylizedLinmasShield(canvas, lx.toFloat(), ly.toFloat(), logoSize.toFloat(), logoSize.toFloat())
            }
        }

        // 4. Draw Map or QR Code on right
        if (qrSize > 0) {
            val finalLat = lat ?: -7.872722
            val finalLng = lng ?: 111.462639

            val qx = actualW - qrSize - qrPad
            val blockH = if (config.type == WatermarkType.FLOATING) strpH - Math.round(w * 0.03f).toInt() * 2 else strpH
            val qy = actualSy + Math.round((blockH - qrSize) / 2.0).toInt() - Math.round(pad * 0.65f).toInt()

            canvas.save()
            val mapPath = Path()
            val mapRadius = qrSize * 0.10f
            mapPath.addRoundRect(
                RectF(qx.toFloat(), qy.toFloat(), (qx + qrSize).toFloat(), (qy + qrSize).toFloat()),
                mapRadius, mapRadius,
                Path.Direction.CW
            )
            canvas.clipPath(mapPath)

            var mapLoaded = false
            if (allowNetwork) {
                try {
                    val zoom = 17
                    val tile = MapHelpers.getTileNumber(finalLat, finalLng, zoom)
                    val cacheKey = "${tile.first}_${tile.second}_$zoom"
                    var tileBmp = tileCache.get(cacheKey)

                    if (tileBmp == null) {
                        val urlsToTry = listOf(
                            "https://mt1.google.com/vt/lyrs=y&x=${tile.first}&y=${tile.second}&z=$zoom", // Hybrid Satellite
                            "https://mt1.google.com/vt/lyrs=s&x=${tile.first}&y=${tile.second}&z=$zoom", // Standard Satellite
                            "https://tile.openstreetmap.org/$zoom/${tile.first}/${tile.second}.png"     // OpenStreetMap (Very reliable fallback)
                        )
                        for (tileUrl in urlsToTry) {
                            try {
                                val url = URL(tileUrl)
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SipedasApp/1.0")
                                conn.connectTimeout = 3000
                                conn.readTimeout = 3000
                                conn.useCaches = true
                                
                                val responseCode = conn.responseCode
                                if (responseCode == 200) {
                                    conn.inputStream.use { stream ->
                                        val downloaded = BitmapFactory.decodeStream(stream)
                                        if (downloaded != null) {
                                            tileBmp = downloaded
                                            tileCache.put(cacheKey, downloaded)
                                        }
                                    }
                                }
                                if (tileBmp != null) break
                            } catch (e: Exception) {
                                android.util.Log.e("SIPEDAS", "Gagal fetch tile dari $tileUrl: ${e.message}")
                            }
                        }
                    }

                    if (tileBmp != null) {
                        val scaledTile = Bitmap.createScaledBitmap(tileBmp!!, qrSize, qrSize, true)
                        canvas.drawBitmap(scaledTile, qx.toFloat(), qy.toFloat(), null)
                        scaledTile.recycle()

                        val halfSize = qrSize / 2
                        val targetX = qx + halfSize
                        val targetY = qy + halfSize

                        val outerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb(80, 239, 68, 68)
                            style = Paint.Style.FILL
                        }
                        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.RED
                            style = Paint.Style.FILL
                        }
                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.WHITE
                            style = Paint.Style.STROKE
                            strokeWidth = 2.5f
                        }

                        canvas.drawCircle(targetX.toFloat(), targetY.toFloat(), 15f, outerCirclePaint)
                        canvas.drawCircle(targetX.toFloat(), targetY.toFloat(), 6f, pinPaint)
                        canvas.drawCircle(targetX.toFloat(), targetY.toFloat(), 6f, borderPaint)

                        mapLoaded = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SIPEDAS", "Gagal fetching map tile: ${e.message}")
                }
            }

            if (!mapLoaded) {
                // High-fidelity vibrant vector map schematic (soft light green parks, sky-blue background, grey borders, white roads and red pin)
                val bgMapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E0F2FE") // modern light sky blue (representing water/space)
                    style = Paint.Style.FILL
                }
                val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FFFFFF") // white road lanes
                    strokeWidth = 10f
                    style = Paint.Style.STROKE
                }
                val roadBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#CBD5E1") // grey road borders
                    strokeWidth = 14f
                    style = Paint.Style.STROKE
                }
                val greenSpacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#DCFCE7") // soft light green park areas
                    style = Paint.Style.FILL
                }
                
                // Draw land background
                canvas.drawRect(qx.toFloat(), qy.toFloat(), (qx + qrSize).toFloat(), (qy + qrSize).toFloat(), bgMapPaint)
                
                // Draw roundrect park designs
                canvas.drawRoundRect(
                    (qx + qrSize * 0.08f).toFloat(), (qy + qrSize * 0.08f).toFloat(),
                    (qx + qrSize * 0.42f).toFloat(), (qy + qrSize * 0.48f).toFloat(),
                    8f, 8f, greenSpacePaint
                )
                canvas.drawRoundRect(
                    (qx + qrSize * 0.58f).toFloat(), (qy + qrSize * 0.52f).toFloat(),
                    (qx + qrSize * 0.92f).toFloat(), (qy + qrSize * 0.92f).toFloat(),
                    8f, 8f, greenSpacePaint
                )
                
                // Intersecting road networks
                // Horizontal main road
                canvas.drawLine(qx.toFloat(), (qy + qrSize * 0.45f).toFloat(), (qx + qrSize).toFloat(), (qy + qrSize * 0.45f).toFloat(), roadBorderPaint)
                canvas.drawLine(qx.toFloat(), (qy + qrSize * 0.45f).toFloat(), (qx + qrSize).toFloat(), (qy + qrSize * 0.45f).toFloat(), roadPaint)
                
                // Vertical main road
                canvas.drawLine((qx + qrSize * 0.5f).toFloat(), qy.toFloat(), (qx + qrSize * 0.5f).toFloat(), (qy + qrSize).toFloat(), roadBorderPaint)
                canvas.drawLine((qx + qrSize * 0.5f).toFloat(), qy.toFloat(), (qx + qrSize * 0.5f).toFloat(), (qy + qrSize).toFloat(), roadPaint)
                
                // Diagonal secondary highway
                val diagPaint = Paint(roadPaint).apply { strokeWidth = 7f }
                val diagBorderPaint = Paint(roadBorderPaint).apply { strokeWidth = 11f }
                canvas.drawLine(qx.toFloat(), qy.toFloat(), (qx + qrSize).toFloat(), (qy + qrSize).toFloat(), diagBorderPaint)
                canvas.drawLine(qx.toFloat(), qy.toFloat(), (qx + qrSize).toFloat(), (qy + qrSize).toFloat(), diagPaint)

                // Location PIN drop
                val halfSize = qrSize / 2
                val targetX = qx + halfSize
                val targetY = qy + halfSize
                
                // Pulse waves under pin
                val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#3B82F6") // Blue GPS accuracy pulse
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    alpha = 180
                }
                canvas.drawCircle(targetX.toFloat(), targetY.toFloat(), 18f, pulsePaint)
                canvas.drawCircle(targetX.toFloat(), targetY.toFloat(), 30f, Paint(pulsePaint).apply { alpha = 90 })
                
                // Pin shadow
                canvas.drawCircle(targetX.toFloat(), (targetY + 4).toFloat(), 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#33000000")
                    style = Paint.Style.FILL
                })
                
                // Pin head and needle path
                val pinPath = Path().apply {
                    moveTo(targetX.toFloat(), targetY.toFloat())
                    lineTo((targetX - 7).toFloat(), (targetY - 14).toFloat())
                    addArc(
                        (targetX - 7).toFloat(), (targetY - 21).toFloat(),
                        (targetX + 7).toFloat(), (targetY - 7).toFloat(),
                        180f, 180f
                    )
                    lineTo(targetX.toFloat(), targetY.toFloat())
                    close()
                }
                canvas.drawPath(pinPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#EF4444") // vibrant red pin
                    style = Paint.Style.FILL
                })
                canvas.drawCircle(targetX.toFloat(), (targetY - 14).toFloat(), 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                })
                
                mapLoaded = true
            }
            canvas.restore()
        }

        // 5. Draw Metadata Texts exactly matching sipedas formatting
        val parsedColor = try { Color.parseColor(config.color) } catch (e: Exception) { Color.argb((0.90f * 255).toInt(), 255, 210, 0) }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parsedColor
            textSize = fT.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val danruPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((0.90f * 255).toInt(), 255, 255, 255)
            textSize = fB.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((0.90f * 255).toInt(), 160, 210, 255)
            textSize = fB.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val addrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((0.90f * 255).toInt(), 180, 248, 200) // Soft pale green
            textSize = fAddr.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((0.85f * 255).toInt(), 140, 180, 220)
            textSize = fS.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val coordStr = if (lat != null && lng != null) {
            "${String.format(java.util.Locale.US, "%.6f", lat)}, ${String.format(java.util.Locale.US, "%.6f", lng)}"
        } else {
            "Koordinat tidak tersedia"
        }
        val danruStr = if (danru.isEmpty()) "—" else danru

        var ty = actualSy + padV

        // Emulate top textBaseline alignment using negative font ascent offset
        canvas.drawText(config.title, tx.toFloat(), ty - titlePaint.fontMetrics.ascent, titlePaint)
        ty += Math.round(fT * 1.22f).toInt()

        val label = if (config.danruLabel.isBlank()) "Danru" else config.danruLabel
        canvas.drawText("$label: $danruStr", tx.toFloat(), ty - danruPaint.fontMetrics.ascent, danruPaint)
        ty += lh

        canvas.drawText(timeStr, tx.toFloat(), ty - timePaint.fontMetrics.ascent, timePaint)
        ty += lh

        addrLines.forEach { ln ->
            canvas.drawText(ln, tx.toFloat(), ty - addrPaint.fontMetrics.ascent, addrPaint)
            ty += lhAddr
        }

        canvas.drawText(coordStr, tx.toFloat(), ty - coordPaint.fontMetrics.ascent, coordPaint)

        // 6. Draw "SI-PEDAS mobile" Branding label
        val spF = Math.max(5, Math.round(actualW * 0.013f).toInt())
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((0.55f * 255).toInt(), 255, 205, 0)
            textSize = spF.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val subBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((0.35f * 255).toInt(), 255, 255, 255)
            textSize = Math.round(spF * 0.72f).toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }

        val brandX = actualW - Math.round(pad * 0.6f).toInt()
        val cardBottom = if (config.type == WatermarkType.FLOATING) {
            val margin = Math.round(w * 0.03f).toInt()
            actualH - margin
        } else {
            actualH
        }
        val brandY = cardBottom - Math.round(pad * 0.4f).toInt()

        canvas.drawText("SI-PEDAS", brandX.toFloat(), brandY.toFloat() - brandPaint.fontMetrics.descent, brandPaint)
        canvas.drawText("mobile", brandX.toFloat(), (brandY - Math.round(spF * 1.15f).toInt()).toFloat() - subBrandPaint.fontMetrics.descent, subBrandPaint)
        
        if (config.type == WatermarkType.FLOATING) {
            canvas.restore()
        }
    }

    fun calculateRequiredHeight(
        context: Context,
        w: Int,
        h: Int,
        address: String,
        config: WatermarkConfig
    ): Int {
        if (config.type == WatermarkType.SIMPLE) {
            val padV = 4
            val baseFB = Math.max(9, Math.round((w * 0.75f) * 0.10f * 0.30f).toInt())
            val fT = (baseFB * config.fontSizeTitle).toInt()
            val fD = (baseFB * config.fontSizeDate).toInt()
            val fL = (baseFB * config.fontSizeLoc).toInt()
            val fC = (baseFB * config.fontSizeCoord).toInt()
            
            val tempPaint = Paint().apply { textSize = fL.toFloat() }
            val tw = w - 32
            val addrLines = wrapText(tempPaint, address, tw.toFloat())
            
            val totalHeight = padV + fT + Math.round(fD * 1.35f) + Math.round(fL * 1.35f * addrLines.size) + Math.round(fC * 1.35f) + padV + 12
            return totalHeight
        }
        
        val bar = Math.max(3, Math.round(w * 0.006f).toInt())
        val pad = Math.round(w * 0.022f).toInt()
        val padV = 5

        val minDim = (w * 0.75f).toInt()
        val logoSize = Math.round(minDim * 0.10f).toInt()

        val qrSize = Math.max(80, Math.min(340, Math.round(minDim * 0.125f).toInt()))
        val qrPad = Math.round(pad * 0.4f).toInt()

        val fT = (Math.max(11, Math.round(logoSize * 0.35f).toInt()) * config.fontSizeTitle).toInt()
        val fB = (Math.max(9, Math.round(logoSize * 0.30f).toInt()) * config.fontSizeDate).toInt()
        val fAddr = (Math.max(9, Math.round(logoSize * 0.30f).toInt()) * config.fontSizeLoc).toInt()
        val fS = (Math.max(7, Math.round(fB * 0.70f).toInt()) * config.fontSizeCoord).toInt()
        val lh = Math.round(fB * 1.25f).toInt()
        val lhAddr = Math.round(fAddr * 1.22f).toInt()

        val tx = bar + Math.round(pad * 0.35f).toInt() + logoSize + Math.round(pad * 0.45f).toInt()
        val tw = w - tx - pad - (qrSize + qrPad * 2)

        val tempPaintForWrap = Paint().apply {
            textSize = fAddr.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val addrLines = wrapText(tempPaintForWrap, address, tw.toFloat())

        val spF = Math.max(5, Math.round(w * 0.013f).toInt())
        val brandHeightWithPadding = Math.round(spF * 1.5f)

        val minHeightForMap = qrSize + (qrPad * 2) + brandHeightWithPadding
        val minHeightForLogo = logoSize + (padV * 2)
        val textHeight = (padV * 2) + Math.round(fT * 1.22f).toInt() + lh + lh + (addrLines.size * lhAddr) + Math.round(fS * 1.22f).toInt()

        var maxRequiredHeight = maxOf(minHeightForMap, minHeightForLogo, textHeight)
        
        if (config.type == WatermarkType.FLOATING) {
            val margin = Math.round(w * 0.03f).toInt()
            maxRequiredHeight += margin * 2
        }
        
        val baseMinHeight = Math.round(h * 0.04f)
        return maxOf(baseMinHeight, maxRequiredHeight)
    }

    fun createWatermarkOverlay(
        context: Context,
        overlayWidth: Int,
        overlayHeight: Int,
        danru: String,
        timeStr: String,
        address: String,
        lat: Double?,
        lng: Double?,
        config: WatermarkConfig
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(overlayWidth, overlayHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawSipedasWatermark(
            context = context,
            canvas = canvas,
            w = overlayWidth,
            h = overlayHeight,
            strpH = overlayHeight,
            sy = 0,
            danru = danru,
            timeStr = timeStr,
            address = address,
            lat = lat,
            lng = lng,
            config = config,
            allowNetwork = true
        )

        return bitmap
    }

    fun drawWatermark(
        context: Context,
        src: Bitmap,
        danru: String,
        timeStr: String,
        address: String,
        lat: Double?,
        lng: Double?,
        config: WatermarkConfig
    ): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)

        val w = src.width
        val h = src.height

        val strpH = calculateRequiredHeight(context, w, h, address, config)
        val sy = h - strpH

        drawSipedasWatermark(
            context = context,
            canvas = canvas,
            w = w,
            h = h,
            strpH = strpH,
            sy = sy,
            danru = danru,
            timeStr = timeStr,
            address = address,
            lat = lat,
            lng = lng,
            config = config,
            allowNetwork = true
        )

        return result
    }

    private fun drawStylizedLinmasShield(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#a022d3ee")
            style = Paint.Style.FILL
        }

        val path = Path().apply {
            moveTo(x + w / 2, y)
            cubicTo(x + w * 0.8f, y, x + w, y + h * 0.2f, x + w, y + h * 0.5f)
            cubicTo(x + w, y + h * 0.8f, x + w / 2, y + h, x + w / 2, y + h)
            cubicTo(x + w / 2, y + h, x, y + h * 0.8f, x, y + h * 0.5f)
            cubicTo(x, y + h * 0.2f, x + w * 0.2f, y, x + w / 2, y)
            close()
        }
        canvas.drawPath(path, shieldPaint)

        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#ffd200")
            style = Paint.Style.FILL
        }
        val cx = x + w / 2
        val cy = y + h / 2
        val rOuter = w * 0.22f
        val rInner = w * 0.09f
        drawStarPath(canvas, cx, cy, 5, rOuter, rInner, starPaint)
    }

    private fun drawStarPath(canvas: Canvas, cx: Float, cy: Float, numPoints: Int, rOuter: Float, rInner: Float, paint: Paint) {
        val path = Path()
        var angle = -Math.PI / 2
        val delta = Math.PI / numPoints
        for (i in 0 until numPoints * 2) {
            val r = if (i % 2 == 0) rOuter else rInner
            val px = cx + (r * Math.cos(angle)).toFloat()
            val py = cy + (r * Math.sin(angle)).toFloat()
            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
            angle += delta
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
