package com.karoohass.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.karoohass.core.ConnectionPolicy
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class HttpRequest(val method: String, val url: String, val headers: Map<String, String> = emptyMap(), val body: ByteArray? = null, val waitForConnection: Boolean = false)
data class HttpResponse(val code: Int, val headers: Map<String, String>, val body: ByteArray?, val error: String? = null)
sealed class TransportException(message: String) : Exception(message) { data object WifiUnavailable : TransportException("Wi-Fi connection is required"); data class Failure(val detail: String) : TransportException(detail) }
interface HttpTransport { suspend fun execute(request: HttpRequest): HttpResponse }

class DirectWifiTransport(private val context: Context) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork?.takeIf { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true } ?: throw TransportException.WifiUnavailable
        val connection = network.openConnection(URL(request.url)) as HttpURLConnection
        try {
            connection.requestMethod = request.method; connection.connectTimeout = 10_000; connection.readTimeout = 15_000
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            request.body?.let { bytes -> connection.doOutput = true; connection.outputStream.use { it.write(bytes) } }
            val stream = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.let { BufferedInputStream(it).use { input -> input.readBytes().also { data -> if (data.size > 100_000) throw TransportException.Failure("Response exceeds 100 KB") } } }
            HttpResponse(connection.responseCode, connection.headerFields.filterValues { it != null }.mapValues { it.value.joinToString(",") }, bytes)
        } catch (exception: TransportException) { throw exception } catch (exception: Exception) { throw TransportException.Failure(exception.message ?: "Direct Wi-Fi request failed") } finally { connection.disconnect() }
    }
}

class KarooTransport(context: Context) : HttpTransport {
    // Binding to Karoo System Service is comparatively expensive. Only do it when the
    // user selected Companion fallback and a request actually needs that transport.
    private val service by lazy { KarooSystemService(context.applicationContext).also { it.connect() } }
    override suspend fun execute(request: HttpRequest): HttpResponse = withTimeout(20_000) {
        suspendCancellableCoroutine { continuation ->
            if (!service.connected) { continuation.resumeWith(Result.failure(TransportException.Failure("Karoo System Service is unavailable"))); return@suspendCancellableCoroutine }
            var consumerId: String? = null
            consumerId = service.addConsumer<OnHttpResponse>(
                OnHttpResponse.MakeHttpRequest(request.method, request.url, request.headers, request.body, waitForConnection = request.waitForConnection),
                onError = { error -> if (continuation.isActive) continuation.resumeWith(Result.failure(TransportException.Failure(error))) },
            ) { event ->
                val complete = event.state as? HttpResponseState.Complete ?: return@addConsumer
                service.removeConsumer(consumerId.orEmpty())
                if (!continuation.isActive) return@addConsumer
                if (complete.body?.size ?: 0 > 100_000) continuation.resumeWith(Result.failure(TransportException.Failure("Response exceeds 100 KB")))
                else continuation.resume(HttpResponse(complete.statusCode, complete.headers, complete.body, complete.error))
            }
            continuation.invokeOnCancellation { consumerId?.let(service::removeConsumer) }
        }
    }
}

class PolicyTransport(private val policy: () -> ConnectionPolicy, private val direct: HttpTransport, private val karoo: HttpTransport) : HttpTransport {
    override suspend fun execute(request: HttpRequest) = if (policy() == ConnectionPolicy.WIFI_ONLY) direct.execute(request) else karoo.execute(request)
}
