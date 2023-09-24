package io.github.populus_omnibus.vikbot.db

import io.github.populus_omnibus.vikbot.bot.DatabaseAccess
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.warn

object DatabaseHandler {

    private val logger by getLogger()

    fun loadDatabase(config: DatabaseAccess) {
        if (config.driver == "org.sqlite.JDBC") {
            logger.warn { "SQLite database backend is not recommended in production, consider using a proper database like MariaDB" }
        }

        TransactionManager.defaultDatabase = Database.connect(config.address, config.driver, config.password, config.password)


    }
}