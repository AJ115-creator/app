package com.example.eyetracking.repository

import com.example.eyetracking.data.EyeTrackingData
import com.example.eyetracking.data.EyeTrackingDataDto

interface EyeTrackingRepository {
    suspend fun saveEyeTrackingData(data: EyeTrackingData): EyeTrackingDataDto?
    suspend fun getUserEyeTrackingData(userId: Int): List<EyeTrackingDataDto>?
    suspend fun getAllEyeTrackingData(): List<EyeTrackingDataDto>?
    suspend fun getEyeTrackingDataBySession(userId: Int, sessionTimestamp: String): EyeTrackingDataDto?
    suspend fun updateEyeTrackingData(sno: Int, data: EyeTrackingData): Boolean
    suspend fun deleteEyeTrackingData(sno: Int): Boolean
}
