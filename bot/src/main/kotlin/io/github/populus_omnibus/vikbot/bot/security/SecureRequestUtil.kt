package io.github.populus_omnibus.vikbot.bot.security

import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedConnectionOperator
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpClientBuilder
import org.apache.http.config.Registry
import org.apache.http.conn.DnsResolver
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.impl.conn.SystemDefaultDnsResolver
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.warn
import java.lang.invoke.MethodHandles
import java.net.InetAddress
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaMethod

object SecureRequestUtil : DnsResolver {
    private val logger by getLogger()

    private var builders: IdentityHashMap<ExtendedHttpClientBuilder, Boolean> = IdentityHashMap()

    @Suppress("UNCHECKED_CAST") // I know what I'm doing
    private val createConnectionSocketFactory: ExtendedHttpClientBuilder.() -> Registry<ConnectionSocketFactory> by lazy {

        // First, the method descriptor
        val method = ExtendedHttpClientBuilder::class.functions.find { it.name == "createConnectionSocketFactory" }!!
        method.isAccessible = true // here's the problem

        // Unreflect our private method
        val handle = MethodHandles.privateLookupIn(ExtendedHttpClientBuilder::class.java, MethodHandles.lookup())
            .unreflectSpecial(method.javaMethod, ExtendedHttpClientBuilder::class.java)

        return@lazy { // Kotlin lambda is required for proper JVM signature
            handle(this) as Registry<ConnectionSocketFactory> // create an invoker for this
        }
    }

    fun configureSecurely(builder: HttpClientBuilder): HttpClientBuilder = builder.apply {

        if (this is ExtendedHttpClientBuilder) {
            this@SecureRequestUtil.builders += this to true
            this.setConnectionManagerFactory { operator, connectionFactory ->
                // More JVM reflection magic


                val manager = PoolingHttpClientConnectionManager(
                    ExtendedConnectionOperator(createConnectionSocketFactory(), null, this@SecureRequestUtil),
                    connectionFactory,
                    -1,
                    TimeUnit.MILLISECONDS
                )
                manager.maxTotal = 3000
                manager.defaultMaxPerRoute = 1500
                manager
            }
        }

        this.setDnsResolver(this@SecureRequestUtil)

    }

    override fun resolve(host: String?): Array<InetAddress> {
        return SystemDefaultDnsResolver.INSTANCE.resolve(host).filter { address ->
            address.isLoopbackAddress.not().also {
                if (!it) {
                    logger.warn { "Trying to resolve loopback address: $address" }
                }
            }
        }.toTypedArray()
    }

}