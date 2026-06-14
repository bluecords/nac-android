package nac.chat.api.internals

import nac.chat.api.StoatAPI
import nac.chat.api.internals.SpecialUsers.PLATFORM_MODERATION_USER
import nac.chat.core.model.schemas.Channel
import nac.chat.core.model.schemas.ChannelType

object DirectMessages {
    fun unreadDMs(): List<Channel> {
        return StoatAPI.channelCache.values
            .filter {
                it.channelType in listOf(
                    ChannelType.DirectMessage, ChannelType.Group
                ) && it.active == true && it.lastMessageID != null
            }
            .filter {
                it.id?.let { id ->
                    StoatAPI.unreads.hasUnread(
                        id,
                        it.lastMessageID!!,
                        serverId = null
                    )
                } ?: false
            }
    }

    fun hasPlatformModerationDM(): Boolean {
        return unreadDMs().any {
            it.channelType == ChannelType.DirectMessage &&
                    it.recipients?.contains(PLATFORM_MODERATION_USER) ?: false
        }
    }

    fun getPlatformModerationDM(): Channel? {
        return unreadDMs().firstOrNull {
            it.channelType == ChannelType.DirectMessage &&
                    it.recipients?.contains(PLATFORM_MODERATION_USER) ?: false
        }
    }
}
