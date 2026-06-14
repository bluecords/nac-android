package nac.chat.api.routes.microservices.health

import nac.chat.api.StoatHttp
import nac.chat.api.StoatJson
import nac.chat.core.model.schemas.HealthNotice
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

suspend fun healthCheck(): HealthNotice {
    val response = StoatHttp.get("https://health.revolt.chat/api/health").bodyAsText()
    return StoatJson.decodeFromString(HealthNotice.serializer(), response)
}