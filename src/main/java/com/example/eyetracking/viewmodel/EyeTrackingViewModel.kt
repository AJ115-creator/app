package com.example.eyetracking.viewmodel

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eyetracking.data.EyeTrackingData
import com.example.eyetracking.data.UserDetails
import com.example.eyetracking.repository.EyeTrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EyeTrackingViewModel @Inject constructor(
    private val eyeTrackingRepository: EyeTrackingRepository
) : ViewModel() {
    
    fun saveEyeMetrics(
        userDetails: UserDetails,
        gazeDuration: Double,
        dwellTime: Double,
        saccadeLength: Double,
        distractorSaccades: Int,
        fixationCount: Int,
        refixationRatio: Double,
        calibrationQuality: Double? = null
    ) {
        viewModelScope.launch {
            val eyeTrackingData = EyeTrackingData(
                userId = null, // Will be auto-assigned by database
                age = userDetails.age,
                gender = userDetails.gender,
                gazeDuration = gazeDuration,
                dwellTime = dwellTime,
                saccadeLength = saccadeLength,
                distractorSaccades = distractorSaccades,
                fixationCount = fixationCount,
                refixationRatio = refixationRatio,
                deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}",
                calibrationQuality = calibrationQuality
            )
            
            val savedData = eyeTrackingRepository.saveEyeTrackingData(eyeTrackingData)
            if (savedData != null) {
                Log.d("EyeTracking", "Eye tracking data saved successfully with user_id: ${savedData.userId}")
            } else {
                Log.e("EyeTracking", "Failed to save eye tracking data")
            }
        }
    }
}
