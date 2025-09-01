
package com.example.eyetracking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.eyetracking.databinding.ActivityTrackingBinding
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackingBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    // Eye tracking components
    private lateinit var mediaPipeEyeTracker: MediaPipeEyeTracker
    private lateinit var calibrator: Calibrator
    private var isTracking = false

    // Head tracking data from CalibrationActivity
    private var startWidth: Double = 0.0
    private var startHeight: Double = 0.0
    private lateinit var headStartingPos: Pair<Double, Double>


    // Smoothing buffer for gaze tracking
    private val gazeBuffer = mutableListOf<Pair<Float, Float>>()
    private val bufferSize = 10

    // Gaze tracking data
    private val gazePoints = mutableListOf<GazePoint>()
    private var trackingStartTime = 0L

    companion object {
        private const val TAG = "TrackingActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO: Receive calibrator object and head tracking data from CalibrationActivity
        // For now, creating a new one for compilation
        calibrator = Calibrator() 
        headStartingPos = Pair(0.0, 0.0)


        // Initialize MediaPipe eye tracker
        mediaPipeEyeTracker = MediaPipeEyeTracker(this) { result, imageWidth, imageHeight ->
            onFaceLandmarkerResult(result, imageWidth, imageHeight)
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        setupUI()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupUI() {
        // Start tracking button
        binding.btnStartTracking?.setOnClickListener {
            if (!isTracking) {
                startTracking()
            } else {
                stopTracking()
            }
        }

        // Stop tracking button
        binding.btnStopTracking?.setOnClickListener {
            stopTracking()
        }
    }

    private fun startTracking() {
        if (!mediaPipeEyeTracker.isReady()) {
            Toast.makeText(this, "MediaPipe not ready. Please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        if (!calibrator.fitted) {
            Toast.makeText(this, "Please complete calibration first", Toast.LENGTH_SHORT).show()
            return
        }

        isTracking = true
        trackingStartTime = System.currentTimeMillis()
        gazePoints.clear()
        gazeBuffer.clear()

        binding.btnStartTracking?.text = "Stop Tracking"
        Toast.makeText(this, "Eye tracking started", Toast.LENGTH_SHORT).show()

        Log.d(TAG, "Eye tracking started")
    }

    private fun stopTracking() {
        isTracking = false
        binding.btnStartTracking?.text = "Start Tracking"

        Toast.makeText(this, "Eye tracking stopped. ${gazePoints.size} gaze points recorded.", Toast.LENGTH_SHORT).show()

        Log.d(TAG, "Eye tracking stopped. Total points: ${gazePoints.size}")
    }

    /**
     * Handle MediaPipe face landmark results for gaze tracking
     */
    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        if (!isTracking) return

        // Extract features from MediaPipe landmarks
        val features = EyeFeatureExtractor.extractFeatures(result, imageWidth, imageHeight, startWidth, startHeight, headStartingPos)
        if (features == null) {
            Log.w(TAG, "Could not extract features for tracking")
            return
        }

        // Predict gaze using calibrated model
        val predictedGaze = calibrator.predict(features)
        if (predictedGaze != null) {
            // Add to smoothing buffer
            gazeBuffer.add(predictedGaze)
            if (gazeBuffer.size > bufferSize) {
                gazeBuffer.removeAt(0)
            }

            // Calculate smoothed gaze
            val smoothedGaze = if (gazeBuffer.isNotEmpty()) {
                val avgX = gazeBuffer.map { it.first }.average()
                val avgY = gazeBuffer.map { it.second }.average()
                Pair(avgX.toFloat(), avgY.toFloat())
            } else {
                predictedGaze
            }

            // Update tracking overlay
            runOnUiThread {
                binding.trackingOverlay?.updateGaze(
                    smoothedGaze.first,
                    smoothedGaze.second
                )
            }

            // Record gaze point
            val currentTime = System.currentTimeMillis()
            val gazePoint = GazePoint(
                x = smoothedGaze.first.toDouble(),
                y = smoothedGaze.second.toDouble(),
                timestamp = currentTime,
                confidence = 1.0f // You can calculate actual confidence if needed
            )

            addGazePoint(gazePoint)

            Log.d(TAG, "Gaze tracked: (${smoothedGaze.first}, ${smoothedGaze.second})")
        }
    }

    /**
     * Add a gaze point to the tracking data
     */
    private fun addGazePoint(gazePoint: GazePoint) {
        gazePoints.add(gazePoint)

        // Optional: Limit the number of stored points to prevent memory issues
        if (gazePoints.size > 10000) {
            gazePoints.removeAt(0)
        }
    }

    /**
     * Get current tracking metrics
     */
    private fun getCurrentMetrics(): TrackingMetrics {
        val totalPoints = gazePoints.size
        val duration = if (trackingStartTime > 0) {
            (System.currentTimeMillis() - trackingStartTime) / 1000.0
        } else {
            0.0
        }

        val averageConfidence = if (gazePoints.isNotEmpty()) {
            gazePoints.map { it.confidence }.average()
        } else {
            0.0
        }

        return TrackingMetrics(
            totalPoints = totalPoints,
            duration = duration,
            averageConfidence = averageConfidence.toFloat(),
            samplingRate = if (duration > 0) totalPoints / duration else 0.0
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.trackingPreview.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, GazeAnalyzer())
                }

            try {
                cameraProvider.unbindAll()

                camera = cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPipeEyeTracker.release()
        cameraExecutor.shutdown()
    }

    /**
     * Image analyzer that processes frames through MediaPipe
     */
    private inner class GazeAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            mediaPipeEyeTracker.processFrame(image)
        }
    }
}

/**
 * Data class to represent a gaze point
 */
data class GazePoint(
    val x: Double,
    val y: Double,
    val timestamp: Long,
    val confidence: Float
)

/**
 * Data class to represent tracking metrics
 */
data class TrackingMetrics(
    val totalPoints: Int,
    val duration: Double,
    val averageConfidence: Float,
    val samplingRate: Double
)

