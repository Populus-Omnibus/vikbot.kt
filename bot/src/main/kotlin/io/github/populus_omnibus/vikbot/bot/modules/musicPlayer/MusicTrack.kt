package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import kotlin.time.Duration

class MusicTrack(
    val title: String,
    val url: String,
    val duration: Duration,
    val bitrate: UInt,
) {
    val isResolved: Boolean
        get() = audioData != null
    var audioData: ByteArray? = null
        get() {
            if (!isResolved) {
                resolveData()
            }
            return field!!
        }

    fun resolveData() {
        if (isResolved) {
            return
        }
        audioData = ByteArray(0) //TODO
    }
}
