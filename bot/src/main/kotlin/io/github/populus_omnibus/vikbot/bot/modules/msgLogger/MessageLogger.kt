package io.github.populus_omnibus.vikbot.bot.modules.msgLogger

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.*
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.bot.MessageLoggingLevel
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.internal.entities.ReceivedMessage
import kotlin.time.Duration.Companion.days

object MessageLogger {

    private val messageMemory = createMemory<Long, UserMessage>()

    internal val currentTrackedMessageCount: Int
        get() = messageMemory.size

    @Module
    fun init(bot: VikBotHandler) {

        bot.messageReceivedEvent[64] = { event ->
            val server = bot.config.servers[event.guild.idLong]
            if (event.author.isNotMe && server.messageLoggingLevel >= MessageLoggingLevel.DELETED && server.deletedMessagesChannel != null) {
                val msg = event.message
                if (msg is ReceivedMessage) {
                    messageMemory[event.messageIdLong] = msg.toUserMessage()
                }
            }
            EventResult.PASS
        }

        bot.messageDeleteEvent[80] = { event ->
            val guild = bot.config.servers[event.guild.idLong]
            if (guild.deletedMessagesChannel != null && guild.messageLoggingLevel >= MessageLoggingLevel.DELETED) {
                val channel = event.jda.getTextChannelById(guild.deletedMessagesChannel!!)!!

                val message = messageMemory[event.messageIdLong]

                if (message != null) {
                    channel.sendMessageEmbeds(message.second.toEmbed(event.jda, "deleted a").build()).complete()
                }
            }
            EventResult.PASS
        }
        bot.messageUpdateEvent[80] = { event ->
            val msg = event.message
            if (msg is ReceivedMessage && msg.author.isNotMe) {
                val guild = bot.config.servers[event.guild.idLong]

                val oldMsg = messageMemory[msg.idLong]
                messageMemory[msg.idLong] = msg.toUserMessage()

                if (guild.deletedMessagesChannel != null && guild.messageLoggingLevel >= MessageLoggingLevel.ANY) {

                    val channel = event.jda.getTextChannelById(guild.deletedMessagesChannel!!)!!

                    if (oldMsg != null) {
                        channel.sendMessageEmbeds(oldMsg.second.toEmbed(event.jda, "edited", event.jumpUrl).build()).complete()
                    }
                }
            }
            EventResult.PASS
        }

        bot += messageMemory.maintainEvent(delay = 14.days)
    }

    private data class UserMessage(
        val author: Long,
        val content: String,
        val channel: Long,
        val embedLinks: List<String>
    ) {
        suspend fun toEmbed(jda: JDA, title: String, link: String? = null) = coroutineScope {
            var iconUrl: String? = null
            var userName: String = author.toString()

            jda.getUserById(author)?.let {
                iconUrl = it.avatarUrl
                userName = it.effectiveName
            }
            EmbedBuilder().apply {
                setAuthor(userName, null, iconUrl)
                setFooter(Clock.System.now().toString())
                setDescription("""
                    **<@$author> $title ${link?.let { "[message]($it)" } ?: "message"}**
                    $content
                """.trimIndent())
            }
        }
    }

    private fun ReceivedMessage.toUserMessage() = UserMessage(
        author.idLong,
        contentRaw,
        channel.idLong,
        embeds.mapNotNull { it.url }
    )
}
