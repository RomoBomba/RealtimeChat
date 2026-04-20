package com.shevs.realtimechat.plugins

import com.shevs.realtimechat.data.DataStore
import com.shevs.realtimechat.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mindrot.jbcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val activeSessions = ConcurrentHashMap<String, WebSocketServerSession>()
val publicMessageHistory = mutableListOf<PublicMessage>()
const val MAX_PUBLIC_HISTORY = 100

fun Application.configureRouting() {
    routing {
        get("/") {
            val htmlContent = this::class.java.classLoader.getResource("static/index.html")?.readText()
            if (htmlContent != null) {
                call.respondText(htmlContent, ContentType.Text.Html.withCharset(Charsets.UTF_8))
            } else {
                call.respondText("HTML not found", ContentType.Text.Plain.withCharset(Charsets.UTF_8))
            }
        }

        post("/api/register") {
            val request = call.receive<Map<String, String>>()
            val username = request["username"]?.trim() ?: ""
            val password = request["password"] ?: ""

            if (username.length < 3 || password.length < 6) {
                call.respondText("Username min 3, password min 6 chars", ContentType.Text.Plain.withCharset(Charsets.UTF_8), HttpStatusCode.BadRequest)
                return@post
            }

            val users = DataStore.loadUsers()
            if (users.any { it.username.equals(username, ignoreCase = true) }) {
                call.respondText("Username already exists", ContentType.Text.Plain.withCharset(Charsets.UTF_8), HttpStatusCode.Conflict)
                return@post
            }

            val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
            val user = User(username = username, passwordHash = hashed)
            users.add(user)
            DataStore.saveUsers(users)
            call.respondText("{\"message\": \"User registered\"}", ContentType.Application.Json.withCharset(Charsets.UTF_8), HttpStatusCode.Created)
        }

        post("/api/login") {
            val request = call.receive<Map<String, String>>()
            val username = request["username"] ?: ""
            val password = request["password"] ?: ""

            val user = DataStore.findUser(username)
            if (user == null || !BCrypt.checkpw(password, user.passwordHash)) {
                call.respondText("Invalid credentials", ContentType.Text.Plain.withCharset(Charsets.UTF_8), HttpStatusCode.Unauthorized)
                return@post
            }

            val token = JWT.create()
                .withClaim("username", username)
                .withClaim("userId", user.id)
                .withExpiresAt(Date(System.currentTimeMillis() + 86400000))
                .sign(Algorithm.HMAC256("your-256-bit-secret"))

            call.respondText("{\"token\": \"$token\"}", ContentType.Application.Json.withCharset(Charsets.UTF_8))
        }

        authenticate("auth-jwt") {
            get("/api/contacts") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: ""
                val contacts = DataStore.loadContacts(userId)
                call.respond(contacts.map { it.contactUsername })
            }

            post("/api/contacts") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString() ?: ""
                val request = call.receive<Map<String, String>>()
                val contactUsername = request["contactUsername"] ?: ""

                if (DataStore.findUser(contactUsername) == null) {
                    call.respondText("User not found", ContentType.Text.Plain.withCharset(Charsets.UTF_8), HttpStatusCode.NotFound)
                    return@post
                }

                DataStore.addContact(userId, contactUsername)
                call.respondText("{\"message\": \"Contact added\"}", ContentType.Application.Json.withCharset(Charsets.UTF_8), HttpStatusCode.Created)
            }

            get("/api/messages/public") {
                val messages = publicMessageHistory.takeLast(100)
                call.respond(messages)
            }

            delete("/api/messages/public") {
                DataStore.clearPublicHistory()
                publicMessageHistory.clear()
                call.respond(HttpStatusCode.OK)
            }

            get("/api/messages/private/{username}") {
                val principal = call.principal<JWTPrincipal>()
                val currentUser = principal?.payload?.getClaim("username")?.asString() ?: ""
                val otherUser = call.parameters["username"] ?: ""
                val messages = DataStore.loadPrivateMessages(currentUser, otherUser)
                call.respond(messages.takeLast(100))
            }

            delete("/api/messages/private/{username}") {
                val principal = call.principal<JWTPrincipal>()
                val currentUser = principal?.payload?.getClaim("username")?.asString() ?: ""
                val otherUser = call.parameters["username"] ?: ""
                DataStore.clearPrivateHistory(currentUser, otherUser)
                call.respond(HttpStatusCode.OK)
            }
        }

        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
            if (token == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token"))
                return@webSocket
            }

            val username = try {
                val verifier = JWT.require(Algorithm.HMAC256("your-256-bit-secret")).build()
                val decoded = verifier.verify(token)
                decoded.getClaim("username").asString()
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }

            println("User connected: $username")
            activeSessions[username] = this

            publicMessageHistory.takeLast(50).forEach { msg ->
                send(Frame.Text(Json.encodeToString(msg)))
            }

            val welcomeMsg = PublicMessage("System", "Welcome $username!", System.currentTimeMillis())
            send(Frame.Text(Json.encodeToString(welcomeMsg)))

            broadcastUserEvent(UserEvent(username, true))

            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            try {
                                val msg = Json.decodeFromString<PublicMessage>(text)
                                addToPublicHistory(msg)
                                broadcastPublicMessage(msg)
                            } catch (e: Exception) {
                                try {
                                    val privateMsg = Json.decodeFromString<PrivateMessage>(text)
                                    DataStore.addPrivateMessage(privateMsg)
                                    activeSessions[privateMsg.to]?.send(Frame.Text(Json.encodeToString(privateMsg)))
                                    val confirmMsg = mapOf("type" to "private_confirm", "id" to privateMsg.id, "timestamp" to privateMsg.timestamp)
                                    send(Frame.Text(Json.encodeToString(confirmMsg)))
                                } catch (e2: Exception) {
                                    try {
                                        val typing = Json.decodeFromString<TypingEvent>(text)
                                        broadcastTypingEvent(typing, username)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                if (e !is java.io.IOException && e !is kotlinx.coroutines.CancellationException) {
                    println("WebSocket error for $username: ${e.message}")
                }
            } finally {
                println("User disconnected: $username")
                activeSessions.remove(username)
                try {
                    broadcastUserEvent(UserEvent(username, false))
                } catch (_: Exception) {}
            }
        }
    }
}

fun addToPublicHistory(message: PublicMessage) {
    publicMessageHistory.add(message)
    if (publicMessageHistory.size > MAX_PUBLIC_HISTORY) {
        publicMessageHistory.removeAt(0)
    }
    DataStore.addPublicMessage(message)
}

suspend fun broadcastPublicMessage(message: PublicMessage) {
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
    if (event.to != null && event.to.isNotEmpty()) {
        activeSessions[event.to]?.send(Frame.Text(json))
    } else {
        activeSessions.forEach { (username, session) ->
            if (username != excludeUsername) {
                try {
                    session.send(Frame.Text(json))
                } catch (_: Exception) {}
            }
        }
    }
}