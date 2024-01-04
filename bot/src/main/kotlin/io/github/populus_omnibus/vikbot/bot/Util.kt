package io.github.populus_omnibus.vikbot.bot

import io.github.populus_omnibus.vikbot.VikBotHandler
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import net.dv8tion.jda.api.entities.Member
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.util.*
import kotlin.time.Duration

val Member?.isBotAdmin: Boolean
    get() = this?.roles?.any { it.idLong == VikBotHandler.config.adminId } ?: false

fun Long.toUserTag() = "<@$this>"

fun Long.toRoleTag() = "<@$this>"

fun Long.toChannelTag() = "<#$this>"

private fun Number.padTime(length: Int = 2) = this.toString().padStart(length, '0')

//This function prevents Discord from formatting a date/time using a markdown list syntax
@Deprecated("Use enString instead", ReplaceWith("stringify()"))
fun LocalDateTime.prettyPrint(escaped: Boolean = false): String = this.stringify()

@Deprecated("Use enString instead", ReplaceWith("stringify()"))
fun java.time.LocalDateTime.prettyPrint(escaped: Boolean = false) = this.stringify()

@Deprecated("Use enString instead", ReplaceWith("stringify()"))
fun Instant.prettyPrint(escaped: Boolean = false) = this.stringify()

@Deprecated("Use enString instead", ReplaceWith("stringify()"))
fun OffsetDateTime.prettyPrint(escaped: Boolean = false): String = this.stringify()

fun String.chunkedMaxLength(maxSize: Int, separator: Char = '\n'): Sequence<String> = sequence {
    var index = 0
    while (index + maxSize < this@chunkedMaxLength.length) {
        val n = this@chunkedMaxLength.lastIndexOf(startIndex = maxSize, char = separator)
        yield(substring(index, n))
        index = n
    }
    yield(substring(index))
}



private val activeTimeZone by lazy { TimeZone.of(VikBotHandler.config.activeTimeZone) }

fun Instant.stringify(displaySeconds: Boolean = false): String {
    return this.toLocalDateTime(activeTimeZone).run {
        "${
            dayOfWeek.getDisplayName(
                TextStyle.SHORT,
                Locale.ENGLISH
            )
        }, " +
                "$year.${month.value.padTime()}.${dayOfMonth.padTime()}. " +
                "${hour.padTime()}:${minute.padTime()}${
                    if (displaySeconds) {
                        second.padTime()
                    } else ""
                }"
    }
}

fun java.time.Instant.stringify(displaySeconds: Boolean = false) = this.toKotlinInstant().stringify(displaySeconds)

fun OffsetDateTime.stringify(displaySeconds: Boolean = false) = this.toInstant().stringify(displaySeconds)

fun LocalDateTime.stringify(displaySeconds: Boolean = false) = this.toInstant(activeTimeZone).stringify(displaySeconds)

fun java.time.LocalDateTime.stringify(displaySeconds: Boolean = false) = this.toKotlinLocalDateTime().stringify(displaySeconds)


@Deprecated("Use enString instead", ReplaceWith("stringify()"))
val Instant.localString
    get() = this.stringify()

@Deprecated("Use enString instead", ReplaceWith("stringify()"))
val java.time.Instant.localString
    get() = this.stringify()

@Deprecated("Use enString instead", ReplaceWith("stringify()"))
val OffsetDateTime.localString
    get() = this.stringify()

fun Duration.stringify(showZeroHours: Boolean = false): String {
    return this.toComponents { hours, minutes, seconds, _ ->
        (if (hours > 0 || showZeroHours) "${hours.padTime()}:" else "") +
                "${minutes.padTime()}:" +
                seconds.padTime()
    }
}