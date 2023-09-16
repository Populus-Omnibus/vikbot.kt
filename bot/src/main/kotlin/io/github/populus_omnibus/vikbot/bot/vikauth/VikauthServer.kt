package io.github.populus_omnibus.vikbot.bot.vikauth

import com.macasaet.fernet.Key
import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.slf4j.kotlin.debug
import org.slf4j.kotlin.error
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import java.io.File
import java.net.ServerSocket

@OptIn(ExperimentalSerializationApi::class)
object VikauthServer {

    val accounts: MCAccounts = File("mc_accounts.json").inputStream().use {
        Json.decodeFromStream<MCAccounts>(it).synchronized()
    }

    val fernetKey by lazy {
        Key(VikBotHandler.config.vikAuthFernet)
    }

    private val logger by getLogger()
    fun startVikauthServer(port: Int = VikBotHandler.config.vikAuthPort): Thread {
        val serverThread = Thread {
            val socket = ServerSocket(port)
            logger.info { "Server is listening on port $port" }
            while (true) {
                try {
                    val connection = socket.accept()
                    val handler = AuthHandler(connection)
                    logger.debug { "Client connected from ${connection.inetAddress}" }
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            handler()
                        } catch (e: Throwable) {
                            logger.error(e) { "Exception while handling vikauth request" }
                        }
                    }
                } catch (e: Throwable) {
                    logger.error(e) { "Exception on vikauth listener" }
                }
            }
        }
        serverThread.isDaemon = true
        logger.info { "Starting VikAuth server" }
        serverThread.start()
        return serverThread
    }

    fun save() = synchronized(accounts){
        File("mc_accounts.json").outputStream().use {
            Json.encodeToStream(accounts, it)
        }
    }
}