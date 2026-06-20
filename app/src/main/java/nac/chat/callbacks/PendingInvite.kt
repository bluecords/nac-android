package nac.chat.callbacks

/**
 * Holds an invite code captured while the user was signed out, so it can be auto-joined
 * once they finish authenticating and land in the app. Mirrors the deep-link pattern used
 * for pending channel notifications. Consumed (and cleared) in ChatRouterScreen.
 */
object PendingInvite {
    @Volatile
    var code: String? = null
}
