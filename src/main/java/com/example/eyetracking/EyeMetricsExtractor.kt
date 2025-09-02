package com.example.eyetracking

import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Eye Metrics Extractor - Tracks and calculates 6 key eye tracking metrics
 * Based on JavaScript implementation from eye_features.js
 */
class EyeMetricsExtractor(
    private val fixationRadius: Float = 50f,           // pixels
    private val fixationDuration: Long = 100L,         // milliseconds  
    private val saccadeThreshold: Float = 30f,         // pixels
    private val distractorThreshold: Float = 200f      // pixels
) {
    // Data structures for tracking
    private val gazeHistory = mutableListOf<GazePoint>()
    private val fixations = mutableListOf<Fixation>()
    private val saccades = mutableListOf<Saccade>()
    private var currentFixation: FixationData? = null
    private var lastGazePoint: GazePoint? = null
    private var lastTimestamp: Long? = null
    
    // Metric counters
    private var totalGazeDuration: Long = 0L
    private var totalDwellTime: Long = 0L
    private var totalSaccadeLength: Float = 0f
    private var distractorSaccadeCount: Int = 0
    private var fixationCount: Int = 0
    private var refixationCount: Int = 0
    
    // Areas of interest for dwell time calculation
    private val areasOfInterest = mutableListOf<AreaOfInterest>()
    
    // Refixation tracking
    private val visitedAreas = mutableMapOf<String, Int>()
    private var currentArea: AreaOfInterest? = null
    
    data class GazePoint(
        val x: Float,
        val y: Float,
        val timestamp: Long
    )
    
    data class FixationData(
        val start: GazePoint,
        var center: GazePoint,
        var duration: Long
    )
    
    // Track fixation durations separately since Fixation class only has x,y,radius
    private val fixationDurations = mutableListOf<Long>()
    
    data class Saccade(
        val start: GazePoint,
        val end: GazePoint,
        val length: Float,
        val duration: Long
    )
    
    data class AreaOfInterest(
        val id: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )
    
    data class EyeMetrics(
        val gazeDuration: Long,           // 1. Total time spent looking (ms)
        val dwellTime: Long,              // 2. Time spent in specific areas (ms)
        val saccadeLength: Float,         // 3. Average rapid eye movement distance (pixels)
        val distractorSaccades: Int,      // 4. Count of movements to non-interest areas
        val fixationCount: Int,           // 5. Number of distinct fixations
        val refixationRatio: Float,       // 6. Ratio of revisits to areas (0-1)
        // Additional metrics
        val totalSaccades: Int,
        val avgFixationDuration: Long,
        val totalTime: Long
    )
    
    /**
     * Process a new gaze point and update all metrics
     */
    fun processGazePoint(x: Float, y: Float, timestamp: Long) {
        val gazePoint = GazePoint(x, y, timestamp)
        
        // Add to history
        gazeHistory.add(gazePoint)
        
        // Keep only last 1000 points to prevent memory issues
        if (gazeHistory.size > 1000) {
            gazeHistory.removeAt(0)
        }
        
        // Update all metrics
        updateGazeDuration(timestamp)
        detectFixation(gazePoint)
        detectSaccade(gazePoint)
        updateDwellTime(gazePoint)
        updateRefixationRatio(gazePoint)
        
        lastGazePoint = gazePoint
        lastTimestamp = timestamp
    }
    
    /**
     * 1. Gaze Duration - Total time spent looking at any point
     */
    private fun updateGazeDuration(timestamp: Long) {
        lastTimestamp?.let { last ->
            val timeDiff = timestamp - last
            totalGazeDuration += timeDiff
        }
    }
    
    /**
     * 2. Dwell Time - Time spent in specific areas of interest
     */
    private fun updateDwellTime(gazePoint: GazePoint) {
        val area = getCurrentArea(gazePoint)
        
        if (area != null && currentArea?.id == area.id) {
            // Still in the same area
            lastTimestamp?.let { last ->
                val timeDiff = gazePoint.timestamp - last
                totalDwellTime += timeDiff
            }
        }
        
        currentArea = area
    }
    
    /**
     * 3. Saccade Length - Distance of rapid eye movements
     */
    private fun detectSaccade(gazePoint: GazePoint) {
        val last = lastGazePoint ?: return
        
        val distance = calculateDistance(last, gazePoint)
        val timeDiff = gazePoint.timestamp - last.timestamp
        
        // Detect saccade (rapid movement)
        if (distance > saccadeThreshold && timeDiff < 100) {
            val saccade = Saccade(
                start = last,
                end = gazePoint,
                length = distance,
                duration = timeDiff
            )
            saccades.add(saccade)
            totalSaccadeLength += distance
            
            // 4. Check if it's a distractor saccade
            if (isDistractorSaccade(gazePoint)) {
                distractorSaccadeCount++
            }
        }
    }
    
    /**
     * 4. Distractor Saccades - Saccades to non-interest areas
     */
    private fun isDistractorSaccade(gazePoint: GazePoint): Boolean {
        // Check if the gaze point is outside all areas of interest
        return getCurrentArea(gazePoint) == null
    }
    
    /**
     * 5. Fixation Count - Number of distinct fixations
     */
    private fun detectFixation(gazePoint: GazePoint) {
        val current = currentFixation
        
        if (current == null) {
            // Start new fixation
            currentFixation = FixationData(
                start = gazePoint,
                center = gazePoint,
                duration = 0L
            )
        } else {
            val distance = calculateDistance(current.center, gazePoint)
            
            if (distance <= fixationRadius) {
                // Continue current fixation
                current.duration = gazePoint.timestamp - current.start.timestamp
            } else {
                // End current fixation if it meets minimum duration
                if (current.duration >= fixationDuration) {
                    // Store fixation in list - Fixation class only takes x, y, radius
                    fixations.add(
                        Fixation(
                            x = current.center.x.toDouble(),  // Convert Float to Double
                            y = current.center.y.toDouble()   // Convert Float to Double
                        )
                    )
                    fixationDurations.add(current.duration)  // Track duration separately
                    fixationCount++
                }
                
                // Start new fixation
                currentFixation = FixationData(
                    start = gazePoint,
                    center = gazePoint,
                    duration = 0L
                )
            }
        }
    }
    
    /**
     * 6. Refixation Ratio - Ratio of revisits to areas
     */
    private fun updateRefixationRatio(gazePoint: GazePoint) {
        val area = getCurrentArea(gazePoint)
        
        area?.let {
            val visitCount = visitedAreas[it.id] ?: 0
            visitedAreas[it.id] = visitCount + 1
            
            if (visitCount > 0) {
                refixationCount++
            }
        }
    }
    
    /**
     * Get the current area of interest for a gaze point
     */
    private fun getCurrentArea(gazePoint: GazePoint): AreaOfInterest? {
        return areasOfInterest.find { area ->
            isPointInArea(gazePoint, area)
        }
    }
    
    /**
     * Check if a point is within an area of interest
     */
    private fun isPointInArea(point: GazePoint, area: AreaOfInterest): Boolean {
        return point.x >= area.x && 
               point.x <= area.x + area.width &&
               point.y >= area.y && 
               point.y <= area.y + area.height
    }
    
    /**
     * Calculate Euclidean distance between two points
     */
    private fun calculateDistance(point1: GazePoint, point2: GazePoint): Float {
        val dx = point2.x - point1.x
        val dy = point2.y - point1.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
    
    /**
     * Get all extracted metrics
     */
    fun getMetrics(): EyeMetrics {
        val totalTime = if (gazeHistory.isNotEmpty()) {
            gazeHistory.last().timestamp - gazeHistory.first().timestamp
        } else {
            0L
        }
        
        val avgSaccadeLength = if (saccades.isNotEmpty()) {
            totalSaccadeLength / saccades.size
        } else {
            0f
        }
        
        val refixationRatio = if (fixationCount > 0) {
            refixationCount.toFloat() / fixationCount
        } else {
            0f
        }
        
        // Calculate average fixation duration from our tracked durations
        val avgFixationDuration = if (fixationDurations.isNotEmpty()) {
            fixationDurations.sum() / fixationDurations.size
        } else {
            0L
        }
        
        return EyeMetrics(
            gazeDuration = totalGazeDuration,
            dwellTime = totalDwellTime,
            saccadeLength = avgSaccadeLength,
            distractorSaccades = distractorSaccadeCount,
            fixationCount = fixationCount,
            refixationRatio = refixationRatio,
            totalSaccades = saccades.size,
            avgFixationDuration = avgFixationDuration,
            totalTime = totalTime
        )
    }
    
    /**
     * Add an area of interest for dwell time calculation
     */
    fun addAreaOfInterest(id: String, x: Float, y: Float, width: Float, height: Float) {
        areasOfInterest.add(AreaOfInterest(id, x, y, width, height))
    }
    
    /**
     * Clear all areas of interest
     */
    fun clearAreasOfInterest() {
        areasOfInterest.clear()
    }
    
    /**
     * Reset all metric counters
     */
    fun reset() {
        gazeHistory.clear()
        fixations.clear()
        fixationDurations.clear()  // Clear duration tracking
        saccades.clear()
        currentFixation = null
        lastGazePoint = null
        lastTimestamp = null
        
        totalGazeDuration = 0L
        totalDwellTime = 0L
        totalSaccadeLength = 0f
        distractorSaccadeCount = 0
        fixationCount = 0
        refixationCount = 0
        
        visitedAreas.clear()
        currentArea = null
    }
    
    /**
     * Get real-time metric updates
     */
    fun getRealTimeMetrics(): EyeMetrics {
        return getMetrics()
    }
}
