package io.github.populus_omnibus.vikbot.bot.modules.moderation

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.api.createMemory
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.api.maintainEvent
import io.github.populus_omnibus.vikbot.api.set
import io.github.populus_omnibus.vikbot.plusAssign
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.slf4j.kotlin.getLogger
import kotlin.time.Duration.Companion.minutes

@Command(CommandType.SERVER)
object ModerationCommands :
    CommandGroup("moderation", "Admin-only commands for executing moderation tasks, such as a channel purge", {
        adminOnly()
    }) {
    internal val logger by getLogger()
    private var deletionRequests = createMemory<Long, List<Message>>()

    @Module
    fun init(bot: VikBotHandler) {
        bot += deletionRequests.maintainEvent(5.minutes)
        this += object :
            SlashCommand("purge", "Purge messages until a certain message or in the past x amount of time") {
            val lastMessageURL by option(
                "lastmessageurl", "(right click and copy message link)",
                SlashOptionType.STRING
            )

            val time by option(
                "minutesbefore", "The action will remove all messages younger than x minutes",
                SlashOptionType.INTEGER
            )

            override suspend fun invoke(event: SlashCommandInteractionEvent) = coroutineScope {
                val pattern = Regex("(https://)?discord\\.com/channels/(?<guild>\\d+)/(?<channel>\\d+)/(?<msg>\\d+)")
                val result = pattern.matchEntire(lastMessageURL ?: "")
                val channelId = result?.groups?.get("channel")?.value?.toLongOrNull()
                val messageId = result?.groups?.get("msg")?.value?.toLongOrNull()
                if (time == null && channelId == null && messageId == null) {
                    event.reply("Please specify at least one of the options").setEphemeral(true).complete()
                    return@coroutineScope
                }
                val toDelete = run {
                    val history = event.channel.iterableHistory.asSequence()
                    history.takeWhile { it.idLong != messageId }
                        .takeWhile {
                            it.timeCreated.toEpochSecond() > (System.currentTimeMillis() / 1000 - (time
                                ?: 1_000_000) * 60)
                        }
                        .toList()
                }
                val response = event.reply("Are you sure you want to delete ${toDelete.size} messages?").addActionRow(
                    Button.success("purgeyes", "Yes"),
                    Button.danger("purgeno", "No")
                ).setEphemeral(true).complete()
                val message = response.retrieveOriginal().complete()
                deletionRequests[message.idLong] = toDelete
            }
        }

        bot.buttonEvents += IdentifiableInteractionHandler("purgeyes") { event ->
            event.deferReply().setEphemeral(true).complete()
            val toDelete = deletionRequests[event.messageIdLong] ?: run {
                event.hook.editOriginal("This message is no longer valid").complete()
                return@IdentifiableInteractionHandler
            }
            processAndDisableButtons(event.message)
            event.hook.editOriginal("Deleting ${toDelete.second.size} messages").complete()
            event.channel.purgeMessages(toDelete.second)
        }

        bot.buttonEvents += IdentifiableInteractionHandler("purgeno") { event ->
            event.deferEdit().complete()
            processAndDisableButtons(event.message)
        }
    }

    private fun processAndDisableButtons(msg: Message) {
        msg.editMessage(msg.contentRaw).setActionRow(msg.actionRows[0].components
            .map { c -> (c as? Button)?.asDisabled() }).complete()
        deletionRequests -= msg.idLong
    }
}