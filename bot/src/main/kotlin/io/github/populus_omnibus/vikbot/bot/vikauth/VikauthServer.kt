package io.github.populus_omnibus.vikbot.bot.vikauth

import com.macasaet.fernet.Key
import io.github.populus_omnibus.vikbot.VikBotHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.slf4j.kotlin.error
import org.slf4j.kotlin.getLogger
import java.io.File
import java.net.ServerSocket

@OptIn(ExperimentalSerializationApi::class)
object VikauthServer {

    val accounts: MCAccounts = File("mc_accounts.json").inputStream().use {
        Json.decodeFromStream(it)
    }

    val fernetKey by lazy {
        Key(VikBotHandler.config.vikAuthFernet)
    }

    private val logger by getLogger()
    fun startVikauthServer(port: Int = 12345): Thread {
        val serverThread = Thread {
            val socket = ServerSocket(port)
            while (true) {
                try {
                    val connection = socket.accept()
                    val handler = AuthHandler(connection)
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
        serverThread.start()
        return serverThread
    }
}