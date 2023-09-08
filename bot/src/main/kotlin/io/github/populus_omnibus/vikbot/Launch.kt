package io.github.populus_omnibus.vikbot

import io.github.populus_omnibus.vikbot.bot.ModuleLoader
import io.github.populus_omnibus.vikbot.bot.BotConfig
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

/**
 * program entry point, initialize bot, modules
 */
object Launch {
    @OptIn(ExperimentalSerializationApi::class)
    @JvmStatic
    fun main(args: Array<String>) {

        val parser = ArgParser("VIKBOT")

        val configFile: String by parser.option(ArgType.String, fullName = "configFile", shortName = "c").default("bot.config.json")

        parser.parse(args)

        val config = File(configFile).inputStream().use { input ->
            Json.decodeFromStream<BotConfig>(input)
        }

        // dynamic module loading
        ModuleLoader(VikBotHandler)

        VikBotHandler.start(config)
    }
}