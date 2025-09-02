package com.example.eyetracking

import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

object EyeFeatureExtractor {
    private const val TAG = "EyeFeatureExtractor"
    
    // Indices for left and right eye keypoints from MediaPipe FaceMesh
    private val LEFT_EYE_KEYPOINTS = intArrayOf(33, 133, 160, 159, 158, 157, 173, 155, 154, 153, 144, 145, 246, 468)
    private val RIGHT_EYE_KEYPOINTS = intArrayOf(362, 263, 387, 386, 385, 384, 398, 382, 381, 380, 374, 373, 466, 473)

    fun extractFeatures(
        result: FaceLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int,
        startWidth: Double,
        startHeight: Double,
        headStartingPos: Pair<Double, Double>
    ): DoubleArray? {
        android.util.Log.d(TAG, "extractFeatures called - imageSize: ${imageWidth}x${imageHeight}, startSize: ${startWidth}x${startHeight}")
        
        if (result.faceLandmarks().isEmpty()) {
            android.util.Log.w(TAG, "No face landmarks in result")
            return null
        }

        val landmarks = result.faceLandmarks()[0]

        // Calculate current head position and scale
        var offsetX = landmarks[0].x().toDouble() * imageWidth
        var offsetY = landmarks[0].y().toDouble() * imageHeight
        var maxX = 0.0
        var maxY = 0.0

        landmarks.forEach {
            val pxX = it.x().toDouble() * imageWidth
            val pxY = it.y().toDouble() * imageHeight
            offsetX = offsetX.coerceAtMost(pxX)
            offsetY = offsetY.coerceAtMost(pxY)
            maxX = maxX.coerceAtLeast(pxX)
            maxY = maxY.coerceAtLeast(pxY)
        }

        val width = maxX - offsetX
        val height = maxY - offsetY

        if (startWidth == 0.0 || startHeight == 0.0) {
            android.util.Log.w(TAG, "Start dimensions not set - cannot extract features")
            return null // Avoid division by zero if start dimensions aren't set
        }

        val scaleX = width / startWidth
        val scaleY = height / startHeight

        // Extract and normalize eye keypoints
        val leftEyeCoordinates = LEFT_EYE_KEYPOINTS.map { landmarks[it] }
            .flatMap { listOf(((it.x() * imageWidth - offsetX) / width) * scaleX, ((it.y() * imageHeight - offsetY) / height) * scaleY) }

        val rightEyeCoordinates = RIGHT_EYE_KEYPOINTS.map { landmarks[it] }
            .flatMap { listOf(((it.x() * imageWidth - offsetX) / width) * scaleX, ((it.y() * imageHeight - offsetY) / height) * scaleY) }

        // Combine all features into a single array
        val features = mutableListOf<Double>()
        features.addAll(leftEyeCoordinates)
        features.addAll(rightEyeCoordinates)
        features.add(scaleX)
        features.add(scaleY)
        features.add(width)
        features.add(height)
        features.add(offsetX - headStartingPos.first)
        features.add(offsetY - headStartingPos.second)
        
        android.util.Log.v(TAG, "Features extracted - size: ${features.size}, scaleX: $scaleX, scaleY: $scaleY")
        return features.toDoubleArray()
    }
}

