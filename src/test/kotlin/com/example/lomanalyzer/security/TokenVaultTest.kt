package com.example.lomanalyzer.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.crypto.AEADBadTagException

class TokenVaultTest {

    @TempDir
    lateinit var tempDir: Path

    private fun vaultFile() = tempDir.resolve("token_vault.bin")

    @Test
    fun `store and retrieve token roundtrip`() {
        val vault = TokenVault(vaultFile())
        val password = "securePass123".toCharArray()
        val token = "vk1.a.very-long-token-value".toByteArray()

        vault.initializeKey(password)
        vault.store(token)

        val retrieved = vault.get()
        assertNotNull(retrieved)
        assertArrayEquals(token, retrieved)
    }

    @Test
    fun `retrieve from reopened vault with correct password`() {
        val file = vaultFile()
        val password = "myPassword".toCharArray()
        val token = "token-data-12345".toByteArray()

        // First session: store
        val vault1 = TokenVault(file)
        vault1.initializeKey(password)
        vault1.store(token)
        vault1.clear()

        // Second session: reopen
        val vault2 = TokenVault(file)
        vault2.initializeKey("myPassword".toCharArray())
        val retrieved = vault2.get()

        assertNotNull(retrieved)
        assertArrayEquals(token, retrieved)
    }

    @Test
    fun `wrong password fails to decrypt`() {
        val file = vaultFile()
        val token = "secret-token".toByteArray()

        val vault1 = TokenVault(file)
        vault1.initializeKey("correctPassword".toCharArray())
        vault1.store(token)
        vault1.clear()

        val vault2 = TokenVault(file)
        assertThrows(AEADBadTagException::class.java) {
            vault2.initializeKey("wrongPassword".toCharArray())
        }
    }

    @Test
    fun `clear wipes state`() {
        val vault = TokenVault(vaultFile())
        vault.initializeKey("pass".toCharArray())
        vault.store("token".toByteArray())

        vault.clear()
        assertNull(vault.get())
    }

    @Test
    fun `get returns null before initialization`() {
        val vault = TokenVault(vaultFile())
        assertNull(vault.get())
    }

    @Test
    fun `hasStoredVault returns false for new vault`() {
        val vault = TokenVault(vaultFile())
        assertFalse(vault.hasStoredVault())
    }

    @Test
    fun `hasStoredVault returns true after store`() {
        val vault = TokenVault(vaultFile())
        vault.initializeKey("pass".toCharArray())
        vault.store("tok".toByteArray())
        assertTrue(vault.hasStoredVault())
    }
}
