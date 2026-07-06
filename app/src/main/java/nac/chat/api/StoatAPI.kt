package nac.chat.api

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import nac.chat.BuildConfig
import nac.chat.NACApplication
import nac.chat.api.StoatAPI.initialize
import nac.chat.api.internals.Members
import nac.chat.api.realtime.DisconnectionState
import nac.chat.api.realtime.RealtimeSocket
import nac.chat.api.routes.user.fetchSelf
import nac.chat.api.unreads.Unreads
import nac.chat.core.model.data.STOAT_BASE
import nac.chat.core.model.schemas.AutumnResource
import nac.chat.core.model.schemas.ChannelType
import nac.chat.core.model.schemas.Emoji
import nac.chat.core.model.schemas.Message
import nac.chat.core.model.schemas.Server
import nac.chat.core.model.schemas.User
import nac.chat.core.model.schemas.ChannelWebhook
import nac.chat.core.model.util.ChannelVoiceState
import nac.chat.persistence.Database
import nac.chat.persistence.SqlStorage
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.chuckerteam.chucker.api.RetentionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.SocketException
import nac.chat.core.model.schemas.Channel as ChannelSchema

fun String.api(): String {
    return "$STOAT_BASE$this"
}

fun buildUserAgent(accessMethod: String = "Ktor"): String {
    return "$accessMethod StoatForAndroid/${BuildConfig.VERSION_NAME} " +
            "${BuildConfig.APPLICATION_ID} Android/${android.os.Build.VERSION.SDK_INT} " +
            "(${android.os.Build.MANUFACTURER} ${android.os.Build.DEVICE}) Kotlin/${KotlinVersion.CURRENT}"
}

@OptIn(ExperimentalSerializationApi::class)
val StoatJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@OptIn(ExperimentalSerializationApi::class)
val StoatCbor = Cbor {
    ignoreUnknownKeys = true
}

val StoatHttp = HttpClient(OkHttp) {
    install(DefaultRequest)
    install(ContentNegotiation) {
        json(StoatJson)
    }

    install(WebSockets)

    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 5)
        retryOnException(maxRetries = 5)

        modifyRequest { request ->
            request.headers.append("x-retry-count", retryCount.toString())
        }

        exponentialDelay()
    }

    install(Logging) { level = LogLevel.INFO }

    val chuckerCollector = ChuckerCollector(
        context = NACApplication.instance,
        showNotification = true,
        retentionPeriod = RetentionManager.Period.ONE_DAY
    )

    val chuckerInterceptor = ChuckerInterceptor.Builder(NACApplication.instance)
        .collector(chuckerCollector)
        .maxContentLength(250_000L)
        .redactHeaders(StoatAPI.TOKEN_HEADER_NAME)
        .alwaysReadResponseBody(true)
        .createShortcut(false)
        .build()

    engine {
        addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .apply {
                    if (chain.request().headers[StoatAPI.TOKEN_HEADER_NAME] == null) {
                        header(StoatAPI.TOKEN_HEADER_NAME, StoatAPI.sessionToken)
                    }
                }
                .build()
            val response = chain.proceed(request)

            // Server rejects this client's app version - surface the blocking
            // update screen regardless of which call site this was.
            if (response.code == 426) {
                runCatching {
                    val body = response.peekBody(Long.MAX_VALUE).string()
                    val minVersion = StoatJson.parseToJsonElement(body)
                        .jsonObject["min_version"]?.jsonPrimitive?.content
                    UpgradeRequiredState.minVersion.value = minVersion
                }
                UpgradeRequiredState.isRequired.value = true
            }

            response
        }
        addInterceptor(chuckerInterceptor)
    }

    defaultRequest {
        url(STOAT_BASE)
        header("User-Agent", buildUserAgent())
        // Real app build version, separate from the free-text User-Agent string -
        // the server uses this to reject clients below its configured minimum.
        header("X-Client-Version", BuildConfig.VERSION_NAME)
    }
}

val mainHandler = Handler(Looper.getMainLooper())

object StoatAPI {
    const val TOKEN_HEADER_NAME = "x-session-token"

    val userCache = mutableStateMapOf<String, User>()
    val serverCache = mutableStateMapOf<String, Server>()
    val channelCache = mutableStateMapOf<String, ChannelSchema>()
    val webhookCache = mutableStateMapOf<String, ChannelWebhook>()
    val emojiCache = mutableStateMapOf<String, Emoji>()
    val messageCache = mutableStateMapOf<String, Message>()
    val voiceStateCache = mutableStateMapOf<String, ChannelVoiceState>()

    val members = Members()

    val unreads = Unreads()

    var selfId: String? = null

    var sessionToken: String = ""
        private set
    var sessionId: String = ""
        private set

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    val realtimeContext = newSingleThreadContext("RealtimeContext")
    val wsFrameChannel = MutableSharedFlow<Any>(
        replay = 0,
        extraBufferCapacity = Int.MAX_VALUE,
    )

    private var socketCoroutine: Job? = null

    // Guards the check-then-act around socketCoroutine below. Reading and
    // writing a plain var isn't atomic across dispatchers/threads -- two
    // near-simultaneous callers (e.g. login's initial connect racing an
    // ON_RESUME-triggered reconnect) could each see socketCoroutine as
    // inactive before either has assigned its own job, both slipping past
    // the guard and each opening a real, separate socket.
    private val connectMutex = Mutex()

    // Tracks consecutive quick disconnects so a socket that's failing
    // repeatedly (e.g. a proxy/network hiccup) backs off instead of
    // reconnecting instantly every time -- same intent as revolt.js's
    // capped-exponential-backoff Controller, which nac-android never had.
    private var consecutiveQuickFailures: Int = 0
    private const val QUICK_FAILURE_WINDOW_MS = 5_000L
    private const val MAX_BACKOFF_MS = 30_000L

    private var openForLocalHydration = true

    fun setSessionHeader(token: String) {
        sessionToken = token
    }

    fun setSessionId(id: String) {
        sessionId = id
    }

    suspend fun loginAs(token: String) {
        setSessionHeader(token)
        fetchSelf()
        startSocketOps()
        unreads.sync()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun connectWS() {
        // The whole test-and-set (check + backoff calc + job assignment)
        // happens under connectMutex as one critical section. Splitting the
        // check and the assignment into separate lock acquisitions would
        // reopen the exact same race it's meant to close -- another caller
        // could slip in between them and also see no active job yet.
        connectMutex.withLock {
            // A connection attempt is already in flight -- don't tear it down
            // and start a fresh one underneath it. Without this guard,
            // overlapping callers (e.g. ON_RESUME firing more than once
            // before the previous attempt settles to Connected) each
            // independently call RealtimeSocket.connect(), which closes
            // whatever socket is currently being established before opening
            // another -- a self-sustaining reconnect thrash where the socket
            // never gets a chance to stay up.
            if (socketCoroutine?.isActive == true) {
                Log.d("RevoltAPI", "A socket connection attempt is already in progress. Skipping.")
                return@withLock
            }

            // Capped exponential backoff, proportional to how many *quick*
            // failures happened in a row (a connection that stayed up for a
            // while before dropping doesn't count -- only ones that die
            // within QUICK_FAILURE_WINDOW_MS of connecting). Without this, a
            // socket that keeps dying immediately after connecting retries
            // instantly forever, each attempt re-triggering the expensive
            // full-channel unread resync on "Reconnected" before the
            // previous one even lands.
            val backoffMs = if (consecutiveQuickFailures > 0) {
                minOf(1000L * (1L shl (consecutiveQuickFailures - 1)), MAX_BACKOFF_MS)
            } else {
                0L
            }

            socketCoroutine = CoroutineScope(Dispatchers.IO).launch {
                if (backoffMs > 0) {
                    Log.d(
                        "RevoltAPI",
                        "Backing off ${backoffMs}ms before reconnecting " +
                            "(quick failure #$consecutiveQuickFailures)"
                    )
                    delay(backoffMs)
                }

                val connectStartedAt = System.currentTimeMillis()
                try {
                    withContext(realtimeContext) {
                        try {
                            RealtimeSocket.connect(sessionToken)
                        } catch (e: SocketException) {
                            Log.d(
                                "RevoltAPI",
                                "Socket closed, probably no big deal /// " + e.message
                            )
                            RealtimeSocket.updateDisconnectionState(DisconnectionState.Disconnected)
                        } catch (e: Exception) {
                            Log.e("RevoltAPI", "WebSocket error", e)
                            RealtimeSocket.updateDisconnectionState(DisconnectionState.Disconnected)
                        }
                    }
                } catch (e: Exception) {
                    try {
                        if (e is InterruptedException) {
                            Log.d("RevoltAPI", "Socket interrupted")
                        } else {
                            Log.e("RevoltAPI", "WebSocket error", e)
                        }
                        RealtimeSocket.updateDisconnectionState(DisconnectionState.Disconnected)
                    } catch (e: Exception) {
                        Sentry.captureMessage("Error in socket error handling: $e")
                    }
                } finally {
                    consecutiveQuickFailures =
                        if (System.currentTimeMillis() - connectStartedAt < QUICK_FAILURE_WINDOW_MS) {
                            consecutiveQuickFailures + 1
                        } else {
                            0
                        }
                }
            }
        }
    }

    private suspend fun startSocketOps() {
        connectWS()

        // Send a ping every roughly 30 seconds else the socket dies
        // Same interval as the web clients (/revolt.js)
        // Note: This will run even if the socket is closed (sendPing will just exit early)
        mainHandler.post(object : Runnable {
            override fun run() {
                runBlocking {
                    RealtimeSocket.sendPing()
                }
                mainHandler.postDelayed(this, 30 * 1000)
            }
        })
    }

    suspend fun initialize() {
        if (sessionToken != "") {
            fetchSelf()
        }
    }

    /**
     * Returns true if the user is logged in and the current user has been fetched at least once.
     * Call [initialize] to fetch the current user first, else this will return false.
     */
    fun isLoggedIn(): Boolean {
        return selfId != null
    }

    /**
     * Clears the API client's state completely.
     */
    fun logout() {
        selfId = null
        sessionToken = ""
        sessionId = ""

        userCache.clear()
        serverCache.clear()
        channelCache.clear()
        emojiCache.clear()
        messageCache.clear()

        members.clear()
        unreads.clear()

        socketCoroutine?.cancel()
        mainHandler.removeCallbacksAndMessages(null)

        clearPersistentCache()
    }

    /**
     * Checks if a session token is valid.
     */
    suspend fun checkSessionToken(token: String): Boolean {
        return try {
            setSessionHeader(token)
            fetchSelf()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Hydrate caches from a local database.
     */
    fun hydrateFromPersistentCache() {
        if (!openForLocalHydration) {
            Log.w("RevoltAPI", "Hydration is closed, but was called")
            // Stale data is worst case, let's track it even in prod
            Sentry.captureMessage("Local hydration called twice or after real data was fetched")
            return
        }

        val db = Database(SqlStorage.driver)

        val channels = db.channelQueries.selectAll().executeAsList().map {
            ChannelSchema(
                id = it.id,
                channelType = try {
                    ChannelType.valueOf(it.channelType)
                } catch (e: Exception) {
                    null
                },
                user = it.userId,
                name = it.name,
                owner = it.owner,
                description = it.description,
                recipients = selfId?.let { selfId ->
                    it.userId?.let { u -> listOf(u, selfId) }
                } ?: it.userId?.let { u -> listOf(u) },
                icon = AutumnResource(
                    id = it.iconId,
                ),
                server = it.server,
                lastMessageID = it.lastMessageId,
                active = it.active == 1L,
                nsfw = it.nsfw == 1L
            )
        }
        channelCache.clear()
        channelCache.putAll(channels.associateBy { it.id!! })

        val servers = db.serverQueries.selectAll().executeAsList().map {
            Server(
                id = it.id,
                owner = it.owner,
                name = it.name,
                description = it.description,
                icon = AutumnResource(
                    id = it.iconId,
                ),
                banner = AutumnResource(
                    id = it.bannerId,
                ),
                flags = it.flags,
                channels = channels
                    .filter { c -> c.server == it.id }
                    .filterNot { c -> c.id == null }
                    .map { c -> c.id!! },
            )
        }
        serverCache.clear()
        serverCache.putAll(servers.associateBy { it.id!! })

        openForLocalHydration = false
    }

    /**
     * Clear the local caching database.
     */
    private fun clearPersistentCache() {
        val db = Database(SqlStorage.driver)
        db.serverQueries.clear()
        db.channelQueries.clear()
    }

    /**
     * Marks database as hydrated (after real data was fetched, for example).
     */
    fun closeHydration() {
        openForLocalHydration = false
    }
}

@Serializable
data class StoatAPIError(val type: String)

@Serializable
data class RateLimitResponse(@SerialName("retry_after") val retryAfter: Int) {
    fun toException(): HitRateLimitException {
        return HitRateLimitException(retryAfter)
    }
}

internal const val NO_RETRY_AFTER = Int.MIN_VALUE

class HitRateLimitException(retryAfter: Int = NO_RETRY_AFTER) :
    Exception(if (retryAfter == NO_RETRY_AFTER) "Hit rate limit" else "Hit rate limit, retry after ${retryAfter}ms")