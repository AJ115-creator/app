package com.example.eyetracking

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression

class Calibrator {
    companion object {
        private const val TAG = "Calibrator"
    }
    
    private var X: MutableList<DoubleArray> = mutableListOf()
    private var Yx: MutableList<Double> = mutableListOf()
    private var Yy: MutableList<Double> = mutableListOf()

    private var tmpX: MutableList<DoubleArray> = mutableListOf()
    private var tmpYx: MutableList<Double> = mutableListOf()
    private var tmpYy: MutableList<Double> = mutableListOf()

    private var olsX: OLSMultipleLinearRegression = OLSMultipleLinearRegression()
    private var olsY: OLSMultipleLinearRegression = OLSMultipleLinearRegression()

    private var betaX: DoubleArray? = null
    private var betaY: DoubleArray? = null
    var fitted = false

    private val calibrationMatrix = CalibrationMatrix()

    fun add(features: DoubleArray, targetPoint: Pair<Float, Float>) {
        android.util.Log.v(TAG, "add() called - features size: ${features.size}, target: (${targetPoint.first}, ${targetPoint.second})")
        
        tmpX.add(features)
        tmpYx.add(targetPoint.first.toDouble())
        tmpYy.add(targetPoint.second.toDouble())

        if (tmpX.size > 40) {
            tmpX.removeAt(0)
            tmpYx.removeAt(0)
            tmpYy.removeAt(0)
        }
        
        android.util.Log.v(TAG, "Buffer sizes - tmpX: ${tmpX.size}, X: ${X.size}")
        trainRealTime()
    }

    private fun trainRealTime() {
        val allX = X + tmpX
        val allYx = Yx + tmpYx
        val allYy = Yy + tmpYy
        
        android.util.Log.v(TAG, "trainRealTime() - total samples: ${allX.size}, features per sample: ${if (allX.isNotEmpty()) allX[0].size else 0}")

        if (allX.isNotEmpty() && allX.size > allX[0].size) {
            try {
                trainModel(allX.toTypedArray(), allYx.toDoubleArray(), allYy.toDoubleArray())
                fitted = true
                android.util.Log.d(TAG, "Model trained successfully - fitted: true")
            } catch (e: Exception) {
                // Handle exceptions during training, e.g., singular matrix
                android.util.Log.e(TAG, "Failed to train model: ${e.message}")
                fitted = false
            }
        } else {
            android.util.Log.v(TAG, "Not enough samples to train: ${allX.size} samples, need > ${if (allX.isNotEmpty()) allX[0].size else "unknown"}")
        }
    }

    private fun trainModel(xData: Array<DoubleArray>, yxData: DoubleArray, yyData: DoubleArray) {
        olsX.newSampleData(yxData, xData)
        betaX = olsX.estimateRegressionParameters()

        olsY.newSampleData(yyData, xData)
        betaY = olsY.estimateRegressionParameters()
    }

    fun predict(features: DoubleArray): Pair<Float, Float>? {
        if (!fitted || betaX == null || betaY == null) {
            android.util.Log.v(TAG, "predict() - not ready: fitted=$fitted, betaX null=${betaX==null}, betaY null=${betaY==null}")
            return null
        }

        val x = betaX!![0] + (1 until betaX!!.size).sumOf { betaX!![it] * features[it - 1] }
        val y = betaY!![0] + (1 until betaY!!.size).sumOf { betaY!![it] * features[it - 1] }
        
        val result = Pair(x.toFloat(), y.toFloat())
        android.util.Log.v(TAG, "predict() - result: (${result.first}, ${result.second})")
        return result
    }

    fun getCurrentPoint(width: Int, height: Int): Pair<Float, Float> {
        return calibrationMatrix.getCurrentPoint(width, height)
    }

    fun isFinished(): Boolean {
        return calibrationMatrix.isFinished()
    }

    fun movePoint(): Boolean {
        android.util.Log.d(TAG, "movePoint() - moving ${tmpX.size} samples to permanent storage")
        
        // Move collected temporary data to permanent storage (like JavaScript)
        X.addAll(tmpX)
        Yx.addAll(tmpYx)
        Yy.addAll(tmpYy)
        tmpX.clear()
        tmpYx.clear()
        tmpYy.clear()
        
        calibrationMatrix.moveNext()
        val finished = calibrationMatrix.isFinished()
        android.util.Log.d(TAG, "movePoint() - calibration finished: $finished, total permanent samples: ${X.size}")
        return !finished
    }

    fun reset() {
        X.clear()
        Yx.clear()
        Yy.clear()
        tmpX.clear()
        tmpYx.clear()
        tmpYy.clear()
        betaX = null
        betaY = null
        fitted = false
        calibrationMatrix.reset()
    }
}

class CalibrationMatrix {
    private var iterator = 0
    private val points = arrayOf(
        floatArrayOf(0.25f, 0.25f), floatArrayOf(0.5f, 0.75f), floatArrayOf(1f, 0.5f), floatArrayOf(0.75f, 0.5f), floatArrayOf(0f, 0.75f),
        floatArrayOf(0.5f, 0.5f), floatArrayOf(1.0f, 0.25f), floatArrayOf(0.75f, 0.0f), floatArrayOf(0.25f, 0.5f), floatArrayOf(0.5f, 0.0f),
        floatArrayOf(0f, 0.5f), floatArrayOf(1.0f, 1.0f), floatArrayOf(0.75f, 1.0f), floatArrayOf(0.25f, 0.0f), floatArrayOf(1.0f, 0.0f),
        floatArrayOf(0f, 1.0f), floatArrayOf(0.25f, 1.0f), floatArrayOf(0.75f, 0.75f), floatArrayOf(0.5f, 0.25f), floatArrayOf(0f, 0.25f),
        floatArrayOf(1.0f, 0.5f), floatArrayOf(0.75f, 0.25f), floatArrayOf(0.5f, 1.0f), floatArrayOf(0.25f, 0.75f), floatArrayOf(0.0f, 0.0f)
    )

    fun moveNext() {
        if (iterator < points.size - 1) {
            iterator++
        }
    }

    fun getCurrentPoint(width: Int, height: Int): Pair<Float, Float> {
        val point = points[iterator]
        return Pair(point[0] * width, point[1] * height)
    }

    fun isFinished(): Boolean {
        return iterator >= points.size - 1
    }

    fun reset() {
        iterator = 0
    }
}

