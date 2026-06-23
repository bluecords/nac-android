package nac.chat.screens.settings.channel

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import nac.chat.R
import nac.chat.activities.StoatTweenFloat
import nac.chat.api.StoatAPI
import nac.chat.api.routes.channel.fetchSingleChannel
import nac.chat.api.routes.channel.setChannelPermissions
import nac.chat.composables.generic.ListHeader
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

private enum class OverrideState { Allow, Neutral, Deny }

private data class PermissionEntry(
    val heading: Int? = null,
    val bit: Long,
    val title: Int,
    val description: Int
)

private val PERMISSION_ENTRIES = listOf(
    PermissionEntry(R.string.channel_permission_heading_admin, 1L shl 0, R.string.channel_permission_manage_channel, R.string.channel_permission_manage_channel_description),
    PermissionEntry(bit = 1L shl 2, title = R.string.channel_permission_manage_permissions, description = R.string.channel_permission_manage_permissions_description),
    PermissionEntry(R.string.channel_permission_heading_channels, 1L shl 20, R.string.channel_permission_view_channel, R.string.channel_permission_view_channel_description),
    PermissionEntry(bit = 1L shl 21, title = R.string.channel_permission_read_message_history, description = R.string.channel_permission_read_message_history_description),
    PermissionEntry(bit = 1L shl 22, title = R.string.channel_permission_send_message, description = R.string.channel_permission_send_message_description),
    PermissionEntry(bit = 1L shl 23, title = R.string.channel_permission_manage_messages, description = R.string.channel_permission_manage_messages_description),
    PermissionEntry(bit = 1L shl 24, title = R.string.channel_permission_manage_webhooks, description = R.string.channel_permission_manage_webhooks_description),
    PermissionEntry(bit = 1L shl 25, title = R.string.channel_permission_invite_others, description = R.string.channel_permission_invite_others_description),
    PermissionEntry(R.string.channel_permission_heading_messaging, 1L shl 26, R.string.channel_permission_send_embeds, R.string.channel_permission_send_embeds_description),
    PermissionEntry(bit = 1L shl 27, title = R.string.channel_permission_upload_files, description = R.string.channel_permission_upload_files_description),
    PermissionEntry(bit = 1L shl 28, title = R.string.channel_permission_masquerade, description = R.string.channel_permission_masquerade_description),
    PermissionEntry(bit = 1L shl 29, title = R.string.channel_permission_react, description = R.string.channel_permission_react_description),
    PermissionEntry(bit = 1L shl 39, title = R.string.channel_permission_bypass_slowmode, description = R.string.channel_permission_bypass_slowmode_description),
    PermissionEntry(R.string.channel_permission_heading_voice, 1L shl 30, R.string.channel_permission_connect, R.string.channel_permission_connect_description),
    PermissionEntry(bit = 1L shl 31, title = R.string.channel_permission_speak, description = R.string.channel_permission_speak_description),
    PermissionEntry(bit = 1L shl 32, title = R.string.channel_permission_video, description = R.string.channel_permission_video_description),
    PermissionEntry(bit = 1L shl 33, title = R.string.channel_permission_mute_members, description = R.string.channel_permission_mute_members_description),
    PermissionEntry(bit = 1L shl 34, title = R.string.channel_permission_deafen_members, description = R.string.channel_permission_deafen_members_description),
    PermissionEntry(bit = 1L shl 35, title = R.string.channel_permission_move_members, description = R.string.channel_permission_move_members_description),
    PermissionEntry(bit = 1L shl 36, title = R.string.channel_permission_listen, description = R.string.channel_permission_listen_description),
    PermissionEntry(R.string.channel_permission_heading_mentions, 1L shl 37, R.string.channel_permission_mention_everyone, R.string.channel_permission_mention_everyone_description),
    PermissionEntry(bit = 1L shl 38, title = R.string.channel_permission_mention_roles, description = R.string.channel_permission_mention_roles_description),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelPermissionEditor(navController: NavController, channelId: String, roleId: String) {
    val channel = StoatAPI.channelCache[channelId]
    val server = channel?.server?.let { StoatAPI.serverCache[it] }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The cached channel can be stale or incomplete (e.g. another client
    // changed permissions, or role_permissions was never fully loaded into
    // this cache entry). setChannelPermissions sends a full-replace
    // allow/deny mask, not a delta - editing from a stale snapshot and
    // saving silently wipes any override this screen didn't load. Always
    // re-fetch the channel fresh before allowing any edits. See nac-android#17.
    var freshLoaded by remember(channelId) { mutableStateOf(false) }
    var loadFailed by remember(channelId) { mutableStateOf(false) }

    LaunchedEffect(channelId) {
        try {
            val fresh = fetchSingleChannel(channelId)
            StoatAPI.channelCache[channelId] = fresh
            freshLoaded = true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.asLog() }
            loadFailed = true
        }
    }

    val initialAllow = if (roleId == "default") {
        channel?.defaultPermissions?.a ?: 0L
    } else {
        channel?.rolePermissions?.get(roleId)?.a ?: 0L
    }
    val initialDeny = if (roleId == "default") {
        channel?.defaultPermissions?.d ?: 0L
    } else {
        channel?.rolePermissions?.get(roleId)?.d ?: 0L
    }

    var allow by remember(channel) { mutableLongStateOf(initialAllow) }
    var deny by remember(channel) { mutableLongStateOf(initialDeny) }
    var saving by remember { mutableStateOf(false) }

    val unsavedChanges = allow != initialAllow || deny != initialDeny

    val role = if (roleId != "default") server?.roles?.get(roleId) else null
    val title = role?.name ?: stringResource(R.string.channel_settings_permissions_default)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = freshLoaded && unsavedChanges && !saving,
                enter = scaleIn(animationSpec = StoatTweenFloat),
                exit = scaleOut(animationSpec = StoatTweenFloat)
            ) {
                FloatingActionButton(onClick = {
                    saving = true
                    scope.launch {
                        try {
                            setChannelPermissions(channelId, roleId, allow, deny)
                            Toast.makeText(
                                context,
                                context.getString(R.string.channel_settings_permissions_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR) { e.asLog() }
                            Toast.makeText(
                                context,
                                context.getString(R.string.channel_settings_permissions_save_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            saving = false
                        }
                    }
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_24dp),
                        contentDescription = stringResource(R.string.channel_settings_permissions_save)
                    )
                }
            }
        }
    ) { pv ->
        Box(Modifier.padding(pv)) {
            if (loadFailed) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.channel_settings_permissions_load_failed),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else if (channel == null || !freshLoaded) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 96.dp)
                ) {
                    PERMISSION_ENTRIES.forEach { entry ->
                        if (entry.heading != null) {
                            ListHeader { Text(stringResource(entry.heading)) }
                        }

                        val state = when {
                            (allow and entry.bit) == entry.bit -> OverrideState.Allow
                            (deny and entry.bit) == entry.bit -> OverrideState.Deny
                            else -> OverrideState.Neutral
                        }

                        PermissionOverrideRow(
                            title = stringResource(entry.title),
                            description = stringResource(entry.description),
                            state = state,
                            onChange = { target ->
                                allow = allow and entry.bit.inv()
                                deny = deny and entry.bit.inv()
                                when (target) {
                                    OverrideState.Allow -> allow = allow or entry.bit
                                    OverrideState.Deny -> deny = deny or entry.bit
                                    OverrideState.Neutral -> {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionOverrideRow(
    title: String,
    description: String,
    state: OverrideState,
    onChange: (OverrideState) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(text = description, style = MaterialTheme.typography.bodySmall)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
            SegmentedButton(
                selected = state == OverrideState.Deny,
                onClick = { onChange(OverrideState.Deny) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                label = { Text(stringResource(R.string.channel_settings_permission_deny)) }
            )
            SegmentedButton(
                selected = state == OverrideState.Neutral,
                onClick = { onChange(OverrideState.Neutral) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                label = { Text(stringResource(R.string.channel_settings_permission_neutral)) }
            )
            SegmentedButton(
                selected = state == OverrideState.Allow,
                onClick = { onChange(OverrideState.Allow) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                label = { Text(stringResource(R.string.channel_settings_permission_allow)) }
            )
        }
    }
}
