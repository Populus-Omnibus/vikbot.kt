package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.bot.chunkedMaxLength
import io.github.populus_omnibus.vikbot.bot.localString
import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.GuildMusicManager.MusicQueryType
import io.github.populus_omnibus.vikbot.bot.stringify
import io.github.populus_omnibus.vikbot.bot.toChannelTag
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.kotlin.getLogger
import kotlin.collections.set
import kotlin.time.Duration.Companion.milliseconds

//@Command(type = CommandType.SERVER)
//replaced by init statements
object MusicPlayerCommands : CommandGroup("music", "Music player") {
    internal val logger by getLogger()
    private val playerInstances: MutableMap<Long, GuildMusicManager> = mutableMapOf()

    @Module
    fun init(bot: VikBotHandler) {
        this += object :
            SlashCommand("next", "show the current playlist") {

            override suspend fun invoke(event: SlashCommandInteractionEvent) = coroutineScope {
                val manager = managerGet(event) ?: return@coroutineScope
                event.deferReply().complete()
                val embed = EmbedBuilder().apply {
                    val (current, next) = manager.trackQuery()
                    setTitle("Playing in " + manager.channel!!.idLong.toChannelTag())
                    addField("Current track", current?.let {it.info.title + " (" + it.duration.milliseconds.stringify() + ")"} ?: "<none>", false)

                    val nextDetails = next.subList(0, minOf(5, next.size)).mapIndexed { index, musicTrack ->
                        "#${index + 1}: ${musicTrack.info.title} (${musicTrack.duration.milliseconds.stringify()})"
                    }.joinToString("\n").chunkedMaxLength(1500).first()
                    addField("Up next", nextDetails, false)
                    setFooter(Clock.System.now().localString)
                }.build()
                event.hook.editOriginalEmbeds(embed).complete()
            }
        }
        this += object : SlashCommand("play", "queue a track for playback") {
            val query by option(
                "query", "expression to search", SlashOptionType.STRING).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val manager = managerGet(event) ?: return@coroutineScope
                event.deferReply().setEphemeral(true).complete()
                val track = manager.queryAudio(query,
                    query.toHttpUrlOrNull()?.let { MusicQueryType.RawURL } ?: MusicQueryType.YouTubeSearch)
                if (track == null) {
                    event.hook.editOriginal("Could not find track").complete()
                    return@coroutineScope
                }
                manager.queue(track)
                event.hook.editOriginal("Queued ${track.info.title}").complete()
            }
        }

        this += object : SlashCommand("volume", "set the guild-wide volume of the bot"){
            val volume by option("volume", "volume to set (0-100)", SlashOptionType.INTEGER)
                .required().default(0x46) //:3

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val manager = playerInstances[event.guild!!.idLong] ?: GuildMusicManager(event.guild!!)
                val actualVol = GuildMusicManager.clampVolume(volume)
                manager.volume = actualVol
                event.reply("Set volume to $actualVol%" +
                        (if(volume != actualVol) " (clamped from $volume%)" else ""))
                    .setEphemeral(true).complete()
            }
        }
        this += object : SlashCommand("skip", "skip the current song") {
            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val manager = managerGet(event, false) ?: return@coroutineScope
                manager.skip()
                event.reply("Skipped").setEphemeral(true).complete()
            }

        }
        bot.guildInitEvent += {
            playerInstances[it.guild.idLong] = GuildMusicManager(it.guild)
        }
        bot.serverCommands += this
    }

    private suspend fun managerGet(event: SlashCommandInteractionEvent, joinIfNeeded: Boolean = true): GuildMusicManager? {
        val manager = playerInstances.getOrDefault(event.guild!!.idLong, GuildMusicManager(event.guild!!))
        if(!manager.isInChannel) {
            if(!joinIfNeeded) {
                event.reply("Not currently playing").setEphemeral(true).complete()
                return null
            }
            event.member?.voiceState?.channel?.let {
                manager.join(it)
            } ?: run {
                event.reply("No voice channel to connect to").setEphemeral(true).complete()
                return null
            }
        }
        return manager
    }
}