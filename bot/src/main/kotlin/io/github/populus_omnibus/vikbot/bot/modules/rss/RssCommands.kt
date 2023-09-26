package io.github.populus_omnibus.vikbot.bot.modules.rss

import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.commands.CommandGroup
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import io.github.populus_omnibus.vikbot.api.commands.SlashOptionType
import io.github.populus_omnibus.vikbot.api.commands.adminOnly
import io.github.populus_omnibus.vikbot.db.RssFeed
import io.github.populus_omnibus.vikbot.db.RssFeeds
import io.github.populus_omnibus.vikbot.db.Servers
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

@Command(type = CommandType.SERVER)
object RssCommands : CommandGroup("rss", "news handling", { adminOnly() } ) {
    init {
        this += object : SlashCommand("setNewsChannel".lowercase(), "Set news channel on this server") {

            val channel by option("channel", "RSS feed channel", SlashOptionType.CHANNEL).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                if (event.guild == null) {
                    event.reply("This command must be used on a server")
                        .setEphemeral(true)
                        .complete()
                } else {
                    transaction {
                        val serverEntry = Servers[event.guild!!.idLong]
                        serverEntry.newsChannel = channel.idLong
                    }
                    event.reply("Saved news channel")
                        .setEphemeral(true)
                        .complete()
                }
            }
        }

        this += object : SlashCommand("addFeed".lowercase(), "Add RSS feed") {
            val feedStr by option("feed", "RSS feed", SlashOptionType.STRING).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                if (event.guild == null) {
                    event.reply("This command must be used on a server")
                        .setEphemeral(true)
                        .complete()
                } else {
                    transaction {
                        val hasRss = RssFeed.count((RssFeeds.guild eq event.guild!!.idLong) and (RssFeeds.feed eq feedStr)) > 0
                        if (hasRss) {
                            event.reply("Feed is already added")
                        } else {
                            RssFeed.new {
                                this.feed = feedStr
                                this.guild = Servers[event.guild!!.idLong].guild
                            }
                            RssModule.updateFeedChannels()
                            event.reply("Feed added to guild")
                        }
                    }.setEphemeral(true)
                        .complete()
                }
            }
        }

        this += object : SlashCommand("removeFeed".lowercase(), "Remove RSS feed") {
            val feed by option("feed", "RSS feed", RssFeedOptionType).required() // no autocomplete for now

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                if (event.guild == null) {
                    event.reply("This command must be used on a server")
                        .setEphemeral(true)
                        .complete()
                } else {
                    transaction {
                        val feed = RssFeed.find { RssFeeds.feed eq feed and (RssFeeds.guild eq event.guild!!.idLong) }
                            .firstOrNull()
                        if (feed == null) {
                            event.reply("Feed is not added to server")
                        } else {
                            feed.delete()
                            RssModule.updateFeedChannels()
                            event.reply("Feed removed from guild")
                        }
                    }.setEphemeral(true)
                        .complete()
                }
            }
        }

        this += object : SlashCommand("listFeeds".lowercase(), "List subscriptions") {

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                transaction {
                    val server = event.guild!!.let { Servers[it.idLong] }
                    if (server.rssFeeds.empty()) {
                        event.reply("There are no feeds")
                            .setEphemeral(true)
                    } else {
                        event.reply(
                            "Server feeds:\n"
                                    + server.rssFeeds.joinToString(separator = "\n- ", prefix = "- ") { it.feed }
                        )
                    }
                }.complete()
            }
        }
    }

    private object RssFeedOptionType : SlashOptionType<String> {

        override val type: OptionType
            get() = OptionType.STRING

        override val optionMapping: (OptionMapping) -> String
            get() = OptionMapping::getAsString

        override val isAutoComplete: Boolean
            get() = true

        override suspend fun autoCompleteAction(event: CommandAutoCompleteInteractionEvent): Unit = coroutineScope {
            val userString = event.focusedOption.value
            transaction {
                val server = transaction { event.guild!!.let { Servers[it.idLong] } }
                val choices = server.rssFeeds.asSequence().map { it.feed }
                    .filter { userString.isEmpty() || it.startsWith(userString) }.take(25)
                event.replyChoiceStrings(choices.toList()).queue()
            }
        }
    }
}