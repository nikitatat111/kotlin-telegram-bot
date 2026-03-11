package bot

import ai.OpenAiService
import storage.ChatHistoryRepository

class BotService(
  private val repository: ChatHistoryRepository,
  private val openAiService: OpenAiService
) {
  suspend fun generateReply(
    chatId: Long,
    userId: Long?,
    userText: String
  ): String {
    repository.saveUserMessage(
      chatId = chatId,
      userId = userId,
      text = userText
    )

    val history = repository.lastMessages(chatId, limit = 12)

    val answer = openAiService.answer(
      history = history,
      userText = userText
    )

    repository.saveAssistantMessage(
      chatId = chatId,
      text = answer
    )

    return answer
  }
}