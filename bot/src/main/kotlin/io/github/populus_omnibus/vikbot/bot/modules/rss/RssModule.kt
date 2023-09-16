package io.github.populus_omnibus.vikbot.bot.modules.rss

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssItem
import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import java.time.format.TextStyle
import java.util.*
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
                if (lastUpdated + feedUpdateDelay <= currentTime) {
                    update()
                    lastUpdated = currentTime
                }
            }

        }
    }

    internal fun updateFeedChannels() {
        logger.info { "Updating RSS listener" }

        val feeds = VikBotHandler.config.serverEntries.asSequence().flatMap { (_, server) -> server.rssFeeds }
            .distinct().map {
                it to (rssFeeds[it] ?: RssFeedObject(Clock.System.now()))
            }

        rssFeeds = feeds.toMap()
    }

    private fun update() {
        logger.info { "Querying RSS feeds" }
        CoroutineScope(Dispatchers.IO).launch {
            for ((feed, updateData) in rssFeeds) {
                logger.info { "Querying feed: $feed" }
                val rssChannel = rssParser.getRssChannel(feed)
                val lastBuildDate = rssChannel.lastBuildDate?.let(RFC822::invoke) ?: rssChannel.items.asSequence().mapNotNull { it.pubDate?.let(RFC822::invoke) }.max()

                //update
                if (updateData.lastUpdate < lastBuildDate) {
                    val newArticles = rssChannel.items.filter { RFC822(it.pubDate!!) > updateData.lastUpdate }
                    updateData.lastUpdate = lastBuildDate
                    postArticles(feed, newArticles)
                }
            }
        }
    }

    private suspend fun postArticles(feed: String, articles: List<RssItem>) = coroutineScope {
        val servers = VikBotHandler.config.serverEntries.asSequence()
            .map { it.value }
            .filter { feed in it.rssFeeds && it.newsChannel != null }
            .toList()
        for(article in articles) {
            val embedBuilder = EmbedBuilder().apply {
                setTitle(article.title, article.link)
                setDescription(article.description)
                setImage(article.image)
                setAuthor(article.author)
                setFooter(article.pubDate?.let { RFC822(it) }?.localString)
            }.build()

            servers.forEach { server ->
                val news = server.newsChannel!!.let { VikBotHandler.jda.getChannelById(MessageChannel::class.java, it) }
                news?.sendMessageEmbeds(embedBuilder)?.complete()
            }
        }
    }

    val Instant.localString
        get() = this.toLocalDateTime(TimeZone.of("CET")).run { "${dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}, $dayOfMonth ${month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} $year $hour:$minute" }
}

data class RssFeedObject(
    var lastUpdate: Instant
)