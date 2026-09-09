package com.thesis.thesisapplication.activities

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.thesis.thesisapplication.R
import com.thesis.thesisapplication.helpers.ParkingView
import java.util.Locale

class ParkingMapActivity : AppCompatActivity() {

    private lateinit var textToSpeech: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            setContentView(R.layout.parking_view)

            val rootView = findViewById<View>(android.R.id.content)
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            val parkingView = findViewById<ParkingView?>(R.id.parkingMap)
            val btnDone = findViewById<MaterialButton?>(R.id.btn_done_parking)

            val targetX = intent.getIntExtra("TARGET_SLOT_X", -1)
            val targetY = intent.getIntExtra("TARGET_SLOT_Y", -1)

            if (targetX != -1 && targetY != -1) {
                parkingView?.postDelayed({
                    parkingView.spawnCarAndPark(targetX, targetY)
                }, 1000)
            }

            // Initialize TTS and speak the instruction immediately
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech.language = Locale.US
                    textToSpeech.speak(
                        "Turn left and you'll arrive in your destination. Please follow the path on your screen to your parking spot.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }
            }

            btnDone?.setOnClickListener {
                val menuIntent = Intent(this, ParkingViewSelectorActivity::class.java)
                menuIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(menuIntent)
                finish()
            }

        } catch (e: Exception) {
            Log.e("ParkingMapActivity", "Crash prevented during onCreate: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}