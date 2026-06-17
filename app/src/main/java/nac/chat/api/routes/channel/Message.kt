package nac.chat.api.routes.channel

import nac.chat.api.StoatAPI
import nac.chat.api.StoatHttp
import nac.chat.api.StoatJson
import nac.chat.api.api
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.net.URLEncoder
import kotlinx.serialization.Serializable

private fun encodeEmoji(emoji: String) = URLEncoder.encode(emoji, "UTF-8")

suspend fun react(channelId: String, messageId: String, emoji: String) {
    StoatHttp.put("/channels/$channelId/messages/$messageId/reactions/${encodeEmoji(emoji)}".api())
}

suspend fun unreact(channelId: String, messageId: String, emoji: String) {
    StoatHttp.delete("/channels/$channelId/messages/$messageId/reactions/${encodeEmoji(emoji)}".api())
}

@Serializable
private data class MoveMessageBody(
    val messageId: String,
    val sourceChannelId: String,
    val targetChannelId: String
)

suspend fun moveMessage(
    messageId: String,
    sourceChannelId: String,
    targetChannelId: String
): Result<Unit> {
    return try {
        val response = StoatHttp.post("https://community.nac.social/bot/api/move-message") {
            header("x-session-token", StoatAPI.sessionToken)
            contentType(ContentType.Application.Json)
            setBody(
                StoatJson.encodeToString(
                    MoveMessageBody.serializer(),
                    MoveMessageBody(messageId, sourceChannelId, targetChannelId)
                )
            )
        }
        if (response.status == HttpStatusCode.OK) {
            Result.success(Unit)
        } else {
            val body = response.bodyAsText()
            Result.failure(Exception(body.ifEmpty { response.status.description }))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}