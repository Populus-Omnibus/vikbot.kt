package io.github.populus_omnibus.vikbot.bot.modules.aprilFoolsNicks

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.administrator
import io.github.populus_omnibus.vikbot.db.NickCache
import io.github.populus_omnibus.vikbot.db.NickName
import io.github.populus_omnibus.vikbot.db.Servers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

@Command
object NicknameCommands : CommandGroup("nick", "Nickname mayhem for april first", {administrator()}) {
    private var nameChanges = false

    init {

        this += object : SlashCommand("nicksToRole".lowercase(), "Set possible nicks to role") {
            val roleId by option("role", "role to assign or null", SlashOptionType.ROLE).required()
            val file by option("file", "Nickname file", SlashOptionType.ATTACHMENT).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val ack = launch { event.deferReply().setEphemeral(true).complete() }

                val lines: List<String> = file.proxy.download().join()!!.bufferedReader().use { file ->
                    file.lines().filter { it.isNotBlank() }.toList()
                }

                NicknameUtil.nickList[roleId.idLong] = lines

                ack.join()
                event.hook.editOriginal("Done :3").complete()
            }
        }


        this += object : SlashCommand("assign", "Assign nicks randomly (after filling data in)") {
            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                nameChanges = true
                val ack = launch { event.deferReply().setEphemeral(true).complete() }
                val random = SecureRandom.getInstanceStrong().asKotlinRandom()

                val nicknameList = NicknameUtil.nickList
                val allNicks = nicknameList.flatMap { it.value }.distinct()
                if (nicknameList.isEmpty()) return@coroutineScope

                //command handling is async, get() is fine
                val allUsers = event.guild?.loadMembers()?.get() ?: run {
                    event.hook.editOriginal("Fail!").complete()
                    return@coroutineScope
                }
                val guildId = transaction { Servers[event.guild!!.idLong].guild }

                allUsers.forEach { member ->
                    transaction {
                        val matching = member.roles.map { it.idLong }.intersect(nicknameList.keys)
                        val nameToAssign = if (matching.isEmpty()) {
                            allNicks.random(random)

                        } else {
                            nicknameList[matching.first()]!!.random(random)
                        }

                        val origNick = run {
                            val user = NickName.find(NickCache.guild eq guildId and NickCache.userId.eq(member.idLong))
                                .firstOrNull()
                            if (user != null) {
                                user.originalNick
                            } else {
                                member.nickname
                            }
                        }


                        try {
                            member.modifyNickname(nameToAssign).complete()
                            NickCache.upsert(NickCache.guild, NickCache.userId) {
                                it[guild] = guildId
                                it[userId] = member.idLong
                                it[originalNick] = origNick
                                it[changedNick] = nameToAssign
                            }
                        } catch (_: PermissionException) {
                        }
                    }
                }
                event.hook.editOriginal("Complete!").complete()
                nameChanges = false
            }
        }
        this += object : SlashCommand("release", "Removes randomly assigned nicknames") {
            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                nameChanges = true
                val guild = event.guild!!
                val ack = launch { event.deferReply().setEphemeral(true).complete() }

                newSuspendedTransaction {
                    val guildId = Servers[guild.idLong].guild
                    val entries = NickName.find(NickCache.guild eq guildId)
                    val members: List<Member> = guild.loadMembers().get()
                    entries.forEach { entry ->
                        members.firstOrNull { it.idLong == entry.user }?.modifyNickname(entry.originalNick)?.complete()
                        entry.delete()
                    }
                }

                ack.join()
                event.hook.editOriginal("Done!").complete()
                nameChanges = false
            }
        }


        VikBotHandler.guildMemberUpdateNicknameEvent[64] = { event ->
            if(!nameChanges) transaction {
                val dbEntry =
                    NickName.find((NickCache.guild eq event.guild.idLong) and (NickCache.userId eq event.member.idLong))
                        .firstOrNull()
                dbEntry?.let {
                    event.member.modifyNickname(it.assignedNick).complete()
                }
            }
            EventResult.PASS
        }
    }
}