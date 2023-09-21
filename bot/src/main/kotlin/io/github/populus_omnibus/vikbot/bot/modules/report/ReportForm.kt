package io.github.populus_omnibus.vikbot.bot.modules.report

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.EventResult
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal

object ReportForm : ListenerAdapter() {
    private const val APP_NAME = "Report message"

    @Module
    fun init(bot: VikBotHandler){
        val reportFormCommand = Commands.context(Command.Type.MESSAGE, APP_NAME)


        bot.guildInitEvent += {
            it.guild.upsertCommand(reportFormCommand).complete()
        }
        bot.messageContextInteractionEvent[64] = { event ->
            if (event.name == APP_NAME) {
                event.replyModal(Modal.create("reportform", "Report message")
                    .addActionRow(TextInput.create("reportReason", "Information about report (can be empty)", TextInputStyle.PARAGRAPH)
                        .setRequired(false).build()).build()).complete()
            }
            EventResult.PASS
        }
        bot.modalEvents += IdentifiableInteractionHandler("reportform"){ event ->
            val channel = bot.config.servers[event.guild?.idLong]?.reportChannel?.let { id -> bot.jda.getTextChannelById(id) }
            channel?.sendMessage("${event.member?.effectiveName} reported ${event.message?.author?.effectiveName} for ${event.message?.jumpUrl}")
                ?.complete()
            //if for some reason the required member and message data is missing, let it pass anyway, can help figure out the cause
            //if no channel is set in the config file, the report will fall on deaf ears
            event.reply("sent!").setEphemeral(true).complete()
        }
    }
}