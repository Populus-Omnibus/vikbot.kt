package io.github.populus_omnibus.vikbot.bot.modules.roleselector

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.administrator
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.db.Servers
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.kotlin.getLogger

@Command(CommandType.SERVER)
object RoleReset :
    CommandGroup("rolereset", "Admin-only commands for adding and editing messages that allow for resetting roles", {
        administrator()
    }) {
    internal val logger by getLogger()

    @Module
    fun init(bot: VikBotHandler) {
        this += object : SlashCommand("publish", "create a role reset message") {
            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                transaction {
                    val serverEntry = Servers[event.guild!!.idLong]

                    var i = 0
                    val buttons = serverEntry.roleGroups.sortedBy { it.name }.map {
                        listOf(
                            Button.danger("rolereset:${it.name}", it.name),
                            Button.secondary("separator${i++}", "|").asDisabled()
                        )
                    }.flatten()
                    buttons.removeLast()
                    if (buttons.isEmpty()) return@transaction

                    //delete previous resetter
                    serverEntry.lastRoleResetMessage?.let {
                        try {
                            bot.jda.getTextChannelById(it.channelId)?.retrieveMessageById(it.messageId)?.complete()
                                ?.delete()?.complete()
                        } catch (t: Throwable) {
                            logger.error("Failed to delete previous role reset message", t)
                        }
                    }

                    //remove last placeholder before sending
                    val msg = event.channel.sendMessage(
                        MessageCreateBuilder().addActionRow(buttons).build()
                    ).complete()
                    msg?.let { serverEntry.setLastRoleResetMessage(it.channel.idLong, it.idLong) } ?: serverEntry.lastRoleResetMessage?.delete()
                }
            }
        }

        bot.buttonEvents += IdentifiableInteractionHandler("rolereset") { event ->
            val groupName = event.interaction.componentId.split(":").getOrNull(1)
            //take roles from role group, find actual existing roles to match, and remove those from the user
            transaction {
                groupName?.let { name ->
                    Servers[event.guild!!.idLong].roleGroups[name]?.roles?.mapNotNull { bot.jda.getRoleById(it.roleId) }
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