package io.github.populus_omnibus.vikbot.bot.modules.roleselector

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.db.Servers
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
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
                        .addActionRow(TextInput.create("data", "Data (role group name \\n user-facing string)", TextInputStyle.PARAGRAPH).build())
                        .build()
                ).complete()
            }
        }

        bot.modalEvents += IdentifiableInteractionHandler("roleresetpublish") { event ->
            val buttonData = event.getValue("data")?.asString?.split("\n")?.windowed(2, 2)?.map { Pair(it[0], it[1]) }
                ?: run {
                    return@IdentifiableInteractionHandler
                }

            transaction {
                val serverEntry = Servers[event.guild!!.idLong]

                buttonData.filterNot { serverEntry.roleGroups.contains(it.first) }.let { pairs ->
                    if (pairs.isNotEmpty()) {
                        event.reply("Following groups not found: ${pairs.joinToString(", ") { it.first }}")
                            .setEphemeral(true).complete()
                        return@transaction
                    }
                }

                val buttons = buttonData.map {
                    listOf(
                        Button.danger("rolereset:${it.first}", it.second),
                        Button.secondary("separator", "|").asDisabled()
                    )
                }.flatten()
                if (buttons.size < 2) return@transaction

                //delete previous resetter
                serverEntry.lastRoleResetMessage?.let {
                    bot.jda.getTextChannelById(it.channelId)?.retrieveMessageById(it.messageId)?.complete()
                        ?.delete()?.complete()
                }
                event.deferEdit().complete()


                //remove last placeholder before sending
                val msg = event.channel.sendMessage(
                    MessageCreateBuilder().addActionRow(buttons.subList(0, buttons.size - 1)).build()
                ).complete()
                msg?.let { serverEntry.setLastRoleResetMessage(it.channel.idLong, it.idLong) } ?: serverEntry.lastRoleResetMessage?.delete()
            }
        }

        bot.buttonEvents += IdentifiableInteractionHandler("rolereset") { event ->
            val groupName = event.interaction.componentId.split(":").getOrNull(1)
            //take roles from role group, find actual existing roles to match, and remove those from the user
            transaction {
                groupName?.let { name ->
                    Servers[event.guild!!.idLong].roleGroups.getOrNull(name)?.roles?.mapNotNull { bot.jda.getRoleById(it.roleId) }
                }
            }.let { roleList ->
                event.member?.let { member ->
                    event.guild?.modifyMemberRoles(member, null, roleList)?.complete()
                }
            }

            event.reply("done").setEphemeral(true).complete()
        }
    }
}