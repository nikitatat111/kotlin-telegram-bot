package app

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
  embeddedServer(
    factory = Netty,
    port = System.getenv("PORT")?.toInt() ?: 8080,
    module = Application::module
  ).start(wait = true)
}
