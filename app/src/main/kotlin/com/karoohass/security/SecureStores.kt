package com.karoohass.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

data class Tokens(val accessToken: String, val refreshToken: String?, val expiresAtMs: Long)

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("protected_tokens", Context.MODE_PRIVATE)
    private val keyAlias = "karoo_hass_tokens"
    private val cipher = "AES/GCM/NoPadding"

    fun save(tokens: Tokens) = prefs.edit().putString("tokens", encrypt("${tokens.accessToken}\u0000${tokens.refreshToken.orEmpty()}\u0000${tokens.expiresAtMs}")).apply()

    fun load(): Tokens? =
        prefs.getString("tokens", null)?.let(::decrypt)?.split('\u0000')?.let { values ->
            values.takeIf { it.size == 3 }?.let { Tokens(it[0], it[1].ifBlank { null }, it[2].toLong()) }
        }

    fun clear() = prefs.edit().clear().apply()

    private fun key() = (
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(keyAlias, null) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    )

    private fun encrypt(value: String): String {
        val c = Cipher.getInstance(cipher)
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val input = Base64.decode(value, Base64.NO_WRAP)
        val c = Cipher.getInstance(cipher)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, input.copyOfRange(0, 12)))
        return String(c.doFinal(input.copyOfRange(12, input.size)))
    }
}

class PinStore(context: Context) {
    private val prefs = context.getSharedPreferences("pin_verifier", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun configured() = prefs.contains("hash")

    fun set(pin: String) {
        require(pin.length in 4..6 && pin.all(Char::isDigit))
        val salt = ByteArray(16).also(random::nextBytes)
        prefs.edit().putString("salt", b64(salt)).putString("hash", b64(derive(pin, salt))).putInt("failures", 0).putLong("lockedUntil", 0).putInt("lockouts", 0).apply()
    }

    fun verify(
        pin: String,
        now: Long = System.currentTimeMillis(),
    ): PinResult {
        if (now < prefs.getLong("lockedUntil", 0)) return PinResult.Locked(prefs.getLong("lockedUntil", 0) - now)
        val salt = prefs.getString("salt", null)?.let(::unb64) ?: return PinResult.Failed
        val expectedHash = prefs.getString("hash", null)?.let(::unb64) ?: return PinResult.Failed
        if (MessageDigest.isEqual(derive(pin, salt), expectedHash)) {
            prefs.edit().putInt("failures", 0).putInt("lockouts", 0).putLong("lockedUntil", 0).apply()
            return PinResult.Success
        }
        val failures = prefs.getInt("failures", 0) + 1
        if (failures < 5) {
            prefs.edit().putInt("failures", failures).apply()
            return PinResult.Failed
        }
        val lockouts = prefs.getInt("lockouts", 0) + 1
        val seconds = minOf(30L * (1L shl (lockouts - 1).coerceAtMost(5)), 15 * 60L)
        prefs.edit().putInt("failures", 0).putInt("lockouts", lockouts).putLong("lockedUntil", now + seconds * 1000).apply()
        return PinResult.Locked(seconds * 1000)
    }

    fun clear() = prefs.edit().clear().apply()

    private fun derive(
        pin: String,
        salt: ByteArray,
    ) = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unb64(value: String) = Base64.decode(value, Base64.NO_WRAP)
}

sealed interface PinResult {
    data object Success : PinResult

    data object Failed : PinResult

    data class Locked(val remainingMs: Long) : PinResult
}
