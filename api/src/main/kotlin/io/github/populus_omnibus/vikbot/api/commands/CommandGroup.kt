package io.github.populus_omnibus.vikbot.api.commands

import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableList
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationMap
import net.dv8tion.jda.api.utils.data.DataObject
import org.jetbrains.annotations.UnmodifiableView
import org.slf4j.kotlin.error
import org.slf4j.kotlin.getLogger
import java.util.function.Predicate

open class CommandGroup(name: String, description: String, configure: SlashCommandData.() -> Unit = {}) : SlashCommand(name, description, configure) {
    private val logger by getLogger()

    override val configure: SlashCommandData.() -> Unit
        get() = SlashCommandConfig@{
            super.configure(this)
            this.addSubcommands(commands.map { command ->
                SubcommandData(command.name, command.description).apply {
                    command.configure(SubcommandDataWrapper(this, this@SlashCommandConfig))
                }
            })
        }

    private val commands = IdentifiableList<SlashCommand>()

    operator fun plusAssign(command: SlashCommand) {
        commands += command
    }

    override suspend fun bindAndInvoke(event: SlashCommandInteractionEvent) {
        commands[event.subcommandName]?.bindAndInvoke(event) ?: logger.error {"executed command event was not found: ${event.fullCommandName}" }
    }

    override suspend fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent) {
        commands[event.subcommandName!!]?.autoCompleteAction(event) ?: run { event.replyChoiceStrings("error").queue() }
    }

    class SubcommandDataWrapper(private val sub: SubcommandData, private val group: SlashCommandData) : SlashCommandData {
        override fun toData(): DataObject = sub.toData()

        override fun setLocalizationFunction(localizationFunction: LocalizationFunction): SlashCommandData {
            error("Function not supported")
        }

        override fun setName(name: String) = apply { sub.name = name }

        override fun setNameLocalization(locale: DiscordLocale, name: String): SlashCommandData = apply {
            sub.setNameLocalization(locale, name)
        }

        override fun setNameLocalizations(map: MutableMap<DiscordLocale, String>): SlashCommandData = apply {
            sub.setNameLocalizations(map)
        }

        override fun setDefaultPermissions(permission: DefaultMemberPermissions): SlashCommandData {
            error("Can't set permission on sub-command")
        }

        override fun setGuildOnly(guildOnly: Boolean): SlashCommandData {
            error("Can't set guild-only property on sub-command")
        }

        override fun setContexts(contexts: Collection<InteractionContextType?>): SlashCommandData {
            error("Can't set context on usb-command")
        }

        override fun setIntegrationTypes(integrationTypes: Collection<IntegrationType?>): SlashCommandData {
            error("Can't set integration type on sub-command")
        }

        override fun setNSFW(nsfw: Boolean): SlashCommandData {
            error("Can't set NSFW property on sub-command")
        }

        override fun getName(): String = sub.name

        override fun getNameLocalizations(): LocalizationMap = sub.nameLocalizations

        override fun getType(): Command.Type = Command.Type.SLASH

        override fun getDefaultPermissions(): DefaultMemberPermissions = group.defaultPermissions

        override fun isGuildOnly(): Boolean = group.isGuildOnly
        override fun getContexts(): @UnmodifiableView Set<InteractionContextType?> = group.contexts

        override fun getIntegrationTypes(): @UnmodifiableView Set<IntegrationType?> = group.integrationTypes

        override fun isNSFW(): Boolean = group.isNSFW
        override fun setDescription(description: String): SlashCommandData = apply {
            sub.description = description
        }

        override fun setDescriptionLocalization(locale: DiscordLocale, description: String): SlashCommandData = apply {
            sub.setDescriptionLocalization(locale, description)
        }

        override fun setDescriptionLocalizations(map: MutableMap<DiscordLocale, String>): SlashCommandData = apply {
            sub.setDescriptionLocalizations(map)
        }

        override fun getDescription(): String = sub.description

        override fun getDescriptionLocalizations(): LocalizationMap = sub.descriptionLocalizations

        override fun removeOptions(condition: Predicate<in OptionData>): Boolean = sub.removeOptions(condition)

        override fun removeSubcommands(condition: Predicate<in SubcommandData>): Boolean {
            //error("Can't remove subcommands on subcommands")
            return false
        }

        override fun removeSubcommandGroups(condition: Predicate<in SubcommandGroupData>): Boolean {
            return false
        }

        override fun getSubcommands(): MutableList<SubcommandData> = mutableListOf()

        override fun getSubcommandGroups(): MutableList<SubcommandGroupData> = mutableListOf()

        override fun getOptions(): MutableList<OptionData> = sub.options

        override fun addOptions(vararg options: OptionData?): SlashCommandData = apply {
            sub.addOptions(*options)
        }

        override fun addSubcommands(vararg subcommands: SubcommandData?): SlashCommandData {
            error("Not yet implemented")
        }

        override fun addSubcommandGroups(vararg groups: SubcommandGroupData?): SlashCommandData {
            error("Not yet implemented")
        }

    }
}
