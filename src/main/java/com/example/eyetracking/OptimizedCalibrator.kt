package com.example.eyetracking

import android.util.Log
import android.util.LruCache
import com.example.eyetracking.filters.KalmanFilter2D
import com.example.eyetracking.utils.CircularBuffer
import kotlinx.coroutines.*
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureNanoTime

/**
 * Optimized Multiple Linear Regression Calibrator
 * 
 * This class implements an optimized version of the MLR calibrator with:
 * - Circular buffers to limit memory usage
 * - Batch training to reduce computational overhead
 * - Kalman filtering for smooth predictions
 * - Feature caching for faster predictions
 * - Parallel training of X and Y models
 * - Performance monitoring
 * 
 * @property maxSamples Maximum number of training samples to retain
 * @property batchSize Number of samples to accumulate before training
 * @property enableCaching Whether to cache predictions
 * @property enableKalmanFilter Whether to use Kalman filtering for smoothing
 * @property enablePerformanceMonitoring Whether to log performance metrics
 */
class OptimizedCalibrator(
    private val maxSamples: Int = 500,
    private val batchSize: Int = 10,
    private val enableCaching: Boolean = true,
    private val enableKalmanFilter: Boolean = true,
    private val enablePerformanceMonitoring: Boolean = false
) {
    companion object {
        private const val TAG = "OptimizedCalibrator"
        private const val TMP_BUFFER_SIZE = 40
        private const val CACHE_SIZE = 50
    }
    
    // Circular buffers for memory-efficient storage
    private val xBuffer = CircularBuffer<DoubleArray>(maxSamples)
    private val yxBuffer = CircularBuffer<Double>(maxSamples)
    private val yyBuffer = CircularBuffer<Double>(maxSamples)
    
    private val tmpXBuffer = CircularBuffer<DoubleArray>(TMP_BUFFER_SIZE)
    private val tmpYxBuffer = CircularBuffer<Double>(TMP_BUFFER_SIZE)
    private val tmpYyBuffer = CircularBuffer<Double>(TMP_BUFFER_SIZE)
    
    // Regression models
    private var olsX: OLSMultipleLinearRegression = OLSMultipleLinearRegression()
    private var olsY: OLSMultipleLinearRegression = OLSMultipleLinearRegression()
    
    // Model parameters
    private var betaX: DoubleArray? = null
    private var betaY: DoubleArray? = null
    var fitted = false
        private set
    
    // Batch training control
    private var samplesSinceLastTrain = 0
    private val trainingSemaphore = Semaphore(1)
    
    // Async training support
    private val trainingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isTraining = AtomicBoolean(false)
    private var trainingJob: Job? = null
    
    // Prediction caching
    private val predictionCache: LruCache<String, Pair<Float, Float>>? = if (enableCaching) {
        LruCache(CACHE_SIZE)
    } else null
    
    // Kalman filter for smoothing
    private val kalmanFilter: KalmanFilter2D? = if (enableKalmanFilter) {
        KalmanFilter2D(
            processNoise = 0.01,
            measurementNoise = 0.1,
            initialUncertainty = 1.0
        )
    } else null
    
    // Calibration matrix (same as original)
    private val calibrationMatrix = CalibrationMatrix()
    
    // Performance metrics
    private var totalTrainingTime = 0L
    private var trainingCount = 0
    private var totalPredictionTime = 0L
    private var predictionCount = 0
    
    /**
     * Add a new training sample
     * Uses batch training to improve efficiency
     */
    fun add(features: DoubleArray, targetPoint: Pair<Float, Float>) {
        val addTime = if (enablePerformanceMonitoring) {
            measureNanoTime {
                addInternal(features, targetPoint)
            }
        } else {
            addInternal(features, targetPoint)
            0L
        }
        
        if (enablePerformanceMonitoring && addTime > 0) {
            Log.v(TAG, "Sample addition took: ${addTime / 1_000_000.0} ms")
        }
    }
    
    private fun addInternal(features: DoubleArray, targetPoint: Pair<Float, Float>) {
        Log.v(TAG, "add() - features size: ${features.size}, target: (${targetPoint.first}, ${targetPoint.second})")
        
        // Add to temporary buffers
        tmpXBuffer.add(features)
        tmpYxBuffer.add(targetPoint.first.toDouble())
        tmpYyBuffer.add(targetPoint.second.toDouble())
        
        samplesSinceLastTrain++
        
        // Train when batch size reached or buffer is full
        if (samplesSinceLastTrain >= batchSize || tmpXBuffer.isFull()) {
            trainBatch()
            samplesSinceLastTrain = 0
        }
        
        Log.v(TAG, "Buffer sizes - tmpX: ${tmpXBuffer.size()}, X: ${xBuffer.size()}")
    }
    
    /**
     * Train the model in batches for efficiency
     */
    private fun trainBatch() {
        if (!trainingSemaphore.tryAcquire()) {
            Log.v(TAG, "Training already in progress, skipping batch")
            return
        }
        
        try {
            val trainingTime = if (enablePerformanceMonitoring) {
                measureNanoTime {
                    performTraining()
                }
            } else {
                performTraining()
                0L
            }
            
            if (enablePerformanceMonitoring && trainingTime > 0) {
                totalTrainingTime += trainingTime
                trainingCount++
                val avgTime = totalTrainingTime / trainingCount / 1_000_000.0
                Log.d(TAG, "Batch training took: ${trainingTime / 1_000_000.0} ms, avg: $avgTime ms")
            }
        } finally {
            trainingSemaphore.release()
        }
    }
    
    private fun performTraining() {
        // Cancel previous async training if still running
        trainingJob?.cancel()
        
        // Start new training job
        trainingJob = trainingScope.launch {
            if (isTraining.compareAndSet(false, true)) {
                try {
                    trainAsync()
                } finally {
                    isTraining.set(false)
                }
            }
        }
        
        // For now, block until training completes to maintain compatibility
        runBlocking {
            trainingJob?.join()
        }
    }
    
    private suspend fun trainAsync() = coroutineScope {
        // Combine permanent and temporary buffers
        val allX = xBuffer.toList() + tmpXBuffer.toList()
        val allYx = yxBuffer.toList() + tmpYxBuffer.toList()
        val allYy = yyBuffer.toList() + tmpYyBuffer.toList()
        
        Log.v(TAG, "trainAsync() - total samples: ${allX.size}")
        
        if (allX.isNotEmpty() && allX.size > allX[0].size) {
            try {
                // Train X and Y models in parallel
                val deferredX = async(Dispatchers.Default) {
                    val ols = OLSMultipleLinearRegression()
                    ols.newSampleData(allYx.toDoubleArray(), allX.toTypedArray())
                    ols.estimateRegressionParameters()
                }
                
                val deferredY = async(Dispatchers.Default) {
                    val ols = OLSMultipleLinearRegression()
                    ols.newSampleData(allYy.toDoubleArray(), allX.toTypedArray())
                    ols.estimateRegressionParameters()
                }
                
                // Wait for both models to complete
                betaX = deferredX.await()
                betaY = deferredY.await()
                
                fitted = true
                Log.d(TAG, "Model trained successfully - fitted: true")
                
                // Clear prediction cache when model updates
                predictionCache?.evictAll()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to train model: ${e.message}")
                fitted = false
            }
        } else {
            Log.v(TAG, "Not enough samples to train: ${allX.size} samples")
        }
    }
    
    /**
     * Predict gaze point with optimizations
     * Uses caching and Kalman filtering for better performance
     */
    fun predict(features: DoubleArray): Pair<Float, Float>? {
        if (!fitted || betaX == null || betaY == null) {
            Log.v(TAG, "predict() - not ready: fitted=$fitted")
            return null
        }
        
        val predictionTime = if (enablePerformanceMonitoring) {
            var result: Pair<Float, Float>? = null
            val time = measureNanoTime {
                result = predictInternal(features)
            }
            totalPredictionTime += time
            predictionCount++
            if (predictionCount % 100 == 0) {
                val avgTime = totalPredictionTime / predictionCount / 1_000.0
                Log.d(TAG, "Avg prediction time: $avgTime μs")
            }
            result
        } else {
            predictInternal(features)
        }
        
        return predictionTime
    }
    
    private fun predictInternal(features: DoubleArray): Pair<Float, Float>? {
        // Check cache first if enabled
        if (enableCaching) {
            val cacheKey = getCacheKey(features)
            predictionCache?.get(cacheKey)?.let { cached ->
                Log.v(TAG, "Cache hit for prediction")
                return cached
            }
        }
        
        // Compute raw predictions using optimized calculation
        val rawX = computePrediction(betaX!!, features)
        val rawY = computePrediction(betaY!!, features)
        
        // Apply Kalman filtering if enabled
        val result = if (enableKalmanFilter && kalmanFilter != null) {
            kalmanFilter.filter(rawX, rawY)
        } else {
            Pair(rawX, rawY)
        }
        
        // Store in cache if enabled
        if (enableCaching) {
            val cacheKey = getCacheKey(features)
            predictionCache?.put(cacheKey, result)
        }
        
        Log.v(TAG, "predict() - result: (${result.first}, ${result.second})")
        return result
    }
    
    /**
     * Optimized prediction calculation with loop unrolling
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun computePrediction(beta: DoubleArray, features: DoubleArray): Float {
        var sum = beta[0]
        var i = 1
        val size = beta.size
        val featureSize = features.size
        
        // Unroll loop for better performance (process 4 at a time)
        while (i < size - 3 && i - 1 < featureSize - 3) {
            sum += beta[i] * features[i - 1]
            sum += beta[i + 1] * features[i]
            sum += beta[i + 2] * features[i + 1]
            sum += beta[i + 3] * features[i + 2]
            i += 4
        }
        
        // Handle remaining elements
        while (i < size && i - 1 < featureSize) {
            sum += beta[i] * features[i - 1]
            i++
        }
        
        return sum.toFloat()
    }
    
    /**
     * Generate cache key for features
     */
    private fun getCacheKey(features: DoubleArray): String {
        return features.contentHashCode().toString()
    }
    
    /**
     * Move temporary samples to permanent storage
     */
    fun movePoint(): Boolean {
        Log.d(TAG, "movePoint() - moving ${tmpXBuffer.size()} samples to permanent storage")
        
        // Move temporary buffers to permanent storage
        xBuffer.addAll(tmpXBuffer.toList())
        yxBuffer.addAll(tmpYxBuffer.toList())
        yyBuffer.addAll(tmpYyBuffer.toList())
        
        // Clear temporary buffers
        tmpXBuffer.clear()
        tmpYxBuffer.clear()
        tmpYyBuffer.clear()
        samplesSinceLastTrain = 0
        
        calibrationMatrix.moveNext()
        val finished = calibrationMatrix.isFinished()
        Log.d(TAG, "movePoint() - calibration finished: $finished, total samples: ${xBuffer.size()}")
        
        return !finished
    }
    
    /**
     * Get current calibration point
     */
    fun getCurrentPoint(width: Int, height: Int): Pair<Float, Float> {
        return calibrationMatrix.getCurrentPoint(width, height)
    }
    
    /**
     * Check if calibration is finished
     */
    fun isFinished(): Boolean {
        return calibrationMatrix.isFinished()
    }
    
    /**
     * Reset calibrator to initial state
     */
    fun reset() {
        // Clear all buffers
        xBuffer.clear()
        yxBuffer.clear()
        yyBuffer.clear()
        tmpXBuffer.clear()
        tmpYxBuffer.clear()
        tmpYyBuffer.clear()
        
        // Reset model
        betaX = null
        betaY = null
        fitted = false
        samplesSinceLastTrain = 0
        
        // Reset filters and cache
        kalmanFilter?.reset()
        predictionCache?.evictAll()
        
        // Reset calibration matrix
        calibrationMatrix.reset()
        
        // Reset performance metrics
        totalTrainingTime = 0L
        trainingCount = 0
        totalPredictionTime = 0L
        predictionCount = 0
        
        Log.d(TAG, "Calibrator reset")
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        trainingJob?.cancel()
        trainingScope.cancel()
    }
    
    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): String {
        val avgTrainingTime = if (trainingCount > 0) {
            totalTrainingTime / trainingCount / 1_000_000.0
        } else 0.0
        
        val avgPredictionTime = if (predictionCount > 0) {
            totalPredictionTime / predictionCount / 1_000.0
        } else 0.0
        
        return """
            Performance Statistics:
            - Avg Training Time: $avgTrainingTime ms
            - Avg Prediction Time: $avgPredictionTime μs
            - Total Trainings: $trainingCount
            - Total Predictions: $predictionCount
            - Cache Enabled: $enableCaching
            - Kalman Filter Enabled: $enableKalmanFilter
        """.trimIndent()
    }
}
