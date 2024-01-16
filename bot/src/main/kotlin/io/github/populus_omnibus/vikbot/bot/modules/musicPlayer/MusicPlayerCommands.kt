package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.bot.isBotAdmin
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.kotlin.getLogger
import kotlin.collections.set

@Suppress("unused")
object MusicPlayerCommands : CommandGroup("music", "Music player") {
    internal val logger by getLogger()
    private val managerInstances: MutableMap<Long, GuildMusicManager> = mutableMapOf()

    @Module
    fun init(bot: VikBotHandler) {
        bot.shutdownEvent += {
            managerInstances.values.forEach { it.leave(true) }
        }
        this += object :
            SlashCommand("next", "show the current playlist") {

            override suspend fun invoke(event: SlashCommandInteractionEvent) = coroutineScope {
                val manager = managerGet(event, false) ?: return@coroutineScope
                event.deferReply().complete()
                val embed = manager.generateTrackerMessageEmbed()
                event.hook.editOriginalEmbeds(embed).complete()
            }
        }
        this += object : SlashCommand("play", "queue a track for playback") {
            val query by option(
                "query", "expression to search", SlashOptionType.STRING).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                playAudio(event, query, false)
            }
        }
        this += object : SlashCommand("playnow", "clear the queue and play a track immediately") {
            val query by option(
                "query", "expression to search", SlashOptionType.STRING).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                if(event.member?.isBotAdmin == false){
                    event.reply("You are not authorised to use this command").setEphemeral(true).complete()
                    return@coroutineScope
                }
                playAudio(event, query, true)
            }
        }

        this += object : SlashCommand("volume", "set the guild-wide volume of the bot"){
            val volume by option("volume", "volume to set (0-100)", SlashOptionType.INTEGER)
                .default(0x46) //:3

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val manager = managerInstances[event.guild!!.idLong] ?: GuildMusicManager(event.guild!!)
                val actualVol = GuildMusicManager.clampVolume(volume)
                manager.volume = actualVol
                event.reply("Set volume to $actualVol%" +
                        (if(volume != actualVol) " (clamped from $volume%)" else ""))
                    .setEphemeral(true).complete()
            }
        }
        this += object : SlashCommand("skip", "skip the current song") {
            val num by option("num", "number of songs to skip", SlashOptionType.INTEGER).default(1)

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val manager = managerGet(event, false) ?: return@coroutineScope
                manager.skip(num)
                event.reply("Skipped").setEphemeral(true).complete()
            }

        }
        this += object : SlashCommand("pause", "pause playback") {
            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val manager = managerGet(event, false) ?: return@coroutineScope
                manager.pause()
                event.reply("Paused").setEphemeral(true).complete()
            }
        }
        this += object : SlashCommand("resume", "resume playback") {
            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val manager = managerGet(event, false) ?: return@coroutineScope
                manager.resume()
                event.reply("Resumed").setEphemeral(true).complete()
            }
        }
        this += object : SlashCommand("leave", "leave the voice channel") {
            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                if(event.member?.isBotAdmin == false){
                    event.reply("You are not authorised to use this command").setEphemeral(true).complete()
                    return@coroutineScope
                }
                val manager = managerGet(event, false) ?: return@coroutineScope
                manager.leave(true)
                event.reply("Left").setEphemeral(true).complete()
            }
        }

        bot.guildInitEvent += {
            managerInstances[it.guild.idLong] = GuildMusicManager(it.guild)
        }
        bot.serverCommands += this
    }

    private suspend fun managerGet(event: SlashCommandInteractionEvent, joinIfNeeded: Boolean = true): GuildMusicManager? {
        val manager = managerInstances.getOrPut(event.guild!!.idLong) { GuildMusicManager(event.guild!!) }
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
    private suspend fun playAudio(event: SlashCommandInteractionEvent, query: String, isForced: Boolean = false) {
        val manager = managerGet(event) ?: return
        event.deferReply().setEphemeral(true).complete()
        val track = manager.queryAudio(query,
            query.toHttpUrlOrNull()?.let { GuildMusicManager.MusicQueryType.RawURL } ?: GuildMusicManager.MusicQueryType.YouTubeSearch)
        if (track == null) {
            event.hook.editOriginal("Could not find track").complete()
            return
        }
        manager.queue(track, isForced)
        event.hook.editOriginal("Queued ${track.info.title}").complete()
    }
}

