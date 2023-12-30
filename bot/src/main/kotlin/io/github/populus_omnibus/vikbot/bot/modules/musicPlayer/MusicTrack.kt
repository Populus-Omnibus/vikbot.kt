package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

class MusicTrack(
    val title: String,
    val url: String,
    val duration: Long,
    val audioData: ByteArray? = null
) {
    fun resolveData(): Boolean {
        if (audioData != null) {
            return false
        }
        //TODO
        return true
    }
}
