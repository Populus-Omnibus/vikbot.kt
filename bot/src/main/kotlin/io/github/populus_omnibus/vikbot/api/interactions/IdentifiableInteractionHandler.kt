package io.github.populus_omnibus.vikbot.api.interactions

import net.dv8tion.jda.api.events.Event
import kotlin.reflect.KProperty


private val logger by org.slf4j.kotlin.toplevel.getLogger()
open class IdentifiableHandler(val id: String)

open class IdentifiableInteractionHandler<T: Event>(id: String, val function: IdentifiableInteractionHandler<T>.(T) -> Unit = {}): IdentifiableHandler(id), suspend (T) -> Unit {
    override suspend fun invoke(event: T) {
        function(event)
    }
}

class IdentifiableList<T : IdentifiableHandler>(private val list: MutableList<T> = mutableListOf()): List<T> by list {
    private val mapManager = object {
        private var _map: Map<String, T>? = null
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Map<String, T> {
            if (_map == null) _map = list.associateBy { it.id }
            return _map!!
        }
        fun clear() {
            _map = null
        }
    }

    private val map: Map<String, T> by mapManager

    operator fun plusAssign(t: T) {
        mapManager.clear()
        list += t
    }


    operator fun get(index: String?) = map[index]

}

suspend operator fun <T: IdentifiableInteractionHandler<E>, E: Event> IdentifiableList<T>.invoke(id: String?, event: E, type: String) {
    this[id?.split(":")?.get(0)]?.invoke(event)
        ?: logger.error("executed $type event was not found: $id")
}
