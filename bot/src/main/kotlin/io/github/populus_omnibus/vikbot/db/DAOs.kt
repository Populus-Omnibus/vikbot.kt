package io.github.populus_omnibus.vikbot.db

import net.dv8tion.jda.api.requests.restaction.GuildAction.RoleData
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.and

class DiscordGuild(guild: EntityID<Long>) : LongEntity(guild) {
    companion object : LongEntityClass<DiscordGuild>(DiscordGuilds) {
        fun getOrCreate(id: Long): DiscordGuild {
            return DiscordGuild.findById(id) ?: DiscordGuild.new(id) {  }
        }
    }

    val guild by DiscordGuilds.guild
    var newsChannel by DiscordGuilds.newsChannel
    var reportChannel by DiscordGuilds.reportChannel
    var deletedMessagesChannel by DiscordGuilds.deletedMessagesChannel
    var messageLoggingLevel by DiscordGuilds.messageLoggingLevel

    /**
     * This will contain all handled voice channels, including temporary ones
     */
    val handledVoiceChannels by HandledVoiceChannel referrersOn HandledVoiceChannels.guild
    val rssFeeds by RssFeed referrersOn RssFeeds.guild
    val referrerRoleGroups by RoleGroup referrersOn RoleGroups.guild

    val roleGroups: RoleGroupAccessor
        get() = RoleGroupAccessor()

    inner class RoleGroupAccessor : SizedIterable<RoleGroup> by referrerRoleGroups {
        operator fun get(name: String) = RoleGroup.find { (RoleGroups.guild eq this@DiscordGuild.guild) and (RoleGroups.name eq name) }.first()
    }
}

class HandledVoiceChannel(channel: EntityID<Long>) : LongEntity(channel) {
    companion object : LongEntityClass<HandledVoiceChannel>(HandledVoiceChannels)

    var guild by DiscordGuild referencedOn HandledVoiceChannels.guild
    val channel by HandledVoiceChannels.channel
    var type by HandledVoiceChannels.channelType
}

class RssFeed(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RssFeed>(RssFeeds)

    var feed by RssFeeds.feed
    var guild by RssFeeds.guild
}

class RoleGroup(group: EntityID<Int>) : IntEntity(group) {
    companion object : IntEntityClass<RoleGroup>(RoleGroups)

    val name by RoleGroups.name
    val guild by DiscordGuild referencedOn RoleGroups.guild
    var maxRolesAllowed by RoleGroups.maxRolesAllowed
    val lastPublished by PublishEntry backReferencedOn PublishData.roleGroup
    var genericRoleId by RoleGroups.genericRoleId
    val roles by RoleEntry referrersOn RoleEntries.group
}

class RoleEntry(role: EntityID<Long>) : LongEntity(role) {
    companion object : LongEntityClass<RoleEntry>(RoleEntries)

    val roleGroup by RoleGroup referencedOn RoleEntries.group
    val role by RoleEntries.roleId
    var description by RoleEntries.description
    var emoteName by RoleEntries.emoteName
    var apiName by RoleEntries.apiName
    var fullName by RoleEntries.fullName
}

class PublishEntry(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PublishEntry>(PublishData)

    var roleGroup by PublishData.roleGroup
    val guild by PublishData.guildId
    var channelId by PublishData.channelId
    var messageId by PublishData.messageId
}

class UserMessage(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserMessage>(UserMessages)

    var guildId by UserMessages.guildId
    var channelId by UserMessages.channelId
    val messageId by UserMessages.messageId
    var timestamp by UserMessages.timestamp
    var contentRaw by UserMessages.content
}
