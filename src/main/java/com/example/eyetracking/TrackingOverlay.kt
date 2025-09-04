package com.example.eyetracking

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Tracking overlay that shows the current gaze point during eye tracking
 */
class TrackingOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gazePaint = Paint().apply {
        color = Color.BLUE  // Changed to blue like calibration
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 200  // Slightly transparent
    }

    private val gazeBorderPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        alpha = 255
    }

    private var gazeX = -1f
    private var gazeY = -1f
    private var isVisible = true

    /**
     * Update the current gaze point
     */
    fun updateGaze(x: Float, y: Float) {
        gazeX = x
        gazeY = y
        invalidate()
    }

    /**
     * Clear the gaze point
     */
    fun clearGaze() {
        gazeX = -1f
        gazeY = -1f
        invalidate()
    }

    /**
     * Set visibility of the gaze indicator
     */
    fun setGazeVisible(visible: Boolean) {
        isVisible = visible
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isVisible || gazeX < 0 || gazeY < 0 || width == 0 || height == 0) return

        // Draw gaze point as a blue circle (same as calibration)
        val circleRadius = 30f  // Slightly larger for better visibility
        canvas.drawCircle(gazeX, gazeY, circleRadius, gazePaint)
        canvas.drawCircle(gazeX, gazeY, circleRadius, gazeBorderPaint)
    }
}

