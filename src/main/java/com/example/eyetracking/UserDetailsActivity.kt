package com.example.eyetracking

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.eyetracking.databinding.ActivityUserDetailsBinding

class UserDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnContinue.setOnClickListener {
            val age = binding.etAge.text.toString()
            val selectedGenderId = binding.rgGender.checkedRadioButtonId
            
            if (age.isNotEmpty() && selectedGenderId != -1) {
                val intent = Intent(this, CalibrationActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please enter your age and select a gender", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
