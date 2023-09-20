package io.github.populus_omnibus.vikbot.bot.modules.roleselector

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.VikBotHandler.config
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.bot.RoleGroup
import io.github.populus_omnibus.vikbot.bot.modules.roleselector.RoleSelectorModule.expiringReplies
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal


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
            val serverEntry = config.servers[event.guild!!.idLong]
            val group = serverEntry.roleGroups[data.groupName]
            val selected = event.interaction.values.filterIsInstance<Role>().toMutableList()
                .sortedBy { it.name } //can only receive roles, but check just in case
            serverEntry.roleGroups[data.groupName] = updateRolesFromReality(selected, group)
            refreshGroupEmbeds(data.groupName)

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

        bot.buttonEvents += IdentifiableInteractionHandler("rolegroupeditlooks") {
            val dir = (it.componentId.split(":").getOrNull(1))
            val data = expiringReplies[it.messageIdLong]?.second as? RoleGroupLooksEditorData
            val group = data?.group
            if (dir == null || data == null || group == null) {
                it.reply("failed").setEphemeral(true).complete()
                return@IdentifiableInteractionHandler
            }
            when (dir) {
                "right" -> {
                    data.currentPage = (data.currentPage + 1).coerceAtMost(group.roles.count() - 1)
                }

                "left" -> {
                    data.currentPage = (data.currentPage - 1).coerceAtLeast(0)
                }

                "modify" -> {
                    val ti1 = TextInput.create("name", "Full name (default if empty)", TextInputStyle.SHORT)
                        .setRequired(false).build()
                    val ti2 =
                        TextInput.create("description", "Description", TextInputStyle.SHORT).setRequired(false).build()
                    it.replyModal(
                        Modal.create("rolegroupeditlooks", "Edit role").addComponents(ActionRow.of(ti1))
                            .addComponents(ActionRow.of(ti2)).build()
                    ).complete()
                    return@IdentifiableInteractionHandler
                }
                else -> {
                    data.group.roles[data.currentPage].apply {
                        descriptor = descriptor.copy(emoteName = "")
                    }
                }

            }
            it.deferEdit().complete()
            data.reload()
            config.save()
        }

        bot.modalEvents += IdentifiableInteractionHandler("rolegroupeditlooks") { interact ->
            val name = interact.getValue("name")?.asString
            val desc = interact.getValue("description")?.asString
            (expiringReplies[interact.message?.idLong]?.second as? RoleGroupLooksEditorData)?.let {
                val current = it.group.roles[it.currentPage]
                current.descriptor = current.descriptor.copy(
                    fullName = if (name.isNullOrEmpty()) current.descriptor.apiName else name, description = desc ?: ""
                )
                it.reload()
            }
            interact.deferEdit().complete()
            config.save()
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
                data.reload()
            }
            EventResult.PASS
        }
    }

    private fun refreshGroupEmbeds(groupName: String) {
        expiringReplies.mapNotNull { (it.value.second as? RoleGroupLooksEditorData) }
            .filter { it.groupName == groupName }.forEach { it.reload() }
    }

    private fun updateRolesFromReality(from: List<Role>, to: RoleGroup): RoleGroup {
        return to.copy(roles = from.asSequence().map { role ->
            to.roles.find { it.roleId == role.idLong }?.let {
                RoleSelectorCommands.validateFromApiRole(role, it)
            } ?: RoleGroup.RoleEntry(role.idLong, RoleGroup.RoleEntry.RoleDescriptor("", role.name, role.name, ""))
        }.toMutableList())
    }
}