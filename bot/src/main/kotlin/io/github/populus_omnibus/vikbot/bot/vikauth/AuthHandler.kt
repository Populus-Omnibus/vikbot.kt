package io.github.populus_omnibus.vikbot.bot.vikauth

import com.macasaet.fernet.StringValidator
import com.macasaet.fernet.Token
import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.bot.toUserTag
import io.github.populus_omnibus.vikbot.db.McLinkedAccount
import io.github.populus_omnibus.vikbot.db.McLinkedAccounts
import io.github.populus_omnibus.vikbot.db.McOfflineAccount
import io.github.populus_omnibus.vikbot.db.McOfflineAccounts
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.EmbedBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*

class AuthHandler(private val connection: Socket) {
    private suspend fun handleMessage(input: C2SVikAuthPacket): S2CVikAuthPacket = coroutineScope {
        return@coroutineScope transaction {
            if (input.premium) {
                McLinkedAccount.find(McLinkedAccounts.accountId eq UUID.fromString(input.id)).firstOrNull()?.let {
                    S2CVikAuthPacket(
                        id = it.uuid.toString(),
                        displayName = input.username,
                        allowed = true,
                    )
                }
            } else {
                McOfflineAccount.find(McOfflineAccounts.token eq input.username).firstOrNull()?.let {
                    S2CVikAuthPacket(
                        token = it.token,
                        displayName = it.displayName,
                        id = it.uuid.toString(),
                        allowed = true,
                    )
                }
            } ?: S2CVikAuthPacket() // empty reply means access denied.
        }
    }

    suspend operator fun invoke() = coroutineScope {
        connection.use {
            val data = connection.getInputStream()!!.let { input ->
                val dataSize = run {
                    val sizeData = input.readNBytes(4)
                    ByteBuffer.wrap(sizeData).getInt()
                }

                return@let input.readNBytes(dataSize)!!
            }


            val fernetIn = Token.fromString(String(data))
            val input = fernetIn.validateAndDecrypt(VikauthServer.fernetKey, object : StringValidator {})!!
            val inputDecoded = Json.decodeFromString<C2SVikAuthPacket>(input)

            // We only need to reply if login is true
            if (inputDecoded.login) {
                val response = handleMessage(inputDecoded).let {
                    if (it.allowed) {
                        launch { sendMessage(inputDecoded, it) }
                    }

                    Json.encodeToString(it)
                }

                val outData = run {
                    val bytes = Token.generate(VikauthServer.fernetKey, response).serialise().toByteArray()
                    val buffer = ByteBuffer.allocate(bytes.size + Int.SIZE_BITS).apply {
                        putInt(bytes.size)
                        put(bytes)
                    }

                    buffer.array()
                }

                connection.getOutputStream().write(outData)
            } else {
                launch { sendMessage(inputDecoded) }
            }
        }
    }

    private suspend fun sendMessage(c2s: C2SVikAuthPacket, s2c: S2CVikAuthPacket? = null) = coroutineScope {
        val channel = VikBotHandler.config.vikAuthChannel?.let { VikBotHandler.jda.getTextChannelById(it) } ?: return@coroutineScope

        val serverStr = if (c2s.serverName != null) "${c2s.serverName}" else "a server"
        val userId by lazy {
            if (c2s.premium) {
                McLinkedAccount.find(McLinkedAccounts.accountId eq c2s.uuid).first().discordUserId
            } else {
                McOfflineAccount.find(McOfflineAccounts.token eq c2s.username).first().discordUserId
            }
        }

        if (c2s.login && s2c != null) {
            transaction {

                val embed = EmbedBuilder().apply {
                    setTitle("${s2c.displayName} joined $serverStr")
                    setDescription("""
                        ${userId.toUserTag()} is now playing on $serverStr.
                    """.trimIndent())
                }.build()
                channel.sendMessageEmbeds(embed)
            }
        } else {
            transaction {
                val embed = EmbedBuilder().apply {
                    setTitle("${c2s.username} left $serverStr")
                }.build()
                channel.sendMessageEmbeds(embed)
            }
        }.complete()
    }
}

