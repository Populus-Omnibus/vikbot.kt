package io.github.populus_omnibus.vikbot.bot

import io.github.populus_omnibus.vikbot.VikBotHandler
import net.dv8tion.jda.api.entities.Member

val Member?.isAdmin: Boolean
    get() = this?.roles?.any { it.idLong == VikBotHandler.config.adminId } ?: false

class DefaultMap<K, V>(
    private val map: MutableMap<K, V>,
    val entryConstructor: (K) -> V,
): MutableMap<K, V> by map {

    constructor(entryConstructor: (K) -> V): this(map = mutableMapOf(), entryConstructor = entryConstructor)

    override operator fun get(key: K): V = map[key] ?: entryConstructor(key).also { map[key] = it }

    override fun equals(other: Any?): Boolean = map == other
    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = map.toString()
}