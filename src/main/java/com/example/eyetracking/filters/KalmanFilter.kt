package com.example.eyetracking.filters

/**
 * Kalman Filter implementation for smoothing gaze predictions.
 * Provides optimal estimation by reducing noise without introducing lag.
 * 
 * @param processNoise Process noise covariance (Q) - lower values = more trust in model
 * @param measurementNoise Measurement noise covariance (R) - lower values = more trust in measurements
 * @param initialUncertainty Initial error covariance (P0)
 */
class KalmanFilter(
    private val processNoise: Double = 0.01,
    private val measurementNoise: Double = 0.1,
    private val initialUncertainty: Double = 1.0
) {
    // State estimate
    private var x: Double = 0.0
    
    // Error covariance
    private var P: Double = initialUncertainty
    
    // Process noise covariance
    private val Q: Double = processNoise
    
    // Measurement noise covariance  
    private val R: Double = measurementNoise
    
    // State transition (F = 1 for simple position model)
    private val F: Double = 1.0
    
    // Measurement model (H = 1 for direct observation)
    private val H: Double = 1.0
    
    /**
     * Predict step of Kalman filter
     * Updates state and covariance based on model
     */
    private fun predict() {
        // State prediction: x = F * x
        x = F * x
        
        // Covariance prediction: P = F * P * F' + Q
        P = F * P * F + Q
    }
    
    /**
     * Update step of Kalman filter
     * Corrects prediction based on measurement
     */
    private fun update(measurement: Double) {
        // Innovation (measurement residual): y = z - H * x
        val y = measurement - H * x
        
        // Innovation covariance: S = H * P * H' + R
        val S = H * P * H + R
        
        // Kalman gain: K = P * H' / S
        val K = P * H / S
        
        // State update: x = x + K * y
        x = x + K * y
        
        // Covariance update: P = (1 - K * H) * P
        P = (1 - K * H) * P
    }
    
    /**
     * Filter a new measurement
     * Combines predict and update steps
     * 
     * @param measurement The new measurement to filter
     * @return The filtered (smoothed) value
     */
    fun filter(measurement: Double): Double {
        predict()
        update(measurement)
        return x
    }
    
    /**
     * Reset the filter to initial state
     */
    fun reset() {
        x = 0.0
        P = initialUncertainty
    }
    
    /**
     * Get current state estimate
     */
    fun getState(): Double = x
    
    /**
     * Get current error covariance
     */
    fun getCovariance(): Double = P
    
    /**
     * Set initial state (useful for warm start)
     */
    fun setState(initialState: Double) {
        x = initialState
    }
}

/**
 * 2D Kalman Filter for X,Y coordinate filtering
 * Convenience class for filtering gaze points
 */
class KalmanFilter2D(
    processNoise: Double = 0.01,
    measurementNoise: Double = 0.1,
    initialUncertainty: Double = 1.0
) {
    private val filterX = KalmanFilter(processNoise, measurementNoise, initialUncertainty)
    private val filterY = KalmanFilter(processNoise, measurementNoise, initialUncertainty)
    
    /**
     * Filter a 2D point
     * 
     * @param x X coordinate measurement
     * @param y Y coordinate measurement
     * @return Filtered (x, y) coordinates
     */
    fun filter(x: Float, y: Float): Pair<Float, Float> {
        val smoothX = filterX.filter(x.toDouble()).toFloat()
        val smoothY = filterY.filter(y.toDouble()).toFloat()
        return Pair(smoothX, smoothY)
    }
    
    /**
     * Reset both filters
     */
    fun reset() {
        filterX.reset()
        filterY.reset()
    }
    
    /**
     * Set initial state for both filters
     */
    fun setState(x: Float, y: Float) {
        filterX.setState(x.toDouble())
        filterY.setState(y.toDouble())
    }
}
