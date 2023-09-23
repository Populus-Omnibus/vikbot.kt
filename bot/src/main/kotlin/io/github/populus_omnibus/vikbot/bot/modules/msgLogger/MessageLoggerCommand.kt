package io.github.populus_omnibus.vikbot.bot.modules.msgLogger

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.*
import io.github.populus_omnibus.vikbot.bot.MessageLoggingLevel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

@Command(type = CommandType.SERVER)
object MessageLoggerCommand : CommandGroup("logger", "Message logger", {adminOnly()}) {
    init {
        this += object : SlashCommand("setChannel".lowercase(), "Set deleted messages channel") {
            val channel by option("channel", "channel", SlashOptionType.CHANNEL).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val server = VikBotHandler.config.servers[event.guild!!.idLong]
                server.deletedMessagesChannel = channel.idLong

                VikBotHandler.config.save()
                event.reply("Deleted message logging channel is set to <#${channel.idLong}.").setEphemeral(true).complete()
            }
        }

        this += object : SlashCommand("setMode".lowercase(), "set logger mode") {
            val mode by option("mode", "new mode", ListOptionType.ofEnum<MessageLoggingLevel>()).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val server = VikBotHandler.config.servers[event.guild!!.idLong]
                server.messageLoggingLevel = mode
                VikBotHandler.config.save()
                event.reply("Config updated").setEphemeral(true).complete()
            }
        }

        this += object : SlashCommand("info", "Get current stats") {
            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val currentServer = VikBotHandler.config.servers[event.guild!!.idLong]
                event.reply("""
                    Logger on this server is set to ${currentServer.messageLoggingLevel}
                    Target channel is <#${currentServer.deletedMessagesChannel}>
                    Currently there are ${MessageLogger.currentTrackedMessageCount} cached messages from the last 2 weeks
                """.trimIndent()).complete()
            }
        }
    }
}