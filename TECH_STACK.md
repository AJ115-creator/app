# Kotlin Eye-Tracking App: Tech Stack & Algorithms

This document outlines the core technologies and algorithms used in this Android eye-tracking application.

## Technology Stack

- **Kotlin**: The primary programming language for developing the Android application.
- **Android SDK**: Provides the foundational framework and APIs for building the app's user interface and managing its lifecycle.
- **CameraX API**: A Jetpack library used to access and manage the device's camera for real-time image capture.
- **MediaPipe Face Landmarker**: A cross-platform computer vision library from Google used for detecting detailed face and eye landmarks from the camera feed.
- **Apache Commons Math**: A Java library used for its implementation of the Ordinary Least Squares (OLS) multiple linear regression algorithm.

## Algorithms & Core Logic

- **Face Landmark Detection**: MediaPipe's core algorithm that identifies 478 specific 3D coordinates on a user's face in real-time.
- **Custom Feature Extraction**: A process that converts the raw facial landmark coordinates into a stable set of numerical features, compensating for head movement and distance from the camera.
- **Ordinary Least Squares (OLS) Multiple Linear Regression**: The statistical model used to learn the relationship between the extracted eye features and the user's on-screen gaze point during calibration.
- **Gaze Prediction**: Uses the trained OLS model to predict the (X, Y) screen coordinates of the user's gaze based on live camera data.
- **Euclidean Distance**: A mathematical calculation used during calibration to measure the distance between the predicted gaze point and the target calibration dot.
- **Moving Average Smoothing**: A technique that averages the last few predicted gaze points to reduce jitter and create a more stable on-screen cursor.
