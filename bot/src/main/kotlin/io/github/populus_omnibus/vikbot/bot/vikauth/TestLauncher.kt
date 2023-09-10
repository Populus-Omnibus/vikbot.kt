package io.github.populus_omnibus.vikbot.bot.vikauth

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.bot.BotConfig
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
object TestLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("VIKBOT")

        val configFile: String by parser.option(ArgType.String, fullName = "configFile", shortName = "c").default("bot.config.json")

        parser.parse(args)


        VikBotHandler.config = File(configFile).inputStream().use { input ->
            Json.decodeFromStream<BotConfig>(input)
        }

        val job = VikauthServer.startVikauthServer(VikBotHandler.config.vikAuthPort)
        job.join()
    }
}