package io.github.populus_omnibus.vikbot.bot.modules.rss

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.commands.*
import io.github.populus_omnibus.vikbot.bot.ServerEntry
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType

@Command
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
                    val serverEntry = VikBotHandler.config.serverEntries.getOrPut(event.guild!!.idLong, ::ServerEntry) // TODO fix it once possible
                    serverEntry.newsChannel = channel.idLong
                    VikBotHandler.config.save()

                    event.reply("Saved news channel")
                        .setEphemeral(true)
                        .complete()
                }
            }
        }

        this += object : SlashCommand("addFeed".lowercase(), "Add RSS feed") {
            val feed by option("feed", "RSS feed", SlashOptionType.STRING).required()

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                if (event.guild == null) {
                    event.reply("This command must be used on a server")
                        .setEphemeral(true)
                        .complete()
                } else {
                    val serverEntry = VikBotHandler.config.serverEntries.getOrPut(event.guild!!.idLong, ::ServerEntry) // TODO fix it once possible
                    if (feed in serverEntry.rssFeeds) {
                        event.reply("Feed is already added")
                    } else {
                        serverEntry.rssFeeds += feed
                        VikBotHandler.config.save()
                        RssModule.updateFeedChannels()
                        event.reply("Feed added to guild")
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
                    val serverEntry = VikBotHandler.config.serverEntries.getOrPut(event.guild!!.idLong, ::ServerEntry) // TODO fix it once possible
                    if (feed !in serverEntry.rssFeeds) {
                        event.reply("Feed is already added")
                    } else {
                        serverEntry.rssFeeds -= feed
                        VikBotHandler.config.save()
                        RssModule.updateFeedChannels()
                        event.reply("Feed removed from guild")
                    }.setEphemeral(true)
                        .complete()
                }
            }
        }

        this += object : SlashCommand("listFeeds".lowercase(), "List subscriptions") {

            override suspend fun invoke(event: SlashCommandInteractionEvent) {
                val server = event.guild?.let { VikBotHandler.config.serverEntries[it.idLong] }
                if (server == null) {
                    event.reply("There are no feeds")
                        .setEphemeral(true)
                } else {
                    event.reply("Server feeds:\n"
                    + server.rssFeeds.joinToString(separator = "\n- ", prefix = "- "))
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
            val server = event.guild?.let { VikBotHandler.config.serverEntries[it.idLong] }
            if (server != null) {
                val choices = server.rssFeeds.asSequence()
                    .filter { userString.isEmpty() || it.startsWith(userString) }.take(25)
                event.replyChoiceStrings(choices.toList()).complete()
            }
        }
    }
}