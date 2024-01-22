package io.github.populus_omnibus.vikbot.bot.modules.tags

import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.db.Tag
import io.github.populus_omnibus.vikbot.db.TagTable
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.transactions.transaction

internal fun Tag.asMessage(): MessageCreateData {
    return transaction {

        MessageCreateBuilder().apply {
            setContent(this@asMessage.content)
            setFiles(
                this@asMessage.attachments.map {
                    FileUpload.fromData(
                        it.data.inputStream,
                        it.embedName
                    )
                }.toList()
            )
        }.build()
    }
}


internal object TagSlashOption : SlashOptionType<Tag> {
    override val type: OptionType
        get() = OptionType.STRING
    override val optionMapping: (OptionMapping) -> Tag
        get() = {mapping ->
            transaction { Tag[mapping.asString] }
        }

    override val isAutoComplete: Boolean
        get() = true

    override suspend fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent) {
        // just some casual db injection stuff...
        val userString = event.focusedOption.value.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%")
        val ids = transaction {
            TagTable.select(TagTable.id).where {
                TagTable.id like LikePattern("${userString}%", '\\')
            }
                 .limit(25).map { it[TagTable.id].value }.toList()
        }

        event.replyChoiceStrings(ids).complete()

    }
}