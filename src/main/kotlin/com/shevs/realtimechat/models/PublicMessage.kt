package com.shevs.realtimechat.models

import kotlinx.serialization.Serializable

@Serializable
data class PublicMessage(
    val username: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)