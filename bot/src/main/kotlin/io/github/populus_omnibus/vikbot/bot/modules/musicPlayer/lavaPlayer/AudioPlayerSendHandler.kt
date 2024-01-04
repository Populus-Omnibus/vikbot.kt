package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.lavaPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import net.dv8tion.jda.api.audio.AudioSendHandler
import java.nio.ByteBuffer

//https://github.com/sedmelluq/LavaPlayer#jda-integration
class AudioPlayerSendHandler(private val audioPlayer: AudioPlayer) : AudioSendHandler {
    private var lastFrame: AudioFrame? = null

    override fun canProvide(): Boolean {
        if(lastFrame != null) return true
        lastFrame = audioPlayer.provide()
        return lastFrame != null
    }

    override fun provide20MsAudio(): ByteBuffer? {
        return if (canProvide()) {
            ByteBuffer.wrap(lastFrame!!.data.also { lastFrame = null })
        } else {
            null
        }
    }

    override fun isOpus() = true
}