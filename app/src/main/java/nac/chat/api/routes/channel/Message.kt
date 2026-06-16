package nac.chat.api.routes.channel

import nac.chat.api.StoatHttp
import nac.chat.api.api
import io.ktor.client.request.delete
import io.ktor.client.request.put
import java.net.URLEncoder

private fun encodeEmoji(emoji: String) = URLEncoder.encode(emoji, "UTF-8")

suspend fun react(channelId: String, messageId: String, emoji: String) {
    StoatHttp.put("/channels/$channelId/messages/$messageId/reactions/${encodeEmoji(emoji)}".api())
}

suspend fun unreact(channelId: String, messageId: String, emoji: String) {
    StoatHttp.delete("/channels/$channelId/messages/$messageId/reactions/${encodeEmoji(emoji)}".api())
}