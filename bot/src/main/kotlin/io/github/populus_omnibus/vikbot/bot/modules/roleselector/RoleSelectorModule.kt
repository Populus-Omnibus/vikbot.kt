package io.github.populus_omnibus.vikbot.bot.modules.roleselector

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.CustomMessageData
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.createMemory
import io.github.populus_omnibus.vikbot.api.maintainEvent
import io.github.populus_omnibus.vikbot.bot.modules.roleselector.RoleSelectorModule.interactionDeletionWarning
import io.github.populus_omnibus.vikbot.db.*
import io.github.populus_omnibus.vikbot.plusAssign
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.kotlin.error
import kotlin.time.Duration.Companion.minutes


object RoleSelectorModule {
    val expiringReplies = createMemory<Long, CustomMessageData>()
    private val maintainDuration = 14.minutes
    val interactionDeletionWarning =
        "This message is deleted after ${maintainDuration.inWholeMinutes} minutes as the interaction expires."

    @Module
    operator fun invoke(bot: VikBotHandler) {
        bot += expiringReplies.maintainEvent(maintainDuration) { _, data ->
            data.msg.delete().complete()
        }
    }
}

/** This class should **never** be constructed in a direct message context, only in guilds. **/
class RoleSelectorGroupAutocompleteString(
) : SlashOptionType<String> {
    override val type = OptionType.STRING
    override val optionMapping = OptionMapping::getAsString
    override val isAutoComplete = true

    override suspend fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent) {
        val selected: List<String> = transaction {
            val groups = Servers[event.guild!!.idLong].roleGroups.asSequence().map { it.name }

            return@transaction (event.focusedOption.value.takeIf(String::isNotBlank)?.let { string ->
                groups.filter { it.startsWith(string) }
            } ?: groups).take(25).toList()
        }
        event.replyChoiceStrings(selected).complete()
    }
}

open class RoleGroupEditorData(
    msg: Message, val groupName: String,
) : CustomMessageData(msg)

class RoleGroupLooksEditorData
private constructor(
    msg: Message, groupName: String, var currentPage: Int = 0
) : RoleGroupEditorData(msg, groupName) {
    val group: RoleGroup
        get() = transaction { RoleGroup.find { RoleGroups.guild eq msg.guild.idLong and (RoleGroups.name eq groupName) }.first() }

    companion object {
        fun create(groupName: String, interaction: SlashCommandInteractionEvent): RoleGroupLooksEditorData? {
            val channel = interaction.channel as? GuildMessageChannel ?: run { return null }
            val msg = transaction {
                val group = Servers[channel.guild.idLong].RoleGroupAccessor().let {
                    it[groupName] ?: it.newRoleGroup(groupName)
                }
                val buttons = mutableListOf(
                    Button.primary("rolegroupeditlooks:left", Emoji.fromFormatted("◀"))
                        .asDisabled(),
                    Button.primary("rolegroupeditlooks:right", Emoji.fromFormatted("▶"))
                        .withDisabled(group.roles.count() < 2),
                    Button.secondary("rolegroupeditlooks:modify", "Modify"),
                    Button.secondary("rolegroupeditlooks:removeemote", "Remove emote")
                )


                val msg = group.roles.minByOrNull { it.apiName }?.let { data ->
                    val send = MessageCreateBuilder().addActionRow(buttons).addEmbeds(getEmbed(data))
                        .setContent(interactionDeletionWarning).build()
                    interaction.reply(send).complete().retrieveOriginal().complete()
                }
                msg
            }
            return msg?.let { RoleGroupLooksEditorData(it, groupName) }
        }

        fun getEmbed(data: RoleEntry): MessageEmbed {
            val botUser = VikBotHandler.jda.selfUser
            return EmbedBuilder().setAuthor(botUser.effectiveName, null, botUser.effectiveAvatarUrl)
                .setColor(VikBotHandler.config.embedColor)
                .addField(data.apiName, "Chosen name: ${data.fullName}  ${data.emoteName}\nDesc: ${data.description}", false)
                .build()
        }
    }

    fun reload() {
        //page number validation
        currentPage = currentPage.coerceIn(0..<group.roles.size)

        val act = action@{
            group.roles.sortedBy { it.apiName }.getOrNull(currentPage)?.let { data ->
                val builder = MessageEditBuilder().setEmbeds(getEmbed(data)).setContent(interactionDeletionWarning)
                val buttons = this.msg.actionRows[0]?.actionComponents ?: run {
                    RoleSelectorCommands.logger.error { "Buttons not found in a paginated message!" }
                    return@action
                }
                this.msg.editMessage(builder.setActionRow(buttons.apply {
                    this[0] = buttons[0]?.withDisabled(currentPage == 0)
                    this[1] = buttons[1]?.withDisabled(currentPage >= group.roles.size - 1)
                }).build()).queue()
            }
        }

        if (TransactionManager.currentOrNull() != null) {
            act()
        } else {
            transaction { act() }
        }
    }
}