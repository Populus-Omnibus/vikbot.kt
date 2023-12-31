package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.operator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

/**
 * Bot system logging module.
 */
@Command(CommandType.OWNER)
object Syslog : SlashCommand("syslogChannel".lowercase(), "System logging channel, may be null.", {operator()}) {
    val channel by option("channel", "System logging channel, may be null.", SlashOptionType.CHANNEL)

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        VikBotHandler.config.syslogChannel = channel?.idLong
        VikBotHandler.config.save()
        event.reply("System logging channel set to ${channel?.asMention ?: "null"}.").setEphemeral(true).complete()
    }


    fun log(message: String) {
        val channel = VikBotHandler.config.syslogChannel?.let { VikBotHandler.jda.getTextChannelById(it) }
        channel?.sendMessage(message)?.complete()
    }

    suspend fun queueLog(message: String) = coroutineScope {
        launch (Dispatchers.IO) {
            val channel = VikBotHandler.config.syslogChannel?.let { VikBotHandler.jda.getTextChannelById(it) }
            channel?.sendMessage(message)?.complete()
        }
    }
}