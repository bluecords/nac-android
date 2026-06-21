package nac.chat.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.internals.PermissionBit
import nac.chat.api.internals.Roles
import nac.chat.api.internals.has
import nac.chat.api.routes.channel.removeMember
import nac.chat.api.routes.server.banMember
import nac.chat.api.routes.server.kickMember
import nac.chat.composables.generic.SheetButton
import nac.chat.core.model.schemas.User
import nac.chat.internals.Platform
import kotlinx.coroutines.launch

@Composable
fun ColumnScope.GroupDMMemberContextSheet(
    userId: String,
    channelId: String,
    dismissSheet: suspend () -> Unit,
    onRequestUpdateMembers: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    val channel = StoatAPI.channelCache[channelId]
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(channel) {
        if (channel == null) {
            dismissSheet()
        }
    }

    if (channel == null) return

    if (channel.owner == StoatAPI.selfId && userId != StoatAPI.selfId) {
        SheetButton(
            headlineContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Text(
                        stringResource(
                            R.string.member_context_sheet_remove_from_channel,
                            channel.name ?: stringResource(R.string.unknown)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person_off_24dp),
                        contentDescription = null
                    )
                }
            },
            onClick = {
                scope.launch {
                    removeMember(channelId, userId)
                    onRequestUpdateMembers()
                    dismissSheet()
                }
            }
        )
    }

    // TODO replace with something useful (currently so that your sheet is not empty if you don't have permissions)
    SheetButton(
        headlineContent = {
            Text(stringResource(R.string.user_info_sheet_copy_id))
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_identifier_copy_24dp),
                contentDescription = null
            )
        },
        onClick = {
            clipboardManager.setText(AnnotatedString(userId))

            if (Platform.needsShowClipboardNotification()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.copied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )


}

@Composable
fun ColumnScope.ServerMemberContextSheet(
    userId: String,
    serverId: String,
    channelId: String,
    dismissSheet: suspend () -> Unit,
    onRequestUpdateMembers: suspend () -> Unit
) {
    val server = StoatAPI.serverCache[serverId]
    val channel = StoatAPI.channelCache[channelId]
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(server) {
        if (server == null || channel == null) {
            dismissSheet()
        }
    }

    if (server == null || channel == null) return

    val selfMember = StoatAPI.selfId?.let { StoatAPI.members.getMember(serverId, it) }
    val selfPermissions = selfMember?.let { Roles.permissionFor(server, it) }
    val isSelf = userId == StoatAPI.selfId
    val isServerOwner = userId == server.owner

    var showKickConfirmation by remember { mutableStateOf(false) }
    var showBanConfirmation by remember { mutableStateOf(false) }

    val targetName = StoatAPI.userCache[userId]?.let { User.resolveDefaultName(it) } ?: userId

    if (showKickConfirmation) {
        AlertDialog(
            onDismissRequest = { showKickConfirmation = false },
            title = { Text(stringResource(R.string.member_context_sheet_kick_confirm, targetName)) },
            confirmButton = {
                Button(onClick = {
                    showKickConfirmation = false
                    scope.launch {
                        kickMember(serverId, userId)
                        onRequestUpdateMembers()
                        dismissSheet()
                    }
                }) {
                    Text(stringResource(R.string.member_context_sheet_kick_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKickConfirmation = false }) {
                    Text(stringResource(R.string.member_context_sheet_kick_confirm_no))
                }
            }
        )
    }

    if (showBanConfirmation) {
        AlertDialog(
            onDismissRequest = { showBanConfirmation = false },
            title = { Text(stringResource(R.string.member_context_sheet_ban_confirm, targetName)) },
            confirmButton = {
                Button(onClick = {
                    showBanConfirmation = false
                    scope.launch {
                        banMember(serverId, userId)
                        onRequestUpdateMembers()
                        dismissSheet()
                    }
                }) {
                    Text(stringResource(R.string.member_context_sheet_ban_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBanConfirmation = false }) {
                    Text(stringResource(R.string.member_context_sheet_ban_confirm_no))
                }
            }
        )
    }

    if (!isSelf && !isServerOwner && selfPermissions has PermissionBit.KickMembers) {
        SheetButton(
            headlineContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Text(
                        stringResource(R.string.member_context_sheet_kick, targetName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person_off_24dp),
                        contentDescription = null
                    )
                }
            },
            onClick = { showKickConfirmation = true }
        )
    }

    if (!isSelf && !isServerOwner && selfPermissions has PermissionBit.BanMembers) {
        SheetButton(
            headlineContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Text(
                        stringResource(R.string.member_context_sheet_ban, targetName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gavel_24dp),
                        contentDescription = null
                    )
                }
            },
            onClick = { showBanConfirmation = true }
        )
    }

    // TODO replace with something useful (currently so that your sheet is not empty if you don't have permissions)
    SheetButton(
        headlineContent = {
            Text(stringResource(R.string.user_info_sheet_copy_id))
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_identifier_copy_24dp),
                contentDescription = null
            )
        },
        onClick = {
            clipboardManager.setText(AnnotatedString(userId))

            if (Platform.needsShowClipboardNotification()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.copied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )


}