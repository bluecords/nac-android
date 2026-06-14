package nac.chat.api.routes.channel

import nac.chat.api.StoatHttp
import nac.chat.api.api
import io.ktor.client.request.delete
import io.ktor.client.request.put

suspend fun react(channelId: String, messageId: String, emoji: String) {
    StoatHttp.put("/channels/$channelId/messages/$messageId/reactions/$emoji".api())
}

suspend fun unreact(channelId: String, messageId: String, emoji: String) {
    StoatHttp.delete("/channels/$channelId/messages/$messageId/reactions/$emoji".api())
}