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

// --- Telegram models ---
@Serializable data class Chat(val id: Long)
@Serializable data class From(val id: Long, val username: String? = null)
@Serializable data class Message(
  val message_id: Long,
  val chat: Chat,
  val from: From? = null,
  val text: String? = null
)
@Serializable data class CallbackQuery(
  val id: String,
  val from: From,
  val message: Message? = null,
  val data: String? = null
)
@Serializable data class InlineKeyboardButton(
  val text: String,
  val callback_data: String? = null,
  val url: String? = null
)
@Serializable data class InlineKeyboardMarkup(
  val inline_keyboard: List<List<InlineKeyboardButton>>
)
@Serializable data class Update(
  val update_id: Long,
  val message: Message? = null,
  val callback_query: CallbackQuery? = null
)

@Serializable data class TgSendMessageReq(
  val chat_id: Long,
  val text: String,
  val parse_mode: String? = "HTML",
  val reply_markup: InlineKeyboardMarkup? = null
)
@Serializable data class TgSendDocumentReq(
  val chat_id: Long,
  val document: String,
  val caption: String? = null,
  val parse_mode: String? = "HTML"
)
@Serializable data class TgAnswerCbReq(val callback_query_id: String)

// getChatMember response (минимально)
@Serializable data class TgUser(val id: Long)
@Serializable data class TgChatMember(val status: String, val user: TgUser)
@Serializable data class TgGetMemberResp(val ok: Boolean, val result: TgChatMember? = null, val description: String? = null)

// --- App ---
fun Application.module() {
  install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

  val tgToken = env("TELEGRAM_TOKEN")
  val webhookSecret = env("TELEGRAM_WEBHOOK_SECRET")
  val telegramApi = env("TELEGRAM_API", "https://api.telegram.org")

  val guideUrl = env("GUIDE_URL")
  val channelUsername = env("CHANNEL_USERNAME") // например: hlebsasha_travel  (без @)
  val channelRef = if (channelUsername.startsWith("@")) channelUsername else "@$channelUsername"
  val pickTourUrl = env("PICK_TOUR_URL", "https://t.me/$channelUsername")

  // Supabase (как было)
  val supabaseUrl = env("SUPABASE_URL")
  val supabaseServiceKey = env("SUPABASE_SERVICE_KEY")

  val http = HttpClient(Java) {
    install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
  }

  suspend fun answerCb(id: String) = runCatching {
    http.post("$telegramApi/bot$tgToken/answerCallbackQuery") {
      contentType(ContentType.Application.Json)
      setBody(TgAnswerCbReq(callback_query_id = id))
    }
  }

  suspend fun sendMsg(chatId: Long, text: String, markup: InlineKeyboardMarkup? = null) = runCatching {
    http.post("$telegramApi/bot$tgToken/sendMessage") {
      contentType(ContentType.Application.Json)
      setBody(TgSendMessageReq(chat_id = chatId, text = text, reply_markup = markup))
    }
  }

  suspend fun sendDoc(chatId: Long, url: String, caption: String? = null) = runCatching {
    http.post("$telegramApi/bot$tgToken/sendDocument") {
      contentType(ContentType.Application.Json)
      setBody(TgSendDocumentReq(chat_id = chatId, document = url, caption = caption))
    }
  }

  suspend fun isSubscribed(userId: Long): Boolean {
    val resp = http.get("$telegramApi/bot$tgToken/getChatMember") {
      url {
        parameters.append("chat_id", channelRef)
        parameters.append("user_id", userId.toString())
      }
    }
    val body = resp.bodyAsText()
    return runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<TgGetMemberResp>(body) }
      .getOrNull()
      ?.result
      ?.status
      .let { status ->
        when (status) {
          "creator", "administrator", "member" -> true
          else -> false // "left", "kicked", null, etc.
        }
      }
  }

  routing {
    get("/") { call.respondText("ok") }

    post("/tg-webhook") {
      // секьюрность
      val secret = call.request.headers["X-Telegram-Bot-Api-Secret-Token"]
      if (secret != webhookSecret) {
        call.respond(HttpStatusCode.Forbidden); return@post
      }

      val update = call.receive<Update>()

      // логируем в Supabase (как было)
      runCatching {
        http.post("$supabaseUrl/rest/v1/tg_updates") {
          headers {
            append("apikey", supabaseServiceKey)
            append(HttpHeaders.Authorization, "Bearer $supabaseServiceKey")
            append(HttpHeaders.ContentType, ContentType.Application.Json)
            append("Prefer", "return=minimal")
          }
          val (chatId, userId, text) = when {
            update.message != null -> Triple(update.message.chat.id, update.message.from?.id ?: 0, update.message.text ?: "")
            update.callback_query?.message != null -> {
              val m = update.callback_query.message!!
              Triple(m.chat.id, update.callback_query.from.id, m.text ?: "")
            }
            else -> Triple(0L, 0L, "")
          }
          setBody(mapOf("chat_id" to chatId, "user_id" to userId, "text" to text, "raw" to update))
        }.bodyAsText()
      }

      // --- обработка callback-кнопок ---
      update.callback_query?.let { cq ->
        val chatId = cq.message?.chat?.id ?: return@let
        val userId = cq.from.id
        when (cq.data) {
          "start_get_guide" -> {
            answerCb(cq.id)
            val text = """
                            Отлично! 🎁
                            Чтобы получить гайд, подпишись на канал Саши:
                            👉 https://t.me/$channelUsername
                            После подписки нажми кнопку ниже.
                        """.trimIndent()
            val markup = InlineKeyboardMarkup(
              listOf(
                listOf(InlineKeyboardButton("Подписаться", url = "https://t.me/$channelUsername")),
                listOf(InlineKeyboardButton("Я подписан", callback_data = "check_sub"))
              )
            )
            sendMsg(chatId, text, markup)
          }

          "check_sub" -> {
            answerCb(cq.id)
            sendMsg(chatId, "Проверяю подписку… секунду ⏳")

            if (isSubscribed(userId)) {
              val okText = """
                                Супер! ✅
                                Подписка есть — держи обещанный подарок 🎁
                            """.trimIndent()
              val markup = InlineKeyboardMarkup(
                listOf(
                  listOf(InlineKeyboardButton("Получить гайд", callback_data = "send_guide"))
                )
              )
              sendMsg(chatId, okText, markup)
            } else {
              val fail = """
                                Похоже, подписка не найдена 😕
                                Подпишись на канал и нажми «Я подписан».
                            """.trimIndent()
              val markup = InlineKeyboardMarkup(
                listOf(
                  listOf(InlineKeyboardButton("Подписаться", url = "https://t.me/$channelUsername")),
                  listOf(InlineKeyboardButton("Я подписан", callback_data = "check_sub"))
                )
              )
              sendMsg(chatId, fail, markup)
            }
          }

          "send_guide" -> {
            answerCb(cq.id)
            sendDoc(chatId, guideUrl, "Твой гайд с ТОП-20 отелями. Приятного планирования ✈️")
            val after = """
                            А вот и гайд, пользуйся на здоровье 😍
                            
                            Если захочешь больше — жми кнопку: «Саша, подбери тур», и Саша соберёт варианты лично под твой запрос!
                        """.trimIndent()
            val markup = InlineKeyboardMarkup(
              listOf(listOf(InlineKeyboardButton("Саша, подбери тур", url = pickTourUrl)))
            )
            sendMsg(chatId, after, markup)
          }
        }
        call.respondText("ok"); return@post
      }

      // --- обычные сообщения ---
      val msg = update.message
      if (msg == null) { call.respondText("ok"); return@post }

      val chatId = msg.chat.id
      val text = msg.text ?: ""

      if (text.startsWith("/start")) {
        val hello = """
                    Привет! 🤍
                    Я Лютик, маленькая собачка и главный помощник Саши Комлевой из @hlebsasha_travel.
                    Хочешь получить Гайд с ТОП-20 проверенными отелями — без рекламы?
                    Жми ниже и забирай подарок 👇
                """.trimIndent()
        val markup = InlineKeyboardMarkup(
          listOf(
            listOf(InlineKeyboardButton("Забрать гайд", callback_data = "start_get_guide"))
          )
        )
        sendMsg(chatId, hello, markup)
        call.respondText("ok"); return@post
      }

      // fallback
      sendMsg(chatId, "Вы написали: $text")
      call.respondText("ok")
    }
  }
}

private fun env(key: String, default: String? = null): String =
  System.getenv(key) ?: default ?: error("Missing env $key")
