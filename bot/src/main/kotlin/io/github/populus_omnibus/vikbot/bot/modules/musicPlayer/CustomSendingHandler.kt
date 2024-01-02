package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import net.dv8tion.jda.api.audio.AudioSendHandler
import java.nio.ByteBuffer

class CustomSendingHandler : AudioSendHandler {
    private var byteBuffer: MutableList<Byte> = mutableListOf()
    var bitrateKbps = 128u

    fun putData(data: ByteArray) {
        byteBuffer.addAll(data.toList())
    }
    fun resetBuffer() {
        byteBuffer = mutableListOf()
    }

    override fun canProvide() = byteBuffer.isNotEmpty()

    override fun provide20MsAudio(): ByteBuffer? {
        //pop first 20ms of audio
        val data = byteBuffer.take(kbpsToBps(bitrateKbps) / 50).toByteArray()
            byteBuffer = byteBuffer.drop(kbpsToBps(bitrateKbps) / 50).toMutableList()
        return ByteBuffer.wrap(data)
    }
    private fun kbpsToBps(kbps: UInt) = (kbps.toDouble() / 8 * 1000).toInt()
}