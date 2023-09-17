package io.github.populus_omnibus.vikbot.bot.modules.voiceHandler

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module


object VoiceHandlerModule {

    val createdChannels = mutableSetOf<Long>()

    @Module
    fun initModule(bot: VikBotHandler) {
        bot.guildVoiceUpdateEvent[64] = { event ->
            val voiceChannels = VikBotHandler.config.servers[event.guild.idLong].handledVoiceChannels
            if (event.channelJoined?.idLong in voiceChannels) {
            }

            EventResult.PASS
        }
    }
}