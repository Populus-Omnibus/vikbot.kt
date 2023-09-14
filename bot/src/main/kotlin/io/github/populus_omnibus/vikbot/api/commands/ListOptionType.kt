package io.github.populus_omnibus.vikbot.api.commands

import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType

class ListOptionType<T>(
    val listSupplier: () -> Set<T>,
    val valueSelector: (T) -> String
) : SlashOptionType<T?> {

    override val type: OptionType
        get() = OptionType.STRING

    override val optionMapping: (OptionMapping) -> T?
        get() = {
            mapping -> listSupplier().find { valueSelector(it) == mapping.asString }
        }

    override val isAutoComplete: Boolean
        get() = true

    override suspend fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent): Unit = coroutineScope {
        val userString = event.focusedOption.value
        val entries = listSupplier().asSequence().map(valueSelector)
            .filter { userString.isEmpty() || it.startsWith(userString) }.take(25)

        event.replyChoiceStrings(entries.toList()).complete()
    }
}