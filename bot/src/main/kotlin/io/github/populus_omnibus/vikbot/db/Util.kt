package io.github.populus_omnibus.vikbot.db

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.selectAll

object Servers {
    operator fun get(id: Long) = DiscordGuild.getOrCreate(id)
}


val SizedIterable<*>.size: Int
    get() = count().toInt()


operator fun <T : Comparable<T>> IdTable<T>.contains(id: T): Boolean {
    return this.selectAll().where { this@contains.id eq id }.any()
}

