package io.github.populus_omnibus.vikbot.bot.modules.mcAuth

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.*
import io.github.populus_omnibus.vikbot.bot.toUserTag
import io.github.populus_omnibus.vikbot.db.McLinkedAccount
import io.github.populus_omnibus.vikbot.db.McLinkedAccounts
import io.github.populus_omnibus.vikbot.db.McOfflineAccount
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jetbrains.exposed.sql.transactions.transaction


@Command(type = CommandType.OWNER)
object McAuthAdminCommands : CommandGroup("vikauthAdmin".lowercase(), "Offline minecraft account tool",
    {
        administrator()
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
                    val onlineAccounts = McLinkedAccount.find { McLinkedAccounts.user eq user.idLong }.toList()

                    if (account == null && onlineAccounts.isEmpty()) {
                        event.reply("No account is used for this user")
                    } else {
                        val builder = StringBuilder()
                        if (account != null) {
                            builder.append("Offline account: `${account.displayName}`, UUID: `${account.uuid}`\n")
                        }
                        if (onlineAccounts.isNotEmpty()) {
                            builder.append("Online accounts: \n- ${onlineAccounts.joinToString("\n- ") { "https://namemc.com/profile/${it.uuid}" }}")
                        }
                        event.reply(builder.toString())
                    }
                }.setEphemeral(ephemeral).complete()
            }
        }

        this += object : SlashCommand("queryName".lowercase(), "[OFFLINE] Query a discord user from account name") {
            val account by option("displayName".lowercase(), "The name of the account", ListOptionType(
                { transaction { McOfflineAccount.all().limit(25).toSet() } }
            ) { it.displayName }).required()
            val ephemeral by option("ephemeral", "Send as ephemeral message, default true", SlashOptionType.BOOLEAN).default(true)

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                event.reply("Account belongs to ${account.discordUserId.toUserTag()}").setEphemeral(ephemeral).complete()
            }
        }

        this += object : SlashCommand("queryUuid".lowercase(), "[OFFLINE] Query account from UUID") {
            val account by option("uuid", "Account UUID", ListOptionType(
                { transaction { McOfflineAccount.all().limit(25).toSet() } }
            ) { it.uuid.toString() })
            val ephemeral by option("ephemeral", "Send as ephemeral message, default true", SlashOptionType.BOOLEAN).default(true)

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                if (account == null) {
                    event.reply("Account not found")
                } else {
                    event.reply("Account belongs to ${account!!.discordUserId.toUserTag()}")
                }.setEphemeral(ephemeral).complete()
            }
        }

        this += object : SlashCommand("userToken".lowercase(), "Get a login token") {
            val user by option("user", "Discord user", SlashOptionType.USER).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                transaction {
                    val account = McOfflineAccount.getByUser(user)

                    if (account == null) {
                        event.reply("No account is used for this user")
                    } else {
                        event.reply("Account(uuid=`${account.id}`, displayName=`${account.displayName}`, skinUrl=`${account.skinUrl}`, token=`${account.token}`)")
                    }
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

        this += object : SlashCommand("setLogging".lowercase(), "Set MC server logging, default is disabled") {
            val channel by option("channel", "Channel to send join/leave messages", SlashOptionType.CHANNEL)

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                VikBotHandler.config.vikAuthChannel = channel?.idLong
                VikBotHandler.config.save()
                event.reply("Logging channel set to ${channel?.asMention ?: "none"}").setEphemeral(true).complete()
            }
        }
    }


}