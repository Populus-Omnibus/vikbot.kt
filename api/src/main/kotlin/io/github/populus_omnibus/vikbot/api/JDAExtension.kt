package io.github.populus_omnibus.vikbot.api

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.attribute.IGuildChannelContainer
import java.util.*
import kotlin.reflect.KProperty

fun IGuildChannelContainer<*>.getTextChannelById(channelId: ULong) = getTextChannelById(channelId.toLong())

// allow threadLocal to be delegate.
operator fun <T> ThreadLocal<T>.getValue(thisRef: Any?, property: KProperty<*>): T = get()
operator fun <T> ThreadLocal<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)

fun <K, V> MutableMap<K, V>.synchronized(): MutableMap<K, V> = Collections.synchronizedMap(this)


val User.isMe: Boolean
    get() = idLong == jda.selfUser.idLong

val User.isNotMe: Boolean
    get() = !isMe
