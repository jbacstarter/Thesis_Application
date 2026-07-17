package com.thesis.thesisapplication.helpers

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.thesis.thesisapplication.R

import android.widget.FrameLayout

fun Activity.showLoading(text: String = "Loading...") {
    val overlay = findViewById<FrameLayout>(R.id.loading_overlay) ?: return
    val textView = findViewById<TextView>(R.id.loading_text)

    // Update the text dynamically
    textView.text = text

    // Make it visible and fade it in
    overlay.visibility = View.VISIBLE
    overlay.alpha = 0f
    overlay.animate()
        .alpha(1f)
        .setDuration(300)
        .start()
}

fun Activity.hideLoading() {
    val overlay = findViewById<FrameLayout>(R.id.loading_overlay) ?: return

    // Fade it out, then completely remove it from the screen
    overlay.animate()
        .alpha(0f)
        .setDuration(300)
        .withEndAction {
            overlay.visibility = View.GONE
        }
        .start()
}