package io.github.populus_omnibus.vikbot.bot.vikauth

import kotlinx.serialization.Serializable


typealias MCAccounts = MutableMap<String, MCAccount>

@Serializable
data class MCAccount (
    val id: String,
    val token: String,
    val displayName: String,
)