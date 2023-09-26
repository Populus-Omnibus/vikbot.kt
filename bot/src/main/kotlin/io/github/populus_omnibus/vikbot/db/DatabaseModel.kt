package io.github.populus_omnibus.vikbot.db

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object DiscordGuilds : LongIdTable(columnName = "guild") {
    val guild = id
    val newsChannel = long("newsChannel").nullable().default(null)
    val reportChannel = long("reportChannel").nullable().default(null)
    val deletedMessagesChannel = long("deletedMessages").nullable().default(null)
    val messageLoggingLevel = enumeration<MessageLoggingLevel>("messageLogging").default(MessageLoggingLevel.NONE)
    // managed channels and rss feeds will be a table, lists are inside out in database
}

object HandledVoiceChannels : LongIdTable(columnName = "channel") {
    val guild = reference("guild", DiscordGuilds)
    val channel = id
    val channelType = enumeration<VoiceChannelType>("type")
}

object RssFeeds : IntIdTable() {
    val guild = reference("guild", DiscordGuilds)
    val feed = text("feed")
}

object RoleGroups : IntIdTable() {
    val name = varchar("id", 255)
    val guild = reference("guild", DiscordGuilds)

    val maxRolesAllowed = integer("maxRoles").default(25)
    val genericRoleId = long("genericRole").nullable()
    init {
        uniqueIndex(name, guild)
    }
}

object PublishData : IntIdTable() {
    val roleGroup = reference("group", RoleGroups).nullable().uniqueIndex()
    val guildId = reference("guild", DiscordGuilds)
    val channelId = long("channel")
    val messageId = long("message")

    init {
        uniqueIndex(roleGroup, guildId)
    }
}

object RoleEntries : LongIdTable(columnName = "role") {
    val group = reference("group", RoleGroups)
    val roleId = id // just to make sure
    val description = text("description", eagerLoading = true).default("")

    val emoteName = varchar("emote", 1024).default("")
    val apiName = varchar("apiName", 1024)
    val fullName = varchar("fullName", 1024).defaultExpression(apiName)
}

object UserMessages : LongIdTable() {
    val guildId = long("guild")
    val channelId = long("channel")
    val messageId = id
    val timestamp = timestamp("timestamp").clientDefault { Clock.System.now() }
    val content = text("text", eagerLoading = true)
}

