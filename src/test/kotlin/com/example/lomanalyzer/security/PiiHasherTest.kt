/*
 * НАЗНАЧЕНИЕ
 * Тесты PiiHasher — псевдонимизации персональных данных (PII) автора перед
 * хранением/экспортом. Хэширование с солью обеспечивает детерминированность при
 * одной соли и несвязываемость между разными солями (защита приватности).
 *
 * ЧТО ВНУТРИ
 * Класс PiiHasherTest с пятью @Test:
 *  - одинаковый вход и соль → одинаковый хэш (детерминированность);
 *  - разные соли → разные хэши (несвязываемость наборов данных);
 *  - разные значения → разные хэши (различимость);
 *  - формат хэша: 64 hex-символа (SHA-256);
 *  - пустая строка тоже хэшируется.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (assert*). java.security.SecureRandom — генерация криптослучайной соли.
 *
 * СВЯЗИ
 * PiiHasher (security). Длина 64 hex-символа указывает на SHA-256.
 */
package com.example.lomanalyzer.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.SecureRandom

/** Тесты детерминированности и формата хэширования PII. */
class PiiHasherTest {

    /** Детерминированность: один и тот же вход с одной солью даёт идентичный хэш. */
    @Test
    fun `same input and salt produce identical hash`() {
        // Случайная соль 16 байт
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        // Дважды хэшируем одно значение с одной солью
        val hash1 = PiiHasher.hash("user123", salt)
        val hash2 = PiiHasher.hash("user123", salt)
        // Результаты должны совпасть
        assertEquals(hash1, hash2)
    }

    /**
     * Несвязываемость: одно и то же значение под разными солями даёт разные хэши,
     * что не позволяет сопоставить автора между сессиями с разными солями.
     */
    @Test
    fun `different salts produce different hashes`() {
        val salt1 = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt2 = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash1 = PiiHasher.hash("user123", salt1)
        val hash2 = PiiHasher.hash("user123", salt2)
        // Разные соли → разные хэши
        assertNotEquals(hash1, hash2)
    }

    /** Различимость: разные входные значения при одной соли дают разные хэши. */
    @Test
    fun `different values produce different hashes`() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash1 = PiiHasher.hash("alice", salt)
        val hash2 = PiiHasher.hash("bob", salt)
        assertNotEquals(hash1, hash2)
    }

    /**
     * Формат: хэш — строка из 64 шестнадцатеричных символов (нижний регистр),
     * что соответствует 256-битному дайджесту SHA-256.
     */
    @Test
    fun `hash is valid hex string of 64 chars`() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = PiiHasher.hash("test", salt)
        // 64 символа = 32 байта = SHA-256
        assertEquals(64, hash.length)
        // Только hex-символы 0-9 и a-f
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    /** Граничный случай: пустая строка корректно хэшируется в 64-символьный хэш. */
    @Test
    fun `empty string is hashable`() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = PiiHasher.hash("", salt)
        assertEquals(64, hash.length)
    }
}
