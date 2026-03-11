package telegram

import kotlinx.serialization.Serializable

@Serializable
data class Chat(val id: Long)

@Serializable
data class From(
  val id: Long,
  val username: String? = null
)

@Serializable
data class Message(
  val message_id: Long,
  val chat: Chat,
  val from: From? = null,
  val text: String? = null
)

@Serializable
data class CallbackQuery(
  val id: String,
  val from: From,
  val message: Message? = null,
  val data: String? = null
)

@Serializable
data class InlineKeyboardButton(
  val text: String,
  val callback_data: String? = null,
  val url: String? = null
)

@Serializable
data class InlineKeyboardMarkup(
  val inline_keyboard: List<List<InlineKeyboardButton>>
)

@Serializable
data class Update(
  val update_id: Long,
  val message: Message? = null,
  val callback_query: CallbackQuery? = null
)

@Serializable
data class TgSendMessageReq(
  val chat_id: Long,
  val text: String,
  val parse_mode: String? = "HTML",
  val reply_markup: InlineKeyboardMarkup? = null
)

@Serializable
data class TgSendDocumentReq(
  val chat_id: Long,
  val document: String,
  val caption: String? = null,
  val parse_mode: String? = "HTML"
)

@Serializable
data class TgAnswerCbReq(
  val callback_query_id: String
)

@Serializable
data class TgUser(val id: Long)

@Serializable
data class TgChatMember(
  val status: String,
  val user: TgUser
)

@Serializable
data class TgGetMemberResp(
  val ok: Boolean,
  val result: TgChatMember? = null,
  val description: String? = null
)