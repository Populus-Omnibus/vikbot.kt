package io.github.populus_omnibus.vikbot.bot.modules.voiceHandler

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import java.util.*
import kotlin.time.Duration.Companion.seconds


object VoiceHandlerModule {

    private val activeChannels: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf<Long>())

    @Module
    fun initModule(bot: VikBotHandler) {
        bot.guildVoiceUpdateEvent[64] = { event ->
            coroutineScope {
                val voiceChannels = VikBotHandler.config.servers[event.guild.idLong].handledVoiceChannels

                launch {
                    if (event.channelJoined?.idLong in voiceChannels) {
                        val newChannel =
                            event.guild.createVoiceChannel("${event.member.effectiveName} által kért voice").apply {
                                this.setParent(event.channelJoined!!.parentCategory)
                                this.setPosition(event.channelJoined!!.position + 1)
                            }.complete()

                        activeChannels += newChannel.idLong
                        event.guild.moveVoiceMember(event.member, newChannel).complete()
                    }
                }
                launch {
                    if (event.channelLeft?.idLong in activeChannels) {
                        delay(5.seconds)
                        val chId = event.channelLeft!!.idLong
                        val channel = event.guild.getChannelById(VoiceChannel::class.java, chId)
                        if (channel != null && channel.members.isEmpty()) {
                            channel.delete().complete()
                        }
                    }
                }
            }

            EventResult.PASS
        }
    }
}