package io.github.populus_omnibus.vikbot.bot

import io.github.populus_omnibus.vikbot.api.synchronized
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
) {
    val servers: DefaultMap<Long, ServerEntry>
        get() = DefaultMap(seMap) { ServerEntry() }

    @Transient
    private val json = Json { prettyPrint = true }
    companion object Lock

    @OptIn(ExperimentalSerializationApi::class, InternalCoroutinesApi::class)
    fun save() = synchronized(Lock) {
        File("bot.config.json").outputStream().use { output ->
            json.encodeToStream(this, output)
        }
    }

    fun getRoleGroup(guildId: Long, groupName: String) : RoleGroup {
        return servers[guildId].roleGroups[groupName]
    }
}

@Serializable
data class ServerEntry(
    var newsChannel: Long? = null,
    var reportChannel: Long? = null,
    var deletedMessagesChannel: Long? = null,
    @SerialName("roleGroups")
    private val rgMap: MutableMap<String, RoleGroup> = mutableMapOf<String, RoleGroup>().synchronized(), //second is the group in which the role is
){
    val roleGroups: DefaultMap<String, RoleGroup>
        get() = DefaultMap(rgMap) { RoleGroup() }
}

@Serializable
data class RoleGroup(
    val roles: MutableList<RoleEntry> = mutableListOf(),
    val maxRolesAllowed: Int? = null,
)
    @Serializable
    data class RoleEntry(
        val roleId: Long,
        val descriptor: RoleDescriptor
    ) {
        @Serializable
        data class RoleDescriptor(
            val emoteName: String, //the full name of the emote that will be displayed in the role selector
            val apiName: String,
            val fullName: String, //custom name for the role, can be different from the role's actual name
            val description: String, //the description that will be displayed in the role selector
        )
}