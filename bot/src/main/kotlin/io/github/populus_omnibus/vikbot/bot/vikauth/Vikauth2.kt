package io.github.populus_omnibus.vikbot.bot.vikauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*


@Serializable
data class C2SVikAuthPacket(
    val username: String,
    val id: String,
    val premium: Boolean,

) {

    val uuid: UUID
        get() = UUID.fromString(id)
}


/**
 * Response packet.
 * For premium users, token will be null,
 * If id is empty, user is not allowed to connect, If id is non-empty, user is allowed to connect.
 */
@Serializable
data class S2CVikAuthPacket constructor(
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