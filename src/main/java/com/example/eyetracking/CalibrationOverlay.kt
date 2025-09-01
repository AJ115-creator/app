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

    private val redPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var calibrationPointX: Float = -1f
    private var calibrationPointY: Float = -1f

    fun updateCalibrationPoint(x: Float, y: Float) {
        calibrationPointX = x
        calibrationPointY = y
        invalidate() // Redraw the view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (calibrationPointX >= 0 && calibrationPointY >= 0) {
            canvas.drawCircle(calibrationPointX, calibrationPointY, 50f, redPaint)
        }
    }
}

