package nac.chat.screens.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nac.chat.R
import nac.chat.activities.StoatTweenFloat
import nac.chat.api.StoatAPI
import nac.chat.api.internals.FriendRequests
import nac.chat.api.routes.channel.addMember
import nac.chat.composables.chat.MemberListItem
import kotlinx.coroutines.launch

class AddGroupMemberScreenViewModel : ViewModel() {
    var selectedMembers = mutableStateListOf<String>()
    var friendSearchQuery by mutableStateOf("")
    var friendsFilteredBySearch = mutableStateListOf<String>()
    var error by mutableStateOf<String?>(null)

    fun updateFriendSearchQuery(query: String, existingRecipients: List<String>) {
        friendSearchQuery = query
        filterFriends(existingRecipients)
    }

    fun filterFriends(existingRecipients: List<String>) {
        friendsFilteredBySearch.clear()
        friendsFilteredBySearch.addAll(FriendRequests.getFriends().filter {
            if (it.id == null || existingRecipients.contains(it.id)) {
                return@filter false
            }

            if (friendSearchQuery.isBlank()) {
                return@filter true
            }

            if (it.displayName == null || it.username == null) {
                return@filter false
            }

            it.displayName!!.contains(friendSearchQuery, ignoreCase = true) ||
                    it.username!!.contains(friendSearchQuery, ignoreCase = true)
        }.map { it.id!! })
    }

    fun addMembers(channelId: String, popBackStack: () -> Unit) {
        try {
            error = null
            viewModelScope.launch {
                selectedMembers.forEach { userId ->
                    addMember(channelId, userId)
                }
                popBackStack()
            }
        } catch (e: Exception) {
            error = e.message
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupMemberScreen(
    navController: NavController,
    channelId: String,
    viewModel: AddGroupMemberScreenViewModel = viewModel()
) {
    val existingRecipients = StoatAPI.channelCache[channelId]?.recipients ?: emptyList()

    LaunchedEffect(Unit) {
        viewModel.filterFriends(existingRecipients)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.add_group_member_title),
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
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = viewModel.selectedMembers.isNotEmpty(),
                enter = scaleIn(animationSpec = StoatTweenFloat),
                exit = scaleOut(animationSpec = StoatTweenFloat)
            ) {
                FloatingActionButton(onClick = {
                    viewModel.addMembers(channelId, navController::popBackStack)
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_24dp),
                        contentDescription = stringResource(R.string.add_group_member_action)
                    )
                }
            }
        }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            AnimatedVisibility(visible = viewModel.error?.isNotBlank() ?: false) {
                Text(
                    text = viewModel.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
            OutlinedTextField(
                value = viewModel.friendSearchQuery,
                onValueChange = { viewModel.updateFriendSearchQuery(it, existingRecipients) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_24dp),
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.add_group_member_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 78.0.dp)) {
                items(viewModel.friendsFilteredBySearch.size) { index ->
                    val friend = StoatAPI.userCache[viewModel.friendsFilteredBySearch[index]]
                        ?: return@items
                    val isSelected = viewModel.selectedMembers.contains(friend.id)

                    MemberListItem(
                        member = null,
                        user = friend,
                        serverId = null,
                        userId = friend.id!!,
                        modifier = Modifier.clickable {
                            if (isSelected) {
                                viewModel.selectedMembers.remove(friend.id)
                            } else {
                                viewModel.selectedMembers.add(friend.id!!)
                            }
                        },
                        trailingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null
                            )
                        }
                    )
                }
            }
        }
    }
}
