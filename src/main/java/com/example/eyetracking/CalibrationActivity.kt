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
            onFaceLandmarkerResult(result, imageWidth, imageHeight)
        }

        setupUI()
        startCamera()

        Log.d(TAG, "CalibrationActivity initialized successfully")
    }

    private fun setupUI() {
        binding.btnStartCalibration.setOnClickListener {
            if (isCalibrating) {
                stopCalibration()
            } else {
                startCalibration()
            }
        }

        binding.btnStopCalibration.setOnClickListener {
            stopCalibration()
        }
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
                        Log.d(TAG, "CameraX frame received")
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
        } catch (e: Exception) {
            Log.e(TAG, "Error starting calibration", e)
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
        if (result.faceLandmarks().isEmpty()) {
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

        if (!isCalibrating) return

        try {
            if (startWidth == 0.0 && startHeight == 0.0) {
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

            val overlayWidth = binding.calibrationOverlay.width
            val overlayHeight = binding.calibrationOverlay.height
            if (overlayWidth <= 0 || overlayHeight <= 0) {
                Log.e(TAG, "Overlay dimensions are invalid: $overlayWidth x $overlayHeight")
                return
            }

            val predictedGaze = calibrator.predict(features)
            if (predictedGaze != null) {
                gazeBuffer.add(predictedGaze)
                if (gazeBuffer.size > bufferSize) {
                    gazeBuffer.removeAt(0)
                }

                val smoothedGaze = if (gazeBuffer.isNotEmpty()) {
                    val avgX = gazeBuffer.map { it.first }.average()
                    val avgY = gazeBuffer.map { it.second }.average()
                    Pair(avgX.toFloat(), avgY.toFloat())
                } else {
                    predictedGaze
                }

                // Ensure both gaze and target are in the same (screen) coordinate space
                val gazeOnScreen = Pair(
                    smoothedGaze.first * overlayWidth,
                    smoothedGaze.second * overlayHeight
                )
                val targetPoint = calibrator.getCurrentPoint(overlayWidth, overlayHeight)

                runOnUiThread {
                    binding.predictedGazeView.x = gazeOnScreen.first - (binding.predictedGazeView.width / 2)
                    binding.predictedGazeView.y = gazeOnScreen.second - (binding.predictedGazeView.height / 2)
                    binding.calibrationOverlay.updateCalibrationPoint(targetPoint.first, targetPoint.second)
                }

                val distance = euclideanDistance(gazeOnScreen, targetPoint)
                val threshold = 0.1 * overlayWidth.toDouble() // 10% of screen width as threshold

                Log.d(TAG, "Gaze: $gazeOnScreen, Target: $targetPoint, Dist: $distance, Thresh: $threshold")

                if (distance < threshold) {
                    proximityCounter++
                    if (proximityCounter >= proximityThreshold) {
                        // Only add data point once gaze is stable
                        calibrator.add(features, targetPoint)
                        moveToNextCalibrationPoint()
                        proximityCounter = 0
                    }
                } else {
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
            handler.postDelayed({ canCollect = true }, 500) // 500ms pause
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        eyeTracker.release() // Close MediaPipe eyeTracker
    }
}
