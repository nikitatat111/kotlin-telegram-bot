package storage

interface ChatHistoryRepository {
  suspend fun saveUserMessage(chatId: Long, userId: Long?, text: String)
  suspend fun saveAssistantMessage(chatId: Long, text: String)
  suspend fun lastMessages(chatId: Long, limit: Int): List<ChatMessage>
}

data class ChatMessage(
  val role: String,
  val text: String
)