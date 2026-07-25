package com.v2ray.ang.util

import android.util.Base64
import com.v2ray.ang.AppConfig
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * App-locked AES-256-GCM encryption for shared server configs.
 *
 * Unlike user password encryption, this uses a key derived from a secret
 * embedded in the app itself. The intent isn't to keep the config private
 * from people the link is shared with - it's to make a config exported from
 * MikuRay only importable by MikuRay: pasting it into stock v2rayNG, other
 * V2Ray/Xray clients, or reading it in a clipboard manager just shows an
 * opaque blob instead of a working vless://, vmess://, etc URI.
 *
 * This is app-level obfuscation, not a security boundary - anyone who
 * decompiles the app can recover [APP_SECRET]. Don't rely on it to protect
 * the config from a motivated attacker; it only stops casual reuse in other
 * clients.
 *
 * Output format: "mikuray-enc:" + Base64(salt[16] + iv[12] + ciphertext+tag)
 */
object CryptoUtils {

    private const val PREFIX = "mikuray-enc:"

    // App-embedded secret used to derive the encryption key. Change this if
    // you fork MikuRay and want your build's exports to be incompatible
    // with other builds (and vice versa).
    private const val APP_SECRET = "MikuRay-\u00b5-Share-Lock-v1"

    private const val ITERATIONS = 50_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    /**
     * Whether [text] looks like a payload produced by [encrypt].
     */
    fun isEncrypted(text: String?): Boolean {
        return text?.trim()?.startsWith(PREFIX) == true
    }

    /**
     * Encrypts [plainText] using the app-embedded key.
     *
     * @return the encrypted payload (prefixed, safe to put on the
     * clipboard), or an empty string if encryption failed.
     */
    fun encrypt(plainText: String): String {
        try {
            val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val payload = salt + iv + cipherText
            return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to encrypt share content", e)
            return ""
        }
    }

    /**
     * Decrypts a payload produced by [encrypt].
     *
     * @return the original plain text, or null if the payload isn't a
     * MikuRay-encrypted config (wrong app, corrupted, or tampered with).
     */
    fun decrypt(payloadWithPrefix: String): String? {
        try {
            val raw = payloadWithPrefix.trim().removePrefix(PREFIX)
            val payload = Base64.decode(raw, Base64.NO_WRAP)
            if (payload.size < SALT_LENGTH + IV_LENGTH) return null

            val salt = payload.copyOfRange(0, SALT_LENGTH)
            val iv = payload.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
            val cipherText = payload.copyOfRange(SALT_LENGTH + IV_LENGTH, payload.size)
            val key = deriveKey(salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to decrypt share content", e)
            return null
        }
    }

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(APP_SECRET.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
