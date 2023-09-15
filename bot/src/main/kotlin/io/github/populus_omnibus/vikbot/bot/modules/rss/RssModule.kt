package io.github.populus_omnibus.vikbot.bot.modules.rss

import com.prof18.rssparser.RssParser
import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.kotlin.getLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object RssModule {

    private val logger by getLogger()

    private var rssFeeds = mapOf<String, RssFeedObject>()
    private val rssParser = RssParser()

    private val feedUpdateDelay: Duration = 10.minutes

    @Module
    fun loadModule(bot: VikBotHandler) {

        updateFeedChannels()

        bot.maintainEvent += object : () -> Unit {

            var lastUpdated: Instant = Instant.DISTANT_PAST

            override fun invoke() {
                val currentTime = Clock.System.now()
                if (lastUpdated + feedUpdateDelay >= currentTime) {
                    update()
                }
            }

        }
    }

    private fun updateFeedChannels() {

        val feeds = VikBotHandler.config.serverEntries.asSequence().flatMap { (_, server) -> server.rssFeeds }
            .distinct().map {
                it to (rssFeeds[it] ?: RssFeedObject(null))
            }

        rssFeeds = feeds.toMap()
    }

    private fun update() {
        CoroutineScope(Dispatchers.IO).launch {
            for ((feed, updateData) in rssFeeds) {
                val rssChannel = rssParser.getRssChannel(feed)
                rssChannel.lastBuildDate
            }

        }
    }
}

data class RssFeedObject(
    var lastUpdate: Nothing?
)