package io.github.populus_omnibus.vikbot.bot.modules.roleselector

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.bot.modules.roleselector.RoleSelectorModule.expiringReplies
import io.github.populus_omnibus.vikbot.db.RoleEntries
import io.github.populus_omnibus.vikbot.db.RoleGroup
import io.github.populus_omnibus.vikbot.db.Servers
import io.github.populus_omnibus.vikbot.db.size
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction


object RoleSelectorEvents {

    @Module
    operator fun invoke(bot: VikBotHandler) {
        bot.entitySelectEvents += IdentifiableInteractionHandler("rolegroupeditchoices") { event ->
            val type = event.componentId.split(":").getOrNull(1)

            //get all roles belonging to the group referenced by the component's id
            event.deferReply().setEphemeral(true).complete()

            val data = expiringReplies[event.messageIdLong]?.second as? RoleGroupEditorData ?: run {
                event.hook.sendMessage("failed").setEphemeral(true).complete()
                return@IdentifiableInteractionHandler
            }
            transaction {
                val serverEntry = Servers[event.guild!!.idLong]
                val group = serverEntry.roleGroups.getOrCreate(data.groupName)
                val selected = event.interaction.values.filterIsInstance<Role>().toMutableList()
                    .sortedBy { it.name } //can only receive roles, but check just in case
                updateRolesFromReality(selected, group)
                refreshGroupEmbeds(data.groupName)
            }
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
            transaction {
                val allRoles = Servers[guildId].roleGroups.getOrCreate(groupName).roles.mapNotNull {
                    event.guild!!.roles.find { role -> role.idLong == it.roleId }
                }
                val selection = event.values.mapNotNull {
                    event.guild!!.roles.find { role -> it.toLongOrNull() == role.idLong }
                }
                    .filter { !it.isManaged } //don't even attempt to add or remove a managed role, in case someone added it to the group

                event.member?.let { user ->
                    event.guild!!.modifyMemberRoles(
                        user,
                        selection,
                        allRoles.intersect(user.roles.toSet()) - selection.toSet()
                    )
                        .complete()
                }
            }
            event.hook.sendMessage("update successful!").complete()
        }

        bot.buttonEvents += IdentifiableInteractionHandler("rolegroupeditlooks") {
            val dir = (it.componentId.split(":").getOrNull(1))
            val data = expiringReplies[it.messageIdLong]?.second as? RoleGroupLooksEditorData
            val group = data?.group
            if (dir == null || data == null || group == null) {
                it.reply("failed").setEphemeral(true).complete()
                return@IdentifiableInteractionHandler
            }
            transaction {
                when (dir) {
                    "right" -> {
                        data.currentPage = (data.currentPage + 1).coerceAtMost(group.roles.size - 1)
                    }

                    "left" -> {
                        data.currentPage = (data.currentPage - 1).coerceAtLeast(0)
                    }

                    "modify" -> {
                        val ti1 = TextInput.create("name", "Full name (default if empty)", TextInputStyle.SHORT)
                            .setRequired(false).build()
                        val ti2 =
                            TextInput.create("description", "Description", TextInputStyle.SHORT).setRequired(false)
                                .build()
                        it.replyModal(
                            Modal.create("rolegroupeditlooks", "Edit role").addComponents(ActionRow.of(ti1))
                                .addComponents(ActionRow.of(ti2)).build()
                        ).queue()
                        return@transaction
                    }

                    else -> {
                        data.group.roles[data.currentPage].apply {
                            this.emoteName = ""
                        }
                    }

                }
                it.deferEdit().queue()
                data.reload()
            }
        }

        bot.modalEvents += IdentifiableInteractionHandler("rolegroupeditlooks") { interact ->
            val name = interact.getValue("name")?.asString
            val desc = interact.getValue("description")?.asString
            transaction {
                (expiringReplies[interact.message?.idLong]?.second as? RoleGroupLooksEditorData)?.let {
                    val current = it.group.roles[it.currentPage]
                    current.apply {
                        fullName = if (name.isNullOrEmpty()) current.apiName else name
                        description = desc ?: ""
                    }
                    it.reload()
                }
            }
            interact.deferEdit().complete()
        }


        //handle paginated role group looks edit
        bot.reactionEvent[64] = lambda@{ event ->
            if (event is MessageReactionAddEvent) {
                transaction {
                    val data = expiringReplies[event.messageIdLong]?.second as? RoleGroupLooksEditorData ?: run {
                        return@transaction
                    }

                    val group = data.group
                    val index = data.currentPage
                    group.roles[index].let { role ->
                        role.apply {
                            emoteName = event.reaction.emoji.formatted
                        }
                    }

                    event.retrieveMessage().complete().clearReactions().complete()
                    data.reload()
                }
            }
            EventResult.PASS
        }
    }

    private fun refreshGroupEmbeds(groupName: String) {
        expiringReplies.mapNotNull { (it.value.second as? RoleGroupLooksEditorData) }
            .filter { it.groupName == groupName }.forEach { it.reload() }
    }

    /**
     * Update role group: (create new, if entry exists from `from`, update or else create a new
     * This is not DB compatible, new logic:
     * 1. delete every entry from DB related to RoleGroup but not existing in the list
     * 2. Upsert every other element (update or create)
     *
     * returns RoleGroup instance
     * // sorry, this will be a raw DB transaction for efficiency
     */
    private fun updateRolesFromReality(from: List<Role>, to: RoleGroup): RoleGroup = to.also {_ ->
        transaction {
            RoleEntries.deleteWhere { (group eq to.id) and (roleId notInList from.map { it.idLong }) }

            RoleEntries.batchUpsert(from) {
                this[RoleEntries.group] = to.id
                this[RoleEntries.roleId] = it.idLong
                this[RoleEntries.apiName] = it.name
                // batchUpsert will update existing entries instead of creating new ones
            }
        }
    }
}