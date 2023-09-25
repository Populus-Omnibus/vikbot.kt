package io.github.populus_omnibus.vikbot.bot.modules.roleselector

import io.github.populus_omnibus.vikbot.VikBotHandler.config
import io.github.populus_omnibus.vikbot.VikBotHandler.jda
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.api.plusAssign
import io.github.populus_omnibus.vikbot.bot.modules.roleselector.RoleSelectorModule.expiringReplies
import io.github.populus_omnibus.vikbot.bot.modules.roleselector.RoleSelectorModule.interactionDeletionWarning
import io.github.populus_omnibus.vikbot.db.*
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import org.jetbrains.exposed.sql.transactions.transaction
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

                transaction {
                    val entry = Servers[guildId]
                    if (entry.roleGroups[groupName] == null) {
                        entry.roleGroups.newRoleGroup(groupName)
                        event.reply("$groupName group created!").queue()
                    }
                    else {
                        event.reply("$groupName already exists").queue()
                    }
                }
            }
        }

        this += object : SlashCommand("delete", "remove a role selector group") {
            val groupName by option(
                "name", "name of the group", RoleSelectorGroupAutocompleteString()
            ).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val removed = transaction {
                    Servers[event.guild!!.idLong].roleGroups[groupName]?.delete() != null
                }
                val reply = event.reply("$groupName ${if (!removed) "does not exist" else "has been removed"}")
                if (!removed) reply.setEphemeral(true)
                reply.queue()
            }
        }

        this += object : SlashCommand("list", "list all role selector groups for this server") {
            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                lateinit var groups: Map<String, RoleGroup>
                val paired = transaction {
                    groups = Servers[event.guild!!.idLong].roleGroups.associateBy { it.name }.toSortedMap()
                    val guildRoles = event.guild!!.roles

                    //first sortedBy call sorts out the order
                    groups.map { group ->
                        group.key to group.value.roles.mapNotNull { entry ->
                            guildRoles.find { it.idLong == entry.role.value }?.let {
                                Pair(it, validateFromApiRole(it, entry))
                            }
                        }
                    }
                }
                //store only once to avoid calling it per group
                val allRoles = jda.roles

                val outputStringData = paired.map { (groupId, rolePairs) ->
                    //this is the string that will be output for each group
                    val groupOutput = rolePairs.joinToString("\n\t") { formattedOutput(it) }

                    val generic = allRoles.find { it.idLong == groups[groupId]?.genericRoleId}

                    "**__${groupId}__** / generic: " + (run {
                        if(config.useRoleTags) generic?.let { "<@&${it.idLong}>" }
                        else generic?.name
                    } ?: "<none>") + "\n\t$groupOutput"
                }

                event.reply(outputStringData.let {
                    if (it.isEmpty()) "server has no groups"
                    else it.joinToString("\n")
                }).complete()
            }

            fun formattedOutput(source: Pair<Role, RoleEntry>): String {
                source.second.let {
                    return "**${if(config.useRoleTags) "<@&${source.first.idLong}> " else it.apiName}**" +
                            "${it.emoteName}\n\t\t" + "(${it.fullName.ifEmpty { "<no full name>" }} \\|\\| " +
                            "${it.description.ifEmpty { "<no desc>" }})"
                }
            }

        }

        this += object : SlashCommand("editchoices", "select roles to include in group") {
            val groupName by option(
                "name", "name of the group", RoleSelectorGroupAutocompleteString()
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
                "name", "name of the group", RoleSelectorGroupAutocompleteString()
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
                "name", "name of the group", RoleSelectorGroupAutocompleteString()
            ).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val group = transaction {
                    event.guild?.let {
                        Servers[it.idLong].roleGroups[groupName]
                    }
                } ?: run {
                    event.reply("failed").setEphemeral(true).complete()
                    return
                }

                val previous = transaction { group.lastPublished }?.let {
                    event.guild!!.getTextChannelById(it.channelId)?.retrieveMessageById(it.messageId)?.complete()
                }


                val menu = StringSelectMenu.create("publishedrolemenu:$groupName")
                    .addOptions(group.roles.sortedBy { it.fullName }.map {
                        val optionBuild = SelectOption.of(it.fullName, it.role.toString())
                            .withDescription(it.description)
                        try {
                            optionBuild.withEmoji(Emoji.fromFormatted(it.emoteName))
                        } catch (_: Exception) {
                            optionBuild
                        }
                    }).setMinValues(0).setMaxValues(group.maxRolesAllowed).build()


                (event.hook.interaction.channel as? GuildMessageChannel)?.let { //should convert, but just in case...
                    transaction { group.updateLastPublished(
                        it.idLong, it.sendMessage("").addActionRow(menu).complete().idLong)
                    }

                    event.reply("$groupName published!").setEphemeral(true).complete()

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
                "name", "name of the group", RoleSelectorGroupAutocompleteString()
            ).required()
            val limit by option("maximum", "the maximum - not setting this value disables it", SlashOptionType.INTEGER)

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                transaction {
                    event.guild?.idLong?.let {
                        Servers[it].roleGroups[groupName]?.let { group ->
                            group.maxRolesAllowed = limit?.coerceIn(1..25) ?: 25
                            event.reply("done").setEphemeral(true).queue()
                        }
                    } ?: event.reply("couldn't find group").setEphemeral(true).queue()
                }
            }
        }

        this += object : SlashCommand("setgeneric", "changes the generic role attached to this group"){
            val groupName by option(
                "name", "name of the group", RoleSelectorGroupAutocompleteString()
            ).required()

            val role by option(
                "role", "the chosen role, not setting one will remove the generic", SlashOptionType.ROLE
            )

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                event.guild?.idLong?.let {
                    Servers[it].roleGroups[groupName]?.let { rg ->
                        rg.genericRoleId = role?.idLong
                        event.reply("done").setEphemeral(true).queue()
                        return
                    }
                    event.reply("failed").setEphemeral(true).queue()
                }

            }
        }


    }


    fun validateFromApiRole(apiRole: Role, storedRole: RoleEntry): RoleEntry {
        return if (storedRole.role.value == apiRole.idLong) {
            // Currently only this branch is used, simply update the role
            storedRole.apiName = apiRole.name
            storedRole
        } else {
            RoleEntry.new(apiRole.idLong) {
                roleGroup = storedRole.roleGroup
                description = storedRole.description
                emoteName = storedRole.emoteName
                apiName = apiRole.name
                fullName = storedRole.fullName
            }.also {
                storedRole.delete()
            }
        }
    }

    private fun pruneRoles(jdaRoles: List<Role>) {
        val allRoles = jdaRoles.groupBy { it.guild }.toList()
        DiscordGuild.all().forEach { entry ->
            val guildRoles = allRoles.firstOrNull { it.first.idLong == entry.guild.value }?.second ?: return@forEach
            val guildGroups = entry.roleGroups
            guildGroups.forEach {rg ->
                //remove any roles not present in actual server roles
                rg.roles.filter { role -> guildRoles.any { gr -> gr.idLong == role.role.value } }.forEach { it.delete() }
            }
        }
    }
}