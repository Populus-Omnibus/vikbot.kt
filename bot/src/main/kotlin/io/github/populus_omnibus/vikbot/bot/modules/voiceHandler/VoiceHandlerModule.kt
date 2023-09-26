package io.github.populus_omnibus.vikbot.bot.modules.voiceHandler

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.db.HandledVoiceChannel
import io.github.populus_omnibus.vikbot.db.HandledVoiceChannels
import io.github.populus_omnibus.vikbot.db.Servers
import io.github.populus_omnibus.vikbot.db.VoiceChannelType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.seconds


object VoiceHandlerModule {
    @Module
    fun initModule(bot: VikBotHandler) {
        bot.guildVoiceUpdateEvent[64] = { event ->
            coroutineScope {

                // launch to create new channel
                launch {
                    transaction {
                        val chId = event.channelJoined?.idLong?.let {
                            HandledVoiceChannel.find { (HandledVoiceChannels.channel eq it) and (HandledVoiceChannels.channelType eq VoiceChannelType.VoiceRequest) }.firstOrNull()
                        }
                        if (chId != null) {
                            val newChannel =
                                event.guild.createVoiceChannel("${event.member.effectiveName} által kért voice").apply {
                                    this.setParent(event.channelJoined!!.parentCategory)
                                    this.setPosition(event.channelJoined!!.position + 1)
                                }.complete()

                            HandledVoiceChannel.new(newChannel.idLong) {
                                guild = Servers[newChannel.guild.idLong]
                                type = VoiceChannelType.Temp
                            }
                            event.guild.moveVoiceMember(event.member, newChannel).complete()
                        }
                    }
                }
                launch {
                        val tmpChannel = event.channelLeft?.idLong?.let {
                            transaction { HandledVoiceChannel.find { HandledVoiceChannels.channel eq it and (HandledVoiceChannels.channelType eq VoiceChannelType.Temp) }.firstOrNull() }
                        }
                        if (tmpChannel != null) {
                            delay(5.seconds)
                            val chId = event.channelLeft!!.idLong
                            val channel = event.guild.getChannelById(VoiceChannel::class.java, chId)
                            if (channel != null && channel.members.isEmpty()) {
                                channel.delete().complete()
                            }
                            transaction {
                                tmpChannel.delete()
                            }
                        }
                }
            }

            EventResult.PASS
        }
    }
}