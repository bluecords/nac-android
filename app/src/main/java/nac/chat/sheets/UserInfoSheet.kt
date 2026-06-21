package nac.chat.sheets

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.internals.BrushCompat
import nac.chat.api.internals.Favorites
import nac.chat.api.internals.PermissionBit
import nac.chat.api.internals.Roles
import nac.chat.api.internals.ULID
import nac.chat.api.internals.has
import nac.chat.api.internals.solidColor
import nac.chat.api.routes.server.editMemberRoles
import nac.chat.api.routes.user.fetchUserProfile
import nac.chat.api.settings.Experiments
import nac.chat.api.settings.FeatureFlags
import nac.chat.composables.chat.RoleListEntry
import nac.chat.composables.chat.UserBadgeList
import nac.chat.composables.chat.UserBadgeRow
import nac.chat.composables.generic.NonIdealState
import nac.chat.composables.generic.SheetButton
import nac.chat.composables.generic.UserAvatar
import nac.chat.composables.markdown.prose.ChatMarkdown
import nac.chat.composables.screens.settings.RawUserOverview
import nac.chat.composables.screens.settings.UserButtons
import nac.chat.composables.sheets.SheetTile
import nac.chat.core.model.schemas.Profile
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoSheet(
    userId: String,
    serverId: String? = null,
    dismissSheet: suspend () -> Unit
) {
    val user = StoatAPI.userCache[userId]

    val member = serverId?.let { StoatAPI.members.getMember(it, userId) }

    val server = StoatAPI.serverCache[serverId]

    val selfPermissions = server?.let { srv ->
        StoatAPI.selfId?.let { StoatAPI.members.getMember(srv.id ?: "", it) }
            ?.let { Roles.permissionFor(srv, it) }
    }
    val canAssignRoles = server != null && selfPermissions has PermissionBit.AssignRoles
    var showRoleEditSheet by remember { mutableStateOf(false) }

    var profile by remember { mutableStateOf<Profile?>(null) }
    var profileNotFound by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        try {
            user?.id?.let { fetchUserProfile(it) }?.let { profile = it }
        } catch (e: Exception) {
            if (e.message == "NotFound") {
                profileNotFound = true
            }
            e.printStackTrace()
        }
    }

    if (user == null) {
        // TODO fetch user in this scenario
        NonIdealState(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_error_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(it)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.user_info_sheet_user_not_found)
                )
            },
            description = {
                Text(
                    text = stringResource(R.string.user_info_sheet_user_not_found_description)
                )
            }
        )
        Spacer(Modifier.height(20.dp))
        return
    }

    var showUserCard by remember { mutableStateOf(false) }
    if (showUserCard) {
        val sheetState = rememberModalBottomSheetState(true)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showUserCard = false }
        ) {
            UserCardSheet(user)
        }
    }

    var showServerIdentityOptions by remember { mutableStateOf(false) }
    if (showServerIdentityOptions) {
        val sheetState = rememberModalBottomSheetState(true)
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showServerIdentityOptions = false }
        ) {
            ServerIdentityOptionsSheet(
                userId = user.id!!
            )
        }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
        modifier = Modifier.padding(16.dp)
    ) {
        item(key = "overview", span = StaggeredGridItemSpan.FullLine) {
            Box {
                RawUserOverview(user, profile, internalPadding = false)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                ) {
                    if (user.id != null && user.id != StoatAPI.selfId) {
                        val isFavorite = Favorites.isFavorite(user.id!!)
                        SmallFloatingActionButton(
                            onClick = { Favorites.toggle(user.id!!) },
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isFavorite) R.drawable.ic_star_shine_24dp__fill
                                    else R.drawable.ic_star_shine_24dp
                                ),
                                contentDescription = stringResource(
                                    if (isFavorite) R.string.favorites_remove
                                    else R.string.favorites_add
                                )
                            )
                        }
                    }

                    if (Experiments.enableServerIdentityOptions.isEnabled) {
                        SmallFloatingActionButton(
                            onClick = { showServerIdentityOptions = true },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_psychology_alt_24dp),
                                contentDescription = null
                            )
                        }
                    }

                    if (FeatureFlags.userCardsGranted) {
                        SmallFloatingActionButton(
                            onClick = { showUserCard = true },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_badge_24dp),
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }

        member?.roles?.let {
            item(key = "roles") {
                SheetTile(
                    header = {
                        Text(stringResource(R.string.user_info_sheet_category_roles))
                    },
                    contentPreview = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            it
                                .map { roleId -> server?.roles?.get(roleId) }
                                .sortedBy { it?.rank ?: 0.0 }
                                .take(3)
                                .forEach { role ->
                                    role?.let {
                                        RoleListEntry(
                                            label = role.name ?: "null",
                                            brush = role.colour?.let { BrushCompat.parseColour(it) }
                                                ?: Brush.solidColor(LocalContentColor.current)
                                        )
                                    }
                                }
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        it
                            .map { roleId -> server?.roles?.get(roleId) }
                            .sortedBy { it?.rank ?: 0.0 }
                            .forEach { role ->
                                role?.let {
                                    RoleListEntry(
                                        label = role.name ?: "null",
                                        brush = role.colour?.let { BrushCompat.parseColour(it) }
                                            ?: Brush.solidColor(LocalContentColor.current)
                                    )
                                }
                            }
                    }
                }
            }
        }
        if (canAssignRoles && !(server?.roles.isNullOrEmpty())) {
            item(key = "edit_roles", span = StaggeredGridItemSpan.FullLine) {
                Button(onClick = { showRoleEditSheet = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.user_info_sheet_edit_roles))
                }
            }
        }

        val accountAt = user.id?.let {
            DateUtils.getRelativeTimeSpanString(
                ULID.asTimestamp(user.id!!),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            ).toString()
        }
        val joinedAt = member?.joinedAt?.let {
            DateUtils.getRelativeTimeSpanString(
                Instant.parse(member.joinedAt!!).toEpochMilliseconds(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            ).toString()
        }

        item(key = "joined") {
            SheetTile(
                header = {
                    Text(stringResource(R.string.user_info_sheet_category_joined))
                },
                contentPreview = {
                    if (joinedAt != null && server?.name != null) {
                        Text(
                            text = joinedAt,
                            fontSize = 14.sp
                        )

                        Text(
                            text = server.name!!,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(4.dp))
                    }

                    accountAt?.let { _ ->
                        Text(
                            text = accountAt,
                            fontSize = 14.sp
                        )

                        Text(
                            text = stringResource(id = R.string.user_info_sheet_category_joined_stoat),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            ) {
                if (joinedAt != null && server?.name != null) {
                    Text(
                        text = joinedAt,
                        style = MaterialTheme.typography.displaySmall
                    )

                    Text(
                        text = server.name!!,
                        style = MaterialTheme.typography.labelMedium
                    )

                    Spacer(Modifier.height(8.dp))
                }

                accountAt?.let { _ ->
                    Text(
                        text = accountAt,
                        style = MaterialTheme.typography.displaySmall
                    )

                    Text(
                        text = stringResource(id = R.string.user_info_sheet_category_joined_stoat),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if ((user.badges ?: 0) > 0) {
            item(key = "info") {
                SheetTile(
                    header = {
                        Text(stringResource(R.string.user_info_sheet_category_badges))
                    },
                    contentPreview = {
                        user.badges?.let { UserBadgeRow(badges = it) }
                    }
                ) {
                    user.badges?.let { UserBadgeList(badges = it) }
                }
            }
        }

        if (user.status?.text != null) {
            item(key = "status") {
                SheetTile(
                    header = {
                        Text(stringResource(R.string.user_info_sheet_category_status))
                    },
                    contentPreview = {
                        Text(
                            text = user.status!!.text!!,
                            fontSize = 14.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                ) {
                    Text(
                        text = user.status!!.text!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (user.bot != null) {
            val resolvedOwner = user.bot!!.owner?.let { StoatAPI.userCache[it] }

            item(key = "bot-owner") {
                SheetTile(
                    header = {
                        Text(stringResource(R.string.user_info_sheet_category_owner))
                    },
                    contentPreview = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            resolvedOwner?.let {
                                UserAvatar(
                                    username = it.displayName ?: it.username
                                    ?: stringResource(R.string.unknown),
                                    avatar = it.avatar,
                                    userId = it.id!!,
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = it.displayName ?: it.username
                                    ?: stringResource(R.string.unknown),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } ?: run {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_error_24dp),
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.unknown),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                ) {
                    resolvedOwner?.let {
                        RawUserOverview(it, null, internalPadding = false)
                    } ?: run {
                        NonIdealState(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_error_24dp),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            title = {
                                Text(
                                    text = stringResource(R.string.user_info_sheet_owner_not_found)
                                )
                            }
                        )
                    }
                }
            }
        }

        if (profile?.content.isNullOrBlank().not()) {
            item(key = "bio", span = StaggeredGridItemSpan.FullLine) {
                SheetTile(
                    header = {
                        Text(stringResource(R.string.user_info_sheet_category_bio))
                    },
                    contentPreview = {
                        ChatMarkdown(content = profile?.content!!, serverId = serverId)
                    }
                ) {
                    SelectionContainer(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        ChatMarkdown(content = profile?.content!!, serverId = serverId)
                    }
                }
            }
        }

        item(key = "actions", span = StaggeredGridItemSpan.FullLine) {
            UserButtons(user, dismissSheet, serverId)
        }
    }

    if (showRoleEditSheet && server != null) {
        val scope = rememberCoroutineScope()
        var pendingRoles by remember(member?.roles) {
            mutableStateOf(member?.roles?.toSet() ?: emptySet())
        }

        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { showRoleEditSheet = false }
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.user_info_sheet_edit_roles),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                (server.roles ?: emptyMap())
                    .toList()
                    .sortedBy { (_, role) -> role.rank ?: 0.0 }
                    .forEach { (roleId, role) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pendingRoles = if (roleId in pendingRoles) {
                                        pendingRoles - roleId
                                    } else {
                                        pendingRoles + roleId
                                    }
                                }
                        ) {
                            Checkbox(
                                checked = roleId in pendingRoles,
                                onCheckedChange = {
                                    pendingRoles = if (it) pendingRoles + roleId else pendingRoles - roleId
                                }
                            )
                            RoleListEntry(
                                label = role.name ?: "null",
                                brush = role.colour?.let { BrushCompat.parseColour(it) }
                                    ?: Brush.solidColor(LocalContentColor.current),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                editMemberRoles(serverId ?: "", userId, pendingRoles.toList())
                                showRoleEditSheet = false
                            } catch (e: Exception) {
                                // swallow - role list just won't update, sheet stays open for retry
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp)
                ) {
                    Text(stringResource(R.string.user_info_sheet_save_roles))
                }
            }
        }
    }

}