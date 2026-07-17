package com.thesis.thesisapplication.helpers

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.thesis.thesisapplication.R

// By putting "Activity." in front of the function name,
// EVERY activity in your app can now use this automatically!
fun Activity.showCustomNotification(message: String, type: String = "info") {

    // Find the views from the included layout
    val notificationView = findViewById<LinearLayout>(R.id.custom_notification) ?: return
    val notifBorder = findViewById<View>(R.id.notif_border)
    val notifBody = findViewById<LinearLayout>(R.id.notif_body)
    val notifIcon = findViewById<TextView>(R.id.notif_icon)
    val notifMessage = findViewById<TextView>(R.id.notif_message)

    notifMessage.text = message

    when (type) {
        "success" -> {
            notifIcon.text = "✓"
            notifBody.setBackgroundColor(Color.parseColor("#4CAF50"))
            notifBorder.setBackgroundColor(Color.parseColor("#2E7D32"))
        }
        "error" -> {
            notifIcon.text = "✗"
            notifBody.setBackgroundColor(Color.parseColor("#F44336"))
            notifBorder.setBackgroundColor(Color.parseColor("#C62828"))
        }
        "warning" -> {
            notifIcon.text = "⚠"
            notifBody.setBackgroundColor(Color.parseColor("#FF9800"))
            notifBorder.setBackgroundColor(Color.parseColor("#EF6C00"))
        }
        "info" -> {
            notifIcon.text = "ℹ"
            notifBody.setBackgroundColor(Color.parseColor("#2196F3"))
            notifBorder.setBackgroundColor(Color.parseColor("#1565C0"))
        }
    }

    // Reset position in case it's called multiple times quickly
    notificationView.translationY = -150f
    notificationView.alpha = 0f

    // Animate In
    notificationView.animate()
        .translationY(0f)
        .alpha(1f)
        .setDuration(500)
        .withEndAction {
            // Wait 3.5 seconds, then animate out
            notificationView.postDelayed({
                notificationView.animate()
                    .translationY(-150f)
                    .alpha(0f)
                    .setDuration(1000)
                    .start()
            }, 2000)
        }
        .start()
}