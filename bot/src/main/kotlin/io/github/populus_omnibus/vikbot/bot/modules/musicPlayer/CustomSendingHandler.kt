package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import net.dv8tion.jda.api.audio.AudioSendHandler
import java.nio.ByteBuffer

class CustomSendingHandler : AudioSendHandler {
    private var byteBuffer: MutableList<Byte> = mutableListOf()
    private var bitrate = 128000u //bytes per second

    fun putData(data: ByteArray) {
        byteBuffer.addAll(data.toList())
    }
    fun resetBuffer() {
        byteBuffer = mutableListOf()
    }

    override fun canProvide() = byteBuffer.isNotEmpty()

    override fun provide20MsAudio(): ByteBuffer? {
        //pop first 20ms of audio
        val data = byteBuffer.take(bitrate.toInt() / 50)
        byteBuffer = byteBuffer.drop(bitrate.toInt() / 50).toMutableList()
        return ByteBuffer.wrap(data.toByteArray())
    }
}