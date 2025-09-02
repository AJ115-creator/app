package com.example.eyetracking

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.eyetracking.databinding.ActivityCalibrationBinding
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
// import com.example.eyetracking.ImageUtils.toBitmap // Not used, consider removing
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt
import kotlin.math.pow

class CalibrationActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "CalibrationActivity"
    }

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var eyeTracker: MediaPipeEyeTracker
    private lateinit var calibrator: Calibrator
    private val handler = Handler(Looper.getMainLooper())

    // Calibration state
    private var isCalibrating = false
    // private var calibrationRunnable: Runnable? = null // Not used, consider removing

    // Head tracking for feature normalization (like JavaScript)
    private var startWidth = 0.0
    private var startHeight = 0.0
    private var headStartingPos = Pair(0.0, 0.0)

    // Gaze smoothing buffer (20 points like JavaScript)
    private val gazeBuffer = mutableListOf<Pair<Float, Float>>()
    private val bufferSize = 5
    private var proximityCounter = 0
    private val proximityThreshold = 10 // Frames of stability required

    // State for per-point pause logic
    private var canCollect = true

    // Add a view for the predicted gaze
    private lateinit var predictedGazeView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the predictedGazeView
        predictedGazeView = binding.predictedGazeView

        cameraExecutor = Executors.newSingleThreadExecutor()
        calibrator = Calibrator()

        // Initialize MediaPipe eye tracker
        eyeTracker = MediaPipeEyeTracker(this) { result, imageWidth, imageHeight ->
            Log.v(TAG, "MediaPipe callback triggered")
            onFaceLandmarkerResult(result, imageWidth, imageHeight)
        }

        setupUI()
        startCamera()

        // Make sure button is visible and enabled
        binding.btnStartCalibration.apply {
            visibility = View.VISIBLE
            isEnabled = true
            isClickable = true
            bringToFront()
            setOnClickListener {
                Log.d(TAG, "Button clicked - immediate feedback")
                Toast.makeText(this@CalibrationActivity, "Button clicked!", Toast.LENGTH_SHORT).show()
                binding.btnStartCalibration.text = "Clicked!"
                startCalibration()
            }
            Log.d(TAG, "Button state - visible: ${visibility == View.VISIBLE}, enabled: $isEnabled")
        }

        // Log overlay dimensions after layout
        binding.calibrationOverlay.post {
            Log.d(TAG, "Overlay ready with dimensions: ${binding.calibrationOverlay.width}x${binding.calibrationOverlay.height}")
        }

        Log.d(TAG, "CalibrationActivity initialized successfully")
    }

    private fun setupUI() {
        Log.d(TAG, "Setting up UI - button listeners")
        
        // Button click listener moved to onCreate for better initialization

        binding.btnStopCalibration.setOnClickListener {
            Log.d(TAG, "Stop calibration button clicked")
            stopCalibration()
        }
        
        Log.d(TAG, "UI setup complete")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        Log.v(TAG, "CameraX frame received - ${imageProxy.width}x${imageProxy.height}")
                        eyeTracker.processFrame(imageProxy)
                        // imageProxy.close() // eyeTracker.processFrame should handle closing
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCalibration() {
        try {
            // Ensure we have valid dimensions before starting calibration
            val dimensions = getValidCalibrationDimensions()
            if (dimensions == null) {
                // Wait for layout to complete
                binding.calibrationOverlay.post {
                    startCalibration()
                }
                return
            }
            
            val (overlayWidth, overlayHeight) = dimensions
            Log.d(TAG, "Starting calibration with dimensions: ${overlayWidth}x${overlayHeight}")
            
            isCalibrating = true
            calibrator.reset()
            gazeBuffer.clear()
            proximityCounter = 0

            startWidth = 0.0
            startHeight = 0.0
            headStartingPos = Pair(0.0, 0.0)

            binding.btnStartCalibration.text = "Calibrating..."
            binding.btnStopCalibration.visibility = View.VISIBLE
            binding.calibrationOverlay.visibility = View.VISIBLE

            // updateCalibrationPointToast() // This is no longer needed

            Toast.makeText(this, "Calibration started. Look at the red circles.", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Calibration started")
            
            // Show the first calibration point immediately
            showCurrentCalibrationPoint()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting calibration", e)
        }
    }

    /**
     * Get valid dimensions for calibration points, with fallback to parent container
     */
    private fun getValidCalibrationDimensions(): Pair<Int, Int>? {
        val overlayWidth = binding.calibrationOverlay.width
        val overlayHeight = binding.calibrationOverlay.height
        
        if (overlayWidth > 0 && overlayHeight > 0) {
            return Pair(overlayWidth, overlayHeight)
        }
        
        // Fallback to parent container dimensions
        val parentWidth = binding.root.width
        val parentHeight = binding.root.height
        
        if (parentWidth > 0 && parentHeight > 0) {
            Log.d(TAG, "Using parent dimensions as fallback: ${parentWidth}x${parentHeight}")
            return Pair(parentWidth, parentHeight)
        }
        
        Log.w(TAG, "No valid dimensions available for calibration")
        return null
    }

    /**
     * Show the current calibration point on the overlay
     */
    private fun showCurrentCalibrationPoint() {
        val dimensions = getValidCalibrationDimensions()
        if (dimensions != null) {
            val (width, height) = dimensions
            val targetPoint = calibrator.getCurrentPoint(width, height)
            binding.calibrationOverlay.updateCalibrationPoint(targetPoint.first, targetPoint.second)
            Log.d(TAG, "Showing calibration point at: (${targetPoint.first}, ${targetPoint.second})")
        }
    }

    private fun updateCalibrationPointToast() {
        if (!isCalibrating) return

        runOnUiThread {
            // This toast is no longer needed as the calibration is visual
        }
    }

    private fun stopCalibration() {
        try {
            isCalibrating = false
            binding.btnStartCalibration.text = "Start Calibration"
            binding.btnStopCalibration.visibility = View.GONE
            binding.predictedGazeView.visibility = View.GONE
            binding.calibrationOverlay.visibility = View.GONE
            Toast.makeText(this, "Calibration stopped", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Calibration stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping calibration", e)
        }
    }

    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        Log.v(TAG, "onFaceLandmarkerResult - faces: ${result.faceLandmarks().size}, calibrating: $isCalibrating")
        
        if (result.faceLandmarks().isEmpty()) {
            Log.d(TAG, "No face detected - showing status message")
            runOnUiThread {
                binding.faceDetectionStatus.visibility = View.VISIBLE
                binding.predictedGazeView.visibility = View.GONE
            }
            return
        }

        runOnUiThread {
            binding.faceDetectionStatus.visibility = View.GONE
            if (isCalibrating) {
                binding.predictedGazeView.visibility = View.VISIBLE
            }
        }

        if (!isCalibrating) {
            Log.v(TAG, "Not calibrating - skipping processing")
            return
        }

        try {
            if (startWidth == 0.0 && startHeight == 0.0) {
                Log.d(TAG, "Initializing head tracking")
                initializeHeadTracking(result)
            }

            val features = EyeFeatureExtractor.extractFeatures(
                result,
                imageWidth,
                imageHeight,
                startWidth,
                startHeight,
                headStartingPos
            )

            if (features == null) {
                Log.w(TAG, "Could not extract features")
                return
            }

            val dimensions = getValidCalibrationDimensions()
            if (dimensions == null) {
                Log.w(TAG, "No valid dimensions available for calibration, skipping frame")
                return
            }
            
            val (overlayWidth, overlayHeight) = dimensions

            // Get prediction or use default center position
            val predictedGaze = calibrator.predict(features)
            val displayGaze = predictedGaze ?: Pair(0.5f, 0.5f)  // Default to center if null
            
            // Always process gaze for visual feedback
            gazeBuffer.add(displayGaze)
            if (gazeBuffer.size > bufferSize) {
                gazeBuffer.removeAt(0)
            }

            val smoothedGaze = if (gazeBuffer.isNotEmpty()) {
                val avgX = gazeBuffer.map { it.first }.average()
                val avgY = gazeBuffer.map { it.second }.average()
                Pair(avgX.toFloat(), avgY.toFloat())
            } else {
                displayGaze
            }

            // Ensure both gaze and target are in the same (screen) coordinate space
            val gazeOnScreen = Pair(
                smoothedGaze.first * overlayWidth,
                smoothedGaze.second * overlayHeight
            )
            val targetPoint = calibrator.getCurrentPoint(overlayWidth, overlayHeight)

            // Always update UI to show blue circle
            runOnUiThread {
                binding.predictedGazeView.visibility = View.VISIBLE  // Ensure visible
                binding.predictedGazeView.x = gazeOnScreen.first - (binding.predictedGazeView.width / 2)
                binding.predictedGazeView.y = gazeOnScreen.second - (binding.predictedGazeView.height / 2)
                binding.calibrationOverlay.updateCalibrationPoint(targetPoint.first, targetPoint.second)
            
            Log.d(TAG, "Gaze: $gazeOnScreen, Target: $targetPoint, Predicted: $predictedGaze")

            // Calculate distance for proximity check
            val distance = euclideanDistance(gazeOnScreen, targetPoint)
            val threshold = 0.1 * overlayWidth.toDouble() // 10% of screen width as threshold

            Log.d(TAG, "Distance: $distance, Threshold: $threshold, ProximityCounter: $proximityCounter")

            // Only collect data and check proximity if model has made a real prediction
            if (predictedGaze != null && distance < threshold) {
                    Log.d(TAG, "Within threshold - collecting data, proximityCounter: $proximityCounter")
                    // CRITICAL FIX: Add data on EVERY frame while near target (like JavaScript)
                    calibrator.add(features, targetPoint)
                    
                    proximityCounter++
                    if (proximityCounter >= proximityThreshold) {
                        Log.d(TAG, "Proximity threshold reached - moving to next point")
                        // Move to next point only after collecting enough samples
                        moveToNextCalibrationPoint()
                        proximityCounter = 0
                    }
                } else {
                    if (predictedGaze == null) {
                        Log.v(TAG, "Prediction is null - model not trained yet")
                    } else {
                        Log.v(TAG, "Outside threshold - distance: $distance")
                    }
                    proximityCounter = 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing face landmarks", e)
        }
    }

    private fun initializeHeadTracking(result: FaceLandmarkerResult) {
        val imageWidth = binding.viewFinder.width
        val imageHeight = binding.viewFinder.height
        if (imageWidth > 0 && imageHeight > 0) {
            startWidth = imageWidth.toDouble()
            startHeight = imageHeight.toDouble()
            val landmarks = result.faceLandmarks()[0]
            headStartingPos = Pair(
                landmarks[0].x().toDouble() * imageWidth,
                landmarks[0].y().toDouble() * imageHeight
            )
        }
    }

    private fun euclideanDistance(point1: Pair<Float, Float>, point2: Pair<Float, Float>): Double {
        return sqrt((point1.first - point2.first).toDouble().pow(2) + (point1.second - point2.second).toDouble().pow(2))
    }

    private fun moveToNextCalibrationPoint() {
        if (!canCollect) return
        canCollect = false

        calibrator.movePoint()

        if (calibrator.isFinished()) {
            stopCalibration()
            runOnUiThread {
                Toast.makeText(this, "Calibration finished!", Toast.LENGTH_LONG).show()
                val intent = Intent(this, TrackingActivity::class.java)
                // Here you would pass the calibration data (e.g., via a singleton or serialized object)
                startActivity(intent)
                finish()
            }
        } else {
            // Add a pause before collecting data for the next point
            handler.postDelayed({ 
                canCollect = true 
                // Show the next calibration point
                showCurrentCalibrationPoint()
            }, 500) // 500ms pause
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        eyeTracker.release() // Close MediaPipe eyeTracker
    }
}
