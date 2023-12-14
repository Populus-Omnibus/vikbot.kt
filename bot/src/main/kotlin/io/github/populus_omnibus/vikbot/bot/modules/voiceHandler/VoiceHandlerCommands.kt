package io.github.populus_omnibus.vikbot.bot.modules.voiceHandler

import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.administrator
import io.github.populus_omnibus.vikbot.db.HandledVoiceChannel
import io.github.populus_omnibus.vikbot.db.HandledVoiceChannels
import io.github.populus_omnibus.vikbot.db.Servers
import io.github.populus_omnibus.vikbot.db.VoiceChannelType
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction


@Command
object VoiceHandlerCommands : CommandGroup("voice", "manage voice channel manager", {
    administrator()
}) {
    init {
        this += object : SlashCommand("addChannel".lowercase(), "Set a channel to be managed") {
            val channel by option("channel", "Select a voice channel", SlashOptionType.CHANNEL).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                try {
                    val voiceChannel = channel.asVoiceChannel().idLong
                    transaction {
                        if (HandledVoiceChannel.findById(voiceChannel)?.type == VoiceChannelType.VoiceRequest) {
                            event.reply("Channel is already managed")
                        } else {
                            HandledVoiceChannel.new(voiceChannel) {
                                type = VoiceChannelType.VoiceRequest
                                guild = Servers[event.guild!!.idLong]
                            }
                            event.reply("Channel added to managed channels")
                        }
                    }
                } catch (e: IllegalStateException) {
                    event.reply("Channel is not a voice channel")
                }.setEphemeral(true).complete()
            }
        }

        this += object : SlashCommand("removeChannel".lowercase(), "Remove a managed channel") {
            val channel by option("channel", "Voice channel to stop being managed", SlashOptionType.CHANNEL).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                transaction {
                    val ch = HandledVoiceChannel.find {
                        HandledVoiceChannels.channel eq channel.idLong and (HandledVoiceChannels.channelType eq VoiceChannelType.Temp) and (HandledVoiceChannels.guild eq channel.guild.idLong)
                    }.firstOrNull()
                    if (ch != null) {
                        ch.delete()
                        event.reply("Channel is no longer managed")
                    } else {
                        event.reply("Channel was not managed voice channel")
                    }
                }.setEphemeral(true).complete()
            }
        }

        this += object : SlashCommand("listChannels".lowercase(), "List managed voice channels") {
            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val managedChannels = transaction { Servers[event.guild!!.idLong].handledVoiceChannels }
                if (managedChannels.empty()) {
                    event.reply("There are no managed voice channel on this server")
                } else {
                    val channelList = managedChannels.joinToString("\n") { "- <#$it>  ${it.type}" }
                    event.reply("Managed voice channels:\n$channelList")

                }.setEphemeral(true).complete()
            }
        }
    }
}