package io.github.populus_omnibus.vikbot.api

import kotlinx.datetime.Instant
import net.dv8tion.jda.api.entities.channel.attribute.IGuildChannelContainer
import java.util.*
import kotlin.reflect.KProperty

fun IGuildChannelContainer.getTextChannelById(channelId: ULong) = getTextChannelById(channelId.toLong())

fun <K, T> createMemory(): MutableMap<K, Pair<Instant, T>> = mutableMapOf<K, Pair<Instant, T>>().synchronized()

// allow threadLocal to be delegate.
operator fun <T> ThreadLocal<T>.getValue(thisRef: Any?, property: KProperty<*>): T = get()
operator fun <T> ThreadLocal<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)

fun <K, V> MutableMap<K, V>.synchronized(): MutableMap<K, V> = Collections.synchronizedMap(this)

