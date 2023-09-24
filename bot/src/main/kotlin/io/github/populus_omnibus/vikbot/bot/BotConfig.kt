package io.github.populus_omnibus.vikbot.bot

import io.github.populus_omnibus.vikbot.api.DefaultMap
import io.github.populus_omnibus.vikbot.api.synchronized
import io.github.populus_omnibus.vikbot.bot.RoleGroup.PublishData
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.File


@Serializable
data class BotConfig(
    val token: String,
    val botName: String = "VIKBOT",
    val initActivity: String = "with the old bot",
    val ownerServers: Set<Long>,
    //var mailChannel: Long,
    val embedColor: Int = 0x03FCC2, //HEX VALUE
    val adminId: Long,
    @SerialName("serverEntries")
    private val seMap: MutableMap<Long, ServerEntry> = mutableMapOf<Long,ServerEntry>().synchronized(),
    val vikAuthPort: Int = 12345,
    val vikAuthFernet: String,
    val useRoleTags: Boolean = true,
    val database: DatabaseAccess = DatabaseAccess(),
) {
    val servers: DefaultMap<Long, ServerEntry>
        get() = DefaultMap(seMap) { ServerEntry() }

    @Transient
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    companion object Lock

    @OptIn(ExperimentalSerializationApi::class, InternalCoroutinesApi::class)
    fun save() = synchronized(Lock) {
        File("bot.config.json").outputStream().use { output ->
            json.encodeToStream(this, output)
        }
    }
}

@Serializable
data class DatabaseAccess(
    val address: String = "jdbc:sqlite:data.db",
    val driver: String = "org.sqlite.JDBC",
    val username: String = "",
    val password: String = "",
)

@Serializable
data class ServerEntry(
    var newsChannel: Long? = null,
    var reportChannel: Long? = null,
    var deletedMessagesChannel: Long? = null,
    var lastRoleResetMessage: PublishData? = null,
    var messageLoggingLevel: MessageLoggingLevel = MessageLoggingLevel.NONE,
    val handledVoiceChannels: MutableList<Long> = mutableListOf(),
    var rssFeeds: MutableList<String> = mutableListOf(),
    @SerialName("roleGroups")
    private val rgMap: MutableMap<String, RoleGroup> = mutableMapOf<String, RoleGroup>().synchronized(), //second is the group in which the role is
){
    val roleGroups: DefaultMap<String, RoleGroup>
        get() = DefaultMap(rgMap) { RoleGroup() }
}

@Serializable
data class RoleGroup(
    val roles: MutableList<RoleEntry> = mutableListOf(),
    var maxRolesAllowed: Int? = null,
    var lastPublished: PublishData? = null,
    var genericRoleId: Long? = null
) {
    @Serializable
    data class PublishData(val channelId: Long, val messageId: Long)

    @Serializable
    data class RoleEntry(
        val roleId: Long,
        var descriptor: RoleDescriptor
    ) {
        @Serializable
        data class RoleDescriptor(
            val emoteName: String, //the full name of the emote that will be displayed in the role selector
            val apiName: String,
            val fullName: String, //custom name for the role, can be different from the role's actual name
            val description: String, //the description that will be displayed in the role selector
        )
    }
}

@Serializable
enum class MessageLoggingLevel {
    NONE, DELETED, ANY
}
