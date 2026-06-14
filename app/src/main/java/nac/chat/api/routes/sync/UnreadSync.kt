package nac.chat.api.routes.sync

import nac.chat.api.StoatHttp
import nac.chat.api.StoatJson
import nac.chat.api.api
import nac.chat.core.model.schemas.ChannelUnreadResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.builtins.ListSerializer

suspend fun syncUnreads(): List<ChannelUnreadResponse> {
    val response = StoatHttp.get("/sync/unreads".api())
        .bodyAsText()

    return StoatJson.decodeFromString(
        ListSerializer(ChannelUnreadResponse.serializer()),
        response
    )
}
