package io.github.populus_omnibus.vikbot.api

import io.github.populus_omnibus.vikbot.api.interactions.sortedListWith


class Event<T>: Iterable<T> {
    companion object {
        fun <T> simple() = Event<suspend (T) -> EventResult>()
    }

    private val subscribersMap = sortedListWith<Pair<Int, T>> ({ a, b ->
        a.first.compareTo(b.first)
    })

    operator fun set(priority: Int, function: T) = run {
        subscribersMap += priority to function
    }

    fun registerSubscribers(priority: Int, vararg functions: T) {
        for (i in functions.indices) {
            this[i + priority] = functions[i]
        }
    }

    @Deprecated("Getting a single item from event is not supported")
    operator fun get(i: Int) = subscribersMap.find { it.first == i }


    /**
     * Probably shouldn't be used
     */
    @Deprecated("subscribers list shouldn't be used")
    val subscribers: List<T>
        get() {
            return subscribersMap.asSequence().map { it.second }.toList()
            }

    override fun iterator(): Iterator<T> {
        return subscribersMap.asSequence().map { it.second }.iterator()
    }
}


@JvmName("typedInvoke")
suspend operator fun <X, P> Event<out suspend (X) -> TypedEventResult<P>>.invoke(x: X): TypedEventResult<P> {
    for (subscriber in this) {
        val result = subscriber(x)
        if (result.consume) {
            return result
        }
    }
    return TypedEventResult(null)
}

suspend operator fun <X> Event<out suspend (X) -> EventResult>.invoke(x: X): EventResult {
    for (subscriber in this) {
        val result = subscriber(x)
        if (result.consume) {
            return result
        }
    }
    return EventResult()
}

fun <T, P> Event<suspend (T) -> TypedEventResult<P>>.subscribe(index: Int, f: suspend (T) -> P? ) = set(index) { t ->
    TypedEventResult(
        f(t)
    )
}

open class EventResult(open val consume: Boolean = false) {
    companion object {
        val PASS = EventResult(consume = false)
        val CONSUME = EventResult(consume = true)
    }
}
class TypedEventResult<T>(val t: T?, override val consume: Boolean = t != null) : EventResult()
