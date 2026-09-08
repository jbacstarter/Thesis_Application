package com.thesis.thesisapplication.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.thesis.thesisapplication.R
import com.thesis.thesisapplication.helpers.GridPoint
import com.thesis.thesisapplication.helpers.ParkingSelectorView
import kotlin.math.abs

// IMPORTANT: If you implemented the custom session timeout logic earlier, change this back to
// class ParkingViewSelectorActivity : SessionTimeoutActivity() {
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

        // --- MAP & BOTTOM PARKING CARD VIEWS ---
        val mapView = findViewById<ParkingSelectorView>(R.id.parking_selector_view)
        val bottomCard = findViewById<MaterialCardView>(R.id.bottom_parking_card)
        val textTitle = findViewById<TextView>(R.id.card_slot_title)
        val textStatus = findViewById<TextView>(R.id.card_slot_status)
        val btnNavigate = findViewById<MaterialButton>(R.id.btn_navigate_here)
        val btnPrev = findViewById<MaterialButton>(R.id.btn_prev_slot)
        val btnNext = findViewById<MaterialButton>(R.id.btn_next_slot)

        // --- MODAL CARDS ---
        val cardProfile = findViewById<MaterialCardView>(R.id.card_profile)
        val cardSettings = findViewById<MaterialCardView>(R.id.card_settings)
        val cardAbout = findViewById<MaterialCardView>(R.id.card_about)

        val btnCloseProfile = findViewById<MaterialButton>(R.id.btn_close_profile)
        val btnCloseSettings = findViewById<MaterialButton>(R.id.btn_close_settings)
        val btnCloseAbout = findViewById<MaterialButton>(R.id.btn_close_about)

        // Helper function to animate cards in and out
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

        mapView.slotListener = object : ParkingSelectorView.OnSlotSelectedListener {
            override fun onSlotSelected(x: Int, y: Int, isOccupied: Boolean) {
                runOnUiThread {
                    currentSlotIndex = allParkingSlots.indexOfFirst { it.x == x && it.y == y }

                    cardProfile.isGone = true
                    cardSettings.isGone = true
                    cardAbout.isGone = true

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

        // Get references to the specific Tooltips so we can animate them
        val tooltips = listOf(
            findViewById<MaterialCardView>(R.id.tooltip_profile),
            findViewById<MaterialCardView>(R.id.tooltip_settings),
            findViewById<MaterialCardView>(R.id.tooltip_about),
            findViewById<MaterialCardView>(R.id.tooltip_logout)
        )

        // This runnable handles the actual fading out of the tooltips
        val hideTooltipsRunnable = Runnable {
            tooltips.forEach { tooltip ->
                tooltip.animate().alpha(0f).setDuration(300).withEndAction {
                    tooltip.isGone = true
                }.start()
            }
        }

        // Drag and Drop Logic
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

        // Toggle Menu Animation
        fun closeFabMenu() {
            isMenuOpen = false
            fabGroup.removeCallbacks(hideTooltipsRunnable) // Stop any running fade timers
            fabGroup.animate().alpha(0f).translationY(-50f).setDuration(200).withEndAction {
                fabGroup.isGone = true
            }.start()
            fabToggle.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        }

        fabToggle.setOnClickListener {
            isMenuOpen = !isMenuOpen
            if (isMenuOpen) {
                // Ensure tooltips are fully visible when menu opens
                tooltips.forEach {
                    it.animate().cancel()
                    it.isVisible = true
                    it.alpha = 1f
                }

                fabGroup.isVisible = true
                fabGroup.alpha = 0f
                fabGroup.translationY = -50f
                fabGroup.animate().alpha(1f).translationY(0f).setDuration(250).start()
                fabToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)

                // Trigger the fade-out effect after 2.5 seconds
                fabGroup.removeCallbacks(hideTooltipsRunnable)
                fabGroup.postDelayed(hideTooltipsRunnable, 2500)

            } else {
                closeFabMenu()
            }
        }

        // --- BUTTON ACTIONS ---

        // Profile Listener bound to both the row AND the button
        val profileClickListener = View.OnClickListener {
            closeFabMenu()
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                FirebaseFirestore.getInstance().collection("Users").document(currentUser.uid).get()
                    .addOnSuccessListener { document ->
                        findViewById<TextView>(R.id.profile_name_text).text = document.getString("fullName") ?: "Unknown User"
                        findViewById<TextView>(R.id.profile_email_text).text = currentUser.email
                    }
            } else {
                findViewById<TextView>(R.id.profile_name_text).text = "Guest User"
                findViewById<TextView>(R.id.profile_email_text).text = "Not logged in"
            }
            showModalCard(cardProfile)
        }
        findViewById<View>(R.id.menu_item_profile).setOnClickListener(profileClickListener)
        findViewById<View>(R.id.fab_profile).setOnClickListener(profileClickListener)

        // Settings Listener
        val settingsClickListener = View.OnClickListener {
            closeFabMenu()
            showModalCard(cardSettings)
        }
        findViewById<View>(R.id.menu_item_settings).setOnClickListener(settingsClickListener)
        findViewById<View>(R.id.fab_settings).setOnClickListener(settingsClickListener)

        // About Listener
        val aboutClickListener = View.OnClickListener {
            closeFabMenu()
            showModalCard(cardAbout)
        }
        findViewById<View>(R.id.menu_item_about).setOnClickListener(aboutClickListener)
        findViewById<View>(R.id.fab_about).setOnClickListener(aboutClickListener)

        // Logout Listener
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