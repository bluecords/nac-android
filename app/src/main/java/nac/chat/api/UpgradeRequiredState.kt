package nac.chat.api

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Set by the OkHttp interceptor in [StoatHttp] whenever the server responds 426
 * Upgrade Required (client below the server's configured minimum app version),
 * and by the WS gateway's UpgradeRequired error frame. Observed at the top level
 * in MainActivity to show a blocking, non-dismissible update screen - there is no
 * per-route handling, since this can fire on any request.
 */
object UpgradeRequiredState {
    val isRequired = MutableStateFlow(false)
    val minVersion = MutableStateFlow<String?>(null)
}
