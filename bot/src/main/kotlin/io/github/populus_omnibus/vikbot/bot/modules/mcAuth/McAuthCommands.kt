package io.github.populus_omnibus.vikbot.bot.modules.mcAuth

import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.db.McOfflineAccount
import io.github.populus_omnibus.vikbot.db.McOfflineAccounts
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.slf4j.kotlin.getLogger
import java.security.SecureRandom
import java.util.*
import kotlin.streams.asSequence

@Command(type = CommandType.OWNER)
object McAuthCommands : CommandGroup("mcauth", "Minecraft offline accounts for BME VIK server") {
    private val logger by getLogger()
    private val random: SecureRandom = SecureRandom.getInstanceStrong()
    private val validator = Regex("[\\d\\w_]+")
    private val tokenChars = "_0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toList()
    init {
        this += object : SlashCommand("register", "Create or update account, reset token") {

            val mcName by option("displayName".lowercase(), "Your new name, allowed characters: [a-zA-Z0-9_]", DisplayNameType()).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val error = validateName(mcName)
                if (error != null) {
                    event.reply(error).setEphemeral(true).complete()
                } else {
                    // Verifying the token is very likely not needed, the probability of collision for 16 character long IDs is *almost* none
                    val token = generateToken()
                    val id = event.user.idLong
                    var newAccount = false
                    transaction {
                        val uuid: UUID = McOfflineAccount.find(McOfflineAccounts.user eq id).firstOrNull()?.uuid ?: UUID.randomUUID().also { newAccount = true }

                        McOfflineAccounts.upsert(McOfflineAccounts.user) {
                            it[user] = id

                            it[accountId] = uuid
                            it[McOfflineAccounts.token] = token
                            it[displayName] = mcName
                        }
                    }
                    event.reply(
                        "Your account has been ${if (newAccount) "registered" else "updated"}\n" +
                                "your token is `$token`"
                    )
                        .setEphemeral(true).complete()
                }
            }
        }

        this += object : SlashCommand("updateName".lowercase(), "Update the name of your offline account") {
            val mcName by option("displayName".lowercase(), "Your new name, allowed characters: [a-zA-Z0-9_]", SlashOptionType.STRING).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                transaction {
                    val existingAccount = McOfflineAccount.getByUser(event.user.idLong)
                    if (existingAccount != null) {
                        validateName(mcName)?.let {
                            event.reply(it).setEphemeral(true).queue()
                            return@transaction
                        }

                        existingAccount.displayName = mcName


                        event.reply("Your name is `$mcName`")
                            .setEphemeral(true).queue()

                    } else {
                        event.reply("Please use register to create a new account").setEphemeral(true).queue()
                    }
                }
            }
        }

        this += SlashCommand("getToken".lowercase(), "Get your token") { event ->
            val existingAccount = transaction { McOfflineAccount.getByUser(event.user) }
            if (existingAccount != null) {
                event.reply("Your login token is `${existingAccount.token}`").setEphemeral(true).complete()
            } else {
                event.reply("You don't have an account\nPlease create one with `/mcauth register <displayName>").setEphemeral(true).complete()
            }
        }

        this += SlashCommand("help", "Get help info") { event ->
            event.reply("""
                If you want to play on BME minecraft server, you can use vikauth (this) to create an offline account connected to your discord profile.  
                You can change the name of this account later, but you can't delete it once created.  
                You'll get a `token`, use that as your login name when joining the server, it will handle the display name.
                
                If you've lost your token, you can generate a new token using register. It **won't** delete your existing account.
            """.trimIndent())
                .setEphemeral(true)
                .complete()
        }
    }

    private fun validateName(name: String): String? {
        return if (name.length < 4 || name.length > 16) {
            "Invalid display name, it must contain at least 4 and most 16 characters"
        } else if (!name.matches(validator)) {
            "Invalid display name, only a-z A-Z digit and _ are allowed"
        } else if (checkIfNameFree(name)) {
            "You can not choose a display name what is already used by a premium user"
        } else null
    }

    /**
     * Generate new token and verify it's not a duplicate
     *
     */
    private fun generateToken(): String {
        while (true) {
            val token =
                random.ints(0, tokenChars.size).asSequence().take(16).map { tokenChars[it] }.joinToString("")
            if (transaction { McOfflineAccounts.select(McOfflineAccounts.token eq token).none() }) return token
        }
    }

    private class DisplayNameType : SlashOptionType<String> {
        override val type: OptionType
            get() = OptionType.STRING

        override val optionMapping: (OptionMapping) -> String
            get() = OptionMapping::getAsString

        override val isAutoComplete: Boolean
            get() = true

        override suspend fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent): Unit = coroutineScope {
            transaction { McOfflineAccount.getByUser(event.user) }?.let { account ->
                event.replyChoiceStrings(account.displayName).complete()
            }
        }
    }


}

private val httpClient = OkHttpClient()

private fun checkIfNameFree(name: String): Boolean {
    val http = Request.Builder().apply {
        url("https://api.mojang.com/users/profiles/minecraft/${name}")
    }.build()
    val data = String(httpClient.newCall(http).execute().body!!.byteStream().readAllBytes()).let {
        Json.parseToJsonElement(it)
    }
    return (data as? JsonObject)?.containsKey("id") == true
}

