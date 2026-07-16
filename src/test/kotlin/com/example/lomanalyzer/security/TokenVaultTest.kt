/*
 * НАЗНАЧЕНИЕ
 * Тесты TokenVault — защищённого хранилища VK-токена на диске, зашифрованного
 * ключом, производным от пароля. Проверяют сохранение/чтение, открытие хранилища
 * в новой сессии с верным паролем, отказ при неверном пароле, очистку состояния
 * и индикатор наличия сохранённого хранилища.
 *
 * ЧТО ВНУТРИ
 * Класс TokenVaultTest (vault во временном каталоге @TempDir):
 *  - roundtrip: store → get возвращает те же байты;
 *  - повторное открытие файла с верным паролем восстанавливает токен;
 *  - неверный пароль → AEADBadTagException (провал аутентификации шифра);
 *  - clear обнуляет состояние (get → null);
 *  - get до инициализации → null;
 *  - hasStoredVault: false для нового, true после store.
 *
 * МЕТОД
 * AEAD-шифрование (GCM): AEADBadTagException при неверном ключе означает, что
 * тег аутентичности не сошёлся, то есть пароль/ключ неправильный.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (@TempDir, assert*, assertThrows). javax.crypto.AEADBadTagException.
 *
 * СВЯЗИ
 * TokenVault (security).
 */
package com.example.lomanalyzer.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.crypto.AEADBadTagException

/** Тесты шифрованного хранилища токена (сохранение, пароль, очистка). */
class TokenVaultTest {

    /** Временный каталог JUnit для файла хранилища (изолирован, авто-удаляется). */
    @TempDir
    lateinit var tempDir: Path

    /** Путь к файлу хранилища внутри временного каталога. */
    private fun vaultFile() = tempDir.resolve("token_vault.bin")

    /**
     * Roundtrip: после инициализации ключа паролем и store(token) метод get
     * возвращает те же самые байты токена (шифрование обратимо).
     */
    @Test
    fun `store and retrieve token roundtrip`() {
        val vault = TokenVault(vaultFile())
        val password = "securePass123".toCharArray()
        val token = "vk1.a.very-long-token-value".toByteArray()

        // Инициализируем ключ паролем и сохраняем токен
        vault.initializeKey(password)
        vault.store(token)

        // Читаем обратно — байты должны совпасть
        val retrieved = vault.get()
        assertNotNull(retrieved)
        assertArrayEquals(token, retrieved)
    }

    /**
     * Персистентность между сессиями: после store+clear в одном экземпляре vault,
     * новый экземпляр с тем же файлом и верным паролем читает токен с диска.
     */
    @Test
    fun `retrieve from reopened vault with correct password`() {
        val file = vaultFile()
        val password = "myPassword".toCharArray()
        val token = "token-data-12345".toByteArray()

        // First session: store
        // Первая сессия: записываем токен и очищаем in-memory состояние
        val vault1 = TokenVault(file)
        vault1.initializeKey(password)
        vault1.store(token)
        vault1.clear()

        // Second session: reopen
        // Вторая сессия: открываем тот же файл тем же паролем
        val vault2 = TokenVault(file)
        vault2.initializeKey("myPassword".toCharArray())
        val retrieved = vault2.get()

        // Токен восстановлен с диска
        assertNotNull(retrieved)
        assertArrayEquals(token, retrieved)
    }

    /**
     * Неверный пароль при открытии хранилища приводит к AEADBadTagException:
     * GCM-тег не проходит проверку, расшифровка невозможна (защита от подбора).
     */
    @Test
    fun `wrong password fails to decrypt`() {
        val file = vaultFile()
        val token = "secret-token".toByteArray()

        // Сохраняем токен под правильным паролем
        val vault1 = TokenVault(file)
        vault1.initializeKey("correctPassword".toCharArray())
        vault1.store(token)
        vault1.clear()

        // Открытие с неверным паролем должно бросить AEADBadTagException
        val vault2 = TokenVault(file)
        assertThrows(AEADBadTagException::class.java) {
            vault2.initializeKey("wrongPassword".toCharArray())
        }
    }

    /** clear() очищает текущее состояние: после него get возвращает null. */
    @Test
    fun `clear wipes state`() {
        val vault = TokenVault(vaultFile())
        vault.initializeKey("pass".toCharArray())
        vault.store("token".toByteArray())

        // Очистка состояния
        vault.clear()
        assertNull(vault.get())
    }

    /** До инициализации ключа хранилище пусто: get возвращает null. */
    @Test
    fun `get returns null before initialization`() {
        val vault = TokenVault(vaultFile())
        assertNull(vault.get())
    }

    /** Для нового (несохранённого) хранилища hasStoredVault возвращает false. */
    @Test
    fun `hasStoredVault returns false for new vault`() {
        val vault = TokenVault(vaultFile())
        assertFalse(vault.hasStoredVault())
    }

    /** После записи токена hasStoredVault становится true (файл хранилища создан). */
    @Test
    fun `hasStoredVault returns true after store`() {
        val vault = TokenVault(vaultFile())
        vault.initializeKey("pass".toCharArray())
        vault.store("tok".toByteArray())
        assertTrue(vault.hasStoredVault())
    }
}
