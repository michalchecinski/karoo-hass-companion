package com.karoohass.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.karoohass.R
import com.karoohass.network.DirectWifiTransport
import com.karoohass.network.HttpRequest
import com.karoohass.security.TokenStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import android.util.Base64

class OAuthManager(private val context: Context, private val tokenStore: TokenStore) {
    private val prefs = context.getSharedPreferences("oauth", Context.MODE_PRIVATE)
    private val clientId = context.getString(R.string.oauth_client_id)
    private val redirectUri = context.getString(R.string.oauth_redirect_uri)
    fun normalizeOrigin(raw: String): String? = runCatching {
        val uri = URI(if (raw.startsWith("https://")) raw else "https://$raw")
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && (uri.path.isNullOrBlank() || uri.path == "/") && uri.query == null && uri.fragment == null)
        URI("https", null, uri.host, if (uri.port == 443) -1 else uri.port, null, null, null).toString()
    }.getOrNull()
    fun authorizationUrl(origin: String): String {
        val state = ByteArray(32).also(SecureRandom()::nextBytes).let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        prefs.edit().putString("state", state).putString("origin", origin).apply()
        return "$origin/auth/authorize?client_id=${encode(clientId)}&redirect_uri=${encode(redirectUri)}&response_type=code&state=${encode(state)}"
    }
    fun configuredOrigin(): String? = prefs.getString("origin", null)?.let(::normalizeOrigin)
    /** null means that the app was merely resumed, rather than launched by OAuth. */
    suspend fun consumeCallback(): Boolean? {
        val code = prefs.getString("code", null) ?: return null
        val state = prefs.getString("callbackState", null)
        if (state != prefs.getString("state", null)) { clearCallback(); return false }
        val origin = prefs.getString("origin", null) ?: return false
        Log.d("KarooHassOAuth", "Exchanging Home Assistant authorization code over Wi-Fi")
        val response = DirectWifiTransport(context).execute(HttpRequest("POST", "$origin/auth/token", mapOf("Content-Type" to "application/x-www-form-urlencoded"), "grant_type=authorization_code&code=${encode(code)}&client_id=${encode(clientId)}".toByteArray()))
        Log.d("KarooHassOAuth", "Home Assistant token response: HTTP ${response.code}")
        if (response.code !in 200..299 || response.body == null) return false
        val body = JSONObject(String(response.body)); val expires = body.optLong("expires_in", 1800)
        tokenStore.save(com.karoohass.security.Tokens(body.getString("access_token"), body.optString("refresh_token").ifBlank { null }, System.currentTimeMillis() + expires * 1000))
        clearCallback(); return true
    }
    suspend fun refresh(): Boolean {
        val tokens = tokenStore.load() ?: return false; val origin = prefs.getString("origin", null) ?: return false; val refresh = tokens.refreshToken ?: return false
        val response = runCatching { DirectWifiTransport(context).execute(HttpRequest("POST", "$origin/auth/token", mapOf("Content-Type" to "application/x-www-form-urlencoded"), "grant_type=refresh_token&refresh_token=${encode(refresh)}&client_id=${encode(clientId)}".toByteArray())) }.getOrNull() ?: return false
        if (response.code !in 200..299 || response.body == null) return false; val body = JSONObject(String(response.body)); tokenStore.save(com.karoohass.security.Tokens(body.getString("access_token"), body.optString("refresh_token", refresh), System.currentTimeMillis() + body.optLong("expires_in", 1800) * 1000)); return true
    }
    suspend fun revoke() { tokenStore.load()?.refreshToken?.let { token -> prefs.getString("origin", null)?.let { origin -> runCatching { DirectWifiTransport(context).execute(HttpRequest("POST", "$origin/auth/revoke", mapOf("Content-Type" to "application/x-www-form-urlencoded"), "token=${encode(token)}&client_id=${encode(clientId)}".toByteArray())) } } }; tokenStore.clear() }
    fun receive(uri: Uri): Boolean {
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            Log.w("KarooHassOAuth", "Authorization callback did not contain both code and state")
            return false
        }
        prefs.edit().putString("code", code).putString("callbackState", state).apply()
        return true
    }
    private fun clearCallback() = prefs.edit().remove("code").remove("callbackState").remove("state").apply()
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}

class OAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); OAuthManager(applicationContext, TokenStore(applicationContext)).receive(intent.data ?: run { finish(); return }); startActivity(Intent(this, com.karoohass.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish() }
}
