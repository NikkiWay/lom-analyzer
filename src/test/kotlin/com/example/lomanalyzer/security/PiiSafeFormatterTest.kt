package com.example.lomanalyzer.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PiiSafeFormatterTest {

    @Test
    fun `masks vk_id in JSON-like format`() {
        val input = """{"vk_id": 123456789}"""
        val result = PiiSafeFormatter.mask(input)
        assertFalse(result.contains("123456789"))
        assertTrue(result.contains("1******89"))
    }

    @Test
    fun `masks vk_id with colon format`() {
        val result = PiiSafeFormatter.mask("vk_id: 987654321")
        assertTrue(result.contains("9******21"))
    }

    @Test
    fun `masks vk_id with equals format`() {
        val result = PiiSafeFormatter.mask("vk_id=555666777")
        assertTrue(result.contains("5******77"))
    }

    @Test
    fun `masks screen_name keeping first 2 and last 2`() {
        val result = PiiSafeFormatter.mask("""screen_name: "ivanov123"""")
        assertTrue(result.contains("iv*****23"))
        assertFalse(result.contains("ivanov123"))
    }

    @Test
    fun `masks firstName to triple star`() {
        val result = PiiSafeFormatter.mask("""firstName: "Иван"""")
        assertTrue(result.contains("***"))
        assertFalse(result.contains("Иван"))
    }

    @Test
    fun `masks lastName to triple star`() {
        val result = PiiSafeFormatter.mask("""lastName: "Петров"""")
        assertTrue(result.contains("***"))
        assertFalse(result.contains("Петров"))
    }

    @Test
    fun `masks first_name underscore variant`() {
        val result = PiiSafeFormatter.mask("""first_name: "Мария"""")
        assertTrue(result.contains("***"))
    }

    @Test
    fun `masks last_name underscore variant`() {
        val result = PiiSafeFormatter.mask("""last_name: "Сидорова"""")
        assertTrue(result.contains("***"))
    }

    @Test
    fun `masks long text fields over 80 chars`() {
        val longText = "a".repeat(100)
        val input = """{"text": "$longText"}"""
        val result = PiiSafeFormatter.mask(input)
        assertTrue(result.contains("<redacted:100>"))
        assertFalse(result.contains(longText))
    }

    @Test
    fun `does not mask short text fields`() {
        val shortText = "hello world"
        val input = """{"text": "$shortText"}"""
        val result = PiiSafeFormatter.mask(input)
        assertTrue(result.contains(shortText))
    }

    @Test
    fun `handles multiple vk_ids in one string`() {
        val input = "vk_id: 111222333 and vk_id: 444555666"
        val result = PiiSafeFormatter.mask(input)
        assertFalse(result.contains("111222333"))
        assertFalse(result.contains("444555666"))
    }

    @Test
    fun `handles nested JSON with vk_id`() {
        val input = """{"author": {"vk_id": 123456789, "name": "test"}}"""
        val result = PiiSafeFormatter.mask(input)
        assertFalse(result.contains("123456789"))
    }

    @Test
    fun `handles URL query parameter with vk_id`() {
        val result = PiiSafeFormatter.mask("user?vk_id=999888777&action=view")
        assertFalse(result.contains("999888777"))
    }

    @Test
    fun `does not mask short vk_id under 3 digits`() {
        val result = PiiSafeFormatter.mask("vk_id: 12")
        assertTrue(result.contains("12")) // Too short to match
    }

    @Test
    fun `empty string returns empty`() {
        assertEquals("", PiiSafeFormatter.mask(""))
    }

    @Test
    fun `string without PII passes through unchanged`() {
        val input = "This is a regular log message with no PII"
        assertEquals(input, PiiSafeFormatter.mask(input))
    }

    @Test
    fun `masks quoted vk_id values`() {
        val result = PiiSafeFormatter.mask("""vk_id: "123456789"""")
        assertFalse(result.contains("123456789"))
    }

    @Test
    fun `masks screen_name with underscores`() {
        val result = PiiSafeFormatter.mask("screen_name: user_name_123")
        assertFalse(result.contains("user_name_123"))
    }

    @Test
    fun `case insensitive vk_id matching`() {
        val result = PiiSafeFormatter.mask("VK_ID: 123456789")
        assertFalse(result.contains("123456789"))
    }

    @Test
    fun `handles whitespace variations`() {
        val result1 = PiiSafeFormatter.mask("vk_id:123456789")
        val result2 = PiiSafeFormatter.mask("vk_id : 123456789")
        assertFalse(result1.contains("123456789"))
        assertFalse(result2.contains("123456789"))
    }
}
