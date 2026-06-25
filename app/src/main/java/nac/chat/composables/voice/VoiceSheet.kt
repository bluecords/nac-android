package nac.chat.composables.voice

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import nac.chat.R
import nac.chat.services.VoiceCallService
import nac.chat.api.StoatAPI
import nac.chat.api.routes.misc.Root
import nac.chat.api.routes.misc.getRootRoute
import nac.chat.api.routes.voice.joinCall
import io.livekit.android.LiveKit
import io.livekit.android.compose.local.RoomLocal
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.util.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

class VoiceSheetViewModel(private val state: SavedStateHandle) : ViewModel() {
    private val _channelId = mutableStateOf(state.get<String>("channelId") ?: "")
    var channelId: String
        get() = _channelId.value
        set(value) {
            _channelId.value = value
            state["channelId"] = value
        }

    // Recreating a brand-new LiveKit Room (and its native audio/RTC resources) on every
    // single join was unreliable - the first join after a clean disconnect would work,
    // but a second join's Room would silently never finish connecting (stuck at
    // CONNECTING, no signaling attempt ever reached the server - confirmed via server-side
    // logs showing zero connection activity for the retry). Room.disconnect() and
    // Room.release() are deliberately separate in the SDK specifically so a Room can be
    // disconnected and later reconnected without releasing its native resources - that's
    // the supported pattern. Keep one Room alive for this ViewModel's lifetime (it already
    // survives across rejoins) and pass it into RoomScope as passedRoom instead of letting
    // a fresh Room get created and released on every join. See nac-android#20.
    private var room: Room? = null
    fun ensureRoom(context: Context): Room {
        return room ?: LiveKit.create(context.applicationContext).also { room = it }
    }

    var voiceLkNode by mutableStateOf("")
    private val _voiceToken = mutableStateOf(state.get<String>("voiceToken") ?: "")
    var voiceToken: String
        get() = _voiceToken.value
        private set(value) {
            _voiceToken.value = value
            state["voiceToken"] = value
        }

    var errorResource by mutableStateOf<Int?>(null)
        private set

    fun setConnectTimeout() {
        errorResource = R.string.voice_error_connect_timeout
    }

    suspend fun getVoiceToken() {
        errorResource = null

        val root: Root
        try {
            root = getRootRoute()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Could not get root route\n" + e.asLog() }
            errorResource = R.string.voice_error_generic
            return
        }

        val lk = root.features.livekit

        if (lk == null) {
            logcat(LogPriority.ERROR) {
                IllegalStateException("LiveKit is not supported by this API version!").asLog()
            }
            errorResource = R.string.voice_error_not_supported
            return
        }

        if (lk.nodes.isEmpty()) {
            logcat(LogPriority.ERROR) { IllegalStateException("No LiveKit nodes available!").asLog() }
            errorResource = R.string.voice_error_no_nodes
            return
        }

        val node = lk.nodes.random()
        try {
            val joined = joinCall(channelId, node.name)
            voiceLkNode = joined.url
            voiceToken = joined.token
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Could not get LiveKit token\n" + e.asLog() }
            errorResource = R.string.voice_error_generic
            return
        }
    }

    override fun onCleared() {
        room?.release()
        room = null
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoiceSheet(
    channelId: String,
    onDisconnect: () -> Unit,
    viewModel: VoiceSheetViewModel = viewModel()
) {
    LaunchedEffect(channelId) {
        viewModel.channelId = channelId
        viewModel.getVoiceToken()
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    RoomScope(
        url = viewModel.voiceLkNode,
        token = viewModel.voiceToken,
        audio = true,
        video = false,
        connect = true,
        passedRoom = viewModel.ensureRoom(context),
    ) {
        val room = RoomLocal.current
        val roomState by room::state.flow.collectAsState()
        val isMicOn by room.localParticipant::isMicrophoneEnabled.flow.collectAsState()
        val isCameraOn by room.localParticipant::isCameraEnabled.flow.collectAsState()
        val isScreenShared by room.localParticipant::isScreenShareEnabled.flow.collectAsState()
        val activeSpeakers by room::activeSpeakers.flow.collectAsState()
        val trackRefs by rememberTracks(passedRoom = room)

        Column {
            var showStatus by remember { mutableStateOf(true) }
                    LaunchedEffect(roomState) {
                        if (roomState == Room.State.CONNECTED) {
                            delay(1000)
                            showStatus = false
                        } else {
                            showStatus = true
                        }
                    }

                    LaunchedEffect(roomState) {
                        if (roomState == Room.State.CONNECTING) {
                            delay(20_000)
                            // Show the timeout error for a moment before tearing down the
                            // UI - calling onDisconnect() in the same instant as setting the
                            // error meant the error text never got a chance to render, so a
                            // failed rejoin looked like it silently dumped you back out with
                            // no explanation. See nac-android#20.
                            viewModel.setConnectTimeout()
                            delay(3_000)
                            onDisconnect()
                        }
                    }

                    LaunchedEffect(roomState) {
                        val intent = Intent(context, VoiceCallService::class.java)
                        when (roomState) {
                            Room.State.CONNECTED -> context.startForegroundService(intent)
                            Room.State.DISCONNECTED -> context.stopService(intent)
                            else -> {}
                        }
                    }

                    AnimatedVisibility(
                        visible = showStatus,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = LinearOutSlowInEasing
                            )
                        ) +
                                expandVertically(
                                    expandFrom = Alignment.Top,
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = LinearOutSlowInEasing
                                    )
                                ),
                        exit = fadeOut(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = LinearOutSlowInEasing
                            )
                        ) +
                                slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = LinearOutSlowInEasing
                                    )
                                ) +
                                shrinkVertically(
                                    shrinkTowards = Alignment.Top,
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = LinearOutSlowInEasing
                                    )
                                )
                    ) {
                        AnimatedContent(
                            roomState
                        ) { roomState ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    8.dp,
                                    Alignment.CenterHorizontally
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                CompositionLocalProvider(
                                    LocalContentColor provides when (roomState) {
                                        Room.State.CONNECTING, Room.State.RECONNECTING -> MaterialTheme.colorScheme.onSurfaceVariant
                                        Room.State.CONNECTED -> MaterialTheme.colorScheme.primary
                                        Room.State.DISCONNECTED -> MaterialTheme.colorScheme.error
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            when (roomState) {
                                                Room.State.CONNECTING -> R.drawable.ic_sprint_24dp
                                                Room.State.CONNECTED -> R.drawable.ic_wifi_tethering_24dp
                                                Room.State.DISCONNECTED -> R.drawable.ic_wifi_tethering_error_24dp
                                                Room.State.RECONNECTING -> R.drawable.ic_sprint_24dp
                                            }
                                        ),
                                        contentDescription = null
                                    )
                                    Text(
                                        text = when (roomState) {
                                            Room.State.CONNECTING -> stringResource(R.string.voice_status_connecting)
                                            Room.State.CONNECTED -> stringResource(R.string.voice_status_connected)
                                            Room.State.DISCONNECTED -> stringResource(R.string.voice_status_disconnected)
                                            Room.State.RECONNECTING -> stringResource(R.string.voice_status_reconnecting)
                                        }
                                    )
                                }
                            }
                        }
                    }

            NacCallLayout(
                room = room,
                channelId = viewModel.channelId,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .weight(1f)
            )

            AnimatedVisibility(viewModel.errorResource != null) {
                viewModel.errorResource?.let { resId ->
                    Text(
                        text = stringResource(resId),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.padding(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 16.dp,
                ),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp,
                )
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            room.localParticipant.setMicrophoneEnabled(!isMicOn)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMicOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isMicOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Icon(
                        painter = if (isMicOn) painterResource(R.drawable.ic_mic_24dp) else painterResource(
                            R.drawable.ic_mic_off_24dp
                        ),
                        contentDescription = "TODO change this string to res"
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        scope.launch {
                            room.localParticipant.setCameraEnabled(!isCameraOn)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCameraOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isCameraOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Icon(
                        painter = if (isCameraOn) painterResource(R.drawable.ic_videocam_24dp) else painterResource(
                            R.drawable.ic_videocam_off_24dp
                        ),
                        contentDescription = "TODO change this string to res"
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        scope.launch {
                            room.localParticipant.setScreenShareEnabled(!isScreenShared)
                        }
                    },
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mobile_share_24px),
                        contentDescription = "TODO change this string to res"
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        // Explicitly disconnect and wait for it before tearing down the UI,
                        // instead of relying on RoomScope's automatic dispose-time cleanup -
                        // that left the server's side of the session in an unclear state,
                        // blocking a clean rejoin even after the stuck-notification bug above
                        // was fixed.
                        scope.launch {
                            room.disconnect()
                            onDisconnect()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(2f)
                        .height(64.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_call_end_24dp__fill),
                        contentDescription = stringResource(R.string.voice_action_disconnect)
                    )
                }
            }
        }
    }
}
