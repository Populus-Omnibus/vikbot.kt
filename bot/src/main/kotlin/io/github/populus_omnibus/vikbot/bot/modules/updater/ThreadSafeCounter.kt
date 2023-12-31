package io.github.populus_omnibus.vikbot.bot.modules.updater

class ThreadSafeCounter {
    private var count = 0
    private val observers: MutableSet<(Int) -> Unit> = mutableSetOf()

    fun increment(): Int {
        return synchronized(this) {
            ++count
        }.also { count ->
            observers.forEach { it(count) }
        }
    }

    fun decrement(): Int {
        return synchronized(this) {
            --count
        }.also { count ->
            observers.forEach { it(count) }
        }
    }

    operator fun inc() = this.apply { increment() }
    operator fun dec() = this.apply { decrement() }

    operator fun plusAssign(observer: (Int) -> Unit) {
        observers.add(observer)
        observer(count)
    }

    operator fun minusAssign(observer: (Int) -> Unit) {
        observers.remove(observer)
    }

    @Synchronized
    fun get(): Int {
        return count
    }
}