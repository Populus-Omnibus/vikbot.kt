package io.github.populus_omnibus.vikbot.bot.modules.mcAuth

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.bot.vikauth.VikauthServer
import org.slf4j.kotlin.getLogger

object McAuthModule {

    private val logger by getLogger()

    private var handlerThread : Thread? = null

    val status: String
        get() = if (handlerThread == null) "stopped" else "running"

    @Module
    fun loadModule(handler: VikBotHandler) {
        handler.initEvent += {
            start()
        }
    }

    fun start() {
        handlerThread = VikauthServer.startVikauthServer()
    }

    fun stop() {
        handlerThread?.interrupt()
        handlerThread?.join()
        handlerThread = null
    }
}