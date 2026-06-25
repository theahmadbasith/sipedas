package com.sipedas.ponorogo.model

data class PhotoItem(
    val id: String,
    val path: String,
    val mimeType: String = "image/jpeg",
    val sizeKB: Long = 0,
    val isCompressed: Boolean = false,
    val isWatermarked: Boolean = false,
    val source: String = "Camera",
    val timestamp: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String = "",
    val isProcessing: Boolean = false,
    val processingLabel: String = ""
)
