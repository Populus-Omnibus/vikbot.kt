package io.github.populus_omnibus.vikbot.db

import org.jetbrains.exposed.sql.SizedIterable

object Servers {
    operator fun get(id: Long) = DiscordGuild.getOrCreate(id)
}


val SizedIterable<*>.size: Int
    get() = count().toInt()

