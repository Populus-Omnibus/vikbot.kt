package io.github.populus_omnibus.vikbot.bot.modules.updater

import kotlinx.serialization.Serializable

@Serializable
data class UpdaterConfig(
    val jvm: String = System.getProperty("java.home"),
    val repo: String = REPO,
    val git: String = "git",
    val directory: String = directory(repo),
    val branch: String = "origin/main",
    val serverPort: Int = 8778,
    val updaterToken: String = "default_token",
) {
    private companion object Default {
        const val REPO: String = "https://github.com/Populus-Omnibus/vikbot.kt.git"

        fun directory(str: String): String = Regex(".*/(?<repo>[^/]+).git").find(str)!!.groups["repo"]!!.value
    }
}
