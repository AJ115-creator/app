package com.example.eyetracking

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate - starting")
        setContentView(R.layout.activity_main)
        Log.d(TAG, "Layout set successfully")
        
        // Launch calibration activity immediately
        findViewById<Button>(R.id.btnStartCalibration).setOnClickListener {
            Log.d(TAG, "Start calibration button clicked - launching UserDetailsActivity")
            val intent = Intent(this, UserDetailsActivity::class.java)
            startActivity(intent)
        }
        
        Log.d(TAG, "MainActivity initialization complete")
    }
}
