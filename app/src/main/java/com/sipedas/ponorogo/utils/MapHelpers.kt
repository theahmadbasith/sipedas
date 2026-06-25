package com.sipedas.ponorogo.utils

import android.location.Address
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

object MapHelpers {
    fun getTileNumber(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val x = Math.floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
        val latRad = Math.toRadians(lat)
        val y = Math.floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)).toInt()
        return Pair(x, y)
    }

    fun formatAddressDetailed(address: Address): String {
        val parts = mutableListOf<String>()
        
        // Add road/street
        address.thoroughfare?.let { parts.add(it) }
        
        // Add sub-locality (village/kelurahan)
        address.subLocality?.let { parts.add(it) }
        
        // Add locality (kecamatan)
        address.locality?.let { parts.add(it) }
        
        // Add sub-admin (kabupaten)
        address.subAdminArea?.let { parts.add(it) }
        
        // Add postal code
        address.postalCode?.let { parts.add(it) }

        val formatted = parts.filter { it.isNotBlank() }.joinToString(", ")
        return if (formatted.isNotBlank()) formatted else address.getAddressLine(0) ?: ""
    }
}
