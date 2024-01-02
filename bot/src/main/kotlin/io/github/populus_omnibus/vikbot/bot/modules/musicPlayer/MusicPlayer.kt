package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.ytWrapper.YtDlpWrapper
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import java.util.*

class MusicPlayer(
    var guild: Guild,
    var currentTrack: MusicTrack? = null,
    val playlist: MutableList<MusicTrack> = mutableListOf(),
    val timer: Timer = Timer(),
) {
    val isInChannel: Boolean
        get() = guild.audioManager.isConnected
    val channel: AudioChannelUnion?
        get() = guild.audioManager.connectedChannel
    private val sendingHandler = CustomSendingHandler()
    private val mutex = kotlinx.coroutines.sync.Mutex()

    companion object {
        const val TIMEOUT = 1000L
    }

    init {
        guild.audioManager.connectTimeout = TIMEOUT
        guild.audioManager.sendingHandler = sendingHandler
    }

    suspend fun join(channel: AudioChannelUnion?) {
        mutex.withLock {
            guild.audioManager.openAudioConnection(channel)
        }
    }
    suspend fun leave() {
        mutex.withLock {
            guild.audioManager.closeAudioConnection()
        }
    }
    suspend fun queue(track: MusicTrack) {
        mutex.withLock {
            playlist.add(track)
        }
    }
    suspend fun rotate() {
        mutex.withLock {
            currentTrack = playlist.removeFirstOrNull()
            if(playlist.isNotEmpty()) playlist.first().resolve()
        }
    }
    suspend fun playImmediately() {
        mutex.withLock {
            currentTrack?.let {
                sendingHandler.bitrateKbps = it.audioData!!.bitrate
                sendingHandler.resetBuffer()
                sendingHandler.putData(it.audioData!!.rawData)
            }
        }
    }
}