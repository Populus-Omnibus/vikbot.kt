package io.github.populus_omnibus.vikbot.bot.vikauth.migration

import io.github.populus_omnibus.vikbot.Launch
import io.github.populus_omnibus.vikbot.api.synchronized
import io.github.populus_omnibus.vikbot.db.McOfflineAccounts
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.kotlin.getLogger
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal object MigrationUtil {

    private val logger by getLogger()

    private val json = Json { ignoreUnknownKeys = true }



    @OptIn(ExperimentalSerializationApi::class)
    val accounts: MCAccounts = File("mc_accounts.json").inputStream().use {
        Json.decodeFromStream<MCAccounts>(it).synchronized()
    }

    @OptIn(ExperimentalUuidApi::class)
    @JvmStatic
    fun main(args: Array<String>) {

        Launch.loadConfigAndDB(args)

        transaction {
            McOfflineAccounts.batchUpsert(accounts.asSequence(), McOfflineAccounts.user) { (user, account) ->
                this[McOfflineAccounts.user] = user.toLong()

                this[McOfflineAccounts.accountId] = Uuid.parse(account.id)
                this[McOfflineAccounts.displayName] = account.displayName
                this[McOfflineAccounts.token] = account.token
            }
        }
        TransactionManager.closeAndUnregister(TransactionManager.defaultDatabase!!)

    }
}