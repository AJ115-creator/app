package com.example.eyetracking

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CalibrationOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "CalibrationOverlay"
    }

    private val redPaint = Paint().apply {
        color = Color.argb(255, 180, 0, 0) // Darker red for better visibility
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val redBorderPaint = Paint().apply {
        color = Color.argb(255, 120, 0, 0) // Even darker border
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private var calibrationPointX: Float = -1f
    private var calibrationPointY: Float = -1f

    fun updateCalibrationPoint(x: Float, y: Float) {
        android.util.Log.d(TAG, "updateCalibrationPoint called - x: $x, y: $y")
        calibrationPointX = x
        calibrationPointY = y
        invalidate() // Redraw the view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        android.util.Log.v(TAG, "onDraw called - point at ($calibrationPointX, $calibrationPointY)")
        if (calibrationPointX >= 0 && calibrationPointY >= 0) {
            // Draw a larger circle (75f instead of 50f) with a border for better visibility
            canvas.drawCircle(calibrationPointX, calibrationPointY, 75f, redPaint)
            canvas.drawCircle(calibrationPointX, calibrationPointY, 75f, redBorderPaint)
            android.util.Log.v(TAG, "Drew red circle at ($calibrationPointX, $calibrationPointY)")
        } else {
            android.util.Log.v(TAG, "Skipping draw - invalid coordinates")
        }
    }
}

