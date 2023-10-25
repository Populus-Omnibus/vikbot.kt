package io.github.populus_omnibus.vikbot.bot.vikauth

import com.macasaet.fernet.Key
import io.github.populus_omnibus.vikbot.VikBotHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.kotlin.debug
import org.slf4j.kotlin.error
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import java.net.ServerSocket

object VikauthServer {


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
}