package com.karoohass.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.karoohass.R
import com.karoohass.auth.OAuthCallbackActivity
import com.karoohass.auth.isOAuthCallbackUri

internal enum class OAuthFailure { TLS, CERTIFICATE, NETWORK, HTTP, HTTPS_REQUIRED }

/** One client per authorization attempt; terminal/released clients ignore late events. */
internal class OAuthWebViewClient(
    private val initialUrl: String,
    private val onCallback: (Uri) -> Unit,
    private val onFailure: (OAuthFailure) -> Unit,
) : WebViewClient() {
    private var active = true
    private var mainDocumentUrl = initialUrl

    fun release() {
        active = false
    }

    private fun fail(
        view: WebView,
        failure: OAuthFailure,
    ) {
        if (!active) return
        active = false
        view.stopLoading()
        onFailure(failure)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        resourceFailed(view, request.isForMainFrame, error.errorCode)
    }

    internal fun resourceFailed(
        view: WebView,
        mainFrame: Boolean,
        errorCode: Int,
    ) {
        if (!mainFrame) return
        fail(view, if (errorCode == ERROR_FAILED_SSL_HANDSHAKE) OAuthFailure.TLS else OAuthFailure.NETWORK)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame) fail(view, OAuthFailure.HTTP)
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        // This callback has no main-frame flag. Cancel every invalid certificate,
        // but only replace the screen for a failure of the current main document.
        certificateFailed(view, error.url)
    }

    internal fun certificateFailed(
        view: WebView,
        url: String,
    ) {
        if (url == mainDocumentUrl) fail(view, OAuthFailure.CERTIFICATE)
    }

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: Bitmap?,
    ) {
        if (active) mainDocumentUrl = url
    }

    private fun navigate(
        view: WebView,
        uri: Uri,
    ): Boolean {
        if (!active) return true
        if (isOAuthCallbackUri(uri.scheme, uri.host, uri.path)) {
            active = false
            onCallback(uri)
            return true
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            fail(view, OAuthFailure.HTTPS_REQUIRED)
            return true
        }
        mainDocumentUrl = uri.toString()
        return false
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean =
        if (request.isForMainFrame) navigate(view, request.url) else false

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(
        view: WebView,
        url: String,
    ): Boolean = navigate(view, Uri.parse(url))
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun OAuthWebView(
    url: String?,
    onRetry: () -> Unit,
    onEditAddress: () -> Unit,
    onFailure: () -> Unit,
) {
    key(url) {
        var failure by remember { mutableStateOf<OAuthFailure?>(null) }
        val context = LocalContext.current
        val client =
            remember {
                OAuthWebViewClient(
                    initialUrl = url.orEmpty(),
                    onCallback = { uri -> openOAuthCallback(context, uri) },
                    onFailure = {
                        failure = it
                        onFailure()
                    },
                )
            }
        if (url == null || failure != null) {
            OAuthError(failure ?: OAuthFailure.NETWORK, onRetry, onEditAddress)
        } else {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = client
                        loadUrl(url)
                    }
                },
                onRelease = { view ->
                    client.release()
                    view.stopLoading()
                    view.destroy()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun OAuthError(
    failure: OAuthFailure,
    onRetry: () -> Unit,
    onEditAddress: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentPadding = PaddingValues(bottom = 74.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(stringResource(R.string.oauth_connection_failed), style = MaterialTheme.typography.titleMedium) }
        item {
            Text(
                stringResource(
                    when (failure) {
                        OAuthFailure.TLS -> R.string.oauth_tls_error
                        OAuthFailure.CERTIFICATE -> R.string.oauth_certificate_error
                        OAuthFailure.NETWORK -> R.string.oauth_network_error
                        OAuthFailure.HTTP -> R.string.oauth_http_error
                        OAuthFailure.HTTPS_REQUIRED -> R.string.origin_https_required
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { Button(onClick = onEditAddress) { Text(stringResource(R.string.oauth_edit_address)) } }
        item { Button(onClick = onRetry) { Text(stringResource(R.string.connection_notice_retry)) } }
    }
}

private fun openOAuthCallback(
    context: Context,
    uri: Uri,
) {
    context.startActivity(Intent(context, OAuthCallbackActivity::class.java).setData(uri))
}
