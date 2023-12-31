package io.github.populus_omnibus.vikbot.bot.modules.mcAuth

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.util.*


private val httpClient = OkHttpClient()

internal fun checkIfNameFree(name: String): Boolean {
    val http = Request.Builder().apply {
        url("https://api.mojang.com/users/profiles/minecraft/${name}")
    }.build()
    val data = String(httpClient.newCall(http).execute().body!!.byteStream().readAllBytes()).let {
        Json.parseToJsonElement(it)
    }
    return (data as? JsonObject)?.containsKey("id") == true
}

internal fun nameToUuid(name: String): UUID? {
    val http = Request.Builder().apply {
        url("https://api.mojang.com/users/profiles/minecraft/${name}")
    }.build()
    val data = String(httpClient.newCall(http).execute().body!!.byteStream().readAllBytes()).let {
        Json.parseToJsonElement(it)
    }
    return (data as? JsonObject)?.get("id")?.jsonPrimitive?.content?.toUuid()
}

private val uuidHyphens = Regex("([a-f0-9]{8})([a-f0-9]{4})(4[a-f0-9]{3})([89aAbB][a-f0-9]{3})([a-f0-9]{12})")

fun CharSequence.toUuid(): UUID {
    return if (this.contains("-")) {
        UUID.fromString(this.toString())
    } else {
        UUID.fromString(uuidHyphens.replace(this, "$1-$2-$3-$4-$5"))
    }
}

internal fun getUserSkin(id: UUID): Pair<ByteBuffer, Boolean>? {
    val http = Request.Builder().apply {
        url("https://sessionserver.mojang.com/session/minecraft/profile/${id}")
    }.build()
    val data = String(httpClient.newCall(http).execute().body!!.byteStream().readAllBytes()).let {
        Json.parseToJsonElement(it)
    }

    val properties = (data as? JsonObject)?.get("properties")?.jsonArray?.get(0)?.jsonPrimitive?.content?.let {
        Base64.getDecoder().decode(it)
    }?.let { String(it) } ?: return null

    val skinObj = Json.parseToJsonElement(properties).jsonObject["textures"]?.jsonObject?.get("SKIN")?.jsonObject ?: return null
    val skin = skinObj["url"]?.jsonPrimitive?.content!!
    val skinHttp = Request.Builder().apply {
        url(skin)
    }.build()

    return httpClient.newCall(skinHttp).execute().body!!.byteStream().readAllBytes().let { ByteBuffer.wrap(it) } to (skinObj["metadata"]?.jsonObject?.get("model")?.jsonPrimitive?.content == "slim")

}
