package com.example.eyetracking

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.eyetracking.ImageUtils.toBitmap

/**
 * Fixed MediaPipe Face Landmarker integration for eye tracking
 * Includes proper error handling and bitmap conversion
 */
@OptIn(ExperimentalGetImage::class)
class MediaPipeEyeTracker(
    private val context: Context,
    private val onResult: (FaceLandmarkerResult, Int, Int) -> Unit
) {

    companion object {
        private const val TAG = "MediaPipeEyeTracker"
        private const val MODEL_PATH = "face_landmarker.task"
    }

    private var faceLandmarker: FaceLandmarker? = null
    private var isInitialized = false
    private var isProcessing = false

    init {
        initializeFaceLandmarker()
    }

    private fun initializeFaceLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()

            // Check if model file exists
            try {
                context.assets.open(MODEL_PATH).use {
                    // File exists, continue with initialization
                }
                baseOptionsBuilder.setModelAssetPath(MODEL_PATH)
                baseOptionsBuilder.setDelegate(Delegate.CPU)
                Log.d(TAG, "Model file found: $MODEL_PATH")
            } catch (e: Exception) {
                Log.e(TAG, "Model file not found at app/src/main/assets/$MODEL_PATH", e)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "ERROR: face_landmarker.task not found in assets", Toast.LENGTH_LONG).show()
                }
                return
            }

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.3f) // Lowered for better detection
                .setMinFacePresenceConfidence(0.3f)  // Lowered for better detection
                .setMinTrackingConfidence(0.3f)      // Lowered for better detection
                .setOutputFaceBlendshapes(false)     // Disabled for performance
                .setOutputFacialTransformationMatrixes(false)
                .setResultListener { result: FaceLandmarkerResult, image: MPImage ->
                    processFaceLandmarkerResult(result, image)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe Face Landmarker error: ${error.message}")
                    isProcessing = false
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            isInitialized = true
            Log.d(TAG, "Face Landmarker initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe FaceLandmarker", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to initialize MediaPipe: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Process camera frame through MediaPipe Face Landmarker
     */
    fun processFrame(imageProxy: ImageProxy) {
        Log.v(TAG, "processFrame called - initialized: $isInitialized, processing: $isProcessing")
        
        if (!isInitialized || faceLandmarker == null) {
            Log.w(TAG, "Face Landmarker not initialized - faceLandmarker is null: ${faceLandmarker == null}")
            imageProxy.close()
            return
        }

        if (isProcessing) {
            // Skip frame if still processing previous one
            Log.v(TAG, "Skipping frame - still processing previous")
            imageProxy.close()
            return
        }

        try {
            isProcessing = true

            // Convert ImageProxy to MediaPipe MPImage using the extension
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()

            // Process frame asynchronously
            faceLandmarker?.detectAsync(mpImage, System.currentTimeMillis())

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            isProcessing = false
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Process Face Landmarker results and pass to callback
     */
    private fun processFaceLandmarkerResult(
        result: FaceLandmarkerResult,
        image: MPImage
    ) {
        isProcessing = false
        Log.v(TAG, "processFaceLandmarkerResult - faces detected: ${result.faceLandmarks().size}")

        if (result.faceLandmarks().isEmpty()) {
            Log.d(TAG, "No face detected in current frame")
            return
        }

        try {
            Log.d(TAG, "Face detected with ${result.faceLandmarks()[0].size} landmarks")
            // Pass the raw result to the callback for proper feature extraction
            onResult(result, image.width, image.height)
            Log.v(TAG, "Successfully passed result to callback")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing face landmarks", e)
        }
    }

    /**
     * Clean up resources
     */
    fun release() {
        try {
            faceLandmarker?.close()
            faceLandmarker = null
            isInitialized = false
            isProcessing = false
            Log.d(TAG, "MediaPipe resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPipe resources", e)
        }
    }

    /**
     * Check if MediaPipe is ready
     */
    fun isReady(): Boolean = isInitialized && faceLandmarker != null && !isProcessing
}

