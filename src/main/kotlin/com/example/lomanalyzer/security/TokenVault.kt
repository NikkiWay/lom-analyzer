package com.example.lomanalyzer.security

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class TokenVault(
    private val vaultFile: Path,
    private val iterations: Int = 100_000,
) {
    companion object {
        private const val AES_KEY_LENGTH = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val SALT_LENGTH = 16
    }

    private var encryptedToken: ByteArray? = null
    private var derivedKey: ByteArray? = null
    private var currentSalt: ByteArray? = null

    fun initializeKey(masterPassword: CharArray) {
        val salt = if (Files.exists(vaultFile)) {
            val fileBytes = Files.readAllBytes(vaultFile)
            fileBytes.copyOfRange(0, SALT_LENGTH)
        } else {
            ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        }

        currentSalt = salt
        derivedKey = deriveKey(masterPassword, salt)

        if (Files.exists(vaultFile)) {
            val fileBytes = Files.readAllBytes(vaultFile)
            val iv = fileBytes.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
            val ciphertext = fileBytes.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, fileBytes.size)
            decryptRaw(derivedKey!!, iv, ciphertext) // verify password
            encryptedToken = fileBytes
        }
    }

    fun store(token: ByteArray) {
        check(derivedKey != null) { "Vault not initialized — call initializeKey first" }
        val key = derivedKey!!
        val salt = currentSalt!!

        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val ciphertext = encryptRaw(key, iv, token)

        val fileBytes = salt + iv + ciphertext
        Files.createDirectories(vaultFile.parent)
        Files.write(vaultFile, fileBytes)
        encryptedToken = fileBytes
    }

    @Suppress("ReturnCount")
    fun get(): ByteArray? {
        val key = derivedKey ?: return null
        val fileBytes = encryptedToken ?: return null
        if (fileBytes.size <= SALT_LENGTH + GCM_IV_LENGTH) return null

        val iv = fileBytes.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
        val ciphertext = fileBytes.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, fileBytes.size)
        return decryptRaw(key, iv, ciphertext)
    }

    fun hasStoredVault(): Boolean = Files.exists(vaultFile)

    fun clear() {
        derivedKey?.let { Arrays.fill(it, 0) }
        derivedKey = null
        currentSalt = null
        encryptedToken = null
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, AES_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun encryptRaw(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(plaintext)
    }

    private fun decryptRaw(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }
}
