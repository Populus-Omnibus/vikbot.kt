package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.createMemory
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.kotlin.getLogger

//@Command(type = CommandType.SERVER)
//replaced by init statements
object MusicPlayerCommands : CommandGroup("music", "Music player", {}) {
    internal val logger by getLogger()
    private var playerInstances = createMemory<ULong, MusicPlayer?>()

    @Module
    fun init(bot: VikBotHandler) {
        if(!dependencyCheck()) {
            logger.info("Music player module requires yt-dlp and ffmpeg to be installed")
            return
        }
        this += object :
            SlashCommand("test", "lorem ipsum") {

            override suspend fun invoke(event: SlashCommandInteractionEvent) = coroutineScope {

            }
        }
        bot.serverCommands += this
    }

    //check if yt-dlp and ffmpeg are installed
    //the music player module requires both, we're effectively denying user access
    fun dependencyCheck(): Boolean {
        return try {
            ProcessBuilder("ffmpeg").start().waitFor() == 0
                    && ProcessBuilder("yt-dlp").start().waitFor() == 0
        } catch (t: Throwable) {
            false
        }
    }
}