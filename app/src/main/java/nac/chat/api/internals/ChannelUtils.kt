package nac.chat.api.internals

import nac.chat.api.StoatAPI
import nac.chat.core.model.schemas.Channel
import nac.chat.core.model.schemas.Server
import nac.chat.core.model.schemas.User

sealed class CategorisedChannelList {
    data class Channel(val channel: nac.chat.core.model.schemas.Channel) :
        CategorisedChannelList()

    data class Category(val category: nac.chat.core.model.schemas.Category) :
        CategorisedChannelList()
}

object ChannelUtils {
    /**
     * Resolves the name of a channel, preferring the name of the channel itself, then the name of the first recipient.
     * @param channel The channel to resolve the name of.
     * @return The name of the channel, or the name of the first recipient if the channel is a DM.
     * @see User.resolveDefaultName
     */
    fun resolveName(channel: Channel): String? {
        channel.name?.let { return it }
        val partnerId = channel.recipients?.firstOrNull { u -> u != StoatAPI.selfId }
            ?: return null
        // Prefer the recipient's server nickname (DMs only carry a plain User with no
        // server context). Mirrors web's User.serverNickname so DMs read consistently
        // with the member list in this single-server community.
        resolveServerNickname(partnerId)?.let { return it }
        return StoatAPI.userCache[partnerId]?.let { User.resolveDefaultName(it) }
    }

    /**
     * Look across every server the current user shares with [userId] for a ServerMember
     * nickname, returning the first non-blank one. Used to prefer a nickname over the raw
     * global username in DM display contexts.
     */
    fun resolveServerNickname(userId: String): String? {
        for (serverId in StoatAPI.serverCache.keys) {
            val nickname = StoatAPI.members.getMember(serverId, userId)?.nickname
            if (!nickname.isNullOrBlank()) return nickname
        }
        return null
    }

    fun resolveDMPartner(channel: Channel): String? {
        return channel.recipients?.firstOrNull { u -> u != StoatAPI.selfId }
    }

    fun categoriseServerFlat(server: Server): List<CategorisedChannelList> {
        val output = mutableListOf<CategorisedChannelList>()

        val uncategorised =
            server.channels?.filter { c ->
                server.categories?.none { cat ->
                    cat.channels?.contains(
                        c
                    ) == true
                } ?: true
            }
                ?.mapNotNull {
                    StoatAPI.channelCache[it]?.let { it1 ->
                        CategorisedChannelList.Channel(it1)
                    }
                } ?: emptyList()
        output.addAll(uncategorised)

        val categories =
            server.categories?.map { CategorisedChannelList.Category(it) } ?: emptyList()
        categories.forEach {
            output.add(it)
            val channels = it.category.channels?.mapNotNull { c ->
                StoatAPI.channelCache[c]?.let { it1 ->
                    CategorisedChannelList.Channel(it1)
                }
            } ?: emptyList()
            output.addAll(channels)
        }

        return output
    }
}
