package com.shevs.realtimechat.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class User(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val passwordHash: String,
    val createdAt: Long = System.currentTimeMillis()
)