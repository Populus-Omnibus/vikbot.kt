package io.github.populus_omnibus.vikbot.bot.modules.updater

import com.sun.net.httpserver.HttpServer
import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.bot.modules.Syslog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okio.use
import java.io.File
import java.net.InetSocketAddress


@OptIn(ExperimentalSerializationApi::class)
object UpdaterUtil : (Int) -> Unit {
    private val mutex = Mutex()
    private val readyToRestart = ThreadSafeCounter()

    private val jvm = System.getProperty("java.home")

    private val config: UpdaterConfig
        get() = VikBotHandler.config.updater

    @Module
    fun init(bot: VikBotHandler) {
        val server = HttpServer.create().apply {
            createContext("/api/vikbot/update") { exchange ->
                try {
                    if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
                        throw IllegalArgumentException("Invalid request method")
                    }

                    val token: UpdaterToken = exchange.requestBody.use { reader ->
                        Json.decodeFromStream(reader)
                    }
                    if (token.token != config.updaterToken) {
                        throw IllegalArgumentException("Invalid request")
                    }

                    exchange.responseHeaders.add("Content-Type", "text/plain")
                    exchange.sendResponseHeaders(200, 0)

                    exchange.responseBody.use { response ->
                        response.write("OK :3".toByteArray())
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        buildBot()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    exchange.responseHeaders.add("Content-Type", "text/plain")
                    exchange.sendResponseHeaders(400, 0)
                    exchange.responseBody.use { response ->
                        response.write(e.message?.toByteArray() ?: "Unknown error".toByteArray())
                    }
                }
            }
            bind(InetSocketAddress("0.0.0.0", config.serverPort), 0)
            start()
        }

    }


    suspend fun buildBot(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                Syslog.queueLog("Update requested, updating repo...")

                // pull repo
                run {
                    val process: Process = ProcessBuilder().directory(File(config.directory))
                        .command("git", "pull").start()

                    process.waitFor().let { exitCode ->
                        if (exitCode != 0) {
                            // Hard reset
                            Syslog.queueLog("Git pull failed with exit code $exitCode, attempting hard reset...")

                            val code = ProcessBuilder().directory(File(config.directory))
                                .command("git", "fetch", "--all").start().waitFor() == 0 &&
                                    ProcessBuilder().directory(File(config.directory))
                                        .command("git", "reset", "--hard", config.branch).start().waitFor() == 0

                            if (!code) {
                                Syslog.queueLog("Git hard reset failed, please update manually.")

                                throw Exception("Git hard reset failed with exit code $exitCode")
                            }
                        }
                    }
                }

                Syslog.log("Repo updated, building...")

                val process: Process = ProcessBuilder().directory(File(config.directory))
                    .command("./gradlew", "build", "-Dorg.gradle.java.home=${config.jvm}", "--no-daemon").start()

                process.waitFor().let { exitCode ->
                    if (exitCode != 0) {
                        Syslog.queueLog("Build failed with exit code $exitCode")
                        throw Exception("Build failed with exit code $exitCode")
                    }
                }

                Syslog.log("Build complete, restarting as soon as possible...")
                readyToRestart += this@UpdaterUtil

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override operator fun invoke(count: Int) {
        if (count == 0) {
            Runtime.getRuntime().exit(0)
        }
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other === this // Singleton
    }

}