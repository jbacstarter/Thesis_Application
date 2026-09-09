package com.thesis.thesisapplication.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.thesis.thesisapplication.R
import com.thesis.thesisapplication.helpers.GridPoint
import com.thesis.thesisapplication.helpers.ParkingSelectorView
import kotlin.math.abs

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class ParkingViewSelectorActivity : AppCompatActivity() {

    private var isMenuOpen = false

    private var allParkingSlots = listOf<GridPoint>()
    private var currentSlotIndex = -1

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

        // --- MAP & BOTTOM PARKING CARD VIEWS ---
        val mapView = findViewById<ParkingSelectorView>(R.id.parking_selector_view)
        val bottomCard = findViewById<MaterialCardView>(R.id.bottom_parking_card)
        val textTitle = findViewById<TextView>(R.id.card_slot_title)
        val textStatus = findViewById<TextView>(R.id.card_slot_status)
        val btnNavigate = findViewById<MaterialButton>(R.id.btn_navigate_here)
        val btnPrev = findViewById<MaterialButton>(R.id.btn_prev_slot)
        val btnNext = findViewById<MaterialButton>(R.id.btn_next_slot)

        val cardProfile = findViewById<MaterialCardView>(R.id.card_profile)
        val cardSettings = findViewById<MaterialCardView>(R.id.card_settings)
        val cardAbout = findViewById<MaterialCardView>(R.id.card_about)

        val btnCloseProfile = findViewById<MaterialButton>(R.id.btn_close_profile)
        val btnCloseSettings = findViewById<MaterialButton>(R.id.btn_close_settings)
        val btnCloseAbout = findViewById<MaterialButton>(R.id.btn_close_about)

        // --- 1. SETUP FIREBASE RTDB CONNECTION ---
        val friendFirebaseOptions = FirebaseOptions.Builder()
            .setApplicationId("1:747138560698:android:26927716a6bb9bffa48883")
            .setApiKey("AIzaSyD7SeI7dZmTFXpg8GtpZl1hPkwGRqoH41I")
            .setDatabaseUrl("https://sensormodule-final-default-rtdb.asia-southeast1.firebasedatabase.app")
            .build()

        val secondaryApp = try {
            FirebaseApp.getInstance("FriendProject")
        } catch (_: IllegalStateException) {
            FirebaseApp.initializeApp(this, friendFirebaseOptions, "FriendProject")
        }

        if (secondaryApp != null) {
            val friendDatabase = FirebaseDatabase.getInstance(secondaryApp)
            val slotsRef = friendDatabase.getReference("final/slots")

            // --- 2. LISTEN FOR LIVE SENSOR CHANGES ---
            slotsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // 0 = Vacant, 1 = Occupied, 2 = Unknown/Grey
                    val liveStatuses = mutableMapOf<String, Int>()

                    for (slotSnapshot in snapshot.children) {
                        val slotId = slotSnapshot.key ?: continue

                        val src = slotSnapshot.child("src").value?.toString() ?: "unknown"
                        val rawStatus = slotSnapshot.child("st").value?.toString() ?: "0"

                        val slotState = if (src == "unknown") {
                            2 // Unknown data (Grey)
                        } else if (rawStatus == "1") {
                            1 // Occupied (Red)
                        } else {
                            0 // Available (Green)
                        }

                        liveStatuses[slotId] = slotState
                        Log.d("SENSOR_DATA", "Slot $slotId | Source: $src | State: $slotState")
                    }

                    mapView.updateLiveStatuses(liveStatuses)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SENSOR_DATA", "Failed to read sensor data: ${error.message}")
                }
            })
        }

        // --- UI HELPER FUNCTIONS ---
        fun showModalCard(cardToShow: MaterialCardView) {
            if (bottomCard.isVisible) {
                bottomCard.animate().translationY(150f).alpha(0f).setDuration(200).withEndAction { bottomCard.isGone = true }.start()
            }
            cardToShow.isVisible = true
            cardToShow.alpha = 0f
            cardToShow.scaleX = 0.8f
            cardToShow.scaleY = 0.8f
            cardToShow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).start()
        }

        fun hideModalCard(cardToHide: MaterialCardView) {
            cardToHide.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(200).withEndAction {
                cardToHide.isGone = true
            }.start()
        }

        btnCloseProfile.setOnClickListener { hideModalCard(cardProfile) }
        btnCloseSettings.setOnClickListener { hideModalCard(cardSettings) }
        btnCloseAbout.setOnClickListener { hideModalCard(cardAbout) }

        // --- PARKING SLOT LOGIC ---
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

        // --- ROUTE TO MAPBOX ---
        btnNavigate.setOnClickListener {
            if (currentSlotIndex in allParkingSlots.indices) {
                val selectedSlot = allParkingSlots[currentSlotIndex]
                val navIntent = Intent(this, Navigation::class.java)
                navIntent.putExtra("TARGET_SLOT_X", selectedSlot.x)
                navIntent.putExtra("TARGET_SLOT_Y", selectedSlot.y)
                startActivity(navIntent)
            }
        }

        mapView.slotListener = object : ParkingSelectorView.OnSlotSelectedListener {
            override fun onSlotSelected(x: Int, y: Int, state: Int) {
                runOnUiThread {
                    currentSlotIndex = allParkingSlots.indexOfFirst { it.x == x && it.y == y }

                    cardProfile.isGone = true
                    cardSettings.isGone = true
                    cardAbout.isGone = true

                    textTitle.text = getString(R.string.parking_spot_title, x, y)

                    when (state) {
                        2 -> { // Unknown
                            textStatus.text = "Status: Unknown"
                            textStatus.setTextColor("#9E9E9E".toColorInt())
                            btnNavigate.isEnabled = false
                            btnNavigate.text = "Spot Unavailable"
                        }
                        1 -> { // Occupied
                            textStatus.text = getString(R.string.status_occupied)
                            textStatus.setTextColor("#D32F2F".toColorInt())
                            btnNavigate.isEnabled = false
                            btnNavigate.text = getString(R.string.spot_unavailable)
                        }
                        else -> { // Available
                            textStatus.text = getString(R.string.status_available)
                            textStatus.setTextColor("#388E3C".toColorInt())
                            btnNavigate.isEnabled = true
                            btnNavigate.text = getString(R.string.navigate_to_spot)
                        }
                    }

                    if (bottomCard.isGone) {
                        bottomCard.isVisible = true
                        bottomCard.translationY = 100f
                        bottomCard.alpha = 1f
                        bottomCard.animate().translationY(0f).setDuration(250).start()
                    }
                }
            }
        }

        // --- FAB MENU & TOOLTIP ANIMATION LOGIC ---
        val fabContainer = findViewById<LinearLayout>(R.id.fab_menu_container)
        val fabToggle = findViewById<FloatingActionButton>(R.id.fab_menu_toggle)
        val fabGroup = findViewById<LinearLayout>(R.id.fab_menu_group)

        val tooltips = listOf(
            findViewById<MaterialCardView>(R.id.tooltip_profile),
            findViewById<MaterialCardView>(R.id.tooltip_settings),
            findViewById<MaterialCardView>(R.id.tooltip_about),
            findViewById<MaterialCardView>(R.id.tooltip_logout)
        )

        val hideTooltipsRunnable = Runnable {
            tooltips.forEach { tooltip ->
                tooltip.animate().alpha(0f).setDuration(300).withEndAction { tooltip.isGone = true }.start()
            }
        }

        var dX = 0f; var dY = 0f; var startX = 0f; var startY = 0f
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
                    if (abs(event.rawX - startX) < clickDragTolerance && abs(event.rawY - startY) < clickDragTolerance) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        fun closeFabMenu() {
            isMenuOpen = false
            fabGroup.removeCallbacks(hideTooltipsRunnable)
            fabGroup.animate().alpha(0f).translationY(-50f).setDuration(200).withEndAction { fabGroup.isGone = true }.start()
            fabToggle.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        }

        fabToggle.setOnClickListener {
            isMenuOpen = !isMenuOpen
            if (isMenuOpen) {
                tooltips.forEach { it.animate().cancel(); it.isVisible = true; it.alpha = 1f }
                fabGroup.isVisible = true
                fabGroup.alpha = 0f
                fabGroup.translationY = -50f
                fabGroup.animate().alpha(1f).translationY(0f).setDuration(250).start()
                fabToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                fabGroup.removeCallbacks(hideTooltipsRunnable)
                fabGroup.postDelayed(hideTooltipsRunnable, 2500)
            } else {
                closeFabMenu()
            }
        }

        val profileClickListener = View.OnClickListener {
            closeFabMenu(); showModalCard(cardProfile)
        }
        findViewById<View>(R.id.menu_item_profile).setOnClickListener(profileClickListener)
        findViewById<View>(R.id.fab_profile).setOnClickListener(profileClickListener)

        val settingsClickListener = View.OnClickListener {
            closeFabMenu(); showModalCard(cardSettings)
        }
        findViewById<View>(R.id.menu_item_settings).setOnClickListener(settingsClickListener)
        findViewById<View>(R.id.fab_settings).setOnClickListener(settingsClickListener)

        val aboutClickListener = View.OnClickListener {
            closeFabMenu(); showModalCard(cardAbout)
        }
        findViewById<View>(R.id.menu_item_about).setOnClickListener(aboutClickListener)
        findViewById<View>(R.id.fab_about).setOnClickListener(aboutClickListener)

        val logoutClickListener = View.OnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, UserLoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.menu_item_logout).setOnClickListener(logoutClickListener)
        findViewById<View>(R.id.fab_logout).setOnClickListener(logoutClickListener)
    }
}