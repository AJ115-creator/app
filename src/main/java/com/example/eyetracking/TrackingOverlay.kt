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
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val crosshairPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
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

        // Draw gaze point as a green circle
        canvas.drawCircle(gazeX, gazeY, 20f, gazePaint)

        // Draw crosshair for better visibility
        val crosshairSize = 40f
        canvas.drawLine(
            gazeX - crosshairSize, gazeY,
            gazeX + crosshairSize, gazeY,
            crosshairPaint
        )
        canvas.drawLine(
            gazeX, gazeY - crosshairSize,
            gazeX, gazeY + crosshairSize,
            crosshairPaint
        )
    }
}

