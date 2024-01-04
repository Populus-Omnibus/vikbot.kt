package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.lavaPlayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import java.util.*


class GuildMusicManager(
    var guild: Guild,
) {
    val isInChannel: Boolean
        get() = guild.audioManager.isConnected
    val channel: AudioChannelUnion?
        get() = guild.audioManager.connectedChannel
    private val player: AudioPlayer = playerManager.createPlayer()
    private val sendingHandler = AudioPlayerSendHandler(player)
    internal val trackScheduler = TrackScheduler(this)
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val timer = Timer()

    init {
        guild.audioManager.connectTimeout = TIMEOUT
        guild.audioManager.sendingHandler = sendingHandler
        player.addListener(trackScheduler)
    }

    companion object {
        const val TIMEOUT = 1000L
        private var playerManager: AudioPlayerManager = DefaultAudioPlayerManager()
        init {
            AudioSourceManagers.registerRemoteSources(playerManager)
        }
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
    suspend fun queue(track: AudioTrack) {
        mutex.withLock {
            trackScheduler.queue(track, player)
        }
    }
    suspend fun rotate() {
        mutex.withLock {
            trackScheduler.rotate()
        }
    }
    suspend fun queryYt(query: String): AudioTrack? {
        val loadResultHandler = YtQueryLoadResultHandler()
        mutex.withLock {
            playerManager.loadItemSync("ytsearch: $query", loadResultHandler)
            return loadResultHandler.result.firstOrNull()
            }
        }

    fun onFinish() {
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (trackScheduler.playlist.isEmpty()) {
                    suspend { leave() }
                }
            }
        }, 5000)
    }
}