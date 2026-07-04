package nac.chat.api.routes.sponsor

import nac.chat.api.StoatAPI
import nac.chat.api.StoatHttp
import nac.chat.api.StoatJson
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

private const val SPONSOR_CHECKOUT_WEBHOOK =
    "https://automate.bluecords.solutions/webhook/sponsor-checkout"

@Serializable
private data class SponsorCheckoutRequest(
    val nac_user_id: String,
    val tier: String,
    val amount: Double? = null
)

@Serializable
private data class SponsorCheckoutResponse(
    val checkout_url: String
)

/**
 * Start a FossBilling checkout for the given sponsor tier ("2_99", "9_99", or
 * "gift") via the n8n webhook, and return the checkout URL to open in a
 * browser. External link-out, no Play Billing involved (see claude-repo
 * PROJECTS.md "NAC Sponsorship" for why).
 */
suspend fun startSponsorCheckout(tier: String, amount: Double? = null): String {
    val selfId = StoatAPI.selfId ?: throw Exception("Not logged in")

    val response = StoatHttp.post(SPONSOR_CHECKOUT_WEBHOOK) {
        contentType(ContentType.Application.Json)
        setBody(
            StoatJson.encodeToString(
                SponsorCheckoutRequest.serializer(),
                SponsorCheckoutRequest(nac_user_id = selfId, tier = tier, amount = amount)
            )
        )
    }.body<String>()

    return StoatJson.decodeFromString(SponsorCheckoutResponse.serializer(), response).checkout_url
}
