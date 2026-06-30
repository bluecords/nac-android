package nac.chat.screens.chat.views.forum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.routes.channel.SendMessageReply
import nac.chat.api.routes.channel.fetchMessagesFromChannel
import nac.chat.api.routes.channel.markSolution
import nac.chat.api.routes.channel.sendMessage
import nac.chat.api.routes.channel.unmarkSolution
import nac.chat.core.model.schemas.Channel
import nac.chat.core.model.schemas.Message
import kotlinx.coroutines.launch

class ForumPostDetailViewModel : ViewModel() {
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var post by mutableStateOf<Message?>(null)
    var replies = mutableStateListOf<Message>()
    var replyText by mutableStateOf("")
    var isSending by mutableStateOf(false)

    fun load(channelId: String, postId: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                val result = fetchMessagesFromChannel(
                    channelId = channelId,
                    limit = 100,
                    includeUsers = true,
                    nearby = postId
                )
                result.users?.forEach { user -> user.id?.let { StoatAPI.userCache[it] = user } }
                val allMessages = result.messages ?: emptyList()
                post = allMessages.firstOrNull { it.id == postId }
                replies.clear()
                replies.addAll(
                    allMessages.filter { msg ->
                        msg.id != postId && msg.replies?.contains(postId) == true
                    }.sortedBy { it.id }
                )
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun sendReply(channelId: String, postId: String) {
        val text = replyText.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                isSending = true
                sendMessage(
                    channelId = channelId,
                    content = text,
                    replies = listOf(SendMessageReply(id = postId, mention = false))
                )
                replyText = ""
                load(channelId, postId)
            } catch (e: Exception) {
                error = e.message
            } finally {
                isSending = false
            }
        }
    }

    fun toggleSolution(channelId: String, messageId: String, currentlyMarked: Boolean) {
        viewModelScope.launch {
            try {
                if (currentlyMarked) unmarkSolution(channelId, messageId)
                else markSolution(channelId, messageId)
                // Refresh the post state
                post = post?.copy(
                    forumSolution = if (messageId == post?.id) !currentlyMarked else post?.forumSolution
                )
                val idx = replies.indexOfFirst { it.id == messageId }
                if (idx >= 0) {
                    replies[idx] = replies[idx].copy(forumSolution = !currentlyMarked)
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumPostDetailScreen(
    navController: NavController,
    channel: Channel,
    postId: String,
    viewModel: ForumPostDetailViewModel = viewModel(key = "${channel.id}/$postId")
) {
    val channelId = channel.id ?: return
    val solutionEnabled = channel.solutionEnabled == true
    val selfId = StoatAPI.selfId

    LaunchedEffect(channelId, postId) {
        viewModel.load(channelId, postId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.post?.forumTitle ?: "",
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
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = viewModel.replyText,
                        onValueChange = { viewModel.replyText = it },
                        placeholder = { Text(stringResource(R.string.forum_reply_placeholder)) },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    Button(
                        onClick = { viewModel.sendReply(channelId, postId) },
                        enabled = viewModel.replyText.isNotBlank() && !viewModel.isSending
                    ) {
                        Text(stringResource(R.string.forum_reply_submit))
                    }
                }
            }
        }
    ) { pv ->
        when {
            viewModel.isLoading -> Box(
                Modifier.fillMaxSize().padding(pv),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            viewModel.error != null -> Box(
                Modifier.fillMaxSize().padding(pv),
                contentAlignment = Alignment.Center
            ) { Text(viewModel.error ?: "", color = MaterialTheme.colorScheme.error) }

            else -> {
                val post = viewModel.post
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pv),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Original post
                    if (post != null) {
                        item(key = "post_${post.id}") {
                            PostBody(
                                message = post,
                                isPost = true,
                                solutionEnabled = solutionEnabled,
                                selfId = selfId,
                                postAuthorId = post.author,
                                onToggleSolution = { marked ->
                                    viewModel.toggleSolution(channelId, post.id!!, marked)
                                }
                            )
                        }
                    }

                    if (viewModel.replies.isNotEmpty()) {
                        item(key = "divider") {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        }
                    }

                    // Replies
                    items(viewModel.replies.size, key = { viewModel.replies[it].id ?: it }) { idx ->
                        val reply = viewModel.replies[idx]
                        PostBody(
                            message = reply,
                            isPost = false,
                            solutionEnabled = solutionEnabled,
                            selfId = selfId,
                            postAuthorId = post?.author,
                            onToggleSolution = { marked ->
                                viewModel.toggleSolution(channelId, reply.id!!, marked)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostBody(
    message: Message,
    isPost: Boolean,
    solutionEnabled: Boolean,
    selfId: String?,
    postAuthorId: String?,
    onToggleSolution: (currentlyMarked: Boolean) -> Unit,
) {
    val author = StoatAPI.userCache[message.author]
    val isSolution = message.forumSolution == true
    val canMarkSolution = solutionEnabled && !isPost && selfId != null &&
            (selfId == postAuthorId || selfId == message.author)

    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = author?.displayName ?: author?.username ?: "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSolution) {
                Text(
                    text = stringResource(R.string.forum_solution_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = message.content ?: "",
            style = MaterialTheme.typography.bodyMedium
        )
        if (canMarkSolution) {
            TextButton(
                onClick = { onToggleSolution(isSolution) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSolution) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    stringResource(
                        if (isSolution) R.string.forum_unmark_solution
                        else R.string.forum_mark_solution
                    )
                )
            }
        }
    }
}
