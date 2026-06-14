package nac.chat.api.routes.custom

import nac.chat.api.StoatHttp
import nac.chat.api.StoatJson
import nac.chat.api.api
import nac.chat.core.model.schemas.Emoji
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

suspend fun fetchEmoji(id: String): Emoji {
    val response = StoatHttp.get("/custom/emoji/$id".api()).bodyAsText()
    return StoatJson.decodeFromString(
        Emoji.serializer(),
        response
    )
}
