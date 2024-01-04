package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.lavaPlayer

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import org.slf4j.kotlin.*

enum class QueryLoadResultState {
    TRACK_LOADED,
    PLAYLIST_LOADED,
    NO_MATCHES,
    LOAD_FAILED,
    UNINITIALIZED
}

class YtQueryLoadResultHandler : AudioLoadResultHandler {
    val result: MutableList<AudioTrack> = mutableListOf()
    private var errored = QueryLoadResultState.UNINITIALIZED
    private val logger by getLogger()

    override fun trackLoaded(track: AudioTrack) {
        result.add(track)
        errored = QueryLoadResultState.TRACK_LOADED
    }

    override fun playlistLoaded(playlist: AudioPlaylist) {
        result.addAll(playlist.tracks)
        errored = QueryLoadResultState.PLAYLIST_LOADED
    }

    override fun noMatches() {
        logger.info { "No matches" }
        errored = QueryLoadResultState.NO_MATCHES
    }

    override fun loadFailed(throwable: FriendlyException?) {
        logger.warn(throwable) { "Failed to load track: ${throwable?.message}"}
        errored = QueryLoadResultState.LOAD_FAILED
    }
}