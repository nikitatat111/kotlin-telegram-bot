package ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import storage.ChatMessage

class OpenAiService(
  private val http: HttpClient,
  private val apiKey: String
) {
  suspend fun answer(
    history: List<ChatMessage>,
    userText: String
  ): String {
    val input = buildList {
      add(
        mapOf(
          "role" to "system",
          "content" to listOf(
            mapOf(
              "type" to "input_text",
              "text" to """
                Ты — Лютик, маленькая собачка и помощник Саши Комлевой из travel-проекта.
                Отвечай тепло, вежливо, коротко и по делу.
                Не выдумывай факты.
                Если вопрос про подбор тура — мягко предлагай написать Саше.
                Если вопрос неясный — задай один короткий уточняющий вопрос.
                Если не знаешь ответ — честно скажи об этом.
              """.trimIndent()
            )
          )
        )
      )

      history.forEach {
        add(
          mapOf(
            "role" to it.role,
            "content" to listOf(
              mapOf(
                "type" to "input_text",
                "text" to it.text
              )
            )
          )
        )
      }

      add(
        mapOf(
          "role" to "user",
          "content" to listOf(
            mapOf(
              "type" to "input_text",
              "text" to userText
            )
          )
        )
      )
    }

    val response = http.post("https://api.openai.com/v1/responses") {
      contentType(ContentType.Application.Json)
      header(HttpHeaders.Authorization, "Bearer $apiKey")
      setBody(
        mapOf(
          "model" to "gpt-5.4",
          "input" to input
        )
      )
    }.body<OpenAiResponse>()

    return response.output
      .flatMap { it.content.orEmpty() }
      .firstOrNull { it.type == "output_text" }
      ?.text
      ?.trim()
      ?.takeIf { it.isNotBlank() }
      ?: "Извини, я не смог сейчас сформировать ответ 🤍"
  }
}

@Serializable
data class OpenAiResponse(
  val output: List<OpenAiOutputItem> = emptyList()
)

@Serializable
data class OpenAiOutputItem(
  val type: String,
  val content: List<OpenAiTextContent>? = null
)

@Serializable
data class OpenAiTextContent(
  val type: String,
  val text: String? = null
)