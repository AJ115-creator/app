package com.example.eyetracking

import kotlin.math.pow

/**
 * Direct Kotlin port of Fixation class from fixation.js
 * Uses the exact same algorithm and logic
 */
class Fixation(
    x: Double,
    y: Double,
    private val radius: Double = 100.0
) {
    private var fixation = 0.0
    private var x = x
    private var y = y
    
    /**
     * Processes the given x and y coordinates to detect fixation - exact logic from JS
     * @param x - The current x-coordinate
     * @param y - The current y-coordinate
     * @return - The current fixation level (between 0.0 and 1.0)
     */
    fun process(x: Double, y: Double): Double {
        val distanceSquared = (x - this.x).pow(2) + (y - this.y).pow(2)
        val radiusSquared = radius.pow(2)
        
        if (distanceSquared < radiusSquared) {
            // Inside radius - increase fixation level - exact same logic as JS
            fixation = minOf(fixation + 0.02, 1.0)
        } else {
            // Outside radius - reset fixation - exact same logic as JS
            this.x = x
            this.y = y
            fixation = 0.0
        }
        
        return fixation
    }
    
    /**
     * Get current fixation level
     */
    fun getFixationLevel(): Double = fixation
    
    /**
     * Get current center coordinates
     */
    fun getCenter(): Pair<Double, Double> = Pair(x, y)
    
    /**
     * Reset fixation
     */
    fun reset() {
        fixation = 0.0
    }
}
