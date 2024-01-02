package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.bot.localString
import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.ytWrapper.YtDlpWrapper
import io.github.populus_omnibus.vikbot.bot.modules.roleselector.RoleSelectorGroupAutocompleteString
import io.github.populus_omnibus.vikbot.bot.toChannelTag
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.slf4j.kotlin.getLogger
import kotlin.collections.set
import kotlin.time.Duration.Companion.seconds

//@Command(type = CommandType.SERVER)
//replaced by init statements
object MusicPlayerCommands : CommandGroup("music", "Music player") {
    internal val logger by getLogger()
    private val playerInstances: MutableMap<Long, MusicPlayer> = mutableMapOf()

    @Module
    fun init(bot: VikBotHandler) {
        if(!YtDlpWrapper.dependenciesMet) {
            logger.info("Music player module requires yt-dlp to be installed")
            return
        }
        this += object :
            SlashCommand("test", "joins a channel, then leaves") {

            override suspend fun invoke(event: SlashCommandInteractionEvent) = coroutineScope {
                event.deferReply().setEphemeral(true).complete()
                val player = playerInstances[event.guild!!.idLong] ?: MusicPlayer(event.guild!!)
                player.channel?.let {
                    event.hook.editOriginal("Already in channel <#${it.idLong}>").complete()
                    return@coroutineScope
                }
                val channel = event.member!!.voiceState!!.channel
                if (channel == null) {
                    event.hook.editOriginal("You must be in a voice channel to use this command").complete()
                    return@coroutineScope
                }
                player.join(channel)
                event.hook.editOriginal("Joined channel <#${channel.idLong}>").complete()
                Thread.sleep(2000)
                player.leave()
            }
        }
        this += object :
            SlashCommand("next", "show the current playlist") {

            override suspend fun invoke(event: SlashCommandInteractionEvent) = coroutineScope {
                //TODO: lock?
                val player = playerInstances[event.guild!!.idLong] ?: MusicPlayer(event.guild!!)
                if(!player.isInChannel) {
                    event.reply("Not currently connected").setEphemeral(true).complete()
                    return@coroutineScope
                }
                event.deferReply().complete()
                val embed = EmbedBuilder().apply {
                    val track = player.currentTrack
                    setTitle("Playing in " + player.channel!!.idLong.toChannelTag())
                    addField("Current track", track?.let {it.title + " (" + it.duration + ")"} ?: "<none>", false)
                    val nextDetails = player.playlist.subList(0, minOf(5, player.playlist.size)).mapIndexed { index, musicTrack ->
                        "#${index + 1}: ${musicTrack.title} (${musicTrack.duration})"
                    }.chunked(1500).first().joinToString("\n")
                    addField("Up next", nextDetails, false)
                    setFooter(Clock.System.now().localString)
                }.build()
                event.hook.editOriginalEmbeds(embed).complete()
            }
        }
        this += object :
        SlashCommand("playnow", "play a track immediately") {
            val url by option(
                "url", "url of track", SlashOptionType.STRING
            ).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val player = playerInstances[event.guild!!.idLong] ?: MusicPlayer(event.guild!!)
                player.join(event.member!!.voiceState!!.channel)
                val track = MusicTrack(
                    title = "test",
                    url = url,
                    duration = 0.seconds,
                )
                player.queue(track)
                player.rotate()
                player.playImmediately()
                event.reply("Playing ${track.title}").setEphemeral(true).complete()
            }
        }
        bot.guildInitEvent += {
            playerInstances[it.guild.idLong] = MusicPlayer(it.guild)
        }
        bot.serverCommands += this
    }
}