package nac.chat.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.internals.ChannelUtils
import nac.chat.api.internals.Favorites
import nac.chat.core.model.schemas.ChannelType
import nac.chat.composables.generic.SheetButton

import nac.chat.internals.Platform
import kotlinx.coroutines.launch

@Composable
fun ChannelContextSheet(channelId: String, onHideSheet: suspend () -> Unit) {
    val channel = StoatAPI.channelCache[channelId]
    if (channel == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    if (channel.channelType == ChannelType.DirectMessage) {
        val partnerId = ChannelUtils.resolveDMPartner(channel)
        if (partnerId != null) {
            val isFavorite = Favorites.isFavorite(partnerId)
            SheetButton(
                headlineContent = {
                    Text(
                        text = stringResource(
                            id = if (isFavorite) R.string.favorites_remove
                            else R.string.favorites_add
                        )
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(
                            id = if (isFavorite) R.drawable.ic_star_shine_24dp__fill
                            else R.drawable.ic_star_shine_24dp
                        ),
                        contentDescription = null
                    )
                },
                onClick = {
                    Favorites.toggle(partnerId)
                    coroutineScope.launch {
                        onHideSheet()
                    }
                }
            )
        }
    }

    SheetButton(
        headlineContent = {
            Text(
                text = stringResource(id = R.string.channel_context_sheet_actions_copy_id),
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.ic_identifier_copy_24dp),
                contentDescription = null
            )
        },
        onClick = {
            if (channel.id == null) return@SheetButton

            clipboardManager.setText(AnnotatedString(channel.id!!))

            if (Platform.needsShowClipboardNotification()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.channel_context_sheet_actions_copy_id_copied),
                    Toast.LENGTH_SHORT
                ).show()
            }

            coroutineScope.launch {
                onHideSheet()
            }
        }
    )

    SheetButton(
        headlineContent = {
            Text(
                text = stringResource(id = R.string.channel_context_sheet_actions_mark_read),
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.ic_mark_chat_read_24dp),
                contentDescription = null
            )
        },
        onClick = {
            coroutineScope.launch {
                channel.lastMessageID?.let {
                    StoatAPI.unreads.markAsRead(channelId, it, sync = true)
                }
                onHideSheet()
            }
        }
    )


}
