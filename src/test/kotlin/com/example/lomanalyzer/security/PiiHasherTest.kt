package com.example.lomanalyzer.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.SecureRandom

class PiiHasherTest {

    @Test
    fun `same input and salt produce identical hash`() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash1 = PiiHasher.hash("user123", salt)
        val hash2 = PiiHasher.hash("user123", salt)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `different salts produce different hashes`() {
        val salt1 = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt2 = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash1 = PiiHasher.hash("user123", salt1)
        val hash2 = PiiHasher.hash("user123", salt2)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `different values produce different hashes`() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash1 = PiiHasher.hash("alice", salt)
        val hash2 = PiiHasher.hash("bob", salt)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hash is valid hex string of 64 chars`() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = PiiHasher.hash("test", salt)
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `empty string is hashable`() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = PiiHasher.hash("", salt)
        assertEquals(64, hash.length)
    }
}
