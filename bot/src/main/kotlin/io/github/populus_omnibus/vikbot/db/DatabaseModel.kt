package io.github.populus_omnibus.vikbot.db

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 *  Database model, should be compatible with **PostgreSQL** and SQLite
 *  DO-NOT ATTEMPT TO USE MYSQL/MARIADB
 */

object DiscordGuilds : LongIdTable(columnName = "guild") {
    val guild = id
    val newsChannel = long("newsChannel").nullable().default(null)
    val reportChannel = long("reportChannel").nullable().default(null)
    val deletedMessagesChannel = long("deletedMessages").nullable().default(null)
    val messageLoggingLevel = enumeration<MessageLoggingLevel>("messageLogging").default(MessageLoggingLevel.NONE)
    val vikauthChannel = long("vikauthChannel").nullable().default(null)
    // managed channels and rss feeds will be a table, lists are inside out in database
}

object HandledVoiceChannels : LongIdTable(columnName = "channel") {
    val guild = reference("guild", DiscordGuilds, onDelete = ReferenceOption.CASCADE)
    val channel = id
    val channelType = enumeration<VoiceChannelType>("type")
}

object RssFeeds : IntIdTable() {
    val guild = reference("guild", DiscordGuilds, onDelete = ReferenceOption.CASCADE)
    val feed = text("feed")
}

object RoleGroups : IntIdTable() {
    val name = varchar("name", 255)
    val guild = reference("guild", DiscordGuilds, onDelete = ReferenceOption.CASCADE)

    val maxRolesAllowed = integer("maxRoles").default(25)
    val genericRoleId = long("genericRole").nullable()
    init {
        uniqueIndex(name, guild)
    }
}

object PublishData : IntIdTable() {
    val roleGroup = reference("group", RoleGroups, onDelete = ReferenceOption.CASCADE).nullable()
    val guildId = reference("guild", DiscordGuilds, onDelete = ReferenceOption.CASCADE)
    val channelId = long("channel")
    val messageId = long("message")

    init {
        uniqueIndex(roleGroup, guildId) { roleGroup.isNotNull() }
        uniqueIndex(guildId) { roleGroup.isNull() }
    }
}

object RoleEntries : LongIdTable(columnName = "role") {
    val group = reference("group", RoleGroups, onDelete = ReferenceOption.CASCADE)
    val roleId = id // just to make sure
    val description = text("description", eagerLoading = true).default("")

    val emoteName = varchar("emote", 1024).default("")
    val apiName = varchar("apiName", 1024)
    val fullName = varchar("fullName", 1024).nullable().default(null)
}

object UserMessages : LongIdTable() {
    val guildId = long("guild")
    val channelId = long("channel")
    val messageId = id
    val timestamp = timestamp("timestamp").clientDefault { Clock.System.now() }
    val content = text("text")
    val author = long("author")

    // It won't be searchable (not very efficiently at least) but who cares? - kosmx
    val embedLinks = json<List<String>>("embeds", Json).default(listOf())
}

object TagTable : IdTable<String>() {
    override val id = varchar("id", 64).entityId()
    override val primaryKey = PrimaryKey(id)

    val author = long("author").nullable()
    //val title = varchar("title", 256).nullable()
    val content = text("content")
    //val description = varchar("description", 256)
}

object TagAttachments : IntIdTable() {
    val tag = reference("tag", TagTable, onDelete = ReferenceOption.CASCADE)
    val embedName = varchar("name", 256)
    val embed = blob("data")
}

/**
 * Vikauth entries
 * - user is the linked discord account
 * - offline acc has to be searchable by user, only one acc for every discord member
 *
 * - token for user login, this should be indexed
 * - accountId is a uuid, this must be final
 * - displayName is chosen by users
 * - skinUrl is admin-accessible only, probably won't be used
 */
object McOfflineAccounts : IntIdTable() {
    val user = long("user").uniqueIndex()

    val accountId = uuid("uuid").uniqueIndex()
    val token = varchar("token",16)
    val displayName = varchar("display", 32)
    val skinUrl = text("skin").nullable().default(null)

}

/**
 * For whitelisting users and being trackable
 * - user is the linked discord account
 * - accountId is the uuid of the whitelisted account, for premium accounts, no display name is needed
 */
object McLinkedAccounts : IntIdTable() {
    val user = long("user") // not unique, unlimited amount of account are allowed

    val accountId = uuid("uuid").uniqueIndex()
}
