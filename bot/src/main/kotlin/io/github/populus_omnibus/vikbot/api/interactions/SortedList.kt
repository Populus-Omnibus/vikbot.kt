package io.github.populus_omnibus.vikbot.api.interactions

class SortedList<E> internal constructor(
    private val comparator: Comparator<E>,
    private val backingMap: MutableList<E>,
) : List<E> by backingMap, MutableCollection<E> {

    override fun add(element: E): Boolean = backingMap.add(element).also { backingMap.sortWith(comparator) }

    override fun addAll(elements: Collection<E>): Boolean = backingMap.addAll(elements).also { backingMap.sortWith(comparator) }

    override fun clear() = backingMap.clear()

    override fun retainAll(elements: Collection<E>): Boolean = backingMap.retainAll(elements)

    override fun removeAll(elements: Collection<E>): Boolean = backingMap.removeAll(elements)

    override fun remove(element: E): Boolean = backingMap.remove(element)

    override fun toString(): String = backingMap.toString()
    override fun equals(other: Any?): Boolean = backingMap == other
    override fun hashCode(): Int = backingMap.hashCode()

    override operator fun iterator(): MutableIterator<E> {
        return backingMap.iterator()
    }
}

fun <E> sortedListWith(comparator: Comparator<E>, vararg elements: E) =
    SortedList(comparator, elements.toMutableList().also { it.sortedWith(comparator) })


fun <E: Comparable<E>> sortedListOf(vararg elements: E) = sortedListWith({ a, b -> a.compareTo(b)}, *elements)
