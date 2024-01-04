package io.github.populus_omnibus.vikbot.bot

import io.github.populus_omnibus.vikbot.VikBotHandler
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import net.dv8tion.jda.api.entities.Member
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.*

val Member?.isBotAdmin: Boolean
    get() = this?.roles?.any { it.idLong == VikBotHandler.config.adminId } ?: false

fun Long.toUserTag() = "<@$this>"

fun Long.toRoleTag() = "<@$this>"

fun Long.toChannelTag() = "<#$this>"

private fun padTime(num: Int): String = num.toString().padStart(2, '0')

//This function prevents Discord from formatting a date/time using a markdown list syntax
fun LocalDateTime.prettyPrint(escaped: Boolean = false): String {
    return this.let {
        "$year${if (escaped) "\\." else "."} ${padTime(month.value)}. ${padTime(dayOfMonth)}. ${padTime(hour)}:${padTime(minute)}:${padTime(second)}"
    }
}

fun java.time.LocalDateTime.prettyPrint(escaped: Boolean = false) = this.toKotlinLocalDateTime().prettyPrint(escaped)

fun Instant.prettyPrint(escaped: Boolean = false) = this.toLocalDateTime(TimeZone.currentSystemDefault()).prettyPrint(escaped)

fun OffsetDateTime.prettyPrint(escaped: Boolean = false) = this.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().prettyPrint(escaped)

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

val Instant.localString
    get() = this.toLocalDateTime(activeTimeZone).run { "${dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}, $dayOfMonth ${month.getDisplayName(
        TextStyle.SHORT, Locale.ENGLISH)} $year $hour:$minute" }

val java.time.Instant.localString
    get() = this.toKotlinInstant().localString

val OffsetDateTime.localString
    get() = this.toInstant().localString
