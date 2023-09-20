package io.github.populus_omnibus.vikbot.bot.modules.roleselector

import io.github.populus_omnibus.vikbot.VikBotHandler.config
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.api.plusAssign
import io.github.populus_omnibus.vikbot.bot.RoleGroup
import io.github.populus_omnibus.vikbot.bot.RoleGroup.RoleEntry
import io.github.populus_omnibus.vikbot.bot.modules.roleselector.RoleSelectorModule.expiringReplies
import io.github.populus_omnibus.vikbot.bot.modules.roleselector.RoleSelectorModule.interactionDeletionWarning
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import org.slf4j.kotlin.getLogger

@Command(CommandType.SERVER)
object RoleSelectorCommands :
    CommandGroup("roleselector", "Admin-only commands for adding and editing role selectors", {
        adminOnly()
    }) {
    internal val logger by getLogger()

    init {
        this += object : SlashCommand("add", "add a new role selector group") {
            val groupName by option("name", "name of the group", SlashOptionType.STRING).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val guildId = event.guild!!.idLong
                val entry = config.servers[guildId]
                entry.roleGroups.getOrPut(groupName) { RoleGroup(mutableListOf()) }
                config.save()

                event.reply("$groupName group created!").complete()
            }
        }

        this += object : SlashCommand("delete", "remove a role selector group") {
            val groupName by option(
                "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
            ).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val removed = config.servers[event.guild!!.idLong].roleGroups.remove(groupName)
                config.save()
                val reply = event.reply("$groupName ${if (removed == null) "does not exist" else "has been removed"}")
                if (removed == null) reply.setEphemeral(true)
                reply.complete()
            }
        }

        this += object : SlashCommand("list", "list all role selector groups for this server") {
            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val groups = config.servers[event.guild!!.idLong].roleGroups
                val guildRoles = event.guild!!.roles
                //first sorted map call sorts out the order
                val paired = groups.toSortedMap().map { group ->
                    group.key!! to group.value.roles.mapNotNull { entry ->
                        guildRoles.find { it.idLong == entry.roleId }?.let {
                            validateFromApiRole(it, entry)
                        }
                    }
                }

                val outputStringData = paired.map { (groupId, rolePairs) ->
                    //this is the string that will be output for each group
                    val groupOutput = rolePairs.joinToString("\n\t") { formattedOutput(it) }
                    "**__${groupId}__**\n\t$groupOutput"
                }
                event.reply(outputStringData.let {
                    if (it.isEmpty()) "server has no groups"
                    else it.joinToString("\n")
                }).complete()
            }

            fun formattedOutput(source: RoleEntry): String {
                source.descriptor.let {
                    return "**${it.apiName}** ${it.emoteName}\n\t\t" + "(${it.fullName.ifEmpty { "<no full name>" }} \\|\\| ${it.description.ifEmpty { "<no desc>" }})"
                }
            }

        }

        this += object : SlashCommand("editchoices", "select roles to include in group") {
            val groupName by option(
                "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
            ).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val selectMenu = EntitySelectMenu.create("rolegroupeditchoices:specifics", EntitySelectMenu.SelectTarget.ROLE)
                    .setRequiredRange(0, 25).build()
                expiringReplies += RoleGroupEditorData(
                    event.reply("$interactionDeletionWarning\nEditing: $groupName").addActionRow(selectMenu).complete()
                        .retrieveOriginal().complete(), groupName
                )
            }
        }

        this += object : SlashCommand("editlooks", "edit the description and emote linked to roles of a group") {
            val groupName by option(
                "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
            ).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val data = RoleGroupLooksEditorData.create(groupName, event)
                data?.let {
                    expiringReplies += it
                }
            }
        }

        this += object : SlashCommand("publish", "publish the selected group") {
            val groupName by option(
                "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
            ).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val group = event.guild?.let {
                    config.servers[it.idLong].roleGroups[groupName]
                } ?: run {
                    event.reply("failed").setEphemeral(true).complete()
                    return
                }
                val previous = group.lastPublished?.let {
                    event.guild!!.getTextChannelById(it.channelId)?.retrieveMessageById(it.messageId)?.complete()
                }


                val menu = StringSelectMenu.create("publishedrolemenu:$groupName")
                    .addOptions(group.roles.sortedBy { it.descriptor.fullName }.map {
                        val optionBuild = SelectOption.of(it.descriptor.fullName, it.roleId.toString())
                            .withDescription(it.descriptor.description)
                        try {
                            optionBuild.withEmoji(Emoji.fromFormatted(it.descriptor.emoteName))
                        } catch (_: Exception) {
                            optionBuild
                        }
                    }).setMinValues(0).setMaxValues(group.maxRolesAllowed ?: 25).build()


                (event.hook.interaction.channel as? GuildMessageChannel)?.let { //should convert, but just in case...
                    group.lastPublished = RoleGroup.PublishData(
                        it.idLong, it.sendMessage("").addActionRow(menu).complete().idLong
                    )
                    event.reply("$groupName published!").setEphemeral(true).complete()
                    config.save()

                    //if the new message went through successfully
                    previous?.delete()?.complete()
                    return
                }
                logger.error(
                    "publish command used outside of a text channel (HOW??)\n" + "location: ${event.hook.interaction.channel?.name}"
                )
            }
        }

        this += object : SlashCommand("prune", "remove invalid roles from groups") {
            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                pruneRoles(event.jda.roles)
                event.reply("groups pruned!").setEphemeral(true).complete()
            }
        }

        this += object : SlashCommand("maxroles", "changes the maximum roles to be picked from this group"){
            val groupName by option(
                "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
            ).required()
            val limit by option("maximum", "the maximum - 0 disables it", SlashOptionType.INTEGER).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                config.servers[event.guild?.idLong]?.roleGroups?.get(groupName)?.let { group ->
                    group.maxRolesAllowed = limit.coerceIn(0..25).takeIf { it != 0 }
                    event.reply("done").setEphemeral(true).complete()
                    config.save()
                } ?: run { event.reply("couldn't find group").setEphemeral(true).complete() }
            }
        }

        this += object : SlashCommand("setgeneric", "changes the generic role attached to this group"){
            val groupName by option(
                "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
            ).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val selectMenu = EntitySelectMenu.create("rolegroupeditchoices:generic", EntitySelectMenu.SelectTarget.ROLE)
                    .setRequiredRange(0, 1).build()
                expiringReplies += RoleGroupEditorData(
                    event.reply("$interactionDeletionWarning\nEditing generic for: $groupName")
                        .addActionRow(selectMenu)
                        .addActionRow(Button.secondary("rolegroupeditgeneric_reset", "Reset")).complete()
                        .retrieveOriginal().complete(), groupName
                )
            }
        }


    }


    fun validateFromApiRole(apiRole: Role, storedRole: RoleEntry): RoleEntry {
        return RoleEntry(apiRole.idLong, storedRole.descriptor.copy(apiName = apiRole.name))
    }

    private fun pruneRoles(jdaRoles: List<Role>) {
        val allRoles = jdaRoles.groupBy { it.guild }.toList()
        config.servers.entries.forEach { entry ->
            val guildRoles = allRoles.firstOrNull { it.first.idLong == entry.key }?.second ?: return@forEach
            val guildGroups = entry.value.roleGroups
            guildGroups.forEach {
                //remove any roles not present in actual server roles
                it.value.roles.removeIf { role -> guildRoles.any { gr -> gr.idLong == role.roleId } }
            }
        }
    }
}