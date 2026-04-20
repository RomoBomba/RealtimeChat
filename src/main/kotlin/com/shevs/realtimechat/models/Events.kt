package com.shevs.realtimechat.models

import kotlinx.serialization.Serializable

@Serializable
data class UserEvent(
    val username: String,
    val isJoined: Boolean
)

@Serializable
data class TypingEvent(
    val username: String,
    val isTyping: Boolean,
    val to: String? = null
)