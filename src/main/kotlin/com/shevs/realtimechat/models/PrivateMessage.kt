package com.shevs.realtimechat.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PrivateMessage(
    val id: String = UUID.randomUUID().toString(),
    val from: String,
    val to: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false
)