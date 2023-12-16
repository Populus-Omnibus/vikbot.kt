package io.github.populus_omnibus.vikbot.bot.modules.updater

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File


object UpdaterUtil {
    private val mutex = Mutex()

    private val jvm = System.getProperty("java.home")

    private val config: UpdaterConfig
        get() = VikBotHandler.config.updater

    @Module
    fun init(bot: VikBotHandler) {

    }


    suspend fun buildBot(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                // pull repo
                run {
                    val process: Process = ProcessBuilder().directory(File(config.directory))
                        .command("git", "pull").start()

                    process.waitFor().let { exitCode ->
                        if (exitCode != 0) {
                            // Hard reset
                            val code = ProcessBuilder().directory(File(config.directory))
                                .command("git", "fetch", "--all").start().waitFor() == 0 &&
                                    ProcessBuilder().directory(File(config.directory))
                                        .command("git", "reset", "--hard", config.branch).start().waitFor() == 0

                            if (!code) {
                                throw Exception("Git hard reset failed with exit code $exitCode")
                            }
                        }
                    }
                }

                val process: Process = ProcessBuilder().directory(File(config.directory))
                    .command("./gradlew", "build", "-Dorg.gradle.java.home=${config.jvm}", "--no-daemon").start()

                process.waitFor().let { exitCode ->
                    if (exitCode != 0) {
                        throw Exception("Build failed with exit code $exitCode")
                    }
                }


            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}