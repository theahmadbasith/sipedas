package com.sipedas.ponorogo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "draft_reports")
data class DraftReport(
    @PrimaryKey val id: String, // e.g. "DRAFT-1234567"
    val laporan: String,
    val danru: String,
    val timestamp: String
)

@Entity(
    tableName = "draft_photos",
    foreignKeys = [
        ForeignKey(
            entity = DraftReport::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["draftId"])]
)
data class DraftPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val draftId: String,
    val filePath: String, // Internal location of scaled/watermarked image
    val mimeType: String,
    val source: String, // "camera" or "gallery"
    val lat: Double?,
    val lng: Double?,
    val address: String?,
    val timestamp: String?,
    val orderIdx: Int
)
