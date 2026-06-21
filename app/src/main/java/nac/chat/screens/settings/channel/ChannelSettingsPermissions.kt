package nac.chat.screens.settings.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.internals.BrushCompat
import nac.chat.api.internals.solidColor
import nac.chat.composables.chat.RoleListEntry
import nac.chat.composables.generic.ListHeader
import nac.chat.core.model.schemas.PermissionDescription

private fun countBits(value: Long): Int {
    var bits = 0
    for (i in 0 until 52) {
        if ((value and (1L shl i)) != 0L) bits++
    }
    return bits
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsPermissions(navController: NavController, channelId: String) {
    val channel = StoatAPI.channelCache[channelId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.channel_settings_permissions),
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
            if (channel == null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else {
            val server = channel.server?.let { StoatAPI.serverCache[it] }
            val orderedRoles = (server?.roles ?: emptyMap())
                .toList()
                .sortedBy { (_, role) -> role.rank ?: 0.0 }

            val activeRoles = orderedRoles.filter { (roleId, _) ->
                val override = channel.rolePermissions?.get(roleId)
                countBits(override?.a ?: 0L) > 0 || countBits(override?.d ?: 0L) > 0
            }
            val unusedRoles = orderedRoles.filter { (roleId, _) ->
                val override = channel.rolePermissions?.get(roleId)
                countBits(override?.a ?: 0L) == 0 && countBits(override?.d ?: 0L) == 0
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.channel_settings_permissions_default)) },
                    supportingContent = { Text(stringResource(R.string.channel_settings_permissions_default_description)) },
                    modifier = Modifier
                        .testTag("channel_settings_permissions_default")
                        .clickable {
                            navController.navigate("settings/channel/${channel.id}/permissions/default")
                        }
                )

                if (orderedRoles.isNotEmpty()) {
                    if (activeRoles.isNotEmpty()) {
                        ListHeader { Text(stringResource(R.string.channel_settings_permissions_role_header)) }
                        activeRoles.forEach { (roleId, role) ->
                            val override = channel.rolePermissions?.get(roleId) ?: PermissionDescription(0L, 0L)
                            ListItem(
                                headlineContent = {
                                    RoleListEntry(
                                        label = role.name ?: "null",
                                        brush = role.colour?.let { BrushCompat.parseColour(it) }
                                            ?: Brush.solidColor(LocalContentColor.current),
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            R.string.channel_settings_permissions_role_summary,
                                            countBits(override.a),
                                            countBits(override.d)
                                        )
                                    )
                                },
                                modifier = Modifier.clickable {
                                    navController.navigate("settings/channel/${channel.id}/permissions/$roleId")
                                }
                            )
                        }
                    }

                    if (unusedRoles.isNotEmpty()) {
                        ListHeader { Text(stringResource(R.string.channel_settings_permissions_role_unused_header)) }
                        unusedRoles.forEach { (roleId, role) ->
                            ListItem(
                                headlineContent = {
                                    RoleListEntry(
                                        label = role.name ?: "null",
                                        brush = role.colour?.let { BrushCompat.parseColour(it) }
                                            ?: Brush.solidColor(LocalContentColor.current),
                                    )
                                },
                                supportingContent = {
                                    Text(stringResource(R.string.channel_settings_permissions_role_unused_summary))
                                },
                                modifier = Modifier.clickable {
                                    navController.navigate("settings/channel/${channel.id}/permissions/$roleId")
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
