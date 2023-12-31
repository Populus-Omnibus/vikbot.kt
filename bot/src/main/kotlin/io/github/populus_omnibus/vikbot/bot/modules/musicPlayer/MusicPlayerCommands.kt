package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.kotlin.getLogger
import kotlin.collections.set

//@Command(type = CommandType.SERVER)
//replaced by init statements
object MusicPlayerCommands : CommandGroup("music", "Music player") {
    internal val logger by getLogger()
    private val playerInstances: MutableMap<Long, MusicPlayer> = mutableMapOf()

    @Module
    fun init(bot: VikBotHandler) {
        if(!dependencyCheck()) {
            logger.info("Music player module requires yt-dlp and ffmpeg to be installed")
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
                player.joinNow(channel)
                event.hook.editOriginal("Joined channel <#${channel.idLong}>").complete()
                Thread.sleep(2000)
                player.leave()
            }
        }
        bot.guildInitEvent += {
            playerInstances[it.guild.idLong] = MusicPlayer(it.guild)
        }
        bot.serverCommands += this
    }

    //check if yt-dlp and ffmpeg are installed
    //the music player module requires both, we're effectively denying user access
    private fun dependencyCheck(): Boolean {
        return try {
            //check if ffmpeg and yt-dlp correspond to a valid OS command
            ProcessBuilder("ffmpeg", "-version").start().waitFor() == 0 &&
                    ProcessBuilder("yt-dlp", "--version").start().waitFor() == 0
        } catch (t: Throwable) {
            false
        }
    }
}