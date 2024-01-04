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
        clear()
        player.playTrack(track)
    }
    fun skip(player: AudioPlayer, num: Int) {
        repeat(min(num-1, playlist.size)) {
            playlist.removeFirstOrNull()
        }
        //stopping the track will trigger the onTrackEnd event
        player.stopTrack()
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
        currentTrack = null
        playlist.removeFirstOrNull()?.let {
            player.playTrack(it)
            return
        }
        //timer starts when the last track ends
        manager.leave()



        // endReason == FINISHED: A track finished or died by an exception (mayStartNext = true).
        // endReason == LOAD_FAILED: Loading of a track failed (mayStartNext = true).
        // endReason == STOPPED: The player was stopped.
        // endReason == REPLACED: Another track started playing while this had not finished
        // endReason == CLEANUP: Player hasn't been queried for a while, if you want you can put a
        //                       clone of this back to your queue
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        // An already playing track threw an exception (track end event will still be received separately)
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        // Audio track has been unable to provide us any audio, might want to just start a new track
    }
}