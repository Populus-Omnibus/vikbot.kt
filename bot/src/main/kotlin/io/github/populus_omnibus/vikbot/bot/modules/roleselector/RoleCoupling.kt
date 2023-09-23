package io.github.populus_omnibus.vikbot.bot.modules.roleselector

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.DefaultMap
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.bot.ServerEntry

object RoleCoupling {
    private lateinit var entries : DefaultMap<Long, ServerEntry>

    @Module
    fun init(bot: VikBotHandler) {
        entries = bot.config.servers

        bot.guildMemberRoleAddEvent[64] = { event ->
            val groups = entries[event.guild.idLong].roleGroups.values
            val added = event.roles.map { it.idLong }

            //find any groups that included some of the roles added
            //filter it to ones with a valid generic, and add those roles
            groups.filter { it.roles.any { role -> added.contains(role.roleId) } }.mapNotNull { it.genericRoleId }.forEach { generic ->
                bot.jda.getRoleById(generic)?.let { event.guild.addRoleToMember(event.member, it).complete() }
            }
            EventResult.PASS
        }

        bot.guildMemberRoleRemoveEvent[64] = {event ->
            val groups = entries[event.guild.idLong].roleGroups.values
            val removed = event.roles.map { it.idLong }

            //only remove generic if no more specific roles of that group remain
            val generics = groups.filter {group -> group.roles.none {
                (event.member.roles.map {r -> r.idLong } - removed.toSet()).contains(it.roleId)}
            }.mapNotNull { it.genericRoleId }

            val specifics = groups.filter {group -> removed.contains(group.genericRoleId) }.flatMap { it.roles }.map { it.roleId }

            val toRemove = event.guild.roles.filter { (generics+specifics).contains(it.idLong) }.mapNotNull { it }
            event.guild.modifyMemberRoles(event.member, event.member.roles - toRemove.toSet()).complete()

            EventResult.PASS
        }

    }
}