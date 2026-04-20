package com.shevs.realtimechat.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.server.response.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.shevs.realtimechat.models.Message
import com.shevs.realtimechat.models.UserEvent
import com.shevs.realtimechat.models.TypingEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.mutableListOf

val activeSessions = ConcurrentHashMap<String, WebSocketServerSession>()
val messageHistory = mutableListOf<Message>()
const val MAX_HISTORY = 100

fun Application.configureRouting() {
    routing {
        get("/") {
            val htmlContent = this::class.java.classLoader.getResource("static/index.html")?.readText()
            if (htmlContent != null) {
                call.respondText(htmlContent, ContentType.Text.Html)
            } else {
                call.respondText("HTML not found", ContentType.Text.Plain)
            }
        }

        webSocket("/chat") {
            val username = call.request.queryParameters["username"] ?: "User_${System.currentTimeMillis()}"

            println("User connected: $username")
            activeSessions[username] = this

            messageHistory.takeLast(50).forEach { msg ->
                send(Frame.Text(Json.encodeToString(msg)))
            }

            val welcomeMsg = Message("System", "Welcome $username!", System.currentTimeMillis())
            send(Frame.Text(Json.encodeToString(welcomeMsg)))

            broadcastUserEvent(UserEvent(username, true))

            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()

                            try {
                                val msg = Json.decodeFromString<Message>(text)
                                addToHistory(msg)
                                broadcastMessage(msg)
                            } catch (e: Exception) {
                                try {
                                    val typingEvent = Json.decodeFromString<TypingEvent>(text)
                                    broadcastTypingEvent(typingEvent, username)
                                } catch (e2: Exception) {
                                }
                            }
                        }
                        else -> {}
                    }
                }
            } finally {
                println("User disconnected: $username")
                activeSessions.remove(username)
                broadcastUserEvent(UserEvent(username, false))
            }
        }
    }
}

fun addToHistory(message: Message) {
    messageHistory.add(message)
    if (messageHistory.size > MAX_HISTORY) {
        messageHistory.removeAt(0)
    }
}

suspend fun broadcastMessage(message: Message) {
    val json = Json.encodeToString(message)
    val disconnected = mutableListOf<String>()

    activeSessions.forEach { (username, session) ->
        try {
            session.send(Frame.Text(json))
        } catch (e: Exception) {
            disconnected.add(username)
        }
    }

    disconnected.forEach { activeSessions.remove(it) }
}

suspend fun broadcastUserEvent(event: UserEvent) {
    val json = Json.encodeToString(event)
    val disconnected = mutableListOf<String>()

    activeSessions.forEach { (username, session) ->
        try {
            session.send(Frame.Text(json))
        } catch (e: Exception) {
            disconnected.add(username)
        }
    }

    disconnected.forEach { activeSessions.remove(it) }
}

suspend fun broadcastTypingEvent(event: TypingEvent, excludeUsername: String) {
    val json = Json.encodeToString(event)

    activeSessions.forEach { (username, session) ->
        if (username != excludeUsername) {
            try {
                session.send(Frame.Text(json))
            } catch (e: Exception) {
            }
        }
    }
}