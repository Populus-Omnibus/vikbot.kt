package io.github.populus_omnibus.vikbot.bot

import io.github.populus_omnibus.vikbot.bot.modules.updater.UpdaterConfig
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.File


@Serializable
data class BotConfig(
    val token: String,
    val botName: String = "VIKBOT",
    val initActivity: String = "following instructions",
    val ownerServers: Set<Long>,
    //var mailChannel: Long,
    val embedColor: Int = 0x03FCC2, //HEX VALUE
    val adminId: Long,
    val vikAuthPort: Int = 12345,
    val vikAuthFernet: String,
    var vikAuthChannel: Long? = null, // VikAuth logging channel
    var syslogChannel: Long? = null, // bot syslog channel, primarily for updater
    val updaterToken: String = "default_token",
    val useRoleTags: Boolean = true,
    val activeTimeZone: String = "UTC", // CET for Hungary
    val database: DatabaseAccess = DatabaseAccess(),
    val updater: UpdaterConfig = UpdaterConfig(),
) {

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
) {
    override fun toString(): String {
        return "DatabaseAccess(address='$address', driver='$driver', username='$username')"
    }
}
