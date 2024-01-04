package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.lavaPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.GuildMusicManager

class TrackScheduler(val musicPlayer: GuildMusicManager) : AudioEventAdapter() {
    var currentTrack: AudioTrack? = null
    val playlist: MutableList<AudioTrack> = mutableListOf()
    private var pausedPosition: Long? = null

    fun queue(track: AudioTrack, player: AudioPlayer) {
        playlist.add(track)
        if(currentTrack == null) {
            rotate()
            player.playTrack(currentTrack)
        }
    }
    fun playImmediately(track: AudioTrack, player: AudioPlayer) {
        playlist.clear()
        currentTrack = track
        player.playTrack(currentTrack)
    }
    fun skip(player: AudioPlayer) {
        rotate()
        player.playTrack(currentTrack)
    }
    private fun rotate() {
        currentTrack = playlist.removeFirstOrNull()
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
            rotate()
            player.playTrack(currentTrack ?: run {
                musicPlayer.onFinish()
                return
            })
        }

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