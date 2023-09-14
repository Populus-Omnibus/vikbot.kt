package io.github.populus_omnibus.vikbot.bot.modules.mcAuth

import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.commands.*
import io.github.populus_omnibus.vikbot.bot.vikauth.VikauthServer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent


@Command
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
                    val existingAccount = VikauthServer.accounts[user.id]
                    if (existingAccount == null) {
                        event.reply("No account found").setEphemeral(true).complete()
                    } else {
                        VikauthServer.accounts[user.id] = existingAccount.run {
                            copy(
                                token = newToken ?: token,
                                displayName = newUserName ?: displayName
                            )
                        }
                        event.reply("Account has been updated").setEphemeral(true).complete()
                    }
                }
            }
        }

        this += object : SlashCommand("query", "Query an account from user") {
            val user by option("user", "Discord user", SlashOptionType.USER).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val account = VikauthServer.accounts[user.id]
                if (account == null) {
                    event.reply("No account is used for this user").setEphemeral(true).complete()
                } else {
                    event.reply("Account(uuid=${account.id}, displayName=${account.displayName}")
                        .setEphemeral(true).complete()
                }
            }
        }

        this += object : SlashCommand("queryName".lowercase(), "Query a discord user from account name") {
            val account by option("displayName".lowercase(), "The name of the account", ListOptionType(
                { VikauthServer.accounts.entries }
            ) { it.value.displayName }).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                if (account == null) {
                    event.reply("Account not found")
                } else {
                    event.reply("Account belongs to <@${account!!.key}>")
                }.setEphemeral(true).complete()
            }
        }

        this += object : SlashCommand("queryUuid".lowercase(), "Query account from UUID") {
            val account by option("uuid", "Account UUID", ListOptionType(
                { VikauthServer.accounts.entries }
            ) { it.value.id })

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                if (account == null) {
                    event.reply("Account not found")
                } else {
                    event.reply("Account belongs to <@${account!!.key}>")
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