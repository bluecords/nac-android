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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.routes.channel.deleteWebhook
import nac.chat.api.routes.channel.editWebhook
import nac.chat.core.model.data.STOAT_BASE
import nac.chat.internals.Platform
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsWebhookView(navController: NavController, channelId: String, webhookId: String) {
    val webhook = StoatAPI.webhookCache[webhookId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var name by remember(webhook) { mutableStateOf(webhook?.name ?: "") }
    var saving by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.channel_settings_webhook_delete_confirm)) },
            text = { Text(stringResource(R.string.channel_settings_webhook_delete_confirm_description)) },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.channel_settings_webhook_delete_confirm_no))
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirmation = false
                    val token = webhook?.token
                    if (webhookId.isNotEmpty() && token != null) {
                        deleting = true
                        scope.launch {
                            try {
                                deleteWebhook(webhookId, token)
                                navController.popBackStack()
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR) { e.asLog() }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.channel_settings_webhook_delete_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                deleting = false
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.channel_settings_webhook_delete_confirm_yes))
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
                        text = webhook?.name ?: stringResource(R.string.channel_settings_webhooks),
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
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.channel_settings_webhook_name)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = !saving && name.isNotBlank() && name != webhook?.name,
                    onClick = {
                        val token = webhook?.token
                        if (token != null) {
                            saving = true
                            scope.launch {
                                try {
                                    editWebhook(webhookId, token, name.trim())
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.channel_settings_webhook_saved),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    logcat(LogPriority.ERROR) { e.asLog() }
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.channel_settings_webhook_save_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp)
                ) {
                    Text(stringResource(R.string.channel_settings_webhook_save))
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.channel_settings_webhook_copy_url)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_content_copy_24dp),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        val token = webhook?.token
                        if (webhookId.isNotEmpty() && token != null) {
                            val url = "$STOAT_BASE/webhooks/$webhookId/$token"
                            clipboardManager.setText(AnnotatedString(url))

                            if (Platform.needsShowClipboardNotification()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.channel_settings_webhook_url_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )

                ListItem(
                    headlineContent = {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                            Text(stringResource(R.string.channel_settings_webhook_delete))
                        }
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable(enabled = !deleting) {
                        showDeleteConfirmation = true
                    }
                )
            }
        }
    }
}
