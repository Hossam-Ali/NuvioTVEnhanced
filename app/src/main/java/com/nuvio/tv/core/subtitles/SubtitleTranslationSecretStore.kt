package com.nuvio.tv.core.subtitles

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts API keys with an AES-256-GCM key that lives in the Android Keystore. The keystore key
 * never leaves secure hardware (where available) and cannot be exported, so the stored ciphertext
 * is useless without this device.
 */
internal object SubtitleTranslationSecretStore {
    private const val TAG = "SubtitleTranslationKeys"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "nuvio_subtitle_translation_api_keys"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val FORMAT_PREFIX = "v1:"

    fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            FORMAT_PREFIX + Base64.encodeToString(sealed, Base64.NO_WRAP)
        }.onFailure { Log.w(TAG, "Could not encrypt the API key: ${it.javaClass.simpleName}") }.getOrNull()
    }

    /** Returns "" when there is nothing stored or the keystore key was reset. */
    fun decrypt(stored: String?): String {
        if (stored.isNullOrEmpty() || !stored.startsWith(FORMAT_PREFIX)) return ""
        return runCatching {
            val sealed = Base64.decode(stored.removePrefix(FORMAT_PREFIX), Base64.NO_WRAP)
            val ivLength = 12
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, sealed, 0, ivLength))
            String(cipher.doFinal(sealed, ivLength, sealed.size - ivLength), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "Could not decrypt the stored API key: ${it.javaClass.simpleName}") }.getOrDefault("")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
