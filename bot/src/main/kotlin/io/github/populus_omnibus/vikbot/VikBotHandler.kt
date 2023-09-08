package io.github.populus_omnibus.vikbot

import io.github.populus_omnibus.vikbot.api.Event
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableInteractionHandler
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableList
import io.github.populus_omnibus.vikbot.api.interactions.invoke
import io.github.populus_omnibus.vikbot.api.invoke
import io.github.populus_omnibus.vikbot.bot.BotConfig
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.kotlin.error
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import java.util.*

object VikBotHandler : EventListener {
    private val logger by getLogger()

    val messageReceivedEvent = Event.simple<MessageReceivedEvent>()
    val messageUpdateEvent = Event.simple<MessageUpdateEvent>()

    val initEvent = mutableListOf<(ReadyEvent) -> Unit>().apply { add(::registerCommands) }
    val guildInitEvent = mutableListOf<(GuildReadyEvent) -> Unit>().apply{ add(::registerOwnerCommands) }

    val maintainEvent = mutableListOf<() -> Unit>()

    val commands = mutableListOf<SlashCommand>()
    val ownerServerCommands = mutableListOf<SlashCommand>()

    val buttonEvents = IdentifiableList<IdentifiableInteractionHandler<ButtonInteractionEvent>>()
    val modalEvents = IdentifiableList<IdentifiableInteractionHandler<ModalInteractionEvent>>()

    lateinit var config: BotConfig

    private val timer = Timer()

    private val commandMap: Map<String, SlashCommand> by lazy {
        (commands + ownerServerCommands).associateBy { it.name }
    }

    init {
        ownerServerCommands += object : SlashCommand("stop", "Stops the bot (owner only)", configure = {
            defaultPermissions = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)
        }) {
            val forRestart = option("restart", "Stop for restart is true", SlashOptionType.BOOLEAN).default(false)

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                event.reply("stopping").setEphemeral(true).queue {
                    Runtime.getRuntime().exit(if (event[forRestart]) 0 else 4)
                }
            }
        }

        commands += SlashCommand("ping", "quick self test") {
            it.reply("pong\nclient latency: ${it.jda.gatewayPing}").queue()
        }

    }


    fun start() {
        val client: JDA = JDABuilder.createDefault(config.token).apply {
            // configure here
            setActivity(Activity.playing("emotes.kosmx.dev | following instructions"))
            disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING)
            enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
            addEventListeners(this@VikBotHandler)

        }.build().apply { awaitReady() }

        logger.info("Bot is ready")

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutting down")
            client.shutdown()
        })

        timer.schedule(object : TimerTask(){
            override fun run() {
                maintainEvent.forEach{ it.invoke() }
            }
        }, 1000, 1000)
    }

    override fun onEvent(event: GenericEvent) {
        try {
            when (event) {
                is MessageReceivedEvent -> messageReceivedEvent(event)
                is MessageUpdateEvent -> messageUpdateEvent(event)
                is ReadyEvent -> initEvent.forEach { subscriber -> subscriber(event) }
                is GuildReadyEvent -> guildInitEvent.forEach { subscriber -> subscriber(event) }
                is SlashCommandInteractionEvent -> {
                    commandMap[event.name]?.bindAndInvoke(event)
                        ?: logger.error{ "executed command was not found: ${event.name}" }
                }
                is ButtonInteractionEvent -> buttonEvents(event.button.id, event, "button")
                is ModalInteractionEvent -> modalEvents(event.modalId, event, "modal")
                is CommandAutoCompleteInteractionEvent -> commandMap[event.name]?.autoCompleteAction(event) ?: run { event.replyChoiceStrings("error") }
            }
        } catch (e: Throwable) {
            logger.error(e) { "Error while handling $event: ${e.message}" }
        }
    }

    private fun registerCommands(event: ReadyEvent) {
        event.jda.updateCommands().addCommands(
            commands.map {
                Commands.slash(it.name, it.description).apply {
                    it.configure(this)
                }
            }
        ).queue()
    }

    private fun registerOwnerCommands(event: GuildReadyEvent) {
        if (event.guild.idLong.toULong() in config.ownerServers) {
            logger.info { "Registering owner server commands on ${event.guild}" }
            event.guild.updateCommands().addCommands(
                ownerServerCommands.map {
                    Commands.slash(it.name, it.description).apply {
                        it.configure(this)
                    }
                }
            ).queue()
        }
    }
}