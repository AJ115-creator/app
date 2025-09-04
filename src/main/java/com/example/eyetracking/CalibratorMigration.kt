package com.example.eyetracking

import android.util.Log

/**
 * Migration utility to safely transition between original and optimized calibrator
 * Ensures backward compatibility and allows toggling between implementations
 */
object CalibratorMigration {
    private const val TAG = "CalibratorMigration"
    
    /**
     * Configuration for optimization features
     * Can be controlled via remote config or preferences
     */
    data class OptimizationConfig(
        val useOptimizedCalibrator: Boolean = false,  // Feature flag for gradual rollout
        val maxSamples: Int = 500,
        val batchSize: Int = 10,
        val enableCaching: Boolean = true,
        val enableKalmanFilter: Boolean = true,
        val enablePerformanceMonitoring: Boolean = false
    )
    
    private var currentConfig = OptimizationConfig()
    
    /**
     * Interface for calibrator operations
     * Both original and optimized calibrators can implement this
     */
    interface CalibratorInterface {
        fun add(features: DoubleArray, targetPoint: Pair<Float, Float>)
        fun predict(features: DoubleArray): Pair<Float, Float>?
        fun movePoint(): Boolean
        fun getCurrentPoint(width: Int, height: Int): Pair<Float, Float>
        fun isFinished(): Boolean
        fun reset()
        val fitted: Boolean
    }
    
    /**
     * Wrapper for original Calibrator to implement our interface
     */
    class OriginalCalibratorAdapter(private val calibrator: Calibrator) : CalibratorInterface {
        override fun add(features: DoubleArray, targetPoint: Pair<Float, Float>) {
            calibrator.add(features, targetPoint)
        }
        
        override fun predict(features: DoubleArray): Pair<Float, Float>? {
            return calibrator.predict(features)
        }
        
        override fun movePoint(): Boolean {
            return calibrator.movePoint()
        }
        
        override fun getCurrentPoint(width: Int, height: Int): Pair<Float, Float> {
            return calibrator.getCurrentPoint(width, height)
        }
        
        override fun isFinished(): Boolean {
            return calibrator.isFinished()
        }
        
        override fun reset() {
            calibrator.reset()
        }
        
        override val fitted: Boolean
            get() = calibrator.fitted
    }
    
    /**
     * Wrapper for OptimizedCalibrator to implement our interface
     */
    class OptimizedCalibratorAdapter(val optimized: OptimizedCalibrator) : CalibratorInterface {
        override fun add(features: DoubleArray, targetPoint: Pair<Float, Float>) {
            optimized.add(features, targetPoint)
        }
        
        override fun predict(features: DoubleArray): Pair<Float, Float>? {
            return optimized.predict(features)
        }
        
        override fun movePoint(): Boolean {
            return optimized.movePoint()
        }
        
        override fun getCurrentPoint(width: Int, height: Int): Pair<Float, Float> {
            return optimized.getCurrentPoint(width, height)
        }
        
        override fun isFinished(): Boolean {
            return optimized.isFinished()
        }
        
        override fun reset() {
            optimized.reset()
        }
        
        override val fitted: Boolean
            get() = optimized.fitted
            
        fun getPerformanceStats(): String {
            return optimized.getPerformanceStats()
        }
    }
    
    /**
     * Get the appropriate calibrator based on configuration
     * Returns the interface that both implementations support
     */
    fun getCalibratorInterface(config: OptimizationConfig = currentConfig): CalibratorInterface {
        currentConfig = config
        
        return if (config.useOptimizedCalibrator) {
            Log.d(TAG, "Using OptimizedCalibrator with config: $config")
            val optimized = OptimizedCalibrator(
                maxSamples = config.maxSamples,
                batchSize = config.batchSize,
                enableCaching = config.enableCaching,
                enableKalmanFilter = config.enableKalmanFilter,
                enablePerformanceMonitoring = config.enablePerformanceMonitoring
            )
            OptimizedCalibratorAdapter(optimized)
        } else {
            Log.d(TAG, "Using original Calibrator")
            OriginalCalibratorAdapter(Calibrator())
        }
    }
    
    /**
     * Get a Calibrator instance (always returns original for compatibility)
     * Use this when you need a actual Calibrator object
     */
    fun getCalibrator(config: OptimizationConfig = currentConfig): Calibrator {
        // For now, always return original Calibrator to maintain compatibility
        // The optimized version should be accessed through getCalibratorInterface
        Log.d(TAG, "Returning original Calibrator for compatibility")
        return Calibrator()
    }
    
    /**
     * Create an OptimizedCalibrator directly
     * Use when you specifically want the optimized version
     */
    fun createOptimizedCalibrator(config: OptimizationConfig = currentConfig): OptimizedCalibrator {
        return OptimizedCalibrator(
            maxSamples = config.maxSamples,
            batchSize = config.batchSize,
            enableCaching = config.enableCaching,
            enableKalmanFilter = config.enableKalmanFilter,
            enablePerformanceMonitoring = config.enablePerformanceMonitoring
        )
    }
    
    /**
     * Check if optimizations are available and compatible
     */
    fun isOptimizationAvailable(): Boolean {
        return try {
            // Check if required classes are available
            Class.forName("com.example.eyetracking.OptimizedCalibrator")
            Class.forName("com.example.eyetracking.filters.KalmanFilter")
            Class.forName("com.example.eyetracking.utils.CircularBuffer")
            true
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Optimization classes not found: ${e.message}")
            false
        }
    }
    
    /**
     * Get performance stats if using optimized version
     */
    fun getPerformanceStats(): String {
        return "Performance monitoring available when using OptimizedCalibrator"
    }
}
