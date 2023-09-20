package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.VikBotHandler.config
import io.github.populus_omnibus.vikbot.VikBotHandler.jda
import io.github.populus_omnibus.vikbot.api.*
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.bot.RoleGroup
import io.github.populus_omnibus.vikbot.bot.RoleGroup.RoleEntry
import io.github.populus_omnibus.vikbot.bot.RoleGroup.RoleEntry.RoleDescriptor
import io.github.populus_omnibus.vikbot.bot.ServerEntry
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import org.slf4j.kotlin.getLogger
import kotlin.collections.*
import kotlin.time.Duration.Companion.minutes

@Command(CommandType.SERVER)
object RoleSelector : CommandGroup("roleselector", "Admin-only commands for adding and editing role selectors", {
    adminOnly()
}) {
    private val expiringReplies = createMemory<Long, CustomMessageData>()
    private val logger by getLogger()
    private val maintainDuration = 14.minutes
    private val interactionDeletionWarning =
        "This message is deleted after ${maintainDuration.inWholeMinutes} minutes as the interaction expires."

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
                val selectMenu = EntitySelectMenu.create("rolegroupeditchoices", EntitySelectMenu.SelectTarget.ROLE)
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
                val group = config.servers[event.guild!!.idLong].roleGroups[groupName]
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
                    it.sendMessage("").addActionRow(menu).complete()
                    event.reply("$groupName published!").setEphemeral(true).complete()
                } ?: run {
                    logger.error(
                        "publish command used outside of a text channel (HOW??)\n" + "location: ${event.hook.interaction.channel?.name}"
                    )
                }
            }
        }

        this += object : SlashCommand("prune", "remove invalid roles from groups") {
            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                pruneRoles(event.jda.roles)
                event.reply("groups pruned!").setEphemeral(true).complete()
            }
        }
    }

    @Module
    operator fun invoke(bot: VikBotHandler) {
        bot += expiringReplies.maintainEvent(maintainDuration) { _, data ->
            data.msg.delete().queue()
        }

        bot.entitySelectEvents += IdentifiableInteractionHandler("rolegroupeditchoices") { event ->
            //get all roles belonging to the group referenced by the component's id
            event.deferReply().setEphemeral(true).complete()
            //TODO: refactor
            val data = expiringReplies[event.messageIdLong]?.second as? RoleGroupEditorData ?: run {
                event.hook.sendMessage("failed").complete()
                return@IdentifiableInteractionHandler
            }
            val serverEntry = config.servers[event.guild!!.idLong]
            val group = serverEntry.roleGroups[data.groupName]
            val selected = event.interaction.values.filterIsInstance<Role>()
                .toMutableList().sortedBy{it.name} //can only receive roles, but check just in case

            serverEntry.roleGroups[data.groupName] = updateRolesFromReality(selected, group)
            config.save()
            event.hook.sendMessage("edited group").complete()
        }

        bot.stringSelectEvents += IdentifiableInteractionHandler("publishedrolemenu") { event ->
            val guildId = event.guild?.idLong
            val groupName = event.componentId.split(":").elementAtOrNull(1)
            if (guildId == null || groupName == null) {
                event.reply("action failed!").setEphemeral(true).complete()
                return@IdentifiableInteractionHandler
            }
            event.deferReply().setEphemeral(true).complete()

            val allRoles = config.getRoleGroup(guildId, groupName).roles.mapNotNull {
                event.guild!!.roles.find { role -> role.idLong == it.roleId }
            }
            val selection = event.values.mapNotNull {
                event.guild!!.roles.find { role -> it.toLongOrNull() == role.idLong }
            }
                .filter { !it.isManaged } //don't even attempt to add or remove a managed role, in case someone added it to the group

            event.member?.let { user ->
                event.guild!!.modifyMemberRoles(user, selection, allRoles.intersect(user.roles.toSet()) - selection.toSet())
                    .complete()
            }
            event.hook.sendMessage("update successful!").complete()
        }

        bot.buttonEvents += IdentifiableInteractionHandler("rolegroupeditlooks"){
            val dir = (it.componentId.split(":").getOrNull(1))
            val data = expiringReplies[it.messageIdLong]?.second as? RoleGroupLooksEditorData
            val group = data?.group
            if(dir == null || data == null || group == null){
                it.reply("failed").complete()
                return@IdentifiableInteractionHandler
            }
            when(dir){
                "modify" -> {
                    val ti1 = TextInput.create("name", "Full name (default if empty)", TextInputStyle.SHORT).build()
                    val ti2 = TextInput.create("description", "Description", TextInputStyle.SHORT).build()
                    it.replyModal(Modal.create("rolegroupeditlooks", "Edit role")
                        .addComponents(ActionRow.of(ti1))
                        .addComponents(ActionRow.of(ti2))
                        .build()).complete()
                    return@IdentifiableInteractionHandler
                }
                "right" -> {
                    data.currentPage = (data.currentPage + 1).coerceAtMost(group.roles.count()-1)
                    it.deferEdit().complete()
                }
                "left" -> {
                    data.currentPage = (data.currentPage - 1).coerceAtLeast(0)
                    it.deferEdit().complete()
                }
            }
            config.save()
            data.edit(group)
        }
        bot.modalEvents += IdentifiableInteractionHandler("rolegroupeditlooks:modify"){ interact ->
            val name = interact.getValue("name")?.asString
            val desc = interact.getValue("description")?.asString
            (expiringReplies[interact.message?.idLong]?.second as? RoleGroupLooksEditorData)?.let{
                val current = it.group.roles[it.currentPage]
                current.descriptor = current.descriptor.copy(
                    fullName = if (name.isNullOrEmpty()) current.descriptor.apiName else name,
                    description = desc ?: "")
            }
        }


        //handle paginated role group looks edit
        bot.reactionEvent[64] = lambda@{ event ->
            if (event is MessageReactionAddEvent) {
                val data = expiringReplies[event.messageIdLong]?.second as? RoleGroupLooksEditorData ?: run {
                    return@lambda EventResult.PASS
                }

                val group = data.group
                val index = data.currentPage
                group.roles[index].let { role ->
                    role.descriptor = role.descriptor.copy(emoteName = event.reaction.emoji.formatted)
                }

                config.save()
                event.retrieveMessage().complete().clearReactions().complete()
                data.edit(group)
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

    open class RoleGroupEditorData(
        msg: Message, val groupName: String,
    ) : CustomMessageData(msg)

    class RoleGroupLooksEditorData
    private constructor(
        msg: Message, groupName: String, var currentPage: Int = 0
    ) : RoleGroupEditorData(msg, groupName) {
        val group: RoleGroup
            get() = config.servers[msg.guild.idLong].roleGroups[this.groupName]

        companion object {
            fun create(groupName: String, interaction: SlashCommandInteractionEvent): RoleGroupLooksEditorData? {
                val channel = interaction.channel as? GuildMessageChannel ?: run { return null }
                val group = config.servers[channel.guild.idLong].roleGroups[groupName]
                val buttons = mutableListOf(Button.primary("rolegroupeditlooks:left", Emoji.fromFormatted("◀")).apply {
                    this.asDisabled()
                }, Button.primary("rolegroupeditlooks:right", Emoji.fromFormatted("▶")).apply {
                    if (group.roles.size < 2) this.asDisabled()
                }, Button.secondary("rolegroupeditlooks:modify", "Modify")
                )
                val msg = group.roles.getOrNull(0)?.descriptor?.let { data ->
                    val send = MessageCreateBuilder().addActionRow(buttons).addEmbeds(this.getEmbed(data))
                        .setContent(interactionDeletionWarning).build()
                    interaction.reply(send).complete().retrieveOriginal().complete()
                }
                return msg?.let { RoleGroupLooksEditorData(it, groupName) }
            }

            fun getEmbed(data: RoleDescriptor): MessageEmbed {
                val botUser = jda.selfUser
                return EmbedBuilder().setAuthor(botUser.effectiveName, null, botUser.effectiveAvatarUrl)
                    .setColor(config.embedColor)
                    .addField("Name: ${data.fullName}  ${data.emoteName}", "Desc: ${data.description}", false).build()
            }
        }

        fun edit(group: RoleGroup) {
            group.roles.getOrNull(currentPage)?.descriptor?.let { data ->
                this.msg.editMessage(
                    MessageEditBuilder().setEmbeds(getEmbed(data)).setContent(interactionDeletionWarning).build()
                ).complete() //TODO: modify buttons
            }
        }
    }
}