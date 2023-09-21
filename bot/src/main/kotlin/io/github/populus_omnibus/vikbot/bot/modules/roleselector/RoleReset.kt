package io.github.populus_omnibus.vikbot.bot.modules.roleselector

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.bot.RoleGroup.PublishData
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.slf4j.kotlin.getLogger

@Command(CommandType.SERVER)
object RoleReset :
    CommandGroup("rolereset", "Admin-only commands for adding and editing messages that allow for resetting roles", {
        adminOnly()
    }) {
    internal val logger by getLogger()

    @Module
    fun init(bot: VikBotHandler) {
        this += object : SlashCommand("publish", "create a role reset message") {
            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                event.replyModal(Modal.create("roleresetpublish", "Publishing role resetter")
                    .addActionRow(TextInput.create("data",
                        "Data (role group name \\n user-facing string)",
                        TextInputStyle.PARAGRAPH).build())
                    .build()).complete()
            }
        }
        bot.modalEvents += IdentifiableInteractionHandler("roleresetpublish") { event ->
            val buttonData = event.getValue("data")?.asString?.split("\n")?.windowed(2, 2)?.map { Pair(it[0], it[1]) }
                ?: run {
                    return@IdentifiableInteractionHandler
                }
            val serverEntry = bot.config.servers[event.guild?.idLong] ?: run {
                event.reply("no server entry found").setEphemeral(true).complete()
                return@IdentifiableInteractionHandler
            }

            buttonData.filterNot { serverEntry.roleGroups.containsKey(it.first) }.let { pairs ->
                if(pairs.isNotEmpty()){
                    event.reply("Following groups not found: ${pairs.joinToString(", ") { it.first }}")
                        .setEphemeral(true).complete()
                    return@IdentifiableInteractionHandler
                }
            }

            val buttons = buttonData.map { listOf(
                Button.danger("rolereset:${it.first}", it.second),
                Button.secondary("separator", "|").asDisabled())
            }.flatten()
            if(buttons.size < 2) return@IdentifiableInteractionHandler

            //delete previous resetter
            serverEntry.lastRoleResetMessage?.let {
                bot.jda.getTextChannelById(it.channelId)?.retrieveMessageById(it.messageId)?.complete()
                    ?.delete()?.complete()
            }
            event.deferEdit().complete()


            //remove last placeholder before sending
            val msg = event.channel.sendMessage(MessageCreateBuilder().addActionRow(buttons.subList(0, buttons.size-1)).build()).complete()
            serverEntry.lastRoleResetMessage = msg?.let { PublishData(it.channel.idLong, it.idLong) }
        }
    }
}