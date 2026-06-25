package com.sipedas.ponorogo.utils

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GoogleDriveSheetHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun uploadFoto(
        targetUrl: String,
        base64Data: String,
        mimeType: String,
        source: String,
        lat: Double?,
        lng: Double?,
        timestamp: String,
        address: String,
        noFoto: Int,
        jumlahTotal: Int,
        reportText: String,
        folderId: String
    ): JSONObject {
        try {
            val json = JSONObject().apply {
                put("action", "uploadFoto")
                put("base64Data", base64Data)
                put("mimeType", mimeType)
                put("source", source)
                put("lat", lat ?: 0.0)
                put("lng", lng ?: 0.0)
                put("timestamp", timestamp)
                put("address", address)
                put("noFoto", noFoto)
                put("jumlahTotal", jumlahTotal)
                put("reportText", reportText)
                put("folderId", folderId)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(targetUrl)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d("SIPEDAS_GD", "uploadFoto Response: $body")
                if (response.isSuccessful && body.isNotEmpty()) {
                    return JSONObject(body)
                } else {
                    return JSONObject().put("error", "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("SIPEDAS_GD", "uploadFoto error", e)
            return JSONObject().put("error", e.message)
        }
    }

    fun submitLaporan(
        targetUrl: String,
        reportText: String,
        linkFotoArray: JSONArray,
        remoteFolderUrl: String,
        draftId: String,
        sheetId: String
    ): JSONObject {
        try {
            val json = JSONObject().apply {
                put("action", "submitLaporan")
                put("reportText", reportText)
                put("linkFotoArray", linkFotoArray)
                put("remoteFolderUrl", remoteFolderUrl)
                put("draftId", draftId)
                put("sheetId", sheetId)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(targetUrl)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d("SIPEDAS_GD", "submitLaporan Response: $body")
                if (response.isSuccessful && body.isNotEmpty()) {
                    return JSONObject(body)
                } else {
                    return JSONObject().put("error", "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("SIPEDAS_GD", "submitLaporan error", e)
            return JSONObject().put("error", e.message)
        }
    }
}
