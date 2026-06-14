package nac.chat.api.routes.user

import nac.chat.api.StoatAPIError
import nac.chat.api.StoatHttp
import nac.chat.api.StoatJson
import nac.chat.api.api
import nac.chat.core.model.schemas.Channel
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerializationException

suspend fun openDM(userId: String): Channel {
    val response = StoatHttp.get("/users/$userId/dm".api())
        .bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Error(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    return StoatJson.decodeFromString(Channel.serializer(), response)
}