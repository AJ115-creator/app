
package com.example.eyetracking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.eyetracking.databinding.ActivityTrackingBinding
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class TrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackingBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    // Eye tracking components
    private lateinit var mediaPipeEyeTracker: MediaPipeEyeTracker
    private lateinit var calibrator: Calibrator
    private lateinit var eyeMetricsExtractor: EyeMetricsExtractor
    private var isTracking = false
    
    // Supabase integration - use proper ViewModel access pattern
    private val eyeTrackingViewModel: com.example.eyetracking.viewmodel.EyeTrackingViewModel by viewModels()

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

        // Get calibrator and head tracking data from singleton (should be set by CalibrationActivity)
        calibrator = CalibratorSingleton.calibrator ?: Calibrator()
        startWidth = CalibratorSingleton.startWidth
        startHeight = CalibratorSingleton.startHeight
        headStartingPos = CalibratorSingleton.headStartingPos
        
        if (calibrator.fitted) {
            Log.d(TAG, "Calibrator loaded successfully with head tracking data")
            Log.d(TAG, "Start dimensions: ${startWidth}x${startHeight}, Head position: $headStartingPos")
        } else {
            Log.w(TAG, "Calibrator not fitted - calibration may be needed")
            // Initialize with defaults if not calibrated
            startWidth = 0.0
            startHeight = 0.0
            headStartingPos = Pair(0.0, 0.0)
        }
        
        // Initialize eye metrics extractor
        eyeMetricsExtractor = EyeMetricsExtractor()
        
        // Initialize metrics display with zeros
        initializeMetricsDisplay()


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
        setupWindowInsets()
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
    
    /**
     * Setup WindowInsets to ensure buttons are always above system navigation bar
     */
    private fun setupWindowInsets() {
        // Enable edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navigationBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            // Apply padding to button container to keep it above navigation bar
            binding.buttonContainer?.let { buttonContainer ->
                buttonContainer.updatePadding(
                    bottom = navigationBarsInsets.bottom + 16 // 16dp extra padding for visual separation
                )
                Log.d(TAG, "Applied navigation bar padding: ${navigationBarsInsets.bottom}dp")
            }
            
            // Apply top padding for status bar if needed
            view.updatePadding(
                top = systemBarsInsets.top
            )
            
            Log.d(TAG, "WindowInsets applied - System bars: ${systemBarsInsets.bottom}dp, Navigation: ${navigationBarsInsets.bottom}dp")
            windowInsets
        }
    }
    
    /**
     * Initialize metrics display with default values
     */
    private fun initializeMetricsDisplay() {
        runOnUiThread {
            // Check if TextViews exist and set initial values
            val gazeDurationView = binding.tvGazeDuration
            val dwellTimeView = binding.tvDwellTime
            val saccadeLengthView = binding.tvSaccadeLength
            val distractorSaccadesView = binding.tvDistractorSaccades
            val fixationCountView = binding.tvFixationCount
            val refixationRatioView = binding.tvRefixationRatio
            
            gazeDurationView?.text = "0 ms"
            dwellTimeView?.text = "0 ms"
            saccadeLengthView?.text = "0.0 px"
            distractorSaccadesView?.text = "0"
            fixationCountView?.text = "0"
            refixationRatioView?.text = "0.00"
            
            Log.d(TAG, "Initialized metric displays:")
            Log.d(TAG, "  Gaze Duration TextView: ${gazeDurationView != null}")
            Log.d(TAG, "  Dwell Time TextView: ${dwellTimeView != null}")
            Log.d(TAG, "  Saccade Length TextView: ${saccadeLengthView != null}")
            Log.d(TAG, "  Distractor Saccades TextView: ${distractorSaccadesView != null}")
            Log.d(TAG, "  Fixation Count TextView: ${fixationCountView != null}")
            Log.d(TAG, "  Refixation Ratio TextView: ${refixationRatioView != null}")
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
        
        // Reset metrics extractor for new tracking session
        eyeMetricsExtractor.reset()
        
        // Set up areas of interest immediately for dwell time calculation
        // Use the current screen dimensions or defaults if not available
        val screenWidth = binding.trackingOverlay?.width?.toFloat() ?: 1920f
        val screenHeight = binding.trackingOverlay?.height?.toFloat() ?: 1080f
        
        eyeMetricsExtractor.clearAreasOfInterest()
        
        // Top-left quadrant
        eyeMetricsExtractor.addAreaOfInterest(
            "top_left",
            0f, 0f,
            screenWidth / 2, screenHeight / 2
        )
        
        // Top-right quadrant
        eyeMetricsExtractor.addAreaOfInterest(
            "top_right",
            screenWidth / 2, 0f,
            screenWidth / 2, screenHeight / 2
        )
        
        // Bottom area
        eyeMetricsExtractor.addAreaOfInterest(
            "bottom",
            0f, screenHeight / 2,
            screenWidth, screenHeight / 2
        )
        
        // Center area
        eyeMetricsExtractor.addAreaOfInterest(
            "center",
            screenWidth * 0.25f, screenHeight * 0.25f,
            screenWidth * 0.5f, screenHeight * 0.5f
        )
        
        Log.d(TAG, "Set up 4 areas of interest (${screenWidth}x${screenHeight}) for dwell time tracking")
        
        // Make tracking overlay visible
        runOnUiThread {
            binding.trackingOverlay?.visibility = View.VISIBLE
            binding.trackingOverlay?.setGazeVisible(true)
        }

        binding.btnStartTracking?.text = "Stop Tracking"
        Toast.makeText(this, "Eye tracking started - Blue circle shows your gaze", Toast.LENGTH_SHORT).show()

        Log.d(TAG, "Eye tracking started")
    }

    private fun stopTracking() {
        isTracking = false
        binding.btnStartTracking?.text = "Start Tracking"
        
        // Clear the tracking overlay
        runOnUiThread {
            binding.trackingOverlay?.clearGaze()
            binding.trackingOverlay?.setGazeVisible(false)
        }
        
        // Get final metrics
        val metrics = eyeMetricsExtractor.getMetrics()
        displayMetrics(metrics)
        
        // Save data to Supabase
        saveEyeTrackingDataToSupabase(metrics)

        Toast.makeText(this, "Eye tracking stopped. ${gazePoints.size} gaze points recorded.", Toast.LENGTH_SHORT).show()

        Log.d(TAG, "Eye tracking stopped. Total points: ${gazePoints.size}")
        Log.d(TAG, "Final Metrics - Gaze Duration: ${metrics.gazeDuration}ms, " +
                "Dwell Time: ${metrics.dwellTime}ms, Saccade Length: ${metrics.saccadeLength}px, " +
                "Distractor Saccades: ${metrics.distractorSaccades}, Fixation Count: ${metrics.fixationCount}, " +
                "Refixation Ratio: ${metrics.refixationRatio}")
    }

    /**
     * Handle MediaPipe face landmark results for gaze tracking
     */
    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        if (!isTracking) return
        
        // Initialize head tracking if not done yet (for cases where calibration wasn't completed)
        if (startWidth == 0.0 && startHeight == 0.0 && result.faceLandmarks().isNotEmpty()) {
            startWidth = imageWidth.toDouble()
            startHeight = imageHeight.toDouble()
            val landmarks = result.faceLandmarks()[0]
            headStartingPos = Pair(
                landmarks[0].x().toDouble() * imageWidth,
                landmarks[0].y().toDouble() * imageHeight
            )
            Log.d(TAG, "Initialized head tracking in TrackingActivity")
        }

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

            // Update tracking overlay - convert normalized coordinates to screen pixels
            runOnUiThread {
                val overlayWidth = binding.trackingOverlay?.width ?: 1920
                val overlayHeight = binding.trackingOverlay?.height ?: 1080
                binding.trackingOverlay?.updateGaze(
                    smoothedGaze.first * overlayWidth,
                    smoothedGaze.second * overlayHeight
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
            
            // Process gaze point for metrics extraction
            val screenWidth = binding.trackingOverlay?.width ?: 1920
            val screenHeight = binding.trackingOverlay?.height ?: 1080
            eyeMetricsExtractor.processGazePoint(
                smoothedGaze.first * screenWidth,  // Convert to screen pixels
                smoothedGaze.second * screenHeight,
                currentTime
            )
            
            // Update real-time metrics display
            if (gazePoints.size % 10 == 0) { // Update every 10 frames for more responsive display
                val currentMetrics = eyeMetricsExtractor.getRealTimeMetrics()
                updateMetricsDisplay(currentMetrics)
            }

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

    /**
     * Setup areas of interest for dwell time calculation
     * You can customize these based on your application's UI elements
     */
    private fun setupAreasOfInterest() {
        eyeMetricsExtractor.clearAreasOfInterest()
        
        // Get actual screen dimensions with a post delay to ensure layout is complete
        binding.trackingOverlay?.post {
            val screenWidth = binding.trackingOverlay.width.toFloat()
            val screenHeight = binding.trackingOverlay.height.toFloat()
            
            Log.d(TAG, "Setting up areas of interest: ${screenWidth}x${screenHeight}")
            
            // Top-left quadrant
            eyeMetricsExtractor.addAreaOfInterest(
                "top_left",
                0f, 0f,
                screenWidth / 2, screenHeight / 2
            )
            
            // Top-right quadrant
            eyeMetricsExtractor.addAreaOfInterest(
                "top_right",
                screenWidth / 2, 0f,
                screenWidth / 2, screenHeight / 2
            )
            
            // Bottom area
            eyeMetricsExtractor.addAreaOfInterest(
                "bottom",
                0f, screenHeight / 2,
                screenWidth, screenHeight / 2
            )
            
            // Center area
            eyeMetricsExtractor.addAreaOfInterest(
                "center",
                screenWidth * 0.25f, screenHeight * 0.25f,
                screenWidth * 0.5f, screenHeight * 0.5f
            )
            
            Log.d(TAG, "Added 4 areas of interest for dwell time and refixation tracking")
        }
    }
    
    /**
     * Display metrics in the UI
     */
    private fun displayMetrics(metrics: EyeMetricsExtractor.EyeMetrics) {
        runOnUiThread {
            // Update all metric TextViews that exist in the layout
            binding.tvGazeDuration?.text = "${metrics.gazeDuration}"
            binding.tvDwellTime?.text = "${metrics.dwellTime}"
            binding.tvSaccadeLength?.text = String.format("%.1f", metrics.saccadeLength)
            binding.tvDistractorSaccades?.text = "${metrics.distractorSaccades}"
            binding.tvFixationCount?.text = "${metrics.fixationCount}"
            binding.tvRefixationRatio?.text = String.format("%.2f", metrics.refixationRatio)
            
            // Also log the complete metrics
            val metricsText = """
                Eye Tracking Metrics:
                1. Gaze Duration: ${metrics.gazeDuration} ms
                2. Dwell Time: ${metrics.dwellTime} ms
                3. Saccade Length: %.2f pixels
                4. Distractor Saccades: ${metrics.distractorSaccades}
                5. Fixation Count: ${metrics.fixationCount}
                6. Refixation Ratio: %.2f
                
                Additional Metrics:
                Total Saccades: ${metrics.totalSaccades}
                Avg Fixation Duration: ${metrics.avgFixationDuration} ms
                Total Time: ${metrics.totalTime} ms
            """.trimIndent().format(metrics.saccadeLength, metrics.refixationRatio)
            
            Log.i(TAG, metricsText)
        }
    }
    
    /**
     * Update real-time metrics display
     */
    private fun updateMetricsDisplay(metrics: EyeMetricsExtractor.EyeMetrics) {
        runOnUiThread {
            // Update real-time metrics display (just the values, labels are in XML)
            binding.tvGazeDuration?.text = "${metrics.gazeDuration}"
            binding.tvDwellTime?.text = "${metrics.dwellTime}"
            binding.tvSaccadeLength?.text = String.format("%.1f", metrics.saccadeLength)
            binding.tvDistractorSaccades?.text = "${metrics.distractorSaccades}"
            binding.tvFixationCount?.text = "${metrics.fixationCount}"
            binding.tvRefixationRatio?.text = String.format("%.2f", metrics.refixationRatio)
            
            // Log all 6 metrics to verify they're being calculated
            Log.d(TAG, "Real-time metrics update:")
            Log.d(TAG, "  1. Gaze Duration: ${metrics.gazeDuration} ms")
            Log.d(TAG, "  2. Dwell Time: ${metrics.dwellTime} ms")
            Log.d(TAG, "  3. Saccade Length: ${metrics.saccadeLength} px")
            Log.d(TAG, "  4. Distractor Saccades: ${metrics.distractorSaccades}")
            Log.d(TAG, "  5. Fixation Count: ${metrics.fixationCount}")
            Log.d(TAG, "  6. Refixation Ratio: ${metrics.refixationRatio}")
            Log.d(TAG, "  Additional - Total Saccades: ${metrics.totalSaccades}, Avg Fixation Duration: ${metrics.avgFixationDuration} ms")
        }
    }
    
    /**
     * Save eye tracking data to Supabase
     */
    private fun saveEyeTrackingDataToSupabase(metrics: EyeMetricsExtractor.EyeMetrics) {
        try {
            // Get user details from UserDetailsActivity (you'll need to pass this data)
            // For now, using default values - you should get these from your user input screen
            val userDetails = com.example.eyetracking.data.UserDetails(
                age = 25, // TODO: Get from UserDetailsActivity
                gender = "Other" // TODO: Get from UserDetailsActivity
            )
            
            // Convert metrics to the format expected by Supabase
            val gazeDurationSeconds = metrics.gazeDuration / 1000.0 // Convert ms to seconds
            val dwellTimeSeconds = metrics.dwellTime / 1000.0 // Convert ms to seconds
            val saccadeLengthPixels = metrics.saccadeLength.toDouble()
            val distractorSaccades = metrics.distractorSaccades
            val fixationCount = metrics.fixationCount
            val refixationRatio = metrics.refixationRatio.toDouble()
            
            // Calculate calibration quality based on tracking duration and point count
            val calibrationQuality = if (gazePoints.size > 100 && metrics.totalTime > 5000) {
                0.95 // High quality if we have enough data points and duration
            } else if (gazePoints.size > 50 && metrics.totalTime > 2000) {
                0.75 // Medium quality
            } else {
                0.50 // Lower quality
            }
            
            Log.d(TAG, "Saving eye tracking data to Supabase:")
            Log.d(TAG, "  User: Age=${userDetails.age}, Gender=${userDetails.gender}")
            Log.d(TAG, "  Metrics: Gaze=${gazeDurationSeconds}s, Dwell=${dwellTimeSeconds}s, Saccade=${saccadeLengthPixels}px")
            Log.d(TAG, "  Metrics: Distractors=${distractorSaccades}, Fixations=${fixationCount}, Refixation=${refixationRatio}")
            Log.d(TAG, "  Quality: ${calibrationQuality}")
            
            // Save to Supabase using the ViewModel
            eyeTrackingViewModel.saveEyeMetrics(
                userDetails = userDetails,
                gazeDuration = gazeDurationSeconds,
                dwellTime = dwellTimeSeconds,
                saccadeLength = saccadeLengthPixels,
                distractorSaccades = distractorSaccades,
                fixationCount = fixationCount,
                refixationRatio = refixationRatio,
                calibrationQuality = calibrationQuality
            )
            
            Toast.makeText(this, "Eye tracking data saved to database!", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving eye tracking data to Supabase: ${e.message}", e)
            Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_LONG).show()
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

