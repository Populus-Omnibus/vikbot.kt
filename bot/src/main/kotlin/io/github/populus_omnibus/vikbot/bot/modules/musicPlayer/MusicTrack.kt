package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.ytWrapper.AudioData
import io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.ytWrapper.YtDlpWrapper
import kotlin.time.Duration

class MusicTrack(
    val title: String,
    val url: String,
    val duration: Duration,
) {
    fun resolve() {
        audioData = YtDlpWrapper.resolveData(url)
    }
    var audioData: AudioData? = null
        get() {
            if(field == null) resolve()
            return field
        }
}

