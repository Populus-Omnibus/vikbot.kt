package io.github.populus_omnibus.vikbot.bot.vikauth.migration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


internal typealias MCAccounts = MutableMap<String, MCAccount>

@Serializable
internal data class MCAccount (
    val id: String,
    val token: String,
    @SerialName("displayname")
    val displayName: String,
)