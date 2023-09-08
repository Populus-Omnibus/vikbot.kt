package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.VikBotHandler.config
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.bot.ServerEntry
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType

object RoleSelector {

    @Module
    operator fun invoke(bot: VikBotHandler) {
        bot.commands += CommandGroup("roleselector", "Admin-only commands for adding and editing role selectors"
        ) { this.adminOnly() }.also { commandGroup ->
            commandGroup += object : SlashCommand("addgroup", "add a new role selector group") {
                val groupName by option("name", "name of the group", SlashOptionType.STRING).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val entry = config.getOrAddEntry(event.guild?.id?.toULong())
                    entry?.roleGroups?.getOrPut(groupName) { mutableListOf() }
                    config.save()

                    event.replyEmbeds(EmbedBuilder().apply {
                        setDescription("$groupName group created!")
                        setColor(config.embedColor)
                    }.build()).complete()
                }
            }


            commandGroup += object : SlashCommand("deletegroup", "remove a role selector group") {
                val groupName by option("name", "name of the group",
                    RoleSelectorGroupAutocompleteString(config.serverEntries)).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val removed = config.serverEntries[event.guild?.id?.toULongOrNull()]?.roleGroups?.remove(groupName)
                    config.save()
                    event.reply("$groupName ${if (removed == null) "does not exist" else "has been removed"}").complete()
                }
            }
        }
    }
}

class RoleSelectorGroupAutocompleteString(
    private val entries: Map<ULong, ServerEntry>
) : SlashOptionType<String> {
    override val type = OptionType.STRING
    override val optionMapping = OptionMapping::getAsString
    override val isAutoComplete = true

    override fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent) {
        val groups = entries[event.guild?.id?.toULong() ?: 0u]?.roleGroups?.keys ?: run {
            event.replyChoiceStrings().complete()
            return
        }

        val selected: List<String> = (event.focusedOption.value.takeIf(String::isNotBlank)?.let { string ->
            entries[event.guild?.id?.toULong() ?: 0u]?.roleGroups?.keys?.filter { it.startsWith(string) }
        } ?: groups).take(25)

        event.replyChoiceStrings(selected).complete()
    }
}