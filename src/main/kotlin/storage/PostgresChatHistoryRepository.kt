package storage

import javax.sql.DataSource

class PostgresChatHistoryRepository(
  private val dataSource: DataSource
) : ChatHistoryRepository {

  override suspend fun saveUserMessage(
    chatId: Long,
    userId: Long?,
    text: String
  ) {
    dataSource.connection.use { connection ->
      connection.prepareStatement(
        """
        insert into tg_messages(chat_id, user_id, role, text, created_at)
        values (?, ?, ?, ?, now())
        """.trimIndent()
      ).use { stmt ->
        stmt.setLong(1, chatId)
        if (userId != null) stmt.setLong(2, userId) else stmt.setNull(2, java.sql.Types.BIGINT)
        stmt.setString(3, "user")
        stmt.setString(4, text)
        stmt.executeUpdate()
      }
    }
  }

  override suspend fun saveAssistantMessage(
    chatId: Long,
    text: String
  ) {
    dataSource.connection.use { connection ->
      connection.prepareStatement(
        """
        insert into tg_messages(chat_id, user_id, role, text, created_at)
        values (?, null, ?, ?, now())
        """.trimIndent()
      ).use { stmt ->
        stmt.setLong(1, chatId)
        stmt.setString(2, "assistant")
        stmt.setString(3, text)
        stmt.executeUpdate()
      }
    }
  }

  override suspend fun lastMessages(
    chatId: Long,
    limit: Int
  ): List<ChatMessage> {
    dataSource.connection.use { connection ->
      connection.prepareStatement(
        """
        select role, text
        from (
          select role, text, created_at
          from tg_messages
          where chat_id = ?
          order by created_at desc
          limit ?
        ) t
        order by created_at asc
        """.trimIndent()
      ).use { stmt ->
        stmt.setLong(1, chatId)
        stmt.setInt(2, limit)

        stmt.executeQuery().use { rs ->
          val result = mutableListOf<ChatMessage>()
          while (rs.next()) {
            result += ChatMessage(
              role = rs.getString("role"),
              text = rs.getString("text")
            )
          }
          return result
        }
      }
    }
  }
}