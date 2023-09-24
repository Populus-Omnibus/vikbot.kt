package io.github.populus_omnibus.vikbot.db

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID

class DiscordGuild(guild: EntityID<Long>) : LongEntity(guild) {
    companion object : LongEntityClass<DiscordGuild>(DiscordGuilds)

    var guild by DiscordGuilds.guild
    var newsChannel by DiscordGuilds.newsChannel
    var reportChannel by DiscordGuilds.reportChannel
    var deletedMessagesChannel by DiscordGuilds.deletedMessagesChannel
    var messageLoggingLevel by DiscordGuilds.messageLoggingLevel

    /**
     * This will contain all handled voice channels, including temporary ones
     */
    val handledVoiceChannels by HandledVoiceChannel referrersOn HandledVoiceChannels.guild
    val rssFeeds by RssFeed referrersOn RssFeeds.guild
}

class HandledVoiceChannel(channel: EntityID<Long>) : LongEntity(channel) {
    companion object : LongEntityClass<HandledVoiceChannel>(HandledVoiceChannels)

    var guild by HandledVoiceChannels.guild
    var channel by HandledVoiceChannels.channel
    var type by HandledVoiceChannels.channelType
}

class RssFeed(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RssFeed>(RssFeeds)

    var feed by RssFeeds.feed
    var guild by RssFeeds.guild
}

class RoleGroup(group: EntityID<String>) : Entity<String>(group) {
    companion object : EntityClass<String, RoleGroup>(RoleGroups)

    var name by RoleGroups.id
    var maxRolesAllowed by RoleGroups.maxRolesAllowed
    val lastPublished by PublishEntry backReferencedOn PublishData.roleGroup
    var genericRoleId by RoleGroups.genericRoleId
}

class RoleEntry(role: EntityID<Long>) : LongEntity(role) {
    companion object : LongEntityClass<RoleEntry>(RoleEntries)

    var role by RoleEntries.roleId
    var description by RoleEntries.description
    var emoteName by RoleEntries.emoteName
    var apiName by RoleEntries.apiName
    var fullName by RoleEntries.fullName
}

class PublishEntry(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PublishEntry>(PublishData)

    var roleGroup by PublishData.roleGroup
    var guild by PublishData.guildId
    var channelId by PublishData.channelId
    var messageId by PublishData.messageId
}

class UserMessage(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserMessage>(UserMessages)

    var guildId by UserMessages.guildId
    var channelId by UserMessages.channelId
    var messageId by UserMessages.messageId
    var timestamp by UserMessages.timestamp
    var contentRaw by UserMessages.content
}
