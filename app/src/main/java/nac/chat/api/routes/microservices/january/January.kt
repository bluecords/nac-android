package nac.chat.api.routes.microservices.january

import nac.chat.core.model.data.STOAT_PROXY
import java.net.URLEncoder

fun asJanuaryProxyUrl(url: String): String {
    return "$STOAT_PROXY/proxy?url=${URLEncoder.encode(url, "utf-8")}"
}
