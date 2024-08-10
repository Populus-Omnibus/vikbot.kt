package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.lavaPlayer.AudioPlayerSendHandler
import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.lavaPlayer.TrackScheduler
import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.lavaPlayer.YtQueryLoadResultHandler
import io.github.populus_omnibus.vikbot.bot.security.SecureRequestUtil
import io.github.populus_omnibus.vikbot.db.Servers
import kotlinx.coroutines.runBlocking
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
    private val trackScheduler = TrackScheduler(this, player)
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var timer: Timer = Timer()
        set(value) {
            field.cancel()
            field = value
        }

    init {
        guild.audioManager.connectTimeout = API_TIMEOUT
        guild.audioManager.sendingHandler = sendingHandler
        player.volume = transaction {
            Servers[guild.idLong].vcVolume
        }
        player.addListener(trackScheduler)
    }

    companion object {
        private const val ABSOLUTE_MAX_VOLUME = 100
        const val API_TIMEOUT = 1000L
        const val EMPTY_CHANNEL_TIMEOUT = 30000L
        const val EMPTY_QUEUE_TIMEOUT = 60000L
        private var playerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
            AudioSourceManagers.registerRemoteSources(this)

            this.setHttpBuilderConfigurator { request ->
                SecureRequestUtil.configureSecurely(request)
            }
        }

        fun clampVolume(volume: Int) = volume.coerceIn(0, ABSOLUTE_MAX_VOLUME)
    }


    suspend fun join(channelToJoin: AudioChannelUnion?) {
        mutex.withLock {
            guild.audioManager.openAudioConnection(channelToJoin)
            timer = Timer()

            //periodically check if voice channel is empty
            timer.schedule(object : TimerTask() {
                override fun run() {
                    runBlocking {
                        if (channel?.members?.all { it.user.isBot } == true)
                            leave(true)
                    }
                }
            }, EMPTY_CHANNEL_TIMEOUT, EMPTY_CHANNEL_TIMEOUT)
        }
    }

    fun closeConn() {
        guild.audioManager.closeAudioConnection()
    }

    suspend fun queue(track: AudioTrack, playNow: Boolean = false) {
        mutex.withLock {
            when {
                playNow -> trackScheduler.playNow(track)
                else -> trackScheduler.queue(track)
            }
        }
    }

    suspend fun skip(num: Int = 1) {
        mutex.withLock {
            trackScheduler.skip(num)
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

    fun leave(immediate : Boolean = false) {
        timer.schedule(object : TimerTask() {
            override fun run() {
                runBlocking {
                    mutex.withLock {
                        if (immediate || trackScheduler.currentTrack == null) {
                            trackScheduler.clear()
                            closeConn()
                        }
                    }
                }
            }
        }, if(immediate) 0L else EMPTY_QUEUE_TIMEOUT)
    }
    suspend fun pause() {
        mutex.withLock {
            trackScheduler.pause()
        }
    }
    suspend fun resume() {
        mutex.withLock {
            trackScheduler.resume()
        }
    }

    enum class MusicQueryType {
        RawURL,
        YouTubeSearch
    }
}
