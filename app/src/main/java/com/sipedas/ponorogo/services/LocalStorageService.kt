package com.sipedas.ponorogo.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.jakewharton.disklrucache.DiskLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalStorageService(private val context: Context) {

    companion object {
        // 20MB LRU Cache for memory optimization (sufficient for >12 channels at 480px)
        private const val MAX_MEMORY = 20 * 1024 * 1024
        val thumbnailCache = object : LruCache<String, Bitmap>(MAX_MEMORY) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.allocationByteCount
            }
        }
        
        private var diskLruCache: DiskLruCache? = null
        private const val DISK_CACHE_SIZE = 50 * 1024 * 1024L // 50MB limit strictly enforced
        private const val APP_VERSION = 1
        private const val VALUE_COUNT = 1
        
        fun getDiskCache(context: Context): DiskLruCache? {
            if (diskLruCache == null || diskLruCache?.isClosed == true) {
                try {
                    val cacheDir = File(context.cacheDir, "cctv_thumbnails")
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    diskLruCache = DiskLruCache.open(cacheDir, APP_VERSION, VALUE_COUNT, DISK_CACHE_SIZE)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return diskLruCache
        }
        
        // Helper to sanitize key for DiskLruCache (must match [a-z0-9_-]{1,120})
        private fun sanitizeKey(key: String): String {
            return key.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        }
    }

    // --- CCTV Thumbnails ---
    suspend fun saveCctvThumbnail(cameraId: String, bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            try {
                // Downsample image to maximum width of 480px to save storage/cache heap memory
                val targetWidth = 480
                val processedBitmap = if (bitmap.width > targetWidth) {
                    val scale = targetWidth.toFloat() / bitmap.width
                    val targetHeight = (bitmap.height * scale).toInt()
                    Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                } else {
                    bitmap
                }
                
                // Save to LRU Memory Cache
                thumbnailCache.put(cameraId, processedBitmap)

                // Save to DiskLruCache
                val diskCache = getDiskCache(context)
                val safeKey = sanitizeKey(cameraId)
                val editor = diskCache?.edit(safeKey)
                if (editor != null) {
                    editor.newOutputStream(0).use { 
                        // Write compressed JPEG format at 75% quality for ultra-light surface loading
                        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, it)
                    }
                    editor.commit()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getCctvThumbnail(cameraId: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            val memoryCacheBitmap = thumbnailCache.get(cameraId)
            if (memoryCacheBitmap != null) {
                return@withContext memoryCacheBitmap
            }
            
            try {
                val diskCache = getDiskCache(context)
                val safeKey = sanitizeKey(cameraId)
                val snapshot = diskCache?.get(safeKey)
                
                if (snapshot != null) {
                    snapshot.getInputStream(0).use { inputStream ->
                        // Determine dimensions first
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        
                        // We must decode without consuming the stream, but stream cannot be reset easily in all cases.
                        // However, BitmapFactory.decodeStream might consume it. It's better to read to byte array first,
                        // or just recreate the input stream. DiskLruCache doesn't let us get input stream twice easily 
                        // without calling snapshot.getInputStream(0) again or doing it in two steps.
                        // Let's just decode it normally since thumbnails are already small (480px)
                        
                        val decodeOptions = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        
                        val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                        if (decodedBitmap != null) {
                            thumbnailCache.put(cameraId, decodedBitmap)
                        }
                        return@withContext decodedBitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Fallback for old cache format
            val file = File(context.cacheDir, "cctv_thumb_$cameraId.png")
            if (file.exists()) {
                try {
                    val decodeOptions = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565 
                    }
                    val decodedBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                    if (decodedBitmap != null) {
                        thumbnailCache.put(cameraId, decodedBitmap)
                        // Migrate to DiskLruCache mapping
                        saveCctvThumbnail(cameraId, decodedBitmap)
                        file.delete()
                    }
                    return@withContext decodedBitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return@withContext null
        }
    }
    
    suspend fun clearCctvThumbnails() {
        withContext(Dispatchers.IO) {
            thumbnailCache.evictAll()
            try {
                getDiskCache(context)?.delete()
                diskLruCache = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("cctv_thumb_") && file.name.endsWith(".png")) {
                    file.delete()
                }
            }
        }
    }

    // --- Map Tiles Cache Utilities---
    fun initMapCache() {
        val osmdroidConfig = org.osmdroid.config.Configuration.getInstance()
        osmdroidConfig.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        
        // Optimize cache for offline tiles
        osmdroidConfig.tileFileSystemCacheMaxBytes = (1024 * 1024 * 500).toLong() // 500MB map tiles cache
        osmdroidConfig.tileFileSystemCacheTrimBytes = (1024 * 1024 * 400).toLong() // Trim to 400MB
    }

    suspend fun clearMapTiles() {
        withContext(Dispatchers.IO) {
            val osmdroidCacheDir = File(context.filesDir, "osmdroid/tiles")
            if (osmdroidCacheDir.exists()) {
                osmdroidCacheDir.deleteRecursively()
            }
        }
    }
    
    suspend fun getMapCacheSize(): Long {
        return withContext(Dispatchers.IO) {
            val osmdroidCacheDir = File(context.filesDir, "osmdroid/tiles")
            if (osmdroidCacheDir.exists()) {
                osmdroidCacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            } else {
                0L
            }
        }
    }
}
