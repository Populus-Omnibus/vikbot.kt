package io.github.populus_omnibus.vikbot.bot.modules.report

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.api.createMemory
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.api.maintainEvent
import io.github.populus_omnibus.vikbot.api.plusAssign
import io.github.populus_omnibus.vikbot.bot.prettyPrint
import io.github.populus_omnibus.vikbot.bot.toUserTag
import kotlinx.datetime.Clock
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import kotlin.time.Duration.Companion.minutes

object ReportForm : ListenerAdapter() {
    private const val APP_NAME = "Report message"
    //map to store message data the moment the report form is fired
    private val fallbackMsgData = createMemory<Long, Message>()

    @Module
    fun init(bot: VikBotHandler){
        val reportFormCommand = Commands.context(Command.Type.MESSAGE, APP_NAME)

        bot += fallbackMsgData.maintainEvent(15.minutes)

        bot.serverCommands += object : SlashCommand("setreportchannel","sets the channel to use in this guild for receiving reports", {adminOnly()}){
            val channel by option("channel", "target channel, not supplying this option will cause a reset", SlashOptionType.CHANNEL)

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                bot.config.servers[event.guild?.idLong]?.reportChannel = channel?.idLong
                bot.config.save()
                event.reply("done!").setEphemeral(true).complete()
            }
        }


        bot.guildInitEvent += {
            it.guild.upsertCommand(reportFormCommand).complete()
        }
        bot.messageContextInteractionEvent[64] = { event ->
            if (event.name == APP_NAME) {
                val modal = Modal.create("reportform:${event.target.idLong}", "Report message")
                        .addActionRow(TextInput.create("reportReason", "Information about report (can be empty)", TextInputStyle.PARAGRAPH)
                        .setRequired(false).build()).build()
                fallbackMsgData += event.target
                event.replyModal(modal).complete()
            }
            EventResult.PASS
        }
        bot.modalEvents += IdentifiableInteractionHandler("reportform"){ event ->
            event.deferReply().setEphemeral(true).complete()
            val channel = bot.config.servers[event.guild?.idLong]?.reportChannel?.let { id -> bot.jda.getTextChannelById(id) }
            val msgId = event.modalId.split(":").getOrNull(1)?.toLongOrNull()
            msgId?.let { id ->
                val reportedMessage = try {
                    event.messageChannel.retrieveMessageById(id).complete()
                } catch (_: ErrorResponseException) {
                    fallbackMsgData[id]?.second
                }

                reportedMessage?.let { message ->
                    val reportedUser = message.author
                    val reporter = event.user
                    val reason = event.getValue("reportReason")?.asString?.takeIf { it.isNotEmpty() } ?: "<no reason>"

                    channel?.sendMessageEmbeds(EmbedBuilder().apply {
                        setColor(bot.config.embedColor)
                        setAuthor(reportedUser.effectiveName, null, reportedUser.avatarUrl)
                        setDescription("**${reportedUser.idLong.toUserTag()} had their [message](${message.jumpUrl}) reported**")
                        addField("Content", message.contentRaw, false)
                        addField("Reason", reason, false)
                        addField("Sent at", message.timeCreated.prettyPrint(true), false)
                        addField("Reported by", reporter.idLong.toUserTag(), false)
                        setFooter(Clock.System.now().prettyPrint())
                        reportedMessage.attachments.filter { it.isImage }.getOrNull(0)?.let {
                            setImage(it.url) //if the message contained an image, store that
                        }
                    }.build())?.complete()

                    event.hook.editOriginal("done!").complete()
                }
            } ?: run {
                event.hook.editOriginal("failed, contact admins if your request should've gone through").complete()
            }
            //if no channel is set in the config file, the report will fall on deaf ears
        }
    }
}

