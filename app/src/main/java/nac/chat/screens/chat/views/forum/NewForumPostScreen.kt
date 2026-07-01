package nac.chat.screens.chat.views.forum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import nac.chat.api.StoatAPI
import nac.chat.api.routes.channel.sendForumPost
import nac.chat.core.model.schemas.Channel
import kotlinx.coroutines.launch

class NewForumPostViewModel : ViewModel() {
    var title by mutableStateOf("")
    var content by mutableStateOf("")
    var selectedTags = mutableStateListOf<String>()
    var error by mutableStateOf<String?>(null)
    var isPosting by mutableStateOf(false)

    fun submit(channelId: String, onSuccess: () -> Unit) {
        if (title.isBlank()) {
            error = "Title is required"
            return
        }
        viewModelScope.launch {
            try {
                isPosting = true
                error = null
                sendForumPost(
                    channelId = channelId,
                    title = title.trim(),
                    content = content.trim(),
                    tags = selectedTags.toList()
                )
                onSuccess()
            } catch (e: Exception) {
                error = e.message
            } finally {
                isPosting = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewForumPostScreen(
    navController: NavController,
    channel: Channel,
    viewModel: NewForumPostViewModel = viewModel(key = channel.id)
) {
    val channelId = channel.id ?: return
    val allowedTags = channel.allowedTags ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.forum_new_post),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!viewModel.isPosting) {
                        viewModel.submit(channelId) { navController.popBackStack() }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_24dp),
                    contentDescription = stringResource(R.string.forum_post_submit)
                )
            }
        }
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (viewModel.error != null) {
                Text(
                    text = viewModel.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
                label = { Text(stringResource(R.string.forum_post_title)) },
                singleLine = true,
                isError = viewModel.error != null && viewModel.title.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.content,
                onValueChange = { viewModel.content = it },
                label = { Text(stringResource(R.string.forum_post_content)) },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )

            if (allowedTags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.forum_post_tags),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedTags.forEach { tag ->
                        val selected = viewModel.selectedTags.contains(tag)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selected) viewModel.selectedTags.remove(tag)
                                else viewModel.selectedTags.add(tag)
                            },
                            label = { Text(tag) }
                        )
                    }
                }
            }
        }
    }
}
