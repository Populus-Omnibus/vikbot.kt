package io.github.populus_omnibus.vikbot.bot.vikauth

import com.macasaet.fernet.StringValidator
import com.macasaet.fernet.Token
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*

class AuthHandler(val connection: Socket) {
    private suspend fun handleMessage(input: C2SVikAuthPacket): S2CVikAuthPacket? = coroutineScope {
         VikauthServer.accounts[input.token]?.let {
             S2CVikAuthPacket(
                 token = it.token,
                 displayName = it.displayName,
                 id = it.id
             )
         }
    }

    suspend operator fun invoke() = coroutineScope {
        val data = connection.getInputStream()!!.use { input ->
            val dataSize = run {
                val sizeData = input.readNBytes(4)
                ByteBuffer.wrap(sizeData).getInt()
            }

            return@use input.readNBytes(dataSize)!!
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

        connection.getOutputStream().use {
            it.write(outData)
        }
    }

    @Serializable
    private data class C2SVikAuthPacket(
        val token: String,
    )

    @Serializable
    private data class S2CVikAuthPacket @OptIn(ExperimentalSerializationApi::class) constructor(
        val token: String,
        val id: String,
        @JsonNames("displayname")
        val displayName: String,
        @JsonNames("skin_url")
        val skinUrl: String? = null,
    ) {

        val uuid: UUID
            get() = UUID.fromString(id)
    }
}