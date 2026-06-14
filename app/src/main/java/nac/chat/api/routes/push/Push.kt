package nac.chat.api.routes.push

import nac.chat.api.StoatHttp
import nac.chat.api.routes.account.WebPushData
import nac.chat.api.api
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

suspend fun subscribePush(
    endpoint: String = "fcm",
    auth: String,
    p256diffieHellman: String? = null,
) {
    val data = WebPushData(
        endpoint = endpoint,
        p256diffieHellman = p256diffieHellman ?: "",
        auth = auth
    )

    StoatHttp.post("/push/subscribe".api()) {
        setBody(data)
        contentType(ContentType.Application.Json)
    }
}

suspend fun unsubscribePush() {
    StoatHttp.post("/push/unsubscribe".api())
}