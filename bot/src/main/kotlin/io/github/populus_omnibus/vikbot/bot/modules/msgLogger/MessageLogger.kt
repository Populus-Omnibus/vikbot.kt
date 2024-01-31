package io.github.populus_omnibus.vikbot.bot.modules.msgLogger

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.isNotMe
import io.github.populus_omnibus.vikbot.bot.stringify
import io.github.populus_omnibus.vikbot.bot.toChannelTag
import io.github.populus_omnibus.vikbot.db.*
import kotlinx.datetime.Clock
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.internal.entities.ReceivedMessage
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

object MessageLogger {

    @Module
    fun init(bot: VikBotHandler) {

        bot.messageReceivedEvent[64] = { event ->
            transaction {
                val server = DiscordGuild.getOrCreate(event.guild.idLong)
                if (event.author.isNotMe && server.messageLoggingLevel >= MessageLoggingLevel.RECORD) {
                    val msg = event.message
                    if (msg is ReceivedMessage) {
                        msg.toUserMessage()
                    }
                }
            }
            EventResult.PASS
        }

        bot.messageDeleteEvent[80] = { event ->
            transaction {
                val guild = Servers[event.guild.idLong]
                if (event.channel.idLong == guild.sinkholeChannel) EventResult.CONSUME
                if (guild.deletedMessagesChannel != null && guild.messageLoggingLevel >= MessageLoggingLevel.DELETED) {
                    val channel = event.jda.getTextChannelById(guild.deletedMessagesChannel!!)!!

                    val message = UserMessage.findById(event.messageIdLong)

                    if (message != null) {
                        channel.sendMessageEmbeds(message.toEmbed(event.jda, "deleted a").build()).queue()
                        //message.delete()
                    }
                }
            }
            EventResult.PASS
        }
        bot.messageUpdateEvent[80] = { event ->
            val msg = event.message
            if (msg is ReceivedMessage && msg.author.isNotMe) {
                transaction {
                    val guild = Servers[event.guild.idLong]
                    if (event.channel.idLong == guild.sinkholeChannel) EventResult.CONSUME

                    val oldMsg = UserMessage.findById(msg.idLong)

                    if (guild.deletedMessagesChannel != null && guild.messageLoggingLevel >= MessageLoggingLevel.ANY) {

                        val channel = event.jda.getTextChannelById(guild.deletedMessagesChannel!!)!!

                        if (oldMsg != null) {
                            channel.sendMessageEmbeds(oldMsg.toEmbed(event.jda, "edited", event.jumpUrl).build())
                                .queue()
                        }
                    }
                    msg.toUserMessage()
                }
            }
            EventResult.PASS
        }

        bot.maintainEvent += {
            // TODO every one minute
        }
    }


    fun UserMessage.toEmbed(jda: JDA, title: String, link: String? = null): EmbedBuilder {
        var iconUrl: String? = null
        var userName: String = author.toString()

        jda.getUserById(author)?.let {
            iconUrl = it.avatarUrl
            userName = it.effectiveName
        }
        return EmbedBuilder().apply {
            setAuthor(userName, null, iconUrl)
            setFooter(Clock.System.now().stringify())
            setDescription("""
                    **<@$author> $title ${link?.let { "[message]($it)" } ?: "message"} in ${channelId.toChannelTag()}**
                    $contentRaw
                """.trimIndent())
        }
    }

    private fun ReceivedMessage.toUserMessage() {
        UserMessages.upsert { entry ->
            val msg = this@toUserMessage
            entry[id] = msg.idLong
            entry[author] = msg.author.idLong
            entry[content] = msg.contentRaw
            entry[channelId] = msg.channel.idLong
            entry[guildId] = msg.guild.idLong
            entry[embedLinks] = msg.embeds.mapNotNull { it.url }
        }
    }

}
