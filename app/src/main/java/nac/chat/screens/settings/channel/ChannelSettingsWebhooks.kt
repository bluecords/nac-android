package nac.chat.screens.settings.channel

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import nac.chat.api.StoatAPI
import nac.chat.api.routes.channel.createChannelWebhook
import nac.chat.api.routes.channel.fetchChannelWebhooks
import nac.chat.composables.generic.UserAvatar
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsWebhooks(navController: NavController, channelId: String) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var loading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newWebhookName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    val webhooks = StoatAPI.webhookCache.values.filter { it.channelId == channelId }

    LaunchedEffect(channelId) {
        try {
            fetchChannelWebhooks(channelId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.asLog() }
        } finally {
            loading = false
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!creating) showCreateDialog = false },
            title = { Text(stringResource(R.string.channel_settings_webhooks_create)) },
            text = {
                OutlinedTextField(
                    value = newWebhookName,
                    onValueChange = { newWebhookName = it },
                    placeholder = { Text(stringResource(R.string.channel_settings_webhooks_create_name_hint)) },
                    singleLine = true,
                    enabled = !creating
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, enabled = !creating) {
                    Text(stringResource(R.string.channel_settings_webhook_delete_confirm_no))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newWebhookName.trim()
                        if (name.isEmpty()) return@Button

                        creating = true
                        scope.launch {
                            try {
                                val webhook = createChannelWebhook(channelId, name)
                                showCreateDialog = false
                                newWebhookName = ""
                                webhook.id?.let { navController.navigate("settings/channel/$channelId/webhooks/$it") }
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR) { e.asLog() }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.channel_settings_webhooks_create_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                creating = false
                            }
                        }
                    },
                    enabled = !creating && newWebhookName.isNotBlank()
                ) {
                    Text(stringResource(R.string.channel_settings_webhooks_create))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.channel_settings_webhooks),
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
    ) { pv ->
        Box(Modifier.padding(pv)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.channel_settings_webhooks_create)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_webhook_24dp),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        newWebhookName = ""
                        showCreateDialog = true
                    }
                )

                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                } else if (webhooks.isEmpty()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.channel_settings_webhooks_empty)) }
                    )
                } else {
                    webhooks.forEach { webhook ->
                        ListItem(
                            headlineContent = { Text(webhook.name ?: webhook.id ?: "") },
                            supportingContent = { webhook.id?.let { Text(it) } },
                            leadingContent = {
                                UserAvatar(
                                    username = webhook.name ?: "",
                                    userId = webhook.id ?: "",
                                    avatar = webhook.avatar,
                                    size = 32.dp
                                )
                            },
                            modifier = Modifier.clickable {
                                webhook.id?.let { navController.navigate("settings/channel/$channelId/webhooks/$it") }
                            }
                        )
                    }
                }
            }
        }
    }
}
