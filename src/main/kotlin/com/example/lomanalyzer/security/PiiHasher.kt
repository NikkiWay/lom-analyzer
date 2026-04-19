package com.example.lomanalyzer.security

import java.security.MessageDigest

object PiiHasher {
    fun hash(value: String, sessionSalt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(sessionSalt)
        digest.update(value.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
