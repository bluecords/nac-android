package nac.chat.api.routes.account

import nac.chat.api.StoatAPIError
import nac.chat.api.StoatHttp
import nac.chat.api.StoatJson
import nac.chat.core.model.util.RsResult
import nac.chat.api.api
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
data class RegistrationBody(
    val email: String,
    val password: String,
    val invite: String? = null,
    val captcha: String
)

suspend fun register(body: RegistrationBody): RsResult<Unit, StoatAPIError> {
    val response = StoatHttp.post("/auth/account/create".api()) {
        setBody(body)
        contentType(ContentType.Application.Json)
    }

    val responseContent = response.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), responseContent)
        return RsResult.err(error)
    } catch (e: SerializationException) {
        // Not an error
    }

    return RsResult.ok(Unit)
}
