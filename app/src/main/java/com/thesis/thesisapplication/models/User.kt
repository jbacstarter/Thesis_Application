package com.thesis.thesisapplication.models

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "driver",
    // Add this new line! We default it to empty in case a user hasn't uploaded a photo yet.
    val profileImageUrl: String = ""
)