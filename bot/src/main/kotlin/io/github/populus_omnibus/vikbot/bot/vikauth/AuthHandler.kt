package io.github.populus_omnibus.vikbot.bot.vikauth

import com.macasaet.fernet.StringValidator
import com.macasaet.fernet.Token
import io.github.populus_omnibus.vikbot.db.McLinkedAccount
import io.github.populus_omnibus.vikbot.db.McLinkedAccounts
import io.github.populus_omnibus.vikbot.db.McOfflineAccount
import io.github.populus_omnibus.vikbot.db.McOfflineAccounts
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*

class AuthHandler(private val connection: Socket) {
    private suspend fun handleMessage(input: C2SVikAuthPacket): S2CVikAuthPacket? = coroutineScope {
        return@coroutineScope transaction {
            if (input.premium) {
                McLinkedAccount.find(McLinkedAccounts.accountId eq UUID.fromString(input.id)).firstOrNull()?.let {
                    S2CVikAuthPacket(
                        id = it.uuid.toString()
                    )
                }
            } else {
                McOfflineAccount.find(McOfflineAccounts.token eq input.username).firstOrNull()?.let {
                    S2CVikAuthPacket(
                        token = it.token,
                        displayName = it.displayName,
                        id = it.uuid.toString()
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
            val response = handleMessage(Json.decodeFromString<C2SVikAuthPacket>(input))?.let {
                Json.encodeToString(it)
            } ?: "{}"

            val outData = run {
                val bytes = Token.generate(VikauthServer.fernetKey, response).serialise().toByteArray()
                val buffer = ByteBuffer.allocate(bytes.size + Int.SIZE_BITS).apply {
                    putInt(bytes.size)
                    put(bytes)
                }

                buffer.array()
            }

            connection.getOutputStream().write(outData)
        }
    }

}