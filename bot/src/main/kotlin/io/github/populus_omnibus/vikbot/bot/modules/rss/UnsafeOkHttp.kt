package io.github.populus_omnibus.vikbot.bot.modules.rss

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


object UnsafeOkHttp {

    // http://stackoverflow.com/questions/25509296/trusting-all-certificates-with-okhttp

    private val trustAllCerts: Array<TrustManager> = arrayOf(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }
    )

    val client: OkHttpClient

    init {
        try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val newBuilder = OkHttpClient.Builder()
            newBuilder.sslSocketFactory(sslContext.socketFactory, (trustAllCerts[0] as X509TrustManager))
            newBuilder.hostnameVerifier { hostname: String?, session: SSLSession? -> true }
            client = newBuilder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}

