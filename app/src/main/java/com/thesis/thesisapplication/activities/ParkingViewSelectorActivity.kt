package com.thesis.thesisapplication.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.thesis.thesisapplication.R
import com.thesis.thesisapplication.helpers.GridPoint
import com.thesis.thesisapplication.helpers.ParkingSelectorView
import kotlin.math.abs

class ParkingViewSelectorActivity : AppCompatActivity() {

    private var isMenuOpen = false

    private var allParkingSlots = listOf<GridPoint>()
    private var currentSlotIndex = -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_parking_view_selector)

        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val mapView = findViewById<ParkingSelectorView>(R.id.parking_selector_view)
        val bottomCard = findViewById<MaterialCardView>(R.id.bottom_parking_card)
        val textTitle = findViewById<TextView>(R.id.card_slot_title)
        val textStatus = findViewById<TextView>(R.id.card_slot_status)
        val btnNavigate = findViewById<MaterialButton>(R.id.btn_navigate_here)

        val btnPrev = findViewById<MaterialButton>(R.id.btn_prev_slot)
        val btnNext = findViewById<MaterialButton>(R.id.btn_next_slot)

        mapView.post {
            allParkingSlots = mapView.getAllParkingSlots()
        }

        btnPrev.setOnClickListener {
            if (allParkingSlots.isNotEmpty()) {
                currentSlotIndex--
                if (currentSlotIndex < 0) currentSlotIndex = allParkingSlots.size - 1
                val slot = allParkingSlots[currentSlotIndex]
                mapView.selectSlotProgrammatically(slot.x, slot.y)
            }
        }

        btnNext.setOnClickListener {
            if (allParkingSlots.isNotEmpty()) {
                currentSlotIndex++
                if (currentSlotIndex >= allParkingSlots.size) currentSlotIndex = 0
                val slot = allParkingSlots[currentSlotIndex]
                mapView.selectSlotProgrammatically(slot.x, slot.y)
            }
        }

        mapView.slotListener = object : ParkingSelectorView.OnSlotSelectedListener {
            override fun onSlotSelected(x: Int, y: Int, isOccupied: Boolean) {
                runOnUiThread {
                    currentSlotIndex = allParkingSlots.indexOfFirst { it.x == x && it.y == y }

                    // Assuming you added these to strings.xml earlier
                    textTitle.text = getString(R.string.parking_spot_title, x, y)

                    if (isOccupied) {
                        textStatus.text = getString(R.string.status_occupied)
                        textStatus.setTextColor("#D32F2F".toColorInt())
                        btnNavigate.isEnabled = false
                        btnNavigate.text = getString(R.string.spot_unavailable)
                    } else {
                        textStatus.text = getString(R.string.status_available)
                        textStatus.setTextColor("#388E3C".toColorInt())
                        btnNavigate.isEnabled = true
                        btnNavigate.text = getString(R.string.navigate_to_spot)
                    }

                    if (bottomCard.isGone) {
                        bottomCard.isVisible = true
                        bottomCard.translationY = 100f
                        bottomCard.animate().translationY(0f).setDuration(250).start()
                    }
                }
            }
        }

        val fabContainer = findViewById<LinearLayout>(R.id.fab_menu_container)
        val fabToggle = findViewById<FloatingActionButton>(R.id.fab_menu_toggle)
        val fabGroup = findViewById<LinearLayout>(R.id.fab_menu_group)

        // --- DRAG AND DROP LOGIC FOR THE MENU ---
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f
        val clickDragTolerance = 10f

        fabToggle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = fabContainer.x - event.rawX
                    dY = fabContainer.y - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    fabContainer.x = event.rawX + dX
                    fabContainer.y = event.rawY + dY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val upX = event.rawX
                    val upY = event.rawY
                    if (abs(upX - startX) < clickDragTolerance && abs(upY - startY) < clickDragTolerance) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        // --- TOGGLE MENU LOGIC ---
        fabToggle.setOnClickListener {
            isMenuOpen = !isMenuOpen
            if (isMenuOpen) {
                fabGroup.isVisible = true
                fabGroup.alpha = 0f
                fabGroup.translationY = -50f
                fabGroup.animate().alpha(1f).translationY(0f).setDuration(250).start()
                fabToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            } else {
                fabGroup.animate().alpha(0f).translationY(-50f).setDuration(200).withEndAction {
                    fabGroup.isGone = true
                }.start()
                fabToggle.setImageResource(android.R.drawable.ic_menu_sort_by_size)
            }
        }

        findViewById<FloatingActionButton>(R.id.fab_profile).setOnClickListener {
            Toast.makeText(this, "Opening Profile...", Toast.LENGTH_SHORT).show()
        }

        findViewById<FloatingActionButton>(R.id.fab_settings).setOnClickListener {
            Toast.makeText(this, "Opening Settings...", Toast.LENGTH_SHORT).show()
        }

        findViewById<FloatingActionButton>(R.id.fab_about).setOnClickListener {
            Toast.makeText(this, "About this App...", Toast.LENGTH_SHORT).show()
        }

        findViewById<FloatingActionButton>(R.id.fab_logout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, UserLoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}