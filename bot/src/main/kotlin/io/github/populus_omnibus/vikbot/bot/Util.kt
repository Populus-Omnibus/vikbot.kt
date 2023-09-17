package io.github.populus_omnibus.vikbot.bot

import io.github.populus_omnibus.vikbot.VikBotHandler
import net.dv8tion.jda.api.entities.Member

val Member?.isAdmin: Boolean
    get() = this?.roles?.any { it.idLong == VikBotHandler.config.adminId } ?: false