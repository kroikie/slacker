package com.example.arthurthompson.slacker.model

import com.google.firebase.Timestamp

data class Message(
    val text: String = "",
    val senderName: String = "",
    val senderUid: String = "",
    val senderPhotoUrl: String? = null,
    val timestamp: Timestamp? = null
)
