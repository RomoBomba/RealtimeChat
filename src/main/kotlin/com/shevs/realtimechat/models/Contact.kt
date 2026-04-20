package com.shevs.realtimechat.models

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val userId: String,
    val contactUsername: String,
    val addedAt: Long = System.currentTimeMillis()
)