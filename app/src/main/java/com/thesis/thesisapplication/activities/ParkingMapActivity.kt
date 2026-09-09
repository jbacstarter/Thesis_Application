package com.thesis.thesisapplication.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.thesis.thesisapplication.R
import com.thesis.thesisapplication.helpers.ParkingView

class ParkingMapActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.parking_view)

        val parkingView = findViewById<ParkingView>(R.id.parkingMap)
        val btnDone = findViewById<MaterialButton>(R.id.btn_done_parking)

        val targetX = intent.getIntExtra("TARGET_SLOT_X", -1)
        val targetY = intent.getIntExtra("TARGET_SLOT_Y", -1)

        if (targetX != -1 && targetY != -1) {
            parkingView?.postDelayed({
                parkingView.spawnCarAndPark(targetX, targetY)
            }, 1000)
        }

        // Return user to the selector menu when done
        btnDone.setOnClickListener {
            val intent = Intent(this, ParkingViewSelectorActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }
}