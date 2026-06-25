package com.sipedas.ponorogo.data

import kotlinx.coroutines.flow.Flow

class SipedasRepository(private val sipedasDao: SipedasDao) {

    val allDraftReports: Flow<List<DraftReport>> = sipedasDao.getAllDraftReports()

    suspend fun getDraftReportById(id: String): DraftReport? {
        return sipedasDao.getDraftReportById(id)
    }

    suspend fun getPhotosForDraft(draftId: String): List<DraftPhoto> {
        return sipedasDao.getPhotosForDraft(draftId)
    }

    suspend fun deleteDraft(draftId: String) {
        sipedasDao.deleteDraftReportById(draftId)
        sipedasDao.deletePhotosByDraftId(draftId)
    }

    suspend fun saveDraft(report: DraftReport, photos: List<DraftPhoto>) {
        sipedasDao.insertDraftReport(report)
        sipedasDao.deletePhotosByDraftId(report.id)
        for (photo in photos) {
            sipedasDao.insertDraftPhoto(photo)
        }
    }
}
