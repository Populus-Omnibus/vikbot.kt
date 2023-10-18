package io.github.populus_omnibus.vikbot.bot.modules.tags

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.createMemory
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.api.maintainEvent
import io.github.populus_omnibus.vikbot.api.plusAssign
import io.github.populus_omnibus.vikbot.bot.isAdmin
import io.github.populus_omnibus.vikbot.db.*
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.*

object Tags {

    private val attachmentMap = createMemory<String, () -> EncodedFileUpload>()


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

        bot.globalCommands += CommandGroup("tags", "Collection of helpful pre-defined messages").also { commandGroup ->
            commandGroup += object : SlashCommand("add", "add a new tag, admin only") {
                val tagId by option("id", "new tag id", SlashOptionType.STRING).required()
                val text by option("text", "Tag content", SlashOptionType.STRING)
                val attachment by option("attachment", "Optional attachment", SlashOptionType.ATTACHMENT)

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    if (event.member.isAdmin) {
                        if (tags[tagId] != null) {
                            event.reply("Tag is already exists, modify or delete it first").setEphemeral(true).queue()
                            return
                        }


                        val id = tagId
                        val attachmentName = attachment?.fileName

                        attachment?.proxy?.download()?.get()?.let {
                            attachmentMap[id] = Clock.System.now() to {
                                EncodedFileUpload(
                                    embedName = attachmentName!!,
                                    base64Embed = Base64.getEncoder().encodeToString(it.readAllBytes())
                                )
                            }
                        }

                        Modal.create("tagEdit:${tagId}:create", "Create a new tag: $tagId").apply {
                            addActionRow(TextInput.create("content", "Tag content", TextInputStyle.PARAGRAPH).also {
                                it.value = text
                                it.placeholder = "Tags are useful, pre-defined messages, write here your content!"
                            }.build())
                        }.let {
                            event.replyModal(it.build()).queue()
                        }

                    } else {
                        event.reply("You don't have permission to manage tags").setEphemeral(true).queue()
                    }
                }
            }

            commandGroup += object : SlashCommand("edit", "edit the content of an existing tag, admin only") {
                val tagId by option("id", "tag id", TagAutoCompleteString()).required()
                val attachment by option("attachment", "Optional attachment", SlashOptionType.ATTACHMENT)

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    if (event.member.isAdmin) {
                        val tag = tags[tagId]
                        if (tag != null) {

                            val id = tagId
                            val attachmentName = attachment?.fileName

                            attachment?.proxy?.download()?.get()?.let {
                                attachmentMap[id] = Clock.System.now() to {
                                    EncodedFileUpload(
                                        base64Embed = Base64.getEncoder().encodeToString(it.readAllBytes()),
                                        embedName = attachmentName!!
                                    )
                                }
                            }

                            Modal.create("tagEdit:${tagId}:edit", "Edit tag: $tagId").apply {
                                addActionRow(TextInput.create("content", "tag $tagId", TextInputStyle.PARAGRAPH).also {
                                    it.value = tag.response
                                    it.placeholder = "This is a placeholder text..."
                                }.build())
                            }.let {
                                event.replyModal(it.build()).queue()
                            }
                        } else {
                            event.reply("Tag not found: $tagId").setEphemeral(true).queue()
                        }
                    } else {
                        event.reply("You don't have permission to manage tags").setEphemeral(true).queue()
                    }
                }
            }

            commandGroup += object : SlashCommand("list", "List available tags") {
                val filter by option("filter", "tag filter", SlashOptionType.STRING).default("")

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    tags.entries.fold(StringBuilder()) { builder, (id, record) ->
                        if (filter.isBlank() || id.contains(filter)) {
                            builder.append(" - $id\t${record.response.lines()[0]}")
                            builder.append('\n')
                        }
                        builder
                    }.toString().takeIf(String::isNotBlank)?.let {
                        event.reply(it).queue()
                    } ?: run {
                        event.reply("No tags found").setEphemeral(true).queue()
                    }
                }
            }

            commandGroup += object : SlashCommand("get", "Get a tag by id, similar to /tag command") {
                val tag by option("id", "Tag ID", TagAutoCompleteString()).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    getTagMessage(tag)?.let { event.reply(it).queue() }
                        ?: return event.reply("no tag: $tag").setEphemeral(true).queue()
                }
            }

            commandGroup += object : SlashCommand("view", "Get tag by id, for private view") {
                val tag by option("id", "Tag ID", TagAutoCompleteString()).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    getTagMessage(tag)?.let { event.reply(it).setEphemeral(true).queue() }
                        ?: return event.reply("no tag: $tag").setEphemeral(true).queue()
                }
            }

            commandGroup += object : SlashCommand("remove", "Remove a tag, admin-only") {
                val tagId by option("id", "Tag ID", TagAutoCompleteString()).required()

                override suspend fun invoke(event: SlashCommandInteractionEvent) {
                    if (event.member.isAdmin) {
                        val tag = tags[tagId]
                        if (tag != null) {
                            val embed = EmbedBuilder().apply {
                                setAuthor(tagId)
                                setTitle("Confirm deleting tag: $tagId")
                                setDescription(tag.response)
                            }.build()
                            event.replyEmbeds(embed).setEphemeral(true).addActionRow(
                                Button.primary("deleteTag:$tagId", "Yes, delete this"),
                                Button.danger("cancelmodal", "Cancel")
                            ).queue()

                        } else {
                            event.reply("Tag not found").setEphemeral(true).queue()
                        }
                    } else {
                        event.reply("You don't have permission to manage tags").setEphemeral(true).queue()
                    }
                }
            }
        }

        bot += attachmentMap.maintainEvent()

        bot.modalEvents += IdentifiableInteractionHandler("tagEdit") {event ->
            val id = event.modalId.split(":")[1]
            val replace = event.modalId.split(":")[2] == "edit"
            val attachment = attachmentMap[id]?.second?.invoke() ?: tags[id]?.file
            attachmentMap.remove(id)

            val content = event.getValue("content")!!.asString
            if (!replace && id in tags) {
                event.reply("Tag is already exists").setEphemeral(true).queue()
                return@IdentifiableInteractionHandler
            }
            tags[id] = Record(id, content, attachment)
            Record.save(tags)
            event.reply("Saved!").setEphemeral(true).queue()
        }

        bot.buttonEvents += IdentifiableInteractionHandler("deleteTag") { event ->
            val id = event.button.id!!.split(":")[1]
            if (tags.remove(id) != null) {
                Record.save(tags)
                event.reply("$id is removed").setEphemeral(true).queue()
            } else {
                event.reply("$id was not found").setEphemeral(true).queue()
            }
        }
    }

}

@Serializable
data class EncodedFileUpload(val base64Embed: String, val embedName: String)
