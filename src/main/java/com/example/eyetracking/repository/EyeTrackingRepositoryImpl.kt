package com.example.eyetracking.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.eyetracking.data.EyeTrackingData
import com.example.eyetracking.data.EyeTrackingDataDto
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EyeTrackingRepositoryImpl @Inject constructor(
    private val postgrest: Postgrest
) : EyeTrackingRepository {
    
    override suspend fun saveEyeTrackingData(data: EyeTrackingData): EyeTrackingDataDto? {
        return try {
            withContext(Dispatchers.IO) {
                val dataDto = EyeTrackingDataDto(
                    userId = null, // Let database auto-assign user_id
                    age = data.age,
                    gender = data.gender,
                    gazeDuration = data.gazeDuration,
                    dwellTime = data.dwellTime,
                    saccadeLength = data.saccadeLength,
                    distractorSaccades = data.distractorSaccades,
                    fixationCount = data.fixationCount,
                    refixationRatio = data.refixationRatio,
                    deviceInfo = data.deviceInfo,
                    calibrationQuality = data.calibrationQuality
                )
                
                // Insert using the pattern from Supabase docs
                postgrest.from("eye_tracking_data").insert(dataDto)
                
                // Return the inserted data by querying the latest record
                // Since we can't get the exact inserted record, we'll return a constructed one
                dataDto
            }
        } catch (e: Exception) {
            Log.e("EyeTrackingRepo", "Error saving eye tracking data: ${e.message}")
            null
        }
    }
    
    override suspend fun getUserEyeTrackingData(userId: Int): List<EyeTrackingDataDto>? {
        return withContext(Dispatchers.IO) {
            try {
                postgrest.from("eye_tracking_data").select {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList<EyeTrackingDataDto>()
            } catch (e: Exception) {
                Log.e("EyeTrackingRepo", "Error fetching user data: ${e.message}")
                null
            }
        }
    }
    
    override suspend fun getAllEyeTrackingData(): List<EyeTrackingDataDto>? {
        return withContext(Dispatchers.IO) {
            try {
                postgrest.from("eye_tracking_data").select().decodeList<EyeTrackingDataDto>()
            } catch (e: Exception) {
                Log.e("EyeTrackingRepo", "Error fetching all data: ${e.message}")
                null
            }
        }
    }
    
    override suspend fun getEyeTrackingDataBySession(
        userId: Int, 
        sessionTimestamp: String
    ): EyeTrackingDataDto? {
        return withContext(Dispatchers.IO) {
            try {
                postgrest.from("eye_tracking_data").select {
                    filter {
                        eq("user_id", userId)
                        eq("session_timestamp", sessionTimestamp)
                    }
                }.decodeSingle<EyeTrackingDataDto>()
            } catch (e: Exception) {
                Log.e("EyeTrackingRepo", "Error fetching session data: ${e.message}")
                null
            }
        }
    }
    
    override suspend fun updateEyeTrackingData(sno: Int, data: EyeTrackingData): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                postgrest.from("eye_tracking_data").update({
                    set("age", data.age)
                    set("gender", data.gender)
                    set("gaze_duration", data.gazeDuration)
                    set("dwell_time", data.dwellTime)
                    set("saccade_length", data.saccadeLength)
                    set("distractor_saccades", data.distractorSaccades)
                    set("fixation_count", data.fixationCount)
                    set("refixation_ratio", data.refixationRatio)
                    set("calibration_quality", data.calibrationQuality)
                    set("updated_at", "NOW()")
                }) {
                    filter {
                        eq("sno", sno)
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.e("EyeTrackingRepo", "Error updating data: ${e.message}")
            false
        }
    }
    
    override suspend fun deleteEyeTrackingData(sno: Int): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                postgrest.from("eye_tracking_data").delete {
                    filter {
                        eq("sno", sno)
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.e("EyeTrackingRepo", "Error deleting data: ${e.message}")
            false
        }
    }
}