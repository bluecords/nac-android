package nac.chat.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import nac.chat.BuildConfig
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.routes.server.fetchMember
import nac.chat.api.routes.sponsor.startSponsorCheckout
import nac.chat.api.routes.sponsor.startSponsorManage
import nac.chat.api.settings.FeatureFlags
import nac.chat.api.settings.LoadedSettings
import nac.chat.composables.generic.ListHeader
import nac.chat.persistence.KVStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.androidx.compose.koinViewModel

private const val SPONSOR_SERVER_ID = "01KTD1MYDTQ0SSXXH7C93BCHET"
private const val SPONSOR_ROLE_ID = "01KWN87BF04TY9DACN5KSP9N1R"
private const val SUSTAINER_ROLE_ID = "01KWN87G54FWJ82GB23DNWCDS9"

class SettingsScreenViewModel(
    private val kvStorage: KVStorage
) : ViewModel() {
    fun logout() {
        runBlocking {
            kvStorage.remove("sessionToken")
            kvStorage.remove("selfId")
            kvStorage.remove("selfName")
            kvStorage.remove("selfAvatarUrl")
            LoadedSettings.reset()
            StoatAPI.logout()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsScreenViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showSponsorDialog by remember { mutableStateOf(false) }
    var sponsorGiftAmount by remember { mutableStateOf("50") }
    var isAlreadySponsor by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val selfId = StoatAPI.selfId ?: return@LaunchedEffect
        if (!StoatAPI.members.hasMember(SPONSOR_SERVER_ID, selfId)) {
            try {
                fetchMember(SPONSOR_SERVER_ID, selfId)
            } catch (_: Exception) {
                // Not a member of the NAC server (or a transient fetch failure) --
                // isAlreadySponsor just stays false, same as the tier-picker default.
            }
        }
        val roles = StoatAPI.members.getMember(SPONSOR_SERVER_ID, selfId)?.roles ?: emptyList()
        isAlreadySponsor = SPONSOR_ROLE_ID in roles || SUSTAINER_ROLE_ID in roles
    }

    fun startSponsorCheckoutAndOpen(tier: String, amount: Double? = null) {
        showSponsorDialog = false
        scope.launch {
            try {
                val checkoutUrl = startSponsorCheckout(tier, amount)
                CustomTabsIntent.Builder().build().launchUrl(context, checkoutUrl.toUri())
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Failed to start checkout", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startSponsorManageAndOpen() {
        showSponsorDialog = false
        scope.launch {
            try {
                val portalUrl = startSponsorManage()
                CustomTabsIntent.Builder().build().launchUrl(context, portalUrl.toUri())
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Failed to open subscription management", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.settings),
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 10.dp)
                ) {
                    ListHeader {
                        Text(stringResource(R.string.settings_category_account))
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.settings_account)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lock_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_account")
                            .clickable {
                                navController.navigate("settings/account")
                            }
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.settings_profile)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_id_card_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_profile")
                            .clickable {
                                navController.navigate("settings/profile")
                            }
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.settings_sessions)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_devices_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_sessions")
                            .clickable {
                                navController.navigate("settings/sessions")
                            }
                    )

                    ListHeader {
                        Text("Subscriptions")
                    }

                    ListItem(
                        headlineContent = {
                            Text(text = "Sponsor NAC")
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_workspace_premium_24dp__fill),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_sponsor_nac")
                            .clickable {
                                showSponsorDialog = true
                            }
                    )

                    ListHeader {
                        Text(stringResource(R.string.settings_category_general))
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.settings_appearance)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_palette_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_appearance")
                            .clickable {
                                navController.navigate("settings/appearance")
                            }
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.settings_language)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_language_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_language")
                            .clickable {
                                navController.navigate("settings/language")
                            }
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.settings_chat)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_chat_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_chat")
                            .clickable {
                                navController.navigate("settings/chat")
                            }
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.settings_notifications)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_notifications_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_notifications")
                            .clickable {
                                navController.navigate("settings/notifications")
                            }
                    )

                    ListHeader {
                        Text(stringResource(R.string.settings_category_miscellaneous))
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.about)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_info_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_about")
                            .clickable {
                                navController.navigate("about")
                            }
                    )

                    if (BuildConfig.DEBUG) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Debug"
                                )
                            },
                            leadingContent = {
                                SettingsIcon {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_sign_language_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .testTag("settings_view_debug")
                                .clickable {
                                    navController.navigate("settings/debug")
                                }
                        )
                    }

                    if (FeatureFlags.labsAccessControlGranted) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Labs"
                                )
                            },
                            leadingContent = {
                                SettingsIcon {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_sign_language_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .testTag("settings_view_labs")
                                .clickable {
                                    navController.navigate("labs")
                                }
                        )
                    }

                    if (LoadedSettings.experimentsEnabled) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Experiments"
                                )
                            },
                            leadingContent = {
                                SettingsIcon {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lab_research_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .testTag("settings_view_experiments")
                                .clickable {
                                    navController.navigate("settings/experiments")
                                }
                        )
                    }

                    ListHeader {
                        Text(
                            stringResource(
                                R.string.settings_category_last,
                                BuildConfig.VERSION_NAME
                            )
                        )
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.settings_changelog)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_campaign_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_changelog")
                            .clickable {
                                navController.navigate("changelog/latest")
                            }
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.settings_feedback)
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(id = R.string.settings_feedback_description)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_feedback_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_feedback")
                            .clickable { showFeedbackDialog = true }
                    )

                    if (showFeedbackDialog) {
                        AlertDialog(
                            onDismissRequest = { showFeedbackDialog = false },
                            title = { Text(stringResource(R.string.settings_feedback)) },
                            text = {
                                Column {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.settings_feedback_feature_request)) },
                                        supportingContent = { Text(stringResource(R.string.settings_feedback_feature_request_description)) },
                                        modifier = Modifier.clickable {
                                            showFeedbackDialog = false
                                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/bluecords/nac-android/discussions/categories/ideas".toUri()))
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.settings_feedback_general)) },
                                        supportingContent = { Text(stringResource(R.string.settings_feedback_general_description)) },
                                        modifier = Modifier.clickable {
                                            showFeedbackDialog = false
                                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/bluecords/nac-android/discussions".toUri()))
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.settings_feedback_bug)) },
                                        supportingContent = { Text(stringResource(R.string.settings_feedback_bug_description)) },
                                        modifier = Modifier.clickable {
                                            showFeedbackDialog = false
                                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/bluecords/nac-android/issues".toUri()))
                                        }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showFeedbackDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    if (showSponsorDialog && isAlreadySponsor) {
                        AlertDialog(
                            onDismissRequest = { showSponsorDialog = false },
                            title = { Text("Manage Sponsorship") },
                            text = {
                                Text(
                                    "You're already a NAC supporter. Manage your subscription " +
                                        "(update billing details or cancel) through Stripe's secure portal."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { startSponsorManageAndOpen() }) {
                                    Text("Manage Subscription")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSponsorDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    } else if (showSponsorDialog) {
                        AlertDialog(
                            onDismissRequest = { showSponsorDialog = false },
                            title = { Text("Sponsor NAC") },
                            text = {
                                Column {
                                    ListItem(
                                        headlineContent = { Text("Sponsor — \$2.99/mo") },
                                        supportingContent = { Text("Longer text posts") },
                                        modifier = Modifier.clickable {
                                            startSponsorCheckoutAndOpen("2_99")
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text("Sustainer — \$9.99/mo") },
                                        supportingContent = { Text("All Sponsor perks + even longer posts") },
                                        modifier = Modifier.clickable {
                                            startSponsorCheckoutAndOpen("9_99")
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text("One-time gift") },
                                        supportingContent = { Text("\$10 = 1 month of Sustainer perks") }
                                    )
                                    OutlinedTextField(
                                        value = sponsorGiftAmount,
                                        onValueChange = { sponsorGiftAmount = it },
                                        label = { Text("Amount (USD)") },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val amount = sponsorGiftAmount.toDoubleOrNull()
                                    if (amount != null && amount > 0) {
                                        startSponsorCheckoutAndOpen("gift", amount)
                                    } else {
                                        Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Text("Send Gift")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSponsorDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    ListItem(
                        headlineContent = {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                                Text(
                                    text = stringResource(id = R.string.logout)
                                )
                            }
                        },
                        leadingContent = {
                            SettingsIcon(danger = true) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_logout_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("settings_view_logout")
                            .clickable {
                                viewModel.logout()
                                navController.navigate("login/greeting") {
                                    popUpTo("chat") {
                                        inclusive = true
                                    }
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsIcon(danger: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides
                if (danger) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onBackground
    ) {
        content()
    }
}