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
import io.github.populus_omnibus.vikbot.api.maintainEvent
import io.github.populus_omnibus.vikbot.api.plusAssign
import io.github.populus_omnibus.vikbot.bot.RoleEntry
import io.github.populus_omnibus.vikbot.bot.RoleEntry.RoleDescriptor
import io.github.populus_omnibus.vikbot.bot.RoleGroup
import io.github.populus_omnibus.vikbot.bot.ServerEntry
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.slf4j.kotlin.getLogger
import kotlin.time.Duration.Companion.minutes


object RoleSelector {

    private val expiringReplies = createMemory<Long, Message>()
    private val logger by getLogger()

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
                    val guildId = event.guild!!.idLong
                    val entry = config.servers[guildId]
                    entry.roleGroups.getOrPut(groupName) { RoleGroup(mutableListOf()) }
                    config.save()

                    event.reply("$groupName group created!").complete()
                }
            }


            commandGroup += object : SlashCommand("delete", "remove a role selector group") {
                val groupName by option(
                    "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
                ).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val removed = config.servers[event.guild!!.idLong].roleGroups.remove(groupName)
                    config.save()
                    val reply =
                        event.reply("$groupName ${if (removed == null) "does not exist" else "has been removed"}")
                    if (removed == null) reply.setEphemeral(true)
                    reply.complete()
                }
            }

            commandGroup += object : SlashCommand("list", "list all role selector groups for this server") {
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
                    event.reply(outputStringData.joinToString("\n")).complete()
                }

                fun formattedOutput(source: RoleEntry): String {
                    source.descriptor.let {
                        return "**${it.apiName}** ${it.emoteName}\n\t\t(${it.fullName} \\|\\| ${it.description})"
                    }
                }

            }

            commandGroup += object : SlashCommand("editchoices", "select roles to include in group") {
                val groupName by option(
                    "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
                ).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val selectMenu =
                        EntitySelectMenu.create("rolegroupeditchoices:${groupName}", EntitySelectMenu.SelectTarget.ROLE)
                            .setRequiredRange(0, 25).build()
                    expiringReplies += event.reply("This message is deleted after 14 minutes as the interaction expires.\nEditing: $groupName")
                        .addActionRow(selectMenu).complete()
                }
            }

            //TODO:
            //to modify roles within a group as an admin
            //list all groups in a pageable format
            //for changing the emote, use a reaction handler - maintain the message for 15-30 minutes
            //for changing the name and description, a button shows a modal with the input fields
            commandGroup += object :
                SlashCommand("editlooks", "INACTIVE - edit the description and emote linked to roles of a group") {
                val groupName by option(
                    "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
                ).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val botUser = event.jda.selfUser
                    val group = config.getRoleGroup(event.guild!!.idLong, groupName)
                    event.reply(MessageCreateBuilder()
                        .addActionRow(
                        Button.primary("rolegroupeditlooks-left", Emoji.fromUnicode(":arrow_backward:")),
                        Button.primary("rolegroupeditlooks-right", Emoji.fromUnicode(":arrow_forward:")))
                        .addEmbeds(
                            EmbedBuilder()
                                .setAuthor(botUser.effectiveName, null, botUser.effectiveAvatarUrl)
                                .setColor(config.embedColor)
                                .build())
                        .build()).complete()
                }
            }

            commandGroup += object : SlashCommand("publish", "publish the selected group") {
                val groupName by option(
                    "name", "name of the group", RoleSelectorGroupAutocompleteString(config.servers)
                ).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val group = config.servers[event.guild!!.idLong].roleGroups[groupName]
                    val menu = StringSelectMenu.create("publishedrolemenu:${event.guild!!.idLong}:$groupName").
                        addOptions(group.roles.sortedBy { it.descriptor.fullName } .map {
                            val optionBuild = SelectOption.of(it.descriptor.fullName, it.roleId.toString())
                                .withDescription(it.descriptor.description)
                            try {
                                optionBuild.withEmoji(Emoji.fromFormatted(it.descriptor.emoteName))
                            }
                            catch (_: Exception) { optionBuild }
                        }).setMinValues(0).setMaxValues(group.maxRolesAllowed ?: 25).build()


                    (event.hook.interaction.channel as? GuildMessageChannel)?.let { //should convert, but just in case...
                        it.sendMessage("").addActionRow(menu).complete()
                        event.reply("$groupName published!").setEphemeral(true).complete()
                    } ?: run { logger.error("publish command used outside of a text channel (HOW??)\n" +
                            "location: ${event.hook.interaction.channel?.name}") }
                }
            }

            commandGroup += object : SlashCommand("prune", "remove invalid roles from groups") {
                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    pruneRoles(bot)
                    event.reply("groups pruned!").setEphemeral(true).complete()
                }
            }
        }

        bot.entitySelectEvents += IdentifiableInteractionHandler("rolegroupeditchoices") { event ->
            //get all roles belonging to the group referenced by the component's id
            event.deferReply().setEphemeral(true).complete()
            val groupName = event.componentId.split(":").elementAtOrNull(1) ?: run {
                event.reply("error processing command!").setEphemeral(true).complete()
                return@IdentifiableInteractionHandler
            }
            val serverEntry = config.servers[event.guild!!.idLong]
            val group = serverEntry.roleGroups[groupName]
            val selected = event.interaction.values.filterIsInstance<Role>()
                .toMutableList() //can only receive roles, but check just in case

            serverEntry.roleGroups[groupName] = updateRolesFromReality(selected, group)
            config.save()
            event.hook.sendMessage("edited group").complete()
        }

        bot.stringSelectEvents += IdentifiableInteractionHandler("publishedrolemenu") { event ->
            val guildId = event.componentId.split(":").elementAtOrNull(1)?.toLongOrNull()
            val groupName = event.componentId.split(":").elementAtOrNull(2)
            if(guildId == null || groupName == null){
                event.reply("action failed!").setEphemeral(true).complete()
                return@IdentifiableInteractionHandler
            }
            event.deferReply().setEphemeral(true).complete()

            val allRoles = config.getRoleGroup(guildId, groupName).roles.mapNotNull {
                event.guild!!.roles.find { role -> role.idLong == it.roleId }
            }
            val selection = event.values.mapNotNull {
                event.guild!!.roles.find { role -> it.toLongOrNull() == role.idLong }
            }.filter { !it.isManaged } //don't even attempt to add or remove a managed role, in case someone added it to the group

            event.member?.let { user ->
                event.guild!!.modifyMemberRoles(user, selection, allRoles.intersect(user.roles.toSet()) - selection.toSet())
                    .complete()
            }
            event.hook.sendMessage("update successful!").complete()
        }


        //TODO: more intuitive handling
        //handle paginated role group looks edit
        bot.reactionEvent[64] = lambda@ { event ->
            if(expiringReplies.containsKey(event.messageIdLong) && event is MessageReactionAddEvent){
                val message = event.retrieveMessage().complete()
                if(false) EventResult.PASS //TODO: if message doesn't have the identifying buttons

                //TODO: get role group name from embed
                val group = config.getRoleGroup(event.guild.idLong, "test")
                val index = 0
                group.roles[index].let { role ->
                    group.roles[index] = RoleEntry(role.roleId, role.descriptor.copy(emoteName = event.reaction.emoji.asReactionCode))
                }

                config.save()
            }
            EventResult.PASS
        }
    }

    private fun validateFromApiRole(apiRole: Role, storedRole: RoleEntry): RoleEntry {
        return RoleEntry(apiRole.idLong, storedRole.descriptor.copy(apiName = apiRole.name))
    }

    private fun updateRolesFromReality(from: List<Role>, to: RoleGroup): RoleGroup {
        return to.copy(roles = from.asSequence().map { role ->
            to.roles.find { it.roleId == role.idLong }?.let {
                validateFromApiRole(role, it)
            } ?: RoleEntry(role.idLong, RoleDescriptor("", role.name, role.name, ""))
        }.toMutableList())
    }

    private fun pruneRoles(bot: VikBotHandler) {
        val allRoles = bot.jda.guilds.map {
            it to it.roles
        }
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

/** This class should **never** be constructed in a direct message context, only in guilds. **/
class RoleSelectorGroupAutocompleteString(
    private val entries: Map<Long, ServerEntry>
) : SlashOptionType<String> {
    override val type = OptionType.STRING
    override val optionMapping = OptionMapping::getAsString
    override val isAutoComplete = true

    override suspend fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent) {
        val groups = entries[event.guild!!.idLong]?.roleGroups?.keys ?: run {
            event.replyChoiceStrings().complete()
            return
        }

        val selected: List<String> = (event.focusedOption.value.takeIf(String::isNotBlank)?.let { string ->
            entries[event.guild?.idLong ?: 0]?.roleGroups?.keys?.filter { it.startsWith(string) }
        } ?: groups).take(25)

        event.replyChoiceStrings(selected).complete()
    }
}