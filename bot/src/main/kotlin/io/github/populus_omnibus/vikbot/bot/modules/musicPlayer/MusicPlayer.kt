package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import java.util.*

class MusicPlayer(
    var guild: Guild,
    var channel: AudioChannelUnion? = null,
    var currentTrack: MusicTrack? = null,
    val playlist: MutableList<MusicTrack> = mutableListOf(),
    val timer: Timer = Timer(),
) {

    private val mutex = kotlinx.coroutines.sync.Mutex()
    init {
        guild.audioManager.connectTimeout = 1000L
    }

    private suspend fun joinNow() {
        mutex.withLock {
            channel?.let {
                guild.audioManager.openAudioConnection(it)
            }
        }
    }
    suspend fun joinNow(channel: AudioChannelUnion?) {
        mutex.withLock {
            this.channel = channel
        }
        joinNow()
    }
    suspend fun leave() {
        mutex.withLock {
            guild.audioManager.closeAudioConnection()
        }
    }
}