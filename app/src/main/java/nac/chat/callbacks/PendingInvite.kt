package nac.chat.callbacks

import androidx.compose.runtime.mutableStateOf

/**
 * Holds an invite code captured while the user was signed out, so it can be auto-joined
 * once they finish authenticating and land in the app. Mirrors the deep-link pattern used
 * for pending channel notifications. Consumed (and cleared) in ChatRouterScreen.
 *
 * Backed by Compose state because the signed-out UI reads it during composition to decide
 * whether the Create Account entry point exists at all (registration is invite only). A
 * plain field would leave that button stale if the code is set while a screen reading it is
 * already composed.
 */
object PendingInvite {
    private val state = mutableStateOf<String?>(null)

    var code: String?
        get() = state.value
        set(value) {
            state.value = value
        }
}
