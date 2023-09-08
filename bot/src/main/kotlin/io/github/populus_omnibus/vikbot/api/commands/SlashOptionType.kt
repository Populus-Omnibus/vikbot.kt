package io.github.populus_omnibus.vikbot.api.commands

import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType


interface SlashOptionType<T> {

    val type: OptionType
    val isAutoComplete: Boolean
        get() = false

    val optionMapping: (OptionMapping) -> T
    fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent) {}

    companion object {

        val STRING = object : SlashOptionType<String> {
            override val type: OptionType = OptionType.STRING
            override val optionMapping = OptionMapping::getAsString
        }

        val INTEGER = object : SlashOptionType<Int> {
            override val type = OptionType.INTEGER
            override val optionMapping = OptionMapping::getAsInt
        }

        val BOOLEAN = object : SlashOptionType<Boolean> {
            override val type = OptionType.BOOLEAN
            override val optionMapping = OptionMapping::getAsBoolean

        }

        val USER = object : SlashOptionType<User> {
            override val type = OptionType.USER
            override val optionMapping = OptionMapping::getAsUser
        }

        val CHANNEL = object : SlashOptionType<GuildChannelUnion> {
            override val type = OptionType.CHANNEL
            override val optionMapping = OptionMapping::getAsChannel
        }

        val ROLE = object : SlashOptionType<Role> {
            override val type = OptionType.ROLE
            override val optionMapping = OptionMapping::getAsRole
        }

        val MENTIONABLE = object : SlashOptionType<IMentionable> {
            override val type = OptionType.MENTIONABLE
            override val optionMapping = OptionMapping::getAsMentionable
        }

        val NUMBER = object : SlashOptionType<Double> {
            override val type = OptionType.NUMBER
            override val optionMapping = OptionMapping::getAsDouble
        }

        val ATTACHMENT = object : SlashOptionType<Message.Attachment> {
            override val type = OptionType.ATTACHMENT
            override val optionMapping = OptionMapping::getAsAttachment
        }
    }
}
