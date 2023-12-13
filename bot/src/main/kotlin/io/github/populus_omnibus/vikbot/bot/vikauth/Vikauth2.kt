package io.github.populus_omnibus.vikbot.bot.vikauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*


@Serializable
data class C2SVikAuthPacket(
    val username: String,
    val id: String,
    val premium: Boolean = false, // false if not known or offline. For security reasons, the server should issue another request if it's known that the user is genuine to avoid whitelist scraping.
    val login: Boolean = true, // if false, the server does not need to respond
    val serverName: String? = null, // optional, server can send its name

) {

    val uuid: UUID
        get() = UUID.fromString(id)
}


/**
 * Response packet.
 * For premium users, token will be null,
 */
@Serializable
data class S2CVikAuthPacket constructor(
    /**
     * if this is not explicitly specified, the connection is not allowed
     * For a request with premium = false, the user has to provide shared secret (it's genuine) then the mc server will send another challenge.
     */
    val allowed: Boolean = false, //
    val token: String = "",
    val id: String = "",
    @SerialName("displayname")
    val displayName: String = "",
    @SerialName("skin_url")
    val skinUrl: String = "",
) {

    val uuid: UUID
        get() = UUID.fromString(id)
}