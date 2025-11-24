package io.github.populus_omnibus.vikbot.bot.modules

import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateData

@Command
object OwO : SlashCommand("owo","OwO") {

    override suspend fun invoke(event: SlashCommandInteractionEvent) {
        event.reply(MessageCreateData.fromFiles(openOwOImage())).complete()
    }

    private fun openOwOImage() : FileUpload {
        return OwO::class.java.getResourceAsStream("/owo.png")!!.use {
            FileUpload.fromData(it.readAllBytes(), "owo.png")
        }
    }
}