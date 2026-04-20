package com.shevs.realtimechat.models

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val username: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class UserEvent(
    val username: String,
    val isJoined: Boolean
)

@Serializable
data class TypingEvent(
    val username: String,
    val isTyping: Boolean
)