package com.sipedas.ponorogo.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object CloudinaryHelper {
    private const val TAG = "CloudinaryHelper"
    private const val FALLBACK_IMAGE_URL = "https://images.unsplash.com/photo-1541963463532-d68292c34b19?w=500&auto=format&fit=crop"

    private fun sha1(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { String.format("%02x", it) }
    }

    /**
     * Upload photo to Cloudinary with custom folder and public ID
     */
    suspend fun uploadPhotoToCloudinary(
        photoFile: File,
        cloudName: String,
        uploadPreset: String,
        folderPath: String? = null,
        publicId: String? = null,
        apiKey: String = "",
        apiSecret: String = ""
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cloud = cloudName.trim()
                val preset = uploadPreset.trim()
                if (cloud.isEmpty() || cloud == "sipedas-cloud") {
                    Log.d(TAG, "Empty or default cloud name, returning fallback simulation URL")
                    return@withContext FALLBACK_IMAGE_URL
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val builder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", photoFile.name, photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull()))

                val key = apiKey.trim()
                val secret = apiSecret.trim()

                if (key.isNotEmpty() && secret.isNotEmpty()) {
                    // Signed Upload
                    val timestamp = (System.currentTimeMillis() / 1000).toString()
                    val params = sortedMapOf<String, String>()
                    if (!folderPath.isNullOrEmpty()) {
                        params["folder"] = folderPath
                    }
                    if (!publicId.isNullOrEmpty()) {
                        params["public_id"] = publicId
                    }
                    params["timestamp"] = timestamp

                    val toSignString = params.map { "${it.key}=${it.value}" }.joinToString("&") + secret
                    val signature = sha1(toSignString)

                    builder.addFormDataPart("api_key", key)
                    builder.addFormDataPart("timestamp", timestamp)
                    builder.addFormDataPart("signature", signature)

                    if (!folderPath.isNullOrEmpty()) {
                        builder.addFormDataPart("folder", folderPath)
                    }
                    if (!publicId.isNullOrEmpty()) {
                        builder.addFormDataPart("public_id", publicId)
                    }
                } else {
                    // Unsigned Upload
                    builder.addFormDataPart("upload_preset", preset)
                    if (!folderPath.isNullOrEmpty()) {
                        builder.addFormDataPart("folder", folderPath)
                    }
                    if (!publicId.isNullOrEmpty()) {
                        builder.addFormDataPart("public_id", publicId)
                    }
                }

                val requestBody = builder.build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/$cloud/image/upload")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val resStr = response.body?.string() ?: ""
                    val json = JSONObject(resStr)
                    return@withContext json.optString("secure_url", FALLBACK_IMAGE_URL)
                } else {
                    val code = response.code
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Cloudinary returned error code: $code, body: $errorBody")
                    if (code == 401 || (code == 400 && errorBody.contains("unsigned", ignoreCase = true))) {
                        throw Exception("Cloudinary HTTP $code: Gagal mengautentikasi upload. Pastikan Upload Preset diset ke 'Unsigned' di Cloudinary Console (jika tanpa API Key), atau periksa kembali API Key/Secret Anda.")
                    } else {
                        throw Exception("Cloudinary HTTP $code: $errorBody")
                    }
                }
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "Cloudinary upload failed: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Extract public_id from Cloudinary URL
     */
    fun extractPublicId(url: String): String? {
        val keyword = "/image/upload/"
        if (!url.contains(keyword)) return null
        var path = url.substringAfter(keyword)
        // Remove version segment (e.g., v1234567/)
        val versionRegex = "^v\\d+/(.+)".toRegex()
        val match = versionRegex.matchEntire(path)
        if (match != null) {
            path = match.groupValues[1]
        }
        // Remove extension (e.g., .jpg, .png, etc.)
        val dotIdx = path.lastIndexOf('.')
        if (dotIdx != -1) {
            path = path.substring(0, dotIdx)
        }
        return path
    }

    /**
     * Delete photo from Cloudinary
     */
    suspend fun deletePhoto(
        url: String,
        cloudName: String,
        apiKey: String,
        apiSecret: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cloud = cloudName.trim()
                val key = apiKey.trim()
                val secret = apiSecret.trim()
                
                if (cloud.isEmpty() || cloud == "sipedas-cloud") {
                    Log.d(TAG, "Empty or default cloud, skipping real deletion")
                    return@withContext true
                }
                
                val publicId = extractPublicId(url)
                if (publicId == null) {
                    Log.d(TAG, "Could not extract public ID from URL: $url")
                    return@withContext false
                }
                
                if (key.isEmpty() || secret.isEmpty()) {
                    Log.d(TAG, "API Key or Secret missing, cannot perform authenticated delete")
                    return@withContext false
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                val timestamp = (System.currentTimeMillis() / 1000).toString()
                val params = sortedMapOf<String, String>()
                params["public_id"] = publicId
                params["timestamp"] = timestamp

                val toSignString = "public_id=$publicId&timestamp=$timestamp$secret"
                val signature = sha1(toSignString)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("public_id", publicId)
                    .addFormDataPart("timestamp", timestamp)
                    .addFormDataPart("api_key", key)
                    .addFormDataPart("signature", signature)
                    .build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/$cloud/image/destroy")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val result = response.body?.string() ?: ""
                    val json = JSONObject(result)
                    val status = json.optString("result", "")
                    Log.d(TAG, "Cloudinary delete result for $publicId: $status")
                    return@withContext status == "ok"
                } else {
                    Log.e(TAG, "Cloudinary delete return code: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting photo from Cloudinary: ${e.message}")
            }
            false
        }
    }
}
