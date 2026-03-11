package app

import ai.OpenAiService
import bot.BotService
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import storage.ChatHistoryRepository
import storage.InMemoryChatHistoryRepository
import telegram.InlineKeyboardButton
import telegram.InlineKeyboardMarkup
import telegram.TelegramClient
import telegram.Update
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

fun Application.module() {
  install(ContentNegotiation) {
    json(Json { ignoreUnknownKeys = true })
  }

  val tgToken = env("TELEGRAM_TOKEN")
  val telegramApi = env("TELEGRAM_API", "https://api.telegram.org")

  val openAiApiKey = env("OPENAI_API_KEY")

  val guideUrl = env("GUIDE_URL")
  val channelUsername = env("CHANNEL_USERNAME")
  val channelRef = if (channelUsername.startsWith("@")) channelUsername else "@$channelUsername"
  val pickTourUrl = env("PICK_TOUR_URL", "https://t.me/$channelUsername")

  val http = HttpClient(Java) {
    install(ClientContentNegotiation) {
      json(Json { ignoreUnknownKeys = true })
    }
  }

  val telegramClient = TelegramClient(
    http = http,
    telegramApi = telegramApi,
    tgToken = tgToken
  )

  val repository: ChatHistoryRepository = InMemoryChatHistoryRepository()

  val openAiService = OpenAiService(
    http = http,
    apiKey = openAiApiKey
  )

  val botService = BotService(
    repository = repository,
    openAiService = openAiService
  )

  routing {
    get("/") {
      call.respondText("ok")
    }

    post("/tg-webhook") {
      val update = call.receive<Update>()

      update.callback_query?.let { cq ->
        val chatId = cq.message?.chat?.id ?: run {
          call.respondText("ok")
          return@post
        }
        val userId = cq.from.id

        when (cq.data) {
          "start_get_guide" -> {
            telegramClient.answerCallbackQuery(cq.id)

            val text = """
              Отлично! 🎁
              Чтобы получить гайд, подпишись на канал Саши:
              👉 https://t.me/$channelUsername
              После подписки нажми кнопку ниже.
            """.trimIndent()

            val markup = InlineKeyboardMarkup(
              inline_keyboard = listOf(
                listOf(InlineKeyboardButton("Подписаться", url = "https://t.me/$channelUsername")),
                listOf(InlineKeyboardButton("Я подписан", callback_data = "check_sub"))
              )
            )

            telegramClient.sendMessage(chatId, text, markup)
          }

          "check_sub" -> {
            telegramClient.answerCallbackQuery(cq.id)
            telegramClient.sendMessage(chatId, "Проверяю подписку… секунду ⏳")

            if (telegramClient.isSubscribed(userId, channelRef)) {
              val okText = """
                Супер! ✅
                Подписка есть — держи обещанный подарок 🎁
              """.trimIndent()

              val markup = InlineKeyboardMarkup(
                inline_keyboard = listOf(
                  listOf(InlineKeyboardButton("Получить гайд", callback_data = "send_guide"))
                )
              )

              telegramClient.sendMessage(chatId, okText, markup)
            } else {
              val failText = """
                Похоже, подписка не найдена 😕
                Подпишись на канал и нажми «Я подписан».
              """.trimIndent()

              val markup = InlineKeyboardMarkup(
                inline_keyboard = listOf(
                  listOf(InlineKeyboardButton("Подписаться", url = "https://t.me/$channelUsername")),
                  listOf(InlineKeyboardButton("Я подписан", callback_data = "check_sub"))
                )
              )

              telegramClient.sendMessage(chatId, failText, markup)
            }
          }

          "send_guide" -> {
            telegramClient.answerCallbackQuery(cq.id)
            telegramClient.sendDocument(
              chatId = chatId,
              documentUrl = guideUrl,
              caption = "Твой гайд с ТОП-20 отелями. Приятного планирования ✈️"
            )

            val afterText = """
              А вот и гайд, пользуйся на здоровье 😍
              
              Если захочешь больше — жми кнопку: «Саша, подбери тур», и Саша соберёт варианты лично под твой запрос!
            """.trimIndent()

            val markup = InlineKeyboardMarkup(
              inline_keyboard = listOf(
                listOf(InlineKeyboardButton("Саша, подбери тур", url = pickTourUrl))
              )
            )

            telegramClient.sendMessage(chatId, afterText, markup)
          }
        }

        call.respondText("ok")
        return@post
      }

      val msg = update.message
      if (msg == null) {
        call.respondText("ok")
        return@post
      }

      val chatId = msg.chat.id
      val userId = msg.from?.id
      val text = msg.text?.trim().orEmpty()

      if (text.isBlank()) {
        call.respondText("ok")
        return@post
      }

      if (text.startsWith("/start")) {
        val hello = """
          Привет! 🤍
          Я Лютик, маленькая собачка и главный помощник Саши Комлевой из @hlebsasha_travel.
          Хочешь получить Гайд с ТОП-20 проверенными отелями — без рекламы?
          Жми ниже и забирай подарок 👇
        """.trimIndent()

        val markup = InlineKeyboardMarkup(
          inline_keyboard = listOf(
            listOf(InlineKeyboardButton("Забрать гайд", callback_data = "start_get_guide"))
          )
        )

        telegramClient.sendMessage(chatId, hello, markup)
        call.respondText("ok")
        return@post
      }

      val reply = runCatching {
        botService.generateReply(
          chatId = chatId,
          userId = userId,
          userText = text
        )
      }.getOrElse { e ->
        e.printStackTrace()
        "Сейчас я немного задумался 😅 Попробуй написать ещё раз или сразу нажми сюда: $pickTourUrl"
      }

      telegramClient.sendMessage(chatId, reply)
      call.respondText("ok")
    }
  }
}

private fun env(key: String, default: String? = null): String =
  System.getenv(key) ?: default ?: error("Missing env $key")