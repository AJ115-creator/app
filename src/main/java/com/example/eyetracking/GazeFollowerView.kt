package com.example.eyetracking

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * A reusable view that displays a blue circle following the user's gaze.
 * This view provides consistent gaze visualization across calibration and tracking phases.
 */
class GazeFollowerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val CIRCLE_RADIUS_DP = 12f
        private const val BORDER_WIDTH_DP = 2f
        private const val ANIMATION_DURATION = 100L
    }

    // Paint for the main blue circle
    private val gazePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint for the white border
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = BORDER_WIDTH_DP * resources.displayMetrics.density
        isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, Color.argb(100, 0, 0, 0))
    }

    // Current gaze position
    private var gazeX = -1f
    private var gazeY = -1f
    
    // Animated position for smooth movement
    private var animatedX = -1f
    private var animatedY = -1f
    
    // Animation parameters
    private val smoothingFactor = 0.15f // Lower value = smoother movement
    
    private var isVisible = true
    private val circleRadius = CIRCLE_RADIUS_DP * resources.displayMetrics.density

    init {
        // Ensure the view doesn't clip the shadow
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /**
     * Update the gaze position with smooth animation
     * @param x X coordinate in pixels relative to parent view
     * @param y Y coordinate in pixels relative to parent view
     */
    fun updateGazePosition(x: Float, y: Float) {
        gazeX = x
        gazeY = y
        
        // Initialize animated position on first update
        if (animatedX < 0 || animatedY < 0) {
            animatedX = x
            animatedY = y
        }
        
        invalidate()
    }

    /**
     * Set visibility of the gaze follower
     */
    fun setGazeVisible(visible: Boolean) {
        isVisible = visible
        if (!visible) {
            gazeX = -1f
            gazeY = -1f
            animatedX = -1f
            animatedY = -1f
        }
        invalidate()
    }

    /**
     * Clear the gaze position
     */
    fun clearGaze() {
        gazeX = -1f
        gazeY = -1f
        animatedX = -1f
        animatedY = -1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isVisible || gazeX < 0 || gazeY < 0) return

        // Smooth animation towards target position
        animatedX += (gazeX - animatedX) * smoothingFactor
        animatedY += (gazeY - animatedY) * smoothingFactor

        // Create gradient for better visibility
        val gradient = RadialGradient(
            animatedX, animatedY,
            circleRadius,
            intArrayOf(
                Color.argb(255, 0, 102, 255), // Bright blue center
                Color.argb(200, 0, 102, 255)  // Slightly transparent edge
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        gazePaint.shader = gradient

        // Draw the blue circle
        canvas.drawCircle(animatedX, animatedY, circleRadius, gazePaint)
        
        // Draw white border for better visibility
        canvas.drawCircle(animatedX, animatedY, circleRadius, borderPaint)

        // Continue animating if not at target
        if (kotlin.math.abs(gazeX - animatedX) > 0.5f || 
            kotlin.math.abs(gazeY - animatedY) > 0.5f) {
            postInvalidateDelayed(16) // ~60 FPS
        }
    }

    /**
     * Set the smoothing factor for animation
     * @param factor Value between 0 (no movement) and 1 (instant movement)
     */
    fun setSmoothingFactor(factor: Float) {
        val clampedFactor = factor.coerceIn(0.01f, 1f)
        // Note: smoothingFactor is a val, so this would need to be changed to var if we want this method
    }
}
