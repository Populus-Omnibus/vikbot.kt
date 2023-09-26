package io.github.populus_omnibus.vikbot.db

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedIterable
import kotlin.reflect.KProperty

object Servers {
    operator fun get(id: Long) = DiscordGuild.getOrCreate(id)
}


val SizedIterable<*>.size: Int
    get() = count().toInt()


operator fun <T : Comparable<T>> EntityID<T>.getValue(entity: Entity<T>, property: KProperty<*>): T = this.value
