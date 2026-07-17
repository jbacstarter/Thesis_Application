package com.thesis.thesisapplication.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thesis.thesisapplication.R

class ParkingMapActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This links to you    r XML file that contains the <com.thesis.thesisapplication.helpers.ParkingView ... />
        // Make sure "activity_parking" matches your actual XML filename!
        setContentView(R.layout.parking_view)
    }
}