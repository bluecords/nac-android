package nac.chat.sheets

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nac.chat.R
import nac.chat.api.StoatAPI
import nac.chat.api.internals.CategorisedChannelList
import nac.chat.api.internals.ChannelUtils
import nac.chat.api.routes.channel.moveMessage
import kotlinx.coroutines.launch

@Composable
fun MoveToChannelSheet(
    messageId: String,
    sourceChannelId: String,
    onDone: suspend () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val server = remember {
        StoatAPI.serverCache.values.find { s ->
            s.channels?.contains(sourceChannelId) == true
        }
    }

    val allItems = remember(server) {
        server?.let { ChannelUtils.categoriseServerFlat(it) } ?: emptyList()
    }

    val filteredItems = remember(allItems, query) {
        if (query.isBlank()) {
            allItems
        } else {
            val q = query.trim().lowercase()
            val filtered = mutableListOf<CategorisedChannelList>()
            var pendingCategory: CategorisedChannelList.Category? = null
            for (item in allItems) {
                when (item) {
                    is CategorisedChannelList.Category -> pendingCategory = item
                    is CategorisedChannelList.Channel -> {
                        if (item.channel.name?.lowercase()?.contains(q) == true) {
                            if (pendingCategory != null) {
                                filtered.add(pendingCategory)
                                pendingCategory = null
                            }
                            filtered.add(item)
                        }
                    }
                }
            }
            filtered
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Move to channel",
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search channels...") },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        HorizontalDivider()

        LazyColumn {
            items(filteredItems, key = { item ->
                when (item) {
                    is CategorisedChannelList.Category -> "cat_${item.category.id}"
                    is CategorisedChannelList.Channel -> "ch_${item.channel.id}"
                }
            }) { item ->
                when (item) {
                    is CategorisedChannelList.Category -> {
                        Text(
                            text = item.category.title?.uppercase() ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    is CategorisedChannelList.Channel -> {
                        val channel = item.channel
                        if (channel.id == sourceChannelId) return@items

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !loading) {
                                    coroutineScope.launch {
                                        loading = true
                                        val result = moveMessage(
                                            messageId = messageId,
                                            sourceChannelId = sourceChannelId,
                                            targetChannelId = channel.id ?: return@launch
                                        )
                                        loading = false
                                        result
                                            .onSuccess {
                                                onDone()
                                            }
                                            .onFailure { e ->
                                                Toast.makeText(
                                                    context,
                                                    e.message ?: "Failed to move message",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Text(
                                    text = "#",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = channel.name ?: "",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
