package io.github.populus_omnibus.vikbot.db

import io.github.populus_omnibus.vikbot.bot.DatabaseAccess
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.warn
import java.sql.Connection

object DatabaseHandler {

    private val logger by getLogger()

    fun loadDatabase(config: DatabaseAccess) {
        val isSQLite = config.driver == "org.sqlite.JDBC"
        if (isSQLite) {
            logger.warn { "SQLite database backend is not recommended in production, consider using a proper database like MariaDB" }
        }

        TransactionManager.defaultDatabase =
            Database.connect(config.address, config.driver, config.username, config.password).also {
                transaction(it) {

                    // Auto-create/update the actual database
                    SchemaUtils.createMissingTablesAndColumns(
                        DiscordGuilds,
                        HandledVoiceChannels,
                        RssFeeds,
                        RoleGroups,
                        PublishData,
                        RoleEntries,
                        UserMessages,
                        TagTable,
                        TagAttachments,
                        McOfflineAccounts,
                        McLinkedAccounts,
                    )
                }
            }

        if (isSQLite) {
            TransactionManager.manager.defaultIsolationLevel =
                Connection.TRANSACTION_SERIALIZABLE
        }
    }
}