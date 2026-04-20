package com.shevs.realtimechat

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.shevs.realtimechat.plugins.*

fun main() {
    println("Application started on http://localhost:8080")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSerialization()
        configureSockets()
        configureSecurity()
        configureRouting()
    }.start(wait = true)
}