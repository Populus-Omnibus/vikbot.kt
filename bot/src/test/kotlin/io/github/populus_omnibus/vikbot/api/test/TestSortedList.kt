package io.github.populus_omnibus.vikbot.api.test

import io.github.populus_omnibus.vikbot.api.interactions.sortedListOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random


class TestSortedList {

    @Test
    fun testList() {

        val sortedList = sortedListOf(1, 5, 2)
        for (i in 0 ..< 5721) {
            sortedList += Random.nextInt()
        }

        var i = sortedList[0]
        for (e in sortedList.drop(1)) {
            Assertions.assertTrue(e >= i, "Sorted list is not so sorted")
            i = e
        }

        Assertions.assertTrue(sortedList.size == (3 + 5721), "missing elements")
    }
}