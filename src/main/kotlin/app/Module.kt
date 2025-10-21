package app

import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import io.ktor.http.*

@Serializable
data class Chat(val id: Long)

@Serializable
data class From(val id: Long, val username: String? = null)

@Serializable
data class Message(
  val message_id: Long,
  val chat: Chat,
  val from: From? = null,
  val text: String? = null
)

@Serializable
data class Update(val update_id: Long, val message: Message? = null)

@Serializable
data class TgSendMessageReq(val chat_id: Long, val text: String)


fun Application.module() {
  // Настраиваем JSON сериализацию на сервере
  install(ContentNegotiation) {
    json(Json { ignoreUnknownKeys = true })
  }

  val tgToken = env("TELEGRAM_TOKEN")
  val webhookSecret = env("TELEGRAM_WEBHOOK_SECRET")
  val telegramApi = env("TELEGRAM_API", "https://api.telegram.org")

  // Supabase REST
  val supabaseUrl = env("SUPABASE_URL")               // https://xxx.supabase.co
  val supabaseServiceKey = env("SUPABASE_SERVICE_KEY") // service role

  // HTTP-клиент для Telegram и Supabase
  val http = HttpClient(Java) {
    install(ClientContentNegotiation) {
      json(Json { ignoreUnknownKeys = true })
    }
  }

  routing {
    get("/") {
      call.respondText("ok")
    }

    post("/tg-webhook") {
      val secret = call.request.headers["X-Telegram-Bot-Api-Secret-Token"]
      if (secret != webhookSecret) {
        call.respond(HttpStatusCode.Forbidden)
        return@post
      }

      val update = call.receive<Update>()
      val msg = update.message
      if (msg == null) {
        call.respondText("ok")
        return@post
      }

      val chatId = msg.chat.id
      val text = msg.text ?: ""

      // Сохраняем апдейт в Supabase
      runCatching {
        http.post("$supabaseUrl/rest/v1/tg_updates") {
          headers {
            append("apikey", supabaseServiceKey)
            append(HttpHeaders.Authorization, "Bearer $supabaseServiceKey")
            append(HttpHeaders.ContentType, ContentType.Application.Json)
            append("Prefer", "return=minimal")
          }
          setBody(
            mapOf(
              "chat_id" to chatId,
              "user_id" to (msg.from?.id ?: 0),
              "text" to text,
              "raw" to update
            )
          )
        }.bodyAsText()
      }.onFailure {
        call.application.environment.log.warn("Supabase insert failed: ${it.message}")
      }

      // Ответ пользователю
      val reply = when {
        text.startsWith("/start") -> "Привет! Я Котлин-бот на Ktor + Supabase."
        text.startsWith("/echo ") -> text.removePrefix("/echo ")
        else -> "Вы написали: $text"
      }

      runCatching {
        http.post("$telegramApi/bot$tgToken/sendMessage") {
          contentType(ContentType.Application.Json)
          setBody(TgSendMessageReq(chat_id = chatId, text = reply))
        }
      }

      call.respondText("ok")
    }
  }
}

private fun env(key: String, default: String? = null): String =
  System.getenv(key) ?: default ?: error("Missing env $key")
