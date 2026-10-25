package com.karoohass.auth

import java.net.URI
import java.util.Locale

enum class OriginError { HTTPS_REQUIRED, INVALID_ADDRESS, CREDENTIALS, ORIGIN_ONLY, INVALID_PORT }

data class OriginValidation(val origin: String? = null, val error: OriginError? = null)

/** Syntax validation only: certificate trust and reachability are checked by the connection. */
fun validateHomeAssistantOrigin(raw: String): OriginValidation {
    val input = raw.trim()
    if (input.startsWith("http://", ignoreCase = true)) return OriginValidation(error = OriginError.HTTPS_REQUIRED)
    val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(input)
    if (hasScheme && !input.startsWith("https://", ignoreCase = true)) return OriginValidation(error = OriginError.HTTPS_REQUIRED)
    val uri =
        runCatching { URI(if (hasScheme) input else "https://$input") }.getOrNull()
            ?: return OriginValidation(error = OriginError.INVALID_ADDRESS)
    if (uri.userInfo != null) return OriginValidation(error = OriginError.CREDENTIALS)
    if ((!uri.path.isNullOrEmpty() && uri.path != "/") || uri.query != null || uri.fragment != null) {
        return OriginValidation(error = OriginError.ORIGIN_ONLY)
    }
    if (uri.host.isNullOrBlank()) return OriginValidation(error = OriginError.INVALID_ADDRESS)
    if (uri.port != -1 && uri.port !in 1..65535 || uri.rawAuthority.endsWith(":")) return OriginValidation(error = OriginError.INVALID_PORT)
    return OriginValidation(origin = URI("https", null, uri.host.lowercase(Locale.ROOT), if (uri.port == 443) -1 else uri.port, null, null, null).toString())
}
