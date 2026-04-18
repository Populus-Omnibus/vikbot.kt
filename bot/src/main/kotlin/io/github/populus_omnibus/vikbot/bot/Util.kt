package io.github.populus_omnibus.vikbot.bot

import io.github.populus_omnibus.vikbot.VikBotHandler
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent
import net.dv8tion.jda.api.components.tree.MessageComponentTree
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.util.*
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

val Member?.isBotAdmin: Boolean
    get() = this?.roles?.any { it.idLong == VikBotHandler.config.adminId } ?: false

fun Long.toUserTag() = "<@$this>"

fun Long.toRoleTag() = "<@$this>"

fun Long.toChannelTag() = "<#$this>"

private fun Number.padTime(length: Int = 2) = this.toString().padStart(length, '0')

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
    return toLocalDateTime(activeTimeZone).run {
        "${
            dayOfWeek.toJavaDayOfWeek().getDisplayName(
                TextStyle.SHORT,
                Locale.ENGLISH
            )
        }, " +
                "$year.${month.number.padTime()}.${day.padTime()}. " +
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


fun Duration.stringify(showZeroHours: Boolean = false): String {
    return this.toComponents { hours, minutes, seconds, _ ->
        (if (hours > 0 || showZeroHours) "${hours.padTime()}:" else "") +
                "${minutes.padTime()}:" +
                seconds.padTime()
    }
}


fun <T: ReplyCallbackAction> T.addActionRow(
    vararg buttons: ActionRowChildComponent
) = apply {
    addComponents(
        ActionRow.of(listOf(*buttons))
    )
}

inline fun <reified T: Component> MessageComponentTree.find(): T? {
    return this.find<T>(T::class.java) { true }.getOrNull()
}

inline fun <reified T: Component> MessageComponentTree.findAll(): List<T> {
    return this.findAll<T>(T::class.java)
}
