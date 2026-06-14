package nac.chat.api.routes.account

import nac.chat.api.StoatAPIError
import nac.chat.api.StoatHttp
import nac.chat.api.StoatJson
import nac.chat.api.api
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChangeEmailBody(
    val email: String,
    @SerialName("current_password") val currentPassword: String
)

suspend fun changeEmail(newEmail: String, currentPassword: String) {
    val res = StoatHttp.patch("/auth/account/change/email".api()) {
        setBody(ChangeEmailBody(newEmail, currentPassword))
        contentType(ContentType.Application.Json)
    }
    if (!res.status.isSuccess()) {
        runCatching { StoatJson.decodeFromString(StoatAPIError.serializer(), res.bodyAsText()) }
            .onSuccess { throw Exception(it.type) }

        val errorResponse = res.bodyAsText()
        throw Exception("Failed to change email: $errorResponse")
    }
}

@Serializable
data class ChangePasswordBody(
    val password: String,
    @SerialName("current_password") val currentPassword: String
)

suspend fun changePassword(newPassword: String, currentPassword: String) {
    val res = StoatHttp.patch("/auth/account/change/password".api()) {
        setBody(ChangePasswordBody(newPassword, currentPassword))
        contentType(ContentType.Application.Json)
    }
    if (!res.status.isSuccess()) {
        runCatching { StoatJson.decodeFromString(StoatAPIError.serializer(), res.bodyAsText()) }
            .onSuccess { throw Exception(it.type) }

        val errorResponse = res.bodyAsText()
        throw Exception("Failed to change password: $errorResponse")
    }
}