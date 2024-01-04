package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.lavaPlayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import io.github.populus_omnibus.vikbot.db.Servers
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*


class GuildMusicManager(
    var guild: Guild,
) {
    val isInChannel: Boolean
        get() = guild.audioManager.isConnected
    val channel: AudioChannelUnion?
        get() = guild.audioManager.connectedChannel
    var volume: Int
        get() = player.volume
        set(value) {
            val clamped = clampVolume(value)
            player.volume = clamped
            transaction {
                Servers[guild.idLong].vcVolume = clamped
            }
        }

    private val player: AudioPlayer = playerManager.createPlayer()
    private val sendingHandler = AudioPlayerSendHandler(player)
    private val trackScheduler = TrackScheduler(this)
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val timer = Timer()

    init {
        guild.audioManager.connectTimeout = TIMEOUT
        guild.audioManager.sendingHandler = sendingHandler
        player.volume = transaction {
            Servers[guild.idLong].vcVolume
        }
        player.addListener(trackScheduler)
    }

    companion object {
        private const val ABSOLUTE_MAX_VOLUME = 100
        const val TIMEOUT = 1000L
        private var playerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
            AudioSourceManagers.registerRemoteSources(this)
        }
        public fun clampVolume(volume: Int) = volume.coerceIn(0, ABSOLUTE_MAX_VOLUME)
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
    suspend fun queryAudio(query: String, type: MusicQueryType = MusicQueryType.RawURL): AudioTrack? {
        mutex.withLock {
            val queryString = when (type) {
                MusicQueryType.RawURL -> query
                MusicQueryType.YouTubeSearch -> "ytsearch: $query"
            }
            val loadResultHandler = YtQueryLoadResultHandler()
            playerManager.loadItemSync(queryString, loadResultHandler)
            return loadResultHandler.result.firstOrNull()
        }
    }


    suspend fun trackQuery(): Pair<AudioTrack?, List<AudioTrack>> {
        mutex.withLock {
            return Pair(trackScheduler.currentTrack, trackScheduler.playlist)
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
    enum class MusicQueryType {
        RawURL,
        YouTubeSearch
    }
}
