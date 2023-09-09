package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.VikBotHandler.config
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.bot.ServerEntry
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu

object RoleSelector {

    @Module
    operator fun invoke(bot: VikBotHandler) {
        bot.commands += CommandGroup("roleselector", "Admin-only commands for adding and editing role selectors"
        ) { this.adminOnly() }.also { commandGroup ->
            commandGroup += object : SlashCommand("add", "add a new role selector group") {
                val groupName by option("name", "name of the group", SlashOptionType.STRING).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val entry = config.getOrAddEntry(event.guild?.idLong)
                    entry?.roleGroups?.getOrPut(groupName) { mutableListOf() }
                    config.save()

                    event.reply("$groupName group created!").complete()
                }
            }


            commandGroup += object : SlashCommand("delete", "remove a role selector group") {
                val groupName by option("name", "name of the group",
                    RoleSelectorGroupAutocompleteString(config.serverEntries)).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val removed = config.serverEntries[event.guild?.idLong]?.roleGroups?.remove(groupName)
                    config.save()
                    event.reply("$groupName ${if (removed == null) "does not exist" else "has been removed"}").complete()
                }
            }

            commandGroup += object : SlashCommand("list", "list all role selector groups for this server") {
                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val groups = config.serverEntries[event.guild?.idLong]?.roleGroups?.keys ?: run{
                        event.reply("server has no groups").complete()
                        return
                    }
                    event.reply(groups.sorted().joinToString("\n")).complete()
                }
            }

            commandGroup += object : SlashCommand("edit", "select roles to include in group") {
                val groupName by option("name", "name of the group",
                    RoleSelectorGroupAutocompleteString(config.serverEntries)).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val group = config.serverEntries[event.guild?.idLong]?.roleGroups?.get(groupName) ?: run {
                        event.reply("group not found").complete()
                        return
                    }
                    val selectMenu = EntitySelectMenu.create("rolegroupedit-${event.guild?.id}", EntitySelectMenu.SelectTarget.ROLE)
                        .setRequiredRange(0, 25).build()
                    event.reply("").addActionRow(selectMenu).complete()
                }
            }
        }
    }
}

class RoleSelectorGroupAutocompleteString(
    private val entries: Map<Long, ServerEntry>
) : SlashOptionType<String> {
    override val type = OptionType.STRING
    override val optionMapping = OptionMapping::getAsString
    override val isAutoComplete = true

    override suspend fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent) {
        val groups = entries[event.guild?.idLong ?: 0]?.roleGroups?.keys ?: run {
            event.replyChoiceStrings().complete()
            return
        }

        val selected: List<String> = (event.focusedOption.value.takeIf(String::isNotBlank)?.let { string ->
            entries[event.guild?.idLong ?: 0]?.roleGroups?.keys?.filter { it.startsWith(string) }
        } ?: groups).take(25)

        event.replyChoiceStrings(selected).complete()
    }
}