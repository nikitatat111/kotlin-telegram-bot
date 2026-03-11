package storage

class InMemoryChatHistoryRepository(
  private val maxMessagesPerChat: Int = 20
) : ChatHistoryRepository {

  private val storage = mutableMapOf<Long, ArrayDeque<ChatMessage>>()

  override suspend fun saveUserMessage(
    chatId: Long,
    userId: Long?,
    text: String
  ) {
    val queue = storage.getOrPut(chatId) { ArrayDeque() }
    queue.addLast(ChatMessage(role = "user", text = text))
    trim(queue)
  }

  override suspend fun saveAssistantMessage(
    chatId: Long,
    text: String
  ) {
    val queue = storage.getOrPut(chatId) { ArrayDeque() }
    queue.addLast(ChatMessage(role = "assistant", text = text))
    trim(queue)
  }

  override suspend fun lastMessages(
    chatId: Long,
    limit: Int
  ): List<ChatMessage> {
    return storage[chatId]
      ?.toList()
      ?.takeLast(limit)
      .orEmpty()
  }

  private fun trim(queue: ArrayDeque<ChatMessage>) {
    while (queue.size > maxMessagesPerChat) {
      queue.removeFirst()
    }
  }
}