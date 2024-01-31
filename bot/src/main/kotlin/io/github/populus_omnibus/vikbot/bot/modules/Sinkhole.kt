package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.operator
import io.github.populus_omnibus.vikbot.api.createMemory
import io.github.populus_omnibus.vikbot.api.maintainEvent
import io.github.populus_omnibus.vikbot.api.plusAssign
import io.github.populus_omnibus.vikbot.db.Servers
import io.github.populus_omnibus.vikbot.plusAssign
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.seconds

@Command(type = CommandType.SERVER)
object Sinkhole : CommandGroup("sinkhole", "just a /dev/null", { operator() } ) {
    private val toBeDeleted = createMemory<Long, Message>()
    private val deletionDelay = 30.seconds

    init {
        this += object : SlashCommand("set", "sets the current channel for this very fun feature") {
            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                event.deferReply().setEphemeral(true).complete()
                val success = transaction {
                    if(event.guild?.idLong == null) return@transaction false
                    val server = Servers[event.guild!!.idLong]
                    server.sinkholeChannel = (event.channel as? GuildMessageChannel)?.idLong
                    true
                }
                event.hook.editOriginal(if(success) {
                    "Channel updated to " + event.channel.asMention + ", delay is " + deletionDelay
                } else {
                    "Channel update failed"
                }).complete()
            }
        }

        this += object : SlashCommand("remove", "remove the channel assignment") {
            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                event.deferReply().setEphemeral(true).complete()
                transaction {
                    if(event.guild?.idLong == null) return@transaction
                    val server = Servers[event.guild!!.idLong]
                    server.sinkholeChannel = null
                }
                event.hook.editOriginal("Channel removed from guild config").complete()
            }
        }

        VikBotHandler.guildInitEvent += {event ->
            val channelId = transaction {
                val server = Servers[event.guild.idLong]
                server.sinkholeChannel
            }

            channelId?.let {
                VikBotHandler.jda.getTextChannelById(it)?.let { channel ->
                    channel.iterableHistory
                        .takeWhileAsync { msg -> msg.timeCreated > OffsetDateTime.now().minusHours(1) }
                        .let { messages -> channel.purgeMessages(messages.join())  }
                }
            }
        }

        VikBotHandler += toBeDeleted.maintainEvent(deletionDelay) { _, msg -> msg.delete().queue() }

        VikBotHandler.messageReceivedEvent[16] = { event ->
            transaction {
                val server = Servers[event.guild.idLong]
                if((event.channel as? GuildMessageChannel)?.idLong == server.sinkholeChannel) {
                    toBeDeleted += event.message
                    EventResult.CONSUME
                }
            }
            EventResult.PASS
        }
    }
}