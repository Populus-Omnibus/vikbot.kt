package io.github.populus_omnibus.vikbot.bot.modules.tags

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.*
import io.github.populus_omnibus.vikbot.api.createMemory
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.api.maintainEvent
import io.github.populus_omnibus.vikbot.bot.isAdmin
import io.github.populus_omnibus.vikbot.db.*
import io.github.populus_omnibus.vikbot.plusAssign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

object Tags {

    private val attachmentMap = createMemory<String, EncodedFileUpload>()


    @Module
    operator fun invoke(bot: VikBotHandler) {

        bot.globalCommands += object : SlashCommand("tag", "Collection of helpful pre-defined messages") {
            val tag: Tag by option("id", "Tag ID", TagSlashOption).required()
            val ephemeral by option("ephemeral", "make it ephemeral, default true", SlashOptionType.BOOLEAN).default(true)

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                tag.asMessage().let {
                    event.reply(it).setEphemeral(ephemeral).complete()
                }

            }
        }

        bot.ownerServerCommands += CommandGroup("tags", "Collection of helpful pre-defined messages") {
            moderator()
        }.also { commandGroup ->
            commandGroup += object : SlashCommand("add", "add a new tag, admin only") {
                val tagId by option("id", "new tag id", SlashOptionType.STRING).required()
                val text by option("text", "Tag content", SlashOptionType.STRING)
                val attachment by option("attachment", "Optional attachment", SlashOptionType.ATTACHMENT)

                override suspend fun invoke(event: SlashCommandInteractionEvent) = coroutineScope {

                    if (event.member.isAdmin) {
                        val defer = launch { event.deferReply(true) }
                        if (transaction { Tag.findById(tagId) != null }) {
                            event.reply("Tag is already exists, modify or delete it first").setEphemeral(true).queue()
                            return@coroutineScope
                        }


                        tagId
                        val attachmentName = attachment?.fileName

                        attachment?.proxy?.download()?.get()?.let {
                            attachmentMap[tagId] = Clock.System.now() to EncodedFileUpload(
                                embedName = attachmentName!!,
                                embed = it.readAllBytes()
                            )

                        }

                        Modal.create("tagEdit:${tagId}:create", "Create a new tag: $tagId").apply {
                            addActionRow(TextInput.create("content", "Tag content", TextInputStyle.PARAGRAPH).also {
                                it.value = text
                                it.placeholder = "Tags are useful, pre-defined messages, write here your content!"
                            }.build())
                        }.let {
                            defer.join()
                            event.replyModal(it.build()).queue()
                        }

                    } else {
                        event.reply("You don't have permission to manage tags").setEphemeral(true).queue()
                    }

                }
            }

            commandGroup += object : SlashCommand("edit", "edit the content of an existing tag, admin only") {
                val tag by option("id", "tag id", TagSlashOption).required()
                val attachment by option("attachment", "Optional attachment", SlashOptionType.ATTACHMENT)

                override suspend fun invoke(event: SlashCommandInteractionEvent): Unit = coroutineScope {
                    val defer = launch { event.deferReply(true) }
                    if (event.member.isAdmin) {

                        val attachmentName = attachment?.fileName

                        attachment?.proxy?.download()?.get()?.let {
                            attachmentMap[tag.id.value] = Clock.System.now() to EncodedFileUpload(
                                embed = it.readAllBytes(),
                                embedName = attachmentName!!
                            )
                        }

                        transaction {
                            Modal.create("tagEdit:${tag.id.value}:edit", "Edit tag: ${tag.id.value}").apply {
                                addActionRow(
                                    TextInput.create(
                                        "content",
                                        "tag ${tag.id.value}",
                                        TextInputStyle.PARAGRAPH
                                    ).also {
                                        it.value = tag.content
                                        it.placeholder = "This is a placeholder text..."
                                    }.build()
                                )
                            }
                        }.build().let {
                            defer.join()
                            event.replyModal(it).queue()
                        }

                    } else {
                        defer.join()
                        event.hook.sendMessage("You don't have permission to manage tags").setEphemeral(true).complete()
                    }
                }
            }

            commandGroup += object : SlashCommand("list", "List available tags") {
                val filter by option("filter", "tag filter", SlashOptionType.STRING).default("")
                val ephemeral by option("ephemeral", "make it ephemeral, default true", SlashOptionType.BOOLEAN).default(true)

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    transaction {
                        Tag.all().fold(StringBuilder()) { builder, tag ->
                            if (filter.isBlank() || tag.id.value.contains(filter)) {
                                builder.append(" - ${tag.id.value}\t${tag.content.lines()[0]}")
                                builder.append('\n')
                            }
                            builder
                        }
                    }.toString().takeIf(String::isNotBlank)?.let {
                        event.reply(it).setEphemeral(ephemeral).queue()
                    } ?: run {
                        event.reply("No tags found").setEphemeral(true).queue()
                    }
                }
            }

            commandGroup += object : SlashCommand("get", "Get a tag by id, similar to /tag command") {
                val tag by option("id", "Tag ID", TagSlashOption).required()
                val ephemeral by option("ephemeral", "make it ephemeral, default true", SlashOptionType.BOOLEAN).default(true)

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    tag.asMessage().let { event.reply(it).setEphemeral(ephemeral).complete() }
                }
            }

            commandGroup += object : SlashCommand("remove", "Remove a tag, admin-only") {
                val tag by option("id", "Tag ID", TagSlashOption).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    if (event.member.isAdmin) {
                        val embed = transaction {
                            EmbedBuilder().apply {
                                setAuthor(tag.id.value)
                                setTitle("Confirm deleting tag: ${tag.id.value}")
                                setDescription(tag.content)
                            }.build()
                        }
                        event.replyEmbeds(embed).setEphemeral(true).addActionRow(
                            Button.primary("deleteTag:${tag.id.value}", "Yes, delete this"),
                            Button.danger("cancelmodal", "Cancel")
                        ).complete()

                    } else {
                        event.reply("You don't have permission to manage tags").setEphemeral(true).complete()
                    }
                }
            }

            commandGroup += object : SlashCommand("removeAttachments".lowercase(), "Remove all attachment from tag") {
                val selectedTag by option("id", "Tag ID", TagSlashOption).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    val deleted = transaction {
                        TagAttachments.deleteWhere {
                            this.tag eq selectedTag.id
                        }
                    }
                    event.reply("Removed $deleted attachments.").setEphemeral(true).complete()
                }
            }
        }

        bot += attachmentMap.maintainEvent()

        bot.modalEvents += IdentifiableInteractionHandler("tagEdit") {event ->
            val id = event.modalId.split(":")[1]
            val replace = event.modalId.split(":")[2] == "edit"

            val attachment = attachmentMap[id]?.second
            attachmentMap.remove(id)
            transaction {

                val content = event.getValue("content")!!.asString
                if (!replace && id in TagTable) {
                    event.reply("Tag is already exists").setEphemeral(true).queue()
                    return@transaction null
                }
                TagTable.upsert { entry ->
                    entry[TagTable.id] = id

                    entry[TagTable.content] = content

                }
                attachment?.let { attachment ->
                    TagAttachment.new {
                        tag = Tag[id].id

                        data = ExposedBlob(attachment.embed)
                        embedName = attachment.embedName
                    }
                }
                Unit
            }?.let {
                event.reply("Saved!").setEphemeral(true).complete()
            }
        }

        bot.buttonEvents += IdentifiableInteractionHandler("deleteTag") { event ->
            val id = event.button.id!!.split(":")[1]
            transaction {
                val tag = Tag.findById(id)
                if (tag != null) {
                    tag.delete()
                    event.reply("$id is removed").setEphemeral(true).queue()
                } else {
                    event.reply("$id was not found").setEphemeral(true).queue()
                }
            }
        }
    }

}

@Serializable
data class EncodedFileUpload(val embed: ByteArray, val embedName: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncodedFileUpload

        if (!embed.contentEquals(other.embed)) return false
        if (embedName != other.embedName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = embed.contentHashCode()
        result = 31 * result + embedName.hashCode()
        return result
    }
}
