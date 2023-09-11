package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

@Command
object McAuth : SlashCommand("mcauth", "register offline minecraft account") {
    val displayName by option("displayName".lowercase(), "Set account display name", SlashOptionType.STRING).required()

    private val validator = Regex("[\\d\\w_]+")

    override suspend fun invoke(event: SlashCommandInteractionEvent) = coroutineScope {
        event.reply("cmd").setEphemeral(true).complete()
    }

}

@Command
object McAuthAdminCommands : CommandGroup("vikauth", "Offline minecraft account tool",
    {
        adminOnly()
    }) {
    init {
        this += SlashCommand("register", "Create (or edit) your account") {
            val user by option("user", "who is this", SlashOptionType.STRING).required()
            val token by option("token", "Set specific token", SlashOptionType.STRING)
            val userName by option("displayName".lowercase(), "user display name", SlashOptionType.STRING)
        }
    }
}
