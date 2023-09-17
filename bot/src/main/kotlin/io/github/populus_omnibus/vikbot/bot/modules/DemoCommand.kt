package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

@Command(type = CommandType.SERVER)
object DemoCommand: SlashCommand(
    "helloWorld".lowercase(),
    "Hello world demo command",
) {

    private val someString by option("someString".lowercase(), "Some string", SlashOptionType.STRING)

    override suspend fun invoke(event: SlashCommandInteractionEvent) = coroutineScope {
        launch {
            delay(8000) // suuper efficient delay using kotlin coroutines
            val action = someString?.let {
                event.hook.editOriginal("Here is your string: $it")
            } ?: event.hook.editOriginal("Hello world")

            action.queue()
        }

        event.deferReply().queue()
    }
}