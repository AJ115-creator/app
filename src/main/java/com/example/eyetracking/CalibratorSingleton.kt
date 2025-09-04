package com.example.eyetracking

/**
 * Singleton object to share the calibrator instance between activities
 * This allows the calibration data to be passed from CalibrationActivity to TrackingActivity
 */
object CalibratorSingleton {
    var calibrator: Calibrator? = null
    var startWidth: Double = 0.0
    var startHeight: Double = 0.0
    var headStartingPos: Pair<Double, Double> = Pair(0.0, 0.0)
    
    /**
     * Set calibration data
     */
    fun setCalibrationData(
        calibratorInstance: Calibrator,
        width: Double,
        height: Double,
        headPos: Pair<Double, Double>
    ) {
        calibrator = calibratorInstance
        startWidth = width
        startHeight = height
        headStartingPos = headPos
    }
    
    /**
     * Clear calibration data
     */
    fun clear() {
        calibrator = null
        startWidth = 0.0
        startHeight = 0.0
        headStartingPos = Pair(0.0, 0.0)
    }
    
    /**
     * Check if calibration data is available
     */
    fun isCalibrated(): Boolean {
        return calibrator?.fitted == true
    }
}
