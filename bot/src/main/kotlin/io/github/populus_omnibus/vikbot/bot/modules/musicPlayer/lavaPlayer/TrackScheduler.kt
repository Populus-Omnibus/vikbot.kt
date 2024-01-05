package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.lavaPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.GuildMusicManager
import kotlin.math.min

class TrackScheduler(private val manager: GuildMusicManager, private val player: AudioPlayer)
    : AudioEventAdapter() {
    var currentTrack: AudioTrack? = null
    val playlist: MutableList<AudioTrack> = mutableListOf()
    private var pausedPosition: Long? = null

    fun queue(track: AudioTrack) {
        playlist.add(track)
        if(currentTrack == null) {
            player.playTrack(playlist.removeFirstOrNull())
        }
    }
    fun playNow(track: AudioTrack) {
        this.clear()
        player.playTrack(track)
    }
    fun skip(num: Int) {
        repeat(min(num-1, playlist.size)) {
            playlist.removeFirstOrNull()
        }
        player.playTrack(currentTrack)
    }
    fun pause() {
        player.isPaused = true
    }
    fun resume() {
        player.isPaused = false
    }
    fun clear() {
        currentTrack = null
        playlist.clear()
        player.isPaused = false
        player.stopTrack()
    }

    override fun onPlayerPause(player: AudioPlayer) {
        pausedPosition = player.playingTrack.position
    }

    override fun onPlayerResume(player: AudioPlayer) {
        pausedPosition?.let {
            player.playingTrack.position = it
        }
    }

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        currentTrack = track
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) {
            currentTrack = null
            playlist.removeFirstOrNull()?.let {
                player.playTrack(it)
                return
            }
            manager.leave()
        }
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        // An already playing track threw an exception (track end event will still be received separately)
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        // Audio track has been unable to provide us any audio, might want to just start a new track
    }
}