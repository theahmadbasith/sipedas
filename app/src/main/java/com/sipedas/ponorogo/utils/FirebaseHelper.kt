package com.sipedas.ponorogo.utils

import android.util.Log
import com.sipedas.ponorogo.data.DraftPhoto
import com.sipedas.ponorogo.parser.SipedasParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object FirebaseHelper {
    private const val TAG = "FirebaseHelper"

    /**
     * Backup a single draft to Firebase RTDB after uploading its photos to Cloudinary
     */
    suspend fun backupDraftToFirebase(
        draftId: String,
        text: String,
        danru: String,
        timestamp: String,
        localPhotos: List<DraftPhoto>,
        firebaseUrlStr: String,
        cloudinaryCloud: String,
        cloudinaryPreset: String,
        cloudinaryApiKey: String = "",
        cloudinaryApiSecret: String = ""
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (firebaseUrlStr.trim().isEmpty()) {
                    Log.e(TAG, "Empty Firebase URL! Skipping backup.")
                    return@withContext false
                }

                // 1. Upload each photo to Cloudinary first
                val cloudinaryUrls = mutableListOf<String>()
                localPhotos.forEach { p ->
                    val file = File(p.filePath)
                    val url = if (file.exists()) {
                        CloudinaryHelper.uploadPhotoToCloudinary(
                            photoFile = file,
                            cloudName = cloudinaryCloud,
                            uploadPreset = cloudinaryPreset,
                            apiKey = cloudinaryApiKey,
                            apiSecret = cloudinaryApiSecret
                        ) 
                    } else {
                        null
                    } ?: "https://images.unsplash.com/photo-1541963463532-d68292c34b19?w=500&auto=format&fit=crop"
                    cloudinaryUrls.add(url)
                }

                // 2. Format Firebase JSON payload
                val trimmed = firebaseUrlStr.trim()
                val qIdx = trimmed.indexOf('?')
                val urlWithoutQuery = if (qIdx != -1) trimmed.substring(0, qIdx) else trimmed
                val queryStr = if (qIdx != -1) trimmed.substring(qIdx) else ""
                val cleanUrl = if (urlWithoutQuery.endsWith("/")) urlWithoutQuery else "$urlWithoutQuery/"
                val targetUrl = "${cleanUrl}drafts/$draftId.json$queryStr"

                val jsonPhotos = JSONArray()
                localPhotos.forEachIndexed { idx, p ->
                    val cloudUrl = if (idx < cloudinaryUrls.size) cloudinaryUrls[idx] else ""
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

                val parsed = SipedasParser.parseLaporan(text)

                val payload = JSONObject().apply {
                    put("id", draftId)
                    put("laporan", text)
                    put("danru", danru)
                    put("timestamp", timestamp)
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
                if (response.isSuccessful) {
                    Log.d(TAG, "Draft backed up successfully to Firebase RTDB & Cloudinary!")
                    return@withContext true
                } else {
                    Log.e(TAG, "Firebase backup returned code: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase backup execution failed: ${e.message}", e)
            }
            false
        }
    }

    /**
     * Fetch drafts list from Firebase
     */
    suspend fun fetchOnlineDrafts(firebaseUrlStr: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (firebaseUrlStr.trim().isEmpty()) return@withContext "[]"
                val trimmed = firebaseUrlStr.trim()
                val qIdx = trimmed.indexOf('?')
                val urlWithoutQuery = if (qIdx != -1) trimmed.substring(0, qIdx) else trimmed
                val queryStr = if (qIdx != -1) trimmed.substring(qIdx) else ""
                val cleanUrl = if (urlWithoutQuery.endsWith("/")) urlWithoutQuery else "$urlWithoutQuery/"
                val targetUrl = "${cleanUrl}drafts.json$queryStr"

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(targetUrl)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val resStr = response.body?.string() ?: ""
                    if (resStr.trim() == "null" || resStr.trim().isEmpty()) {
                        return@withContext "[]"
                    }

                    val draftsList = JSONArray()
                    try {
                        if (resStr.trim().startsWith("{")) {
                            val map = JSONObject(resStr)
                            val keys = map.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val obj = map.optJSONObject(key)
                                if (obj != null) {
                                    draftsList.put(obj)
                                }
                            }
                        } else if (resStr.trim().startsWith("[")) {
                            val array = JSONArray(resStr)
                            for (i in 0 until array.length()) {
                                val obj = array.optJSONObject(i)
                                if (obj != null) {
                                    draftsList.put(obj)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing firebase response: ${e.message}")
                    }
                    return@withContext draftsList.toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching online drafts from Firebase: ${e.message}")
            }
            "[]"
        }
    }

    /**
     * Fetch aduan list from Firebase Firestore
     */
    suspend fun fetchOnlineAduan(firebaseUrlStr: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (firebaseUrlStr.trim().isEmpty()) return@withContext "[]"
                val trimmed = firebaseUrlStr.trim()
                
                // Extract projectId from standard RTDB url pattern
                val uri = java.net.URI(trimmed)
                val host = uri.host ?: ""
                val projectId = host.substringBefore(".").replace("-default-rtdb", "")
                
                if (projectId.isEmpty()) return@withContext "[]"

                // Firestore REST API URL
                val targetUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/aduan?orderBy=timestamp%20desc"

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(targetUrl)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val resStr = response.body?.string() ?: ""
                    if (resStr.trim() == "null" || resStr.trim().isEmpty()) {
                        return@withContext "[]"
                    }

                    val aduanList = JSONArray()
                    try {
                        val rootObj = JSONObject(resStr)
                        val documents = rootObj.optJSONArray("documents")
                        if (documents != null) {
                            for (i in 0 until documents.length()) {
                                val doc = documents.getJSONObject(i)
                                val fields = doc.optJSONObject("fields")
                                if (fields != null) {
                                    val mappedObj = mapFirestoreFields(fields)
                                    val docName = doc.optString("name", "")
                                    if (docName.isNotEmpty()) {
                                        mappedObj.put("id", docName.substringAfterLast("/"))
                                    }
                                    // if timestamp doesn't exist, we fallback to createTime
                                    if (!mappedObj.has("timestamp")) {
                                        mappedObj.put("timestamp", doc.optString("createTime", ""))
                                    }
                                    aduanList.put(mappedObj)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing firestore aduan response: ${e.message}")
                    }
                    return@withContext aduanList.toString()
                } else {
                    Log.e(TAG, "Firestore error response: ${response.code} ${response.body?.string()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching online aduan from Firestore: ${e.message}")
            }
            "[]"
        }
    }

    private fun mapFirestoreFields(fieldsObj: JSONObject): JSONObject {
        val result = JSONObject()
        val keys = fieldsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val valObj = fieldsObj.optJSONObject(key)
            if (valObj != null) {
                val parsedVal = parseFirestoreValue(valObj)
                if (parsedVal != null) {
                    result.put(key, parsedVal)
                }
            }
        }
        return result
    }

    private fun parseFirestoreValue(valueObj: JSONObject): Any? {
        if (valueObj.has("stringValue")) return valueObj.getString("stringValue")
        if (valueObj.has("integerValue")) {
            val str = valueObj.getString("integerValue")
            return try { str.toLong() } catch (e: Exception) { str }
        }
        if (valueObj.has("doubleValue")) return valueObj.getDouble("doubleValue")
        if (valueObj.has("booleanValue")) return valueObj.getBoolean("booleanValue")
        if (valueObj.has("mapValue")) {
            val mapFields = valueObj.getJSONObject("mapValue").optJSONObject("fields") ?: JSONObject()
            return mapFirestoreFields(mapFields)
        }
        if (valueObj.has("arrayValue")) {
            val arrayValues = valueObj.getJSONObject("arrayValue").optJSONArray("values") ?: JSONArray()
            val result = JSONArray()
            for (i in 0 until arrayValues.length()) {
                val item = parseFirestoreValue(arrayValues.getJSONObject(i))
                if (item != null) result.put(item)
            }
            return result
        }
        if (valueObj.has("nullValue")) return null
        // fallback
        return null
    }

    /**
     * Delete draft from Firebase
     */
    suspend fun deleteOnlineDraft(draftId: String, firebaseUrlStr: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (firebaseUrlStr.trim().isEmpty()) return@withContext false
                val trimmed = firebaseUrlStr.trim()
                val qIdx = trimmed.indexOf('?')
                val urlWithoutQuery = if (qIdx != -1) trimmed.substring(0, qIdx) else trimmed
                val queryStr = if (qIdx != -1) trimmed.substring(qIdx) else ""
                val cleanUrl = if (urlWithoutQuery.endsWith("/")) urlWithoutQuery else "$urlWithoutQuery/"
                val targetUrl = "${cleanUrl}drafts/$draftId.json$queryStr"

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(targetUrl)
                    .delete()
                    .build()

                val response = client.newCall(request).execute()
                return@withContext response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting online draft: ${e.message}")
            }
            false
        }
    }

    /**
     * Download a file from Cloudinary back to cache
     */
    suspend fun downloadFileToCache(url: String, cacheDir: File): String? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val destinationFile = File(cacheDir, "sipedas_downloaded_${UUID.randomUUID()}.jpg")
                        destinationFile.writeBytes(bytes)
                        return@withContext destinationFile.absolutePath
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading file from Cloudinary: ${e.message}")
            }
            null
        }
    }
}
