package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.VikBotHandler.config
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.api.createMemory
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.api.maintainEvent
import io.github.populus_omnibus.vikbot.api.plusAssign
import io.github.populus_omnibus.vikbot.bot.RoleEntry
import io.github.populus_omnibus.vikbot.bot.ServerEntry
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import kotlin.time.Duration.Companion.minutes

object RoleSelector {

    private val expiringReplies = createMemory<Long, Message>()

    @Module
    operator fun invoke(bot: VikBotHandler) {
        bot += expiringReplies.maintainEvent(14.minutes) { _, msg ->
            msg.delete().queue()
        }

        bot.commands += CommandGroup(
            "roleselector", "Admin-only commands for adding and editing role selectors"
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
                val groupName by option(
                    "name", "name of the group", RoleSelectorGroupAutocompleteString(config.serverEntries)
                ).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val removed = config.serverEntries[event.guild?.idLong]?.roleGroups?.remove(groupName)
                    config.save()
                    val reply =
                        event.reply("$groupName ${if (removed == null) "does not exist" else "has been removed"}")
                    if (removed == null) reply.setEphemeral(true)
                    reply.complete()
                }
            }

            commandGroup += object : SlashCommand("list", "list all role selector groups for this server") {
                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val groups = config.serverEntries[event.guild?.idLong]?.roleGroups ?: run {
                        event.reply("server has no groups").setEphemeral(true).complete()
                        return
                    }
                    val guildRoles = event.guild?.roles ?: run {
                        event.reply("failed retrieval of roles").setEphemeral(true).complete()
                        return
                    }
                    //first sorted map call sorts out the order
                    val paired = groups.toSortedMap().map { entry ->
                        entry.key!! to entry.value.map { role ->
                            Pair(guildRoles.firstOrNull { it.idLong == role.roleId }, role)
                        }.sortedBy { it.first?.name ?: it.second.fullName }
                    }.toMap()

                    val outputStringData = paired.map { (groupId, rolePairs) ->
                        //this is the string that will be output for each group
                        val groupOutput = rolePairs.joinToString("\n\t") { formattedOutput(it) }
                        "**__${groupId}__**\n\t$groupOutput"
                    }
                    event.reply(outputStringData.joinToString("\n")).complete()
                }

                private fun formattedOutput(source: Pair<Role?, RoleEntry>) : String {
                    val (emote, name1, name2, description) = dataFromRolePair(source)
                    return "**$name1** $emote\n\t\t($name2 | $description)"
                }
            }

            commandGroup += object : SlashCommand("editchoices", "select roles to include in group") {
                val groupName by option(
                    "name", "name of the group", RoleSelectorGroupAutocompleteString(config.serverEntries)
                ).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    //if such a role group does not exist, fail
                    val group = config.serverEntries[event.guild?.idLong]?.roleGroups?.get(groupName)
                    if (group.isNullOrEmpty()) {
                        event.reply("group not found or empty").setEphemeral(true).complete()
                        return
                    }

                    val selectMenu =
                        EntitySelectMenu.create("rolegroupedit:${groupName}", EntitySelectMenu.SelectTarget.ROLE)
                            .setRequiredRange(0, 25).build()
                    expiringReplies += event.reply("This message is deleted after 14 minutes as the interaction expires.\nEditing: $groupName")
                        .addActionRow(selectMenu).complete()
                }
            }

            commandGroup += object :
                SlashCommand("editlooks", "edit the description and emote linked to roles of a group") {}
            commandGroup += object : SlashCommand("prunegroups", "remove invalid roles from groups") {
                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    pruneRoles(bot)
                    event.reply("groups pruned!").setEphemeral(true).complete()
                }
            }
        }

        bot.entitySelectEvents += IdentifiableInteractionHandler("rolegroupedit") { event ->
            //get all roles belonging to the group referenced by the component's id
            event.deferReply().setEphemeral(true).complete()
            val groupName = event.componentId.split(":").elementAtOrNull(1)
            val group = config.serverEntries[event.guild?.idLong]?.roleGroups?.get(groupName) ?: run {
                event.reply("group not found").setEphemeral(true).complete()
                return@IdentifiableInteractionHandler
            }

            val selected = event.interaction.values
            group.removeIf { roleEntry -> !selected.map { it.idLong }.contains(roleEntry.roleId) }
            selected.filter { sel -> !group.map { it.roleId }.contains(sel.idLong) }.forEach {
                group.add(RoleEntry(it.idLong))
            }
            config.save()
            event.hook.sendMessage("edited group").complete()
        }
    }

    private fun dataFromRolePair(it: Pair<Role?, RoleEntry>): List<String> {
        val (apiRole, entry) = it
        val emote = entry.emoteName ?: ""
        val name1 = apiRole?.name ?: entry.fullName ?: "<name error>"
        val name2 = entry.fullName ?: apiRole?.name ?: "<name error>"
        val description = entry.description ?: "<no desc>"
        return listOf(emote,name1,name2,description)
    }

    private fun pruneRoles(bot: VikBotHandler) {
        val allRoles = bot.jda.guilds.map {
            it to it.roles
        }
        config.serverEntries.entries.forEach { entry ->
            val guildRoles = allRoles.firstOrNull { it.first.idLong == entry.key }?.second ?: return@forEach
            val guildGroups = entry.value.roleGroups
            guildGroups.forEach {
                //remove any roles not present in actual server roles
                it.value.removeIf { role -> guildRoles.any { gr -> gr.idLong == role.roleId } }
            }
        }
    }

    //TODO:
    //to modify roles within a group as an admin
    //list all groups in a pageable format
    //for changing the emote, use a reaction handler - maintain the message for 15-30 minutes
    //for changing the name and description, a button shows a modal with the input fields
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