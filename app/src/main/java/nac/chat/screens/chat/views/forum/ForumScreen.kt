package nac.chat.screens.chat.views.forum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.routes.channel.fetchMessagesFromChannel
import nac.chat.core.model.schemas.Channel
import nac.chat.core.model.schemas.Message
import kotlinx.coroutines.launch

class ForumScreenViewModel : ViewModel() {
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var posts = mutableStateListOf<Message>()

    fun load(channelId: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                val result = fetchMessagesFromChannel(
                    channelId = channelId,
                    limit = 100,
                    includeUsers = true,
                    sort = "Latest"
                )
                result.users?.forEach { user -> user.id?.let { StoatAPI.userCache[it] = user } }
                val forumPosts = (result.messages ?: emptyList())
                    .filter { it.forumTitle != null && it.replies.isNullOrEmpty() }
                    .sortedByDescending { msg ->
                        msg.reactions?.values?.sumOf { it.size } ?: 0
                    }
                posts.clear()
                posts.addAll(forumPosts)
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ForumScreen(
    navController: NavController,
    channel: Channel,
    viewModel: ForumScreenViewModel = viewModel(key = channel.id)
) {
    val channelId = channel.id ?: return

    LaunchedEffect(channelId) {
        viewModel.load(channelId)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate("forum/$channelId/new_post")
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = stringResource(R.string.forum_new_post)
                )
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

            viewModel.posts.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(pv),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.forum_no_posts)) }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = pv.calculateTopPadding() + 8.dp,
                    bottom = pv.calculateBottomPadding() + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.posts.size, key = { viewModel.posts[it].id ?: it }) { idx ->
                    val post = viewModel.posts[idx]
                    ForumPostCard(
                        post = post,
                        onClick = { navController.navigate("forum/$channelId/post/${post.id}") }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForumPostCard(post: Message, onClick: () -> Unit) {
    val author = StoatAPI.userCache[post.author]
    val reactionCount = post.reactions?.values?.sumOf { it.size } ?: 0
    val hasSolution = post.forumSolution == true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = post.forumTitle ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (hasSolution) {
                    Text(
                        text = stringResource(R.string.forum_solution_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            val tags = post.forumTags
            if (!tags.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = author?.displayName ?: author?.username ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (reactionCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_star_shine_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = reactionCount.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
