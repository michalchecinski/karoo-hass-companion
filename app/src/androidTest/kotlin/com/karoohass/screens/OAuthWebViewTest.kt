package com.karoohass.screens

import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.karoohass.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread

class OAuthWebViewTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun httpsToHttpOnlyServerShowsRecoveryControls() {
        // HTTP bytes returned to a TLS ClientHello reproduce a protocol mismatch.
        val server = ServerSocket(0)
        val worker =
            thread(isDaemon = true) {
                runCatching {
                    while (!server.isClosed) {
                        server.accept().use { socket ->
                            socket.soTimeout = 3000
                            socket.getInputStream().read()
                            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        }
                    }
                }
            }
        var edits = 0
        var retries = 0
        var failures = 0
        try {
            composeRule.setContent {
                AppTheme {
                    OAuthWebView("https://127.0.0.1:${server.localPort}/auth/authorize", { retries++ }, { edits++ }, { failures++ })
                }
            }
            composeRule.waitUntil(20_000) { composeRule.onAllNodesWithText("Couldn't open Home Assistant").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText("Couldn\'t establish a secure connection. Check the address and port. If this address works only with http://, this version cannot use it.").assertIsDisplayed()
            composeRule.onNodeWithText("Edit address").performScrollTo().performClick()
            composeRule.onNodeWithText("Try again").performScrollTo().performClick()
            assertEquals(1, failures)
            assertEquals(1, edits)
            assertEquals(1, retries)
        } finally {
            server.close()
            worker.join(1000)
        }
    }

    @Test fun untrustedCertificateShowsCertificateGuidanceWithoutBypass() {
        val alias = "phase1-fixture-${UUID.randomUUID()}"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setCertificateSubject(X500Principal("CN=localhost"))
                .build(),
        )
        generator.generateKeyPair()
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, null) }
        val tls = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
        val server = tls.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        server.enabledProtocols = arrayOf("TLSv1.2")
        val worker =
            thread(isDaemon = true) {
                runCatching {
                    while (!server.isClosed) {
                        (server.accept() as SSLSocket).use { socket ->
                            runCatching {
                                socket.soTimeout = 3000
                                socket.startHandshake()
                                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                            }
                        }
                    }
                }
            }
        try {
            composeRule.setContent {
                AppTheme { OAuthWebView("https://127.0.0.1:${server.localPort}/auth/authorize", {}, {}, {}) }
            }
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithText("Home Assistant's certificate could not be verified. Use an address with a trusted certificate valid for that address. Check the Karoo's date and time.").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Edit address").performScrollTo().assertIsDisplayed()
        } finally {
            server.close()
            worker.join(1000)
            store.deleteEntry(alias)
        }
    }

    @Test fun clientIgnoresSubresourcesAndReleasedAttempts() {
        composeRule.runOnUiThread {
            val failures = mutableListOf<OAuthFailure>()
            val view = WebView(composeRule.activity)
            val client = OAuthWebViewClient("https://home.example/auth", {}, failures::add)
            client.resourceFailed(view, false, WebViewClient.ERROR_FAILED_SSL_HANDSHAKE)
            client.onReceivedHttpError(view, request(false), response())
            assertEquals(emptyList<OAuthFailure>(), failures)
            client.release()
            client.resourceFailed(view, true, WebViewClient.ERROR_CONNECT)
            assertEquals(emptyList<OAuthFailure>(), failures)
            view.destroy()
        }
    }

    @Test fun clientDistinguishesMainFrameFailuresAndReportsOnlyOnce() {
        composeRule.runOnUiThread {
            val view = WebView(composeRule.activity)
            val failures = mutableListOf<OAuthFailure>()
            val tls = OAuthWebViewClient("https://home.example/auth", {}, failures::add)
            tls.resourceFailed(view, true, WebViewClient.ERROR_FAILED_SSL_HANDSHAKE)
            tls.resourceFailed(view, true, WebViewClient.ERROR_CONNECT)
            assertEquals(listOf(OAuthFailure.TLS), failures)
            OAuthWebViewClient("https://home.example/auth", {}, failures::add).resourceFailed(view, true, WebViewClient.ERROR_HOST_LOOKUP)
            OAuthWebViewClient("https://home.example/auth", {}, failures::add).onReceivedHttpError(view, request(true), response())
            assertEquals(listOf(OAuthFailure.TLS, OAuthFailure.NETWORK, OAuthFailure.HTTP), failures)
            view.destroy()
        }
    }

    @Test fun successfulCallbackSurvivesClientReleaseAndHttpNavigationIsBlocked() {
        composeRule.runOnUiThread {
            val view = WebView(composeRule.activity)
            val callbacks = mutableListOf<Uri>()
            val failures = mutableListOf<OAuthFailure>()
            val client = OAuthWebViewClient("https://home.example/auth", callbacks::add, failures::add)
            val callback = Uri.parse("karoohass://auth-callback?code=fake-code&state=fake-state")
            assertEquals(true, client.shouldOverrideUrlLoading(view, request(true, callback)))
            client.release()
            assertEquals(listOf(callback), callbacks)
            assertEquals(emptyList<OAuthFailure>(), failures)
            val insecure = OAuthWebViewClient("https://home.example/auth", callbacks::add, failures::add)
            assertEquals(true, insecure.shouldOverrideUrlLoading(view, request(true, Uri.parse("http://home.example/auth"))))
            assertEquals(listOf(OAuthFailure.HTTPS_REQUIRED), failures)
            view.destroy()
        }
    }

    @Test fun certificateAttributionTracksMainDocumentNavigation() {
        composeRule.runOnUiThread {
            val view = WebView(composeRule.activity)
            val failures = mutableListOf<OAuthFailure>()
            val client = OAuthWebViewClient("https://first.example/auth", {}, failures::add)
            client.onPageStarted(view, "https://first.example/auth", null)
            client.certificateFailed(view, "https://assets.example/image.png")
            assertEquals(emptyList<OAuthFailure>(), failures)
            client.shouldOverrideUrlLoading(view, request(true, Uri.parse("https://second.example/auth")))
            client.certificateFailed(view, "https://second.example/auth")
            assertEquals(listOf(OAuthFailure.CERTIFICATE), failures)
            view.destroy()
        }
    }

    private fun request(
        mainFrame: Boolean,
        uri: Uri = Uri.parse("https://home.example/auth"),
    ) =
        object : WebResourceRequest {
            override fun getUrl() = uri

            override fun isForMainFrame() = mainFrame

            override fun isRedirect() = false

            override fun hasGesture() = false

            override fun getMethod() = "GET"

            override fun getRequestHeaders() = emptyMap<String, String>()
        }

    private fun response() = WebResourceResponse("text/plain", "UTF-8", 503, "Unavailable", emptyMap(), null)
}
