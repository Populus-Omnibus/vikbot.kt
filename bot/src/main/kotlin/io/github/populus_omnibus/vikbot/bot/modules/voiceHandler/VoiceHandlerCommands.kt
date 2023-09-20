package io.github.populus_omnibus.vikbot.bot.modules.voiceHandler

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent


@Command
object VoiceHandlerCommands : CommandGroup("voice", "manage voice channel manager", {adminOnly()}) {
    init {
        this += object : SlashCommand("addChannel".lowercase(), "Set a channel to be managed") {
            val channel by option("channel", "Select a voice channel", SlashOptionType.CHANNEL).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                try {
                    val voiceChannel = channel.asVoiceChannel().idLong
                    if (voiceChannel in VikBotHandler.config.servers[event.guild!!.idLong].handledVoiceChannels) {
                        event.reply("Channel is already managed")
                    } else {
                        VikBotHandler.config.servers[event.guild!!.idLong].handledVoiceChannels += voiceChannel
                        VikBotHandler.config.save()
                        event.reply("Channel added to managed channels")
                    }
                } catch (e: IllegalStateException) {
                    event.reply("Channel is not a voice channel")
                }.setEphemeral(true).complete()
            }
        }

        this += object : SlashCommand("removeChannel".lowercase(), "Remove a managed channel") {
            val channel by option("channel", "Voice channel to stop being managed", SlashOptionType.CHANNEL).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                if (channel.idLong in VikBotHandler.config.servers[event.guild!!.idLong].handledVoiceChannels) {
                    VikBotHandler.config.servers[event.guild!!.idLong].handledVoiceChannels -= channel.idLong
                    VikBotHandler.config.save()
                    event.reply("Channel is no longer managed")
                } else {
                    event.reply("Channel was not managed voice channel")
                }.setEphemeral(true).complete()
            }
        }

        this += object : SlashCommand("listChannels".lowercase(), "List managed voice channels") {
            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val managedChannels = VikBotHandler.config.servers[event.guild!!.idLong].handledVoiceChannels

                if (managedChannels.isEmpty()) {
                    event.reply("There are no managed voice channel on this server")
                } else {
                    val channelList = managedChannels.joinToString("\n") { "- <#$it>" }
                    event.reply("Managed voice channels:\n$channelList")

                }.setEphemeral(true).complete()
            }
        }
    }
}