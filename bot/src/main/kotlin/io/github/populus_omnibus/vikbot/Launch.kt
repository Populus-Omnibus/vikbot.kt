package io.github.populus_omnibus.vikbot

import io.github.populus_omnibus.vikbot.bot.ModuleLoader
import io.github.populus_omnibus.vikbot.bot.BotConfig
import io.github.populus_omnibus.vikbot.db.DatabaseHandler
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import java.io.File

/**
 * program entry point, initialize bot, modules
 */
object Launch {

    private val logger by getLogger()

    private val json = Json { ignoreUnknownKeys = true }

    @JvmStatic
    fun main(args: Array<String>) {

        loadConfigAndDB(args)

        // dynamic module loading
        logger.info { "Start loading modules" }
        ModuleLoader(VikBotHandler)

        logger.info { "Starting VikBot" }
        VikBotHandler.start()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun loadConfigAndDB(args: Array<String>) {

        val parser = ArgParser("VIKBOT")

        val configFile: String by parser.option(ArgType.String, fullName = "configFile", shortName = "c").default("bot.config.json")

        parser.parse(args)

        VikBotHandler.config = File(configFile).inputStream().use { input ->
            json.decodeFromStream<BotConfig>(input)
        }

        logger.info { "Loading DB: ${VikBotHandler.config.database}" }
        DatabaseHandler.loadDatabase(VikBotHandler.config.database)

    }
}