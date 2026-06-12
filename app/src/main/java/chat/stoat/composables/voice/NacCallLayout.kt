package chat.stoat.composables.voice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import chat.stoat.api.StoatAPI
import chat.stoat.composables.chat.displayNameInChannel
import chat.stoat.composables.generic.UserAvatar
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.util.flow

/**
 * NAC call layout — diverges from upstream's scrolling list.
 *
 *  - Featured window: screen share if active, else the pinned tile, else the
 *    active speaker's camera, else the active speaker's avatar.
 *  - Other cameras render as a thumbnail strip; tap a thumbnail to pin it.
 *  - Voice-only participants get a compact name list, no video real estate.
 */
@Composable
fun NacCallLayout(
    room: Room,
    channelId: String,
    modifier: Modifier = Modifier
) {
    val activeSpeakers by room::activeSpeakers.flow.collectAsState()
    val trackRefs by rememberTracks(passedRoom = room)

    var pinnedIdentity by remember { mutableStateOf<String?>(null) }

    val screenShare = trackRefs.firstOrNull { it.source == Track.Source.SCREEN_SHARE }
    val cameraTracks = trackRefs.filter { it.source == Track.Source.CAMERA }
    val activeSpeakerId = activeSpeakers.firstOrNull()?.identity?.value

    val featured = screenShare
        ?: cameraTracks.firstOrNull { it.participant.identity?.value == pinnedIdentity }
        ?: cameraTracks.firstOrNull { it.participant.identity?.value == activeSpeakerId }
        ?: cameraTracks.firstOrNull()

    val voiceStates = StoatAPI.voiceStateCache[channelId]?.participants ?: emptyList()

    // Two-person video call: remote fills the screen, own camera floats as a
    // small corner tile (PIP). Any third participant, screen share, or larger
    // call falls back to the featured + strip + list layout.
    val localCamera = cameraTracks.firstOrNull { it.participant == room.localParticipant }
    val remoteCameras = cameraTracks.filter { it.participant != room.localParticipant }
    val pipMode = screenShare == null && remoteCameras.size == 1 && voiceStates.size <= 2

    if (pipMode) {
        Box(modifier = modifier.fillMaxSize()) {
            VideoTrackView(
                trackReference = remoteCameras.first(),
                room = room,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
            if (localCamera != null) {
                VideoTrackView(
                    trackReference = localCamera,
                    room = room,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .width(110.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Featured window: a bit over half the call screen, leaving room for
        // the thumbnail strip and participant list below.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            if (featured != null) {
                VideoTrackView(
                    trackReference = featured,
                    room = room,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Nobody is publishing video: feature the active speaker
                val focusId = activeSpeakerId ?: voiceStates.firstOrNull()?.id
                if (focusId != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        UserAvatar(
                            username = displayNameInChannel(focusId, channelId),
                            userId = focusId,
                            avatar = StoatAPI.userCache[focusId]?.avatar,
                            allowAnimation = true,
                            size = 96.dp
                        )
                        Text(
                            text = displayNameInChannel(focusId, channelId),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        val thumbnails = cameraTracks.filter { it != featured }
        if (thumbnails.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(thumbnails.size) { index ->
                    val track = thumbnails[index]
                    VideoTrackView(
                        trackReference = track,
                        room = room,
                        modifier = Modifier
                            .width(168.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                pinnedIdentity = track.participant.identity?.value
                            }
                    )
                }
            }
        }

        // Voice-only participants: names and status icons, no tiles
        val voiceOnly = voiceStates.filter { state ->
            cameraTracks.none { it.participant.identity?.value == state.id }
        }
        if (voiceOnly.isNotEmpty()) {
            LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                items(voiceOnly.size) { index ->
                    val state = voiceOnly[index]
                    VoiceParticipant(
                        state = state,
                        channelId = channelId,
                        speaking = activeSpeakers.any { it.identity?.value == state.id }
                    )
                }
            }
        }
    }
}
