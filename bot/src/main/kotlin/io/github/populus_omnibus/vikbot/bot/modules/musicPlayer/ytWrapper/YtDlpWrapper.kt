package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer.ytWrapper

class YtDlpWrapper {
    companion object {
        val dependenciesMet: Boolean
            get() {
                return try {
                    //check if yt-dlp corresponds to a valid OS command
                    ProcessBuilder("yt-dlp", "--version").start().waitFor() == 0
                } catch (t: Throwable) {
                    false
                }
            }
        fun resolveData(url: String): AudioData {
            return try {
                AudioData(
                    rawData = ProcessBuilder("yt-dlp", "-f", "bestaudio", "-o", "-", url).start().inputStream.readAllBytes(),
                    bitrate = 128u,
                )
            } catch (t: Throwable) {
                return AudioData(
                    rawData = ByteArray(0),
                    bitrate = 128u,
                )
            }
        }
    }
}

class AudioData(
    val rawData: ByteArray,
    val bitrate: UInt,
)
