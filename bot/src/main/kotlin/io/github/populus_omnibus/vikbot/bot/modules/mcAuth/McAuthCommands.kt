package io.github.populus_omnibus.vikbot.bot.modules.mcAuth

import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.*
import io.github.populus_omnibus.vikbot.bot.vikauth.MCAccount
import io.github.populus_omnibus.vikbot.bot.vikauth.VikauthServer
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.kotlin.getLogger
import java.security.SecureRandom
import java.util.*
import kotlin.streams.asSequence

@Command(type = CommandType.OWNER)
object McAuthCommands : CommandGroup("mcauth", "Minecraft offline accounts for BME VIK server") {
    private val logger by getLogger()
    private val random: SecureRandom = SecureRandom.getInstanceStrong()
    private val validator = Regex("[\\d\\w_]+")
    val tokenChars = "_0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toList()
    init {
        this += object : SlashCommand("register", "Create or update account, reset token") {

            val displayName by option("displayName".lowercase(), "Your new name, allowed characters: [a-zA-Z0-9_]", DisplayNameType()).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val error = validateName(displayName)
                if (error != null) {
                    event.reply(error).setEphemeral(true).complete()
                } else {
                    // Verifying the token is very likely not needed, the probability of collision for 16 character long IDs is *almost* none
                    val token = generateToken()
                    val id = event.user.id
                    var newAccount = false
                    val uuid = VikauthServer.accounts[id]?.id ?: UUID.randomUUID().toString().also { newAccount = true }
                    VikauthServer.accounts[id] = MCAccount(
                        id = uuid,
                        token = token,
                        displayName = displayName
                    )
                    VikauthServer.save()
                    event.reply(
                        "Your account has been ${if (newAccount) "registered" else "updated"}\n" +
                                "your token is `$token`"
                    )
                        .setEphemeral(true).complete()
                }
            }
        }

        this += object : SlashCommand("updateName".lowercase(), "Update the name of your offline account") {
            val displayName by option("displayName".lowercase(), "Your new name, allowed characters: [a-zA-Z0-9_]", SlashOptionType.STRING).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                val existingAccount = VikauthServer.accounts[event.user.id]
                if (existingAccount != null) {
                    validateName(displayName)?.let {
                        event.reply(it).setEphemeral(true).complete()
                        return@coroutineScope
                    }

                    VikauthServer.accounts[event.user.id] = existingAccount.copy(displayName = displayName)
                    VikauthServer.save()


                    event.reply("Your name is `$displayName`")
                        .setEphemeral(true).complete()

                } else {
                    event.reply("Please use register to create a new account").setEphemeral(true).complete()
                }
            }
        }

        this += SlashCommand("getToken".lowercase(), "Get your token") { event ->
            val existingAccount = VikauthServer.accounts[event.user.id]
            if (existingAccount != null) {
                event.reply("Your login token is `${existingAccount.token}`").setEphemeral(true).complete()
            } else {
                event.reply("You don't have an account").setEphemeral(true).complete()
            }
        }

        this += SlashCommand("help", "Get help info") { event ->
            event.reply("""
                If you want to play on BME minecraft server, you can use vikauth (this) to create an offline account connected to your discord profile.  
                You can change the name of this account later, but you can't delete it once created.  
                You'll get a `token`, use that as your login name when joining the server, it will handle the display name.
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
            val token = random.ints(0, tokenChars.size).asSequence().take(16).map { tokenChars[it] }.joinToString("")
            if (VikauthServer.accounts.none { (_, account) -> account.token == token }) return token
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
            VikauthServer.accounts[event.user.id]?.let { account ->
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

