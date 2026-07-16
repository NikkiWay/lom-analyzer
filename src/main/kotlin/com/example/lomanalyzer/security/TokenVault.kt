/*
 * НАЗНАЧЕНИЕ
 * Зашифрованное локальное хранилище (vault) для секрета доступа VK — токена
 * доступа. Защищает токен на диске мастер-паролем пользователя так, чтобы без
 * знания пароля расшифровать токен было невозможно. Относится к модулю
 * безопасности приложения (см. docs/architecture.md, раздел про хранение
 * токена VK).
 *
 * ЧТО ВНУТРИ
 * Класс TokenVault: инициализация ключа из мастер-пароля, сохранение (store),
 * чтение (get) и удаление токена, смена пароля (changePassword), полное
 * удаление файла хранилища и затирание ключа из памяти (clear).
 *
 * КРИПТОГРАФИЯ (как именно защищается токен)
 * 1) Вывод ключа (key derivation). Из мастер-пароля (CharArray) и случайной
 *    «соли» (salt, 16 байт) функцией PBKDF2WithHmacSHA256 за 100000 итераций
 *    выводится симметричный ключ длиной 256 бит. PBKDF2 намеренно медленный:
 *    100000 итераций HMAC-SHA256 удорожают перебор пароля. Соль не секретна,
 *    но уникальна для каждого хранилища — она защищает от атак по словарям и
 *    радужным таблицам, делая ключ непредсказуемым даже при одинаковых паролях.
 * 2) Шифрование токена. Алгоритм AES-256-GCM (режим Galois/Counter Mode):
 *    AES шифрует данные выведенным ключом, а GCM дополнительно вычисляет тег
 *    аутентичности (128 бит), который при расшифровке проверяет целостность —
 *    подделка или повреждение шифртекста приведут к исключению. Для каждой
 *    операции шифрования генерируется новый случайный вектор инициализации
 *    (IV/nonce, 12 байт): повтор IV при одном ключе в GCM недопустим, поэтому
 *    SecureRandom даёт каждый раз свежий IV.
 * 3) Формат файла хранилища: salt(16) + iv(12) + ciphertext(шифртекст с тегом).
 *    Соль и IV хранятся в открытом виде рядом с шифртекстом — это нормально,
 *    секретность обеспечивает только мастер-пароль.
 * 4) Проверка пароля. Отдельной «контрольной суммы» пароля нет: при вводе
 *    неверного пароля выводится неверный ключ, и GCM-расшифровка не проходит
 *    проверку тега — бросается исключение, что и трактуется как неверный пароль.
 * 5) Затирание из памяти. Выведенный ключ держится в ByteArray и при clear()
 *    забивается нулями (Arrays.fill), чтобы не оставлять секрет в куче дольше
 *    необходимого (CharArray/ByteArray вместо String — их можно явно очистить).
 *
 * БИБЛИОТЕКИ
 * javax.crypto (JCA/JCE): Cipher (AES/GCM/NoPadding), SecretKeyFactory
 * (PBKDF2WithHmacSHA256), PBEKeySpec, SecretKeySpec, GCMParameterSpec.
 * java.security.SecureRandom — криптостойкий генератор соли и IV.
 * java.nio.file (Files/Path) — чтение и запись файла хранилища.
 *
 * СВЯЗИ
 * Используется AuthManager и OAuthFlow для хранения токена доступа VK;
 * мастер-пароль вводится через MasterPasswordDialog (Compose).
 */
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

/**
 * Зашифрованное хранилище VK-токена на диске, защищённое мастер-паролем.
 *
 * @param vaultFile путь к файлу хранилища (содержит salt + iv + ciphertext).
 * @param iterations число итераций PBKDF2 для вывода ключа (по умолчанию 100000,
 *   чем больше — тем дороже перебор пароля, но дольше разблокировка).
 */
class TokenVault(
    private val vaultFile: Path,
    private val iterations: Int = 100_000,
) {
    companion object {
        /** Длина симметричного AES-ключа в битах (AES-256). */
        private const val AES_KEY_LENGTH = 256
        /** Длина вектора инициализации GCM (nonce) в байтах; 12 байт — рекомендованный размер для GCM. */
        private const val GCM_IV_LENGTH = 12
        /** Длина тега аутентичности GCM в битах (128 — максимальная стойкость проверки целостности). */
        private const val GCM_TAG_LENGTH = 128
        /** Длина случайной соли PBKDF2 в байтах. */
        private const val SALT_LENGTH = 16
    }

    /** Кэш «сырого» содержимого файла хранилища (salt + iv + ciphertext) в памяти. */
    private var encryptedToken: ByteArray? = null
    /** Выведенный из мастер-пароля AES-ключ; null до initializeKey и после clear. */
    private var derivedKey: ByteArray? = null
    /** Текущая соль (из файла либо вновь сгенерированная), использованная при выводе ключа. */
    private var currentSalt: ByteArray? = null

    /**
     * Инициализирует AES-ключ из мастер-пароля. Если файл хранилища уже есть —
     * берёт сохранённую соль и проверяет пароль попыткой расшифровки; если файла
     * нет — генерирует новую случайную соль для будущего сохранения токена.
     *
     * @param masterPassword мастер-пароль пользователя.
     * @throws javax.crypto.AEADBadTagException при неверном пароле (тег GCM не сошёлся).
     */
    fun initializeKey(masterPassword: CharArray) {
        // Соль читаем из первых 16 байт существующего файла либо генерируем новую случайную
        val salt = if (Files.exists(vaultFile)) {
            val fileBytes = Files.readAllBytes(vaultFile)
            fileBytes.copyOfRange(0, SALT_LENGTH)
        } else {
            ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        }

        // Запоминаем соль и выводим из пароля симметричный ключ через PBKDF2
        currentSalt = salt
        derivedKey = deriveKey(masterPassword, salt)

        // Если хранилище уже существует — проверяем корректность пароля пробной расшифровкой
        if (Files.exists(vaultFile)) {
            val fileBytes = Files.readAllBytes(vaultFile)
            // IV идёт сразу после соли, далее — шифртекст (с GCM-тегом в конце)
            val iv = fileBytes.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
            val ciphertext = fileBytes.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, fileBytes.size)
            decryptRaw(derivedKey!!, iv, ciphertext) // verify password — при неверном пароле GCM бросит исключение
            encryptedToken = fileBytes
        }
    }

    /**
     * Шифрует и записывает токен в файл хранилища. Каждый вызов использует новый
     * случайный IV (повтор IV при одном ключе в GCM недопустим).
     *
     * @param token открытые байты токена (для удаления передаётся пустой массив).
     * @throws IllegalStateException если ключ ещё не выведен (initializeKey не вызван).
     */
    fun store(token: ByteArray) {
        check(derivedKey != null) { "Vault not initialized — call initializeKey first" }
        val key = derivedKey!!
        val salt = currentSalt!!

        // Свежий случайный IV/nonce на каждую операцию шифрования
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        // AES-256-GCM: получаем шифртекст с прикреплённым тегом аутентичности
        val ciphertext = encryptRaw(key, iv, token)

        // Складываем файл в формате salt + iv + ciphertext и пишем на диск
        val fileBytes = salt + iv + ciphertext
        Files.createDirectories(vaultFile.parent)
        Files.write(vaultFile, fileBytes)
        encryptedToken = fileBytes
    }

    /**
     * Расшифровывает и возвращает токен, либо null если ключ не выведен,
     * хранилище пусто или содержит только заголовок (salt + iv без шифртекста).
     */
    @Suppress("ReturnCount")
    fun get(): ByteArray? {
        val key = derivedKey ?: return null
        val fileBytes = encryptedToken ?: return null
        // Если данных нет дальше соли и IV — токена в хранилище нет
        if (fileBytes.size <= SALT_LENGTH + GCM_IV_LENGTH) return null

        // Извлекаем IV и шифртекст и расшифровываем с проверкой целостности (GCM-тег)
        val iv = fileBytes.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
        val ciphertext = fileBytes.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, fileBytes.size)
        return decryptRaw(key, iv, ciphertext)
    }

    /** Существует ли файл хранилища на диске (без расшифровки). */
    fun hasStoredVault(): Boolean = Files.exists(vaultFile)

    /** Есть ли в хранилище непустой токен (требует уже выведенного ключа). */
    fun hasToken(): Boolean {
        val data = get() ?: return false
        return data.isNotEmpty()
    }

    /** Удаляет токен, перезаписывая хранилище пустым (зашифрованным) значением. */
    fun removeToken() {
        check(derivedKey != null) { "Vault not initialized — call initializeKey first" }
        store(ByteArray(0))
    }

    /**
     * Меняет мастер-пароль: расшифровывает токен старым ключом, генерирует новую
     * соль, выводит новый ключ и перешифровывает токен под него.
     */
    fun changePassword(newPassword: CharArray) {
        check(derivedKey != null) { "Vault not initialized — call initializeKey first" }
        // Достаём текущий открытый токен под старым ключом
        val plainToken = get() ?: ByteArray(0)
        // Новая соль и новый ключ из нового пароля
        val newSalt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        currentSalt = newSalt
        derivedKey = deriveKey(newPassword, newSalt)
        // Перешифровываем токен под новый ключ
        store(plainToken)
    }

    /** Полностью удаляет хранилище: затирает ключ из памяти и удаляет файл. */
    fun deleteVault() {
        clear()
        Files.deleteIfExists(vaultFile)
    }

    /** Затирает выведенный ключ нулями и сбрасывает состояние хранилища в памяти. */
    fun clear() {
        // Забиваем байты ключа нулями, чтобы секрет не оставался в куче
        derivedKey?.let { Arrays.fill(it, 0) }
        derivedKey = null
        currentSalt = null
        encryptedToken = null
    }

    /**
     * Выводит 256-битный AES-ключ из пароля и соли функцией PBKDF2WithHmacSHA256
     * за заданное число итераций (растягивание ключа против перебора).
     */
    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        // PBEKeySpec задаёт пароль, соль, число итераций и желаемую длину ключа
        val spec = PBEKeySpec(password, salt, iterations, AES_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        // encoded — «сырые» байты выведенного ключа для AES
        return factory.generateSecret(spec).encoded
    }

    /** Шифрует открытый текст алгоритмом AES-256-GCM с заданными ключом и IV. */
    private fun encryptRaw(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // GCMParameterSpec задаёт длину тега аутентичности и IV
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(plaintext)
    }

    /**
     * Расшифровывает шифртекст AES-256-GCM. При неверном ключе или повреждении
     * данных проверка GCM-тега не проходит и бросается исключение.
     */
    private fun decryptRaw(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }
}
