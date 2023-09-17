package io.github.populus_omnibus.vikbot.bot.modules.rss

import kotlinx.datetime.*
import java.lang.IllegalArgumentException

/**
 * Helper object to parse RFC-822 time format from RSS feed
 * https://gist.github.com/svenjacobs/ced311bd27ee0cbb135a595fd03e5a1f
 */
object RFC822 {

    /**
     * Groups:
     *
     * 1 = day of month (1-31)
     * 2 = month (Jan, Feb, Mar, ...)
     * 3 = year (2022)
     * 4 = hour (00-23)
     * 5 = minute (00-59)
     * 6 = OPTIONAL: second (00-59)
     * 7 = time zone (+/-hhmm or letters)
     */
    private val pattern = Regex("^(?:\\w{3}, )?(\\d{1,2}) (\\w{3}) (\\d{4}) (\\d{2}):(\\d{2})(?::(\\d{2}))? ([+-]?\\w+)\$")

    operator fun invoke(time: String) = parse(time)

    fun parse(time: String): Instant {
        val result = pattern.matchEntire(time)
            ?: throw IllegalArgumentException("Unexpected RFC-822 date/time format $time")

        try {
            val local = LocalDateTime(
                year = result.groupValues[3].toInt(),
                monthNumber = Month.valueOf(result.groupValues[2]).ordinal + 1,
                dayOfMonth = result.groupValues[1].toInt(),
                hour = result.groupValues[4].toInt(),
                minute = result.groupValues[5].toInt(),
                second = result.groupValues[6].ifEmpty { "00" }.toInt(),
                nanosecond = 0
            )

            val timeZone = result.groupValues[7]

            return local.toInstant(parseTimeZone(timeZone))
        } catch (e: Exception) {
            throw IllegalArgumentException("Unexpected RFC-822 date/time format $time", e)
        }
    }

    private fun parseTimeZone(timeZone: String): TimeZone {

        val startsWithPlus = timeZone.startsWith('+')
        val startsWithMinus = timeZone.startsWith('-')

        val (hours, minutes) = when {
            startsWithPlus || startsWithMinus -> {
                val hour = timeZone.substring(1..2).toInt()
                val minute = timeZone.substring(3..4).toInt()

                if (startsWithMinus) {
                    Pair(-hour, -minute)
                } else {
                    Pair(hour, minute)
                }
            }
            // Time zones
            else -> when (timeZone) {
                "Z", "UT", "GMT" -> 0
                "EST" -> (-5)
                "EDT" -> (-4)
                "CST" -> (-6)
                "CDT" -> (-5)
                "MST" -> (-7)
                "MDT" -> (-6)
                "PST" -> (-8)
                "PDT" -> (-7)

                // Military
                "A" -> (-1)
                "M" -> (-12)
                "N" -> 1
                "Y" -> 12
                else -> throw IllegalArgumentException("Unexpected time zone format")
            } to 0
        }
        return UtcOffset(hours, minutes).asTimeZone()
    }

    private enum class Month {
        Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec,
    }
}