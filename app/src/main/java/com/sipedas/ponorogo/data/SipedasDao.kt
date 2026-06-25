package com.sipedas.ponorogo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SipedasDao {
    @Query("SELECT * FROM draft_reports ORDER BY timestamp DESC")
    fun getAllDraftReports(): Flow<List<DraftReport>>

    @Query("SELECT * FROM draft_reports WHERE id = :id LIMIT 1")
    suspend fun getDraftReportById(id: String): DraftReport?

    @Query("SELECT * FROM draft_photos WHERE draftId = :draftId ORDER BY orderIdx ASC")
    suspend fun getPhotosForDraft(draftId: String): List<DraftPhoto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraftReport(draftReport: DraftReport)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraftPhoto(draftPhoto: DraftPhoto)

    @Query("DELETE FROM draft_reports WHERE id = :id")
    suspend fun deleteDraftReportById(id: String)

    @Query("DELETE FROM draft_photos WHERE id = :photoId")
    suspend fun deleteDraftPhotoById(photoId: Int)

    @Query("DELETE FROM draft_photos WHERE draftId = :draftId")
    suspend fun deletePhotosByDraftId(draftId: String)

}
