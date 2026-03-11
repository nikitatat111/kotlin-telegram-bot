package telegram

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

class TelegramClient(
  private val http: HttpClient,
  private val telegramApi: String,
  private val tgToken: String
) {
  suspend fun answerCallbackQuery(callbackQueryId: String) {
    http.post("$telegramApi/bot$tgToken/answerCallbackQuery") {
      contentType(ContentType.Application.Json)
      setBody(TgAnswerCbReq(callback_query_id = callbackQueryId))
    }
  }

  suspend fun sendMessage(
    chatId: Long,
    text: String,
    markup: InlineKeyboardMarkup? = null
  ) {
    http.post("$telegramApi/bot$tgToken/sendMessage") {
      contentType(ContentType.Application.Json)
      setBody(
        TgSendMessageReq(
          chat_id = chatId,
          text = text,
          reply_markup = markup
        )
      )
    }
  }

  suspend fun sendDocument(
    chatId: Long,
    documentUrl: String,
    caption: String? = null
  ) {
    http.post("$telegramApi/bot$tgToken/sendDocument") {
      contentType(ContentType.Application.Json)
      setBody(
        TgSendDocumentReq(
          chat_id = chatId,
          document = documentUrl,
          caption = caption
        )
      )
    }
  }

  suspend fun isSubscribed(
    userId: Long,
    channelRef: String
  ): Boolean {
    val resp = http.get("$telegramApi/bot$tgToken/getChatMember") {
      url {
        parameters.append("chat_id", channelRef)
        parameters.append("user_id", userId.toString())
      }
      headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    val body = resp.bodyAsText()

    return runCatching {
      Json { ignoreUnknownKeys = true }
        .decodeFromString<TgGetMemberResp>(body)
    }.getOrNull()
      ?.result
      ?.status
      .let { status ->
        status == "creator" || status == "administrator" || status == "member"
      }
  }
}