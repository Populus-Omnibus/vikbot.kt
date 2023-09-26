package io.github.populus_omnibus.vikbot.bot.modules.msgLogger

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.isNotMe
import io.github.populus_omnibus.vikbot.db.DiscordGuild
import io.github.populus_omnibus.vikbot.db.MessageLoggingLevel
import io.github.populus_omnibus.vikbot.db.Servers
import io.github.populus_omnibus.vikbot.db.UserMessage
import kotlinx.datetime.Clock
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.internal.entities.ReceivedMessage
import org.jetbrains.exposed.sql.transactions.transaction

object MessageLogger {

    @Module
    fun init(bot: VikBotHandler) {

        bot.messageReceivedEvent[64] = { event ->
            transaction {
                val server = DiscordGuild.getOrCreate(event.guild.idLong)
                if (event.author.isNotMe && server.messageLoggingLevel >= MessageLoggingLevel.DELETED && server.deletedMessagesChannel != null) {
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
                if (guild.deletedMessagesChannel != null && guild.messageLoggingLevel >= MessageLoggingLevel.DELETED) {
                    val channel = event.jda.getTextChannelById(guild.deletedMessagesChannel!!)!!

                    val message = UserMessage.findById(event.messageIdLong)

                    if (message != null) {
                        channel.sendMessageEmbeds(message.toEmbed(event.jda, "deleted a").build()).queue()
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

                    val oldMsg = UserMessage.findById(msg.idLong)

                    if (guild.deletedMessagesChannel != null && guild.messageLoggingLevel >= MessageLoggingLevel.ANY) {

                        val channel = event.jda.getTextChannelById(guild.deletedMessagesChannel!!)!!

                        if (oldMsg != null) {
                            channel.sendMessageEmbeds(oldMsg.toEmbed(event.jda, "edited", event.jumpUrl).build())
                                .queue()
                        }
                        msg.toUserMessage()
                    }
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
            setFooter(Clock.System.now().toString())
            setDescription("""
                    **<@$author> $title ${link?.let { "[message]($it)" } ?: "message"}**
                    $contentRaw
                """.trimIndent())
        }
    }

    private fun ReceivedMessage.toUserMessage() = UserMessage.new(this.idLong) {
        val msg = this@toUserMessage
        this.author = msg.author.idLong
        this.contentRaw = msg.contentRaw
        this.channelId = msg.channel.idLong
        this.guildId = msg.guild.idLong
        this.embedLinks = embeds.mapNotNull { it.url }
    }
}
