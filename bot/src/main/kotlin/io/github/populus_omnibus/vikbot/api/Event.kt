package io.github.populus_omnibus.vikbot.api


class Event<T>: Iterable<T> {
    companion object {
        fun <T> simple() = Event<(T) -> EventResult>()
    }

    private val subscribersMap = mutableMapOf<Int, T>()
    private var orderedList: List<T>? = null

    operator fun set(priority: Int, function: T) = run {
        subscribersMap[priority] = function
        orderedList = null
    }

    fun registerSubscribers(priority: Int, vararg functions: T) {
        for (i in functions.indices) {
            this[i + priority] = functions[i]
        }
    }

    operator fun get(i: Int) = subscribersMap[i]

    val indices: List<Int>
        get() = subscribersMap.map { (i, _) -> i }

    val subscribers: List<T>
        get() {
            return orderedList ?: run {
                orderedList =
                    subscribersMap.map { (i, t) -> i to t }.sortedBy { (i, _) -> i }.map { (_, t) -> t }.toList()
                orderedList!!
            }
        }

    override fun iterator(): Iterator<T> = toList().iterator()

}
operator fun <X, P> Event<out (X) -> TypedEventResult<P>>.invoke(x: X): TypedEventResult<P> {
    for (subscriber in subscribers) {
        val result = subscriber(x)
        if (result.consume) {
            return result
        }
    }
    return TypedEventResult(null)
}

operator fun <X> Event<out (X) -> EventResult>.invoke(x: X): EventResult {
    for (subscriber in subscribers) {
        val result = subscriber(x)
        if (result.consume) {
            return result
        }
    }
    return EventResult()
}

fun <T, P> Event<(T) -> TypedEventResult<P>>.subscribe(index: Int, f: (T) -> P? ) = set(index) { t ->
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
