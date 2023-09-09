package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.VikBotHandler.config
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.api.createMemory
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.bot.RoleEntry
import io.github.populus_omnibus.vikbot.bot.ServerEntry
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu

object RoleSelector {

    //message id and role group name + all roles
    private val paginatedGroupEdits = createMemory<Long, Pair<String, MutableList<RoleEntry>>>()

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

            commandGroup += object : SlashCommand("editchoices", "select roles to include in group") {
                val groupName by option("name", "name of the group",
                    RoleSelectorGroupAutocompleteString(config.serverEntries)).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    //if such a role group does not exist, fail
                    if ((config.serverEntries[event.guild?.idLong]?.roleGroups)?.contains(groupName) == true) {
                        event.reply("group not found").complete()
                        return
                    }

                    val selectMenu = EntitySelectMenu.create("rolegroupedit:${groupName}", EntitySelectMenu.SelectTarget.ROLE)
                        .setRequiredRange(0, 25).build()
                    event.reply("").addActionRow(selectMenu).setEphemeral(true).complete()
                }
            }
        }
        bot.entitySelectEvents += IdentifiableInteractionHandler("rolegroupedit") { event ->
            //get all roles belonging to the group referenced by the component's id
            val group = config.serverEntries[event.guild?.idLong]?.roleGroups?.get(event.componentId.split(":").elementAtOrNull(1))
            val selected = event.interaction.values
        }


        //Handle paginated role group edit messages
        bot.reactionEvent[64] = { event ->
            // check if we need to handle reaction
            if(paginatedGroupEdits.containsKey(event.messageIdLong)) {
                //handle
            }
            EventResult.PASS
        }


        //TODO:
        //to modify roles within a group as an admin
        //list all groups in a pageable format
        //for changing the emote, use a reaction handler - maintain the message for 15-30 minutes
        //for changing the name and description, a button shows a modal with the input fields
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