package io.github.populus_omnibus.vikbot.db

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.replace

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


    val lastRoleResetMessage: PublishEntry?
        get() = PublishEntry.find { PublishData.guildId eq guild and (PublishData.roleGroup eq null) }.firstOrNull()

    fun setLastRoleResetMessage(channel: Long, message: Long) {
        PublishData.replace {
            it[guildId] = guild
            it[roleGroup] = null
            it[channelId] = channel
            it[messageId] = message
        }
    }

    inner class RoleGroupAccessor : SizedIterable<RoleGroup> by referrerRoleGroups {
        operator fun get(name: String) =
            getOrNull(name) ?: RoleGroup.new { this.name = name; this.guild = this@DiscordGuild }

        fun getOrNull(name: String) =
            RoleGroup.find { (RoleGroups.guild eq this@DiscordGuild.guild) and (RoleGroups.name eq name) }.firstOrNull()

        fun newRoleGroup(name: String, lambda: RoleGroup.() -> Unit = {}) = RoleGroup.new {
            this.name = name
            this.guild = this@DiscordGuild
            lambda()
        }

        operator fun contains(name: String): Boolean {
            return RoleEntry.find { RoleGroups.name eq name and (RoleGroups.guild eq this@DiscordGuild.guild) }.any()
        }
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

    var name by RoleGroups.name
    var guild by DiscordGuild referencedOn RoleGroups.guild
    var maxRolesAllowed by RoleGroups.maxRolesAllowed
    val lastPublished by PublishEntry optionalBackReferencedOn  PublishData.roleGroup
    var genericRoleId by RoleGroups.genericRoleId
    private val rolesIt by RoleEntry referrersOn RoleEntries.group

    val roles: RolesAccessor
        get() = RolesAccessor()

    inner class RolesAccessor : SizedIterable<RoleEntry> by rolesIt {
        operator fun get(index: Int): RoleEntry {
            return this.sortedBy { it.apiName }[index]
        }
    }

    fun updateLastPublished(channel: Long, message: Long) {
        (lastPublished ?: PublishEntry.new { this.guild = guild; roleGroup = this@RoleGroup.id })
            .apply {
                this.channelId = channel
                this.messageId = message
            }
    }
}

class RoleEntry(role: EntityID<Long>) : LongEntity(role) {
    companion object : LongEntityClass<RoleEntry>(RoleEntries)

    var roleGroup by RoleGroup referencedOn RoleEntries.group
    val role by RoleEntries.roleId
    var description by RoleEntries.description
    var emoteName by RoleEntries.emoteName
    var apiName by RoleEntries.apiName

    private var _fullName by RoleEntries.fullName
    var fullName: String
        get() = _fullName ?: apiName
        set(value) {
            _fullName = value
        }

    val roleId by role
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
    val messageId by UserMessages.messageId
    val idLong by messageId
    var timestamp by UserMessages.timestamp
    var contentRaw by UserMessages.content
    var author by UserMessages.author

    var embedLinks by UserMessages.embedLinks
}
