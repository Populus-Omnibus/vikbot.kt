package io.github.populus_omnibus.vikbot.api

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.dv8tion.jda.api.entities.channel.attribute.IGuildChannelContainer
import kotlin.reflect.KProperty
import kotlin.time.Duration.Companion.minutes

fun IGuildChannelContainer.getTextChannelById(channelId: ULong) = getTextChannelById(channelId.toLong())

fun <T> MutableMap<String, Pair<Instant, T>>.maintainEvent(delay: Int = 15): () -> Unit = object : () -> Unit {
    var lastMaintained = Clock.System.now()

    override fun invoke() {
        if (lastMaintained + 1.minutes < Clock.System.now()) {
            val now = Clock.System.now()
            lastMaintained = now
            entries.removeIf { (_, pair) -> pair.first + delay.minutes > now }
        }
    }
}

// allow threadLocal to be delegate.
operator fun <T> ThreadLocal<T>.getValue(thisRef: Any?, property: KProperty<*>): T = get()
operator fun <T> ThreadLocal<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
