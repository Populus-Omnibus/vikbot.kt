package io.github.populus_omnibus.vikbot.api.commands

import io.github.populus_omnibus.vikbot.api.getValue
import io.github.populus_omnibus.vikbot.api.interactions.IdentifiableHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import kotlin.reflect.KProperty


/**
 * Slash command type, when no options are needed, just supply a function, or extend for options
 * use option to create an option property, then index event with it
 * ```kt
 * event[option]
 * ```
 * or, use it as delegate
 * ```kt
 * val id by option("id", "this is an id type", SlashOptionType.STRING)
 * ```
 * Delegate is **not** thread-safe (you can't access the value after switching thread
 * ```kt
 * val id by option("id", "this is an id type", SlashOptionType.STRING)
 * override operator invoke(event: SlashCommandInteractionEvent) {
 *  println(id) // this is safe, even if users are spamming the command
 *  // every thread will have its own id
 *  event.reply("hello").queue {
 *      println(id) // it WILL throw an error as queue might switch thread/context
 *      // In this case, copy the value or don't use delegate: event[id]
 *  }
 * }
 * ```
 */
open class SlashCommand(name: String, val description: String, configure: SlashCommandData.() -> Unit = {}, private val execute: SlashCommand.(SlashCommandInteractionEvent) -> Unit = {})
    : IdentifiableHandler(id = name) {
    val name: String
        get() = id

    private val logger by getLogger()

    private val options = mutableListOf<Option<*>>()
    operator fun <T> SlashCommandInteractionEvent.get(option: Option<T>): T? = option[this]
    operator fun <T> SlashCommandInteractionEvent.get(option: RequiredOption<T>): T = option[this]
    operator fun <T> SlashCommandInteractionEvent.get(option: DefaultOption<T>): T = option[this]

    private val eventThreadLocal = ThreadLocal<SlashCommandInteractionEvent?>()
    internal val event by eventThreadLocal


    open fun bindAndInvoke(event: SlashCommandInteractionEvent) {
        CoroutineScope(eventThreadLocal.asContextElement(event)).launch {
            launch(Dispatchers.IO) {
                invoke(event)
            }
        }
        logger.info{ "finished running command" }
    }

    /**
     * To implement a custom command, you might override this,
     * This is a coroutine function launched on IO thread
     */
    protected open suspend fun invoke(event: SlashCommandInteractionEvent) = execute(event)

    open val configure: SlashCommandData.() -> Unit = {
        configure()
        this@SlashCommand.options.forEach { option ->
            addOption(
                option.optionType.type,
                option.name,
                option.description,
                option.required,
                option.optionType.isAutoComplete
            )
        }
    }

    open fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent) {
        options.find { it.name == event.focusedOption.name }?.optionType?.autoCompleteAction(event)
            ?: run { event.replyChoiceStrings("error").queue() }
    }

    //operator fun invoke(event: SlashCommandInteractionEvent) = invoke(event)

    protected fun <T> option(name: String, description: String, optionType: SlashOptionType<T>) =
        Option(name, description, optionType).also { options += it }

    open inner class Option<T> internal constructor(
        val name: String,
        val description: String,
        val optionType: SlashOptionType<T>
    ) {
        open val required: Boolean = false

        open operator fun get(event: SlashCommandInteractionEvent): T? = event.getOption(name, optionType.optionMapping)

        open operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
            return (this@SlashCommand.event ?: error("Can't access parameter outside event")).getOption(
                name,
                optionType.optionMapping
            )
        }

        fun required() = RequiredOption(name, description, optionType).also { options[options.size - 1] = it }

        fun default(default: T) =
            DefaultOption(name, description, optionType, default).also { options[options.size - 1] = it }
    }

    inner class RequiredOption<T> internal constructor(
        name: String,
        description: String,
        optionType: SlashOptionType<T>
    ) : Option<T>(name, description, optionType) {
        override val required: Boolean = true

        override fun getValue(thisRef: Any?, property: KProperty<*>): T =
            super.getValue(thisRef, property) ?: error("Required argument missing")

        override fun get(event: SlashCommandInteractionEvent): T {
            return super.get(event) ?: error("Required argument missing")
        }
    }

    inner class DefaultOption<T> internal constructor(
        name: String,
        description: String,
        optionType: SlashOptionType<T>,
        private val default: T
    ) : Option<T>(name, description, optionType) {

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = super.getValue(thisRef, property) ?: default

        override fun get(event: SlashCommandInteractionEvent): T {
            return super.get(event) ?: default
        }
    }



    // Context local thingy
    /*
    class CoroutineLocalEvent(var event: SlashCommandInteractionEvent? = null) : AbstractCoroutineContextElement(CoroutineLocalEvent) {
        companion object Key : CoroutineContext.Key<CoroutineLocalEvent>
    }

    class ContextLocalCommandEvent : ThreadContextElement<SlashCommandInteractionEvent?>, ReadWriteProperty<Any?, SlashCommandInteractionEvent?> {
        companion object Key : CoroutineContext.Key<ContextLocalCommandEvent>
        private var state by ThreadLocal<SlashCommandInteractionEvent?>()

        override val key: CoroutineContext.Key<*>
            get() = Key

        override fun updateThreadContext(context: CoroutineContext): SlashCommandInteractionEvent? {
            val oldState = state
            state = context[CoroutineLocalEvent]?.event
            return oldState
        }

        override fun restoreThreadContext(context: CoroutineContext, oldState: SlashCommandInteractionEvent?) {
            context[CoroutineLocalEvent]?.event = state
            state = oldState
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): SlashCommandInteractionEvent? {
            return state
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: SlashCommandInteractionEvent?) {
            state = value
        }

    }
    /
     */
}



