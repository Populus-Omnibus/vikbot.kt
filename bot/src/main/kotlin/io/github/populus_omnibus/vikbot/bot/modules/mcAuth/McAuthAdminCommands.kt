package io.github.populus_omnibus.vikbot.bot.modules.mcAuth

import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.*
import io.github.populus_omnibus.vikbot.bot.toUserTag
import io.github.populus_omnibus.vikbot.bot.vikauth.VikauthServer
import io.github.populus_omnibus.vikbot.db.McOfflineAccount
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jetbrains.exposed.sql.transactions.transaction


@Command(type = CommandType.OWNER)
object McAuthAdminCommands : CommandGroup("vikauthAdmin".lowercase(), "Offline minecraft account tool",
    {
        adminOnly()
    }) {
    init {
        this += object : SlashCommand("update", "Create (or edit) someones account") {
            val user by option("user", "who is this", SlashOptionType.USER).required()
            val newToken by option("token", "Set specific token", SlashOptionType.STRING)
            val newUserName by option("displayName".lowercase(), "user display name", SlashOptionType.STRING)

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                if (newToken == null && newUserName == null) {
                    event.reply("Nothing happened").setEphemeral(true).complete()
                } else {
                    transaction {
                        val account = McOfflineAccount.getByUser(user)
                        return@transaction if(account == null) {
                            event.reply("No account found")
                        } else {
                            newToken?.let { account.token = it }
                            newUserName?.let { account.displayName = it }

                            event.reply("Account has been updated")
                        }
                    }.setEphemeral(true).complete()
                }
            }
        }

        this += object : SlashCommand("query", "Query an account from user") {
            val user by option("user", "Discord user", SlashOptionType.USER).required()
            val ephemeral by option("ephemeral", "Send as ephemeral message, default true", SlashOptionType.BOOLEAN).default(true)

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                transaction {
                    val account = McOfflineAccount.getByUser(user)

                    if (account == null) {
                        event.reply("No account is used for this user")
                    } else {
                        event.reply("Account(uuid=${account.id}, displayName=${account.displayName}, skinUrl=${account.skinUrl}")
                    }
                }.setEphemeral(ephemeral).complete()
            }
        }

        this += object : SlashCommand("queryName".lowercase(), "Query a discord user from account name") {
            val account by option("displayName".lowercase(), "The name of the account", ListOptionType(
                { transaction { McOfflineAccount.all().limit(25).toSet() } }
            ) { it.displayName }).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                event.reply("Account belongs to ${account.discordUserId.toUserTag()}").setEphemeral(true).complete()
            }
        }

        this += object : SlashCommand("queryUuid".lowercase(), "Query account from UUID") {
            val account by option("uuid", "Account UUID", ListOptionType(
                { transaction { McOfflineAccount.all().limit(25).toSet() } }
            ) { it.uuid.toString() })

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                if (account == null) {
                    event.reply("Account not found")
                } else {
                    event.reply("Account belongs to ${account!!.discordUserId.toUserTag()}")
                }.setEphemeral(true).complete()
            }
        }

        // Server status
        this += SlashCommand("status", "Query service status") {
            it.reply("Server is ${McAuthModule.status}").setEphemeral(true).complete()
        }

        this += object : SlashCommand("stop", "Stop VikAuth service") {
            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val it = launch { event.deferReply().setEphemeral(true).complete() }

                McAuthModule.stop()
                it.join()
                event.hook.editOriginal("Server stopped").complete()
            }
        }

        this += SlashCommand("start", "Start VikAuth service") {
            McAuthModule.start()
            it.reply("Server started").setEphemeral(true).complete()
        }
    }


}