package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.security.TokenVault
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * OAuth flow supporting both Implicit Flow (legacy) and
 * Authorization Code + PKCE per v6 §29.1.
 */
class OAuthFlow(
    private val tokenVault: TokenVault,
    private val logger: Logger,
    private val usePkce: Boolean = true,
) {
    companion object {
        private const val AUTH_URL = "https://oauth.vk.com/authorize"
        @Suppress("unused") const val TOKEN_URL = "https://oauth.vk.com/access_token"
        private const val REDIRECT_URI = "https://oauth.vk.com/blank.html"
        private const val SCOPE = "wall,friends,groups,stats"
        private const val VERIFIER_LENGTH = 64
    }

    private var codeVerifier: String? = null

    // --- PKCE (Authorization Code Flow) ---

    fun buildPkceAuthUrl(clientId: String): String {
        codeVerifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(codeVerifier!!)

        return "$AUTH_URL?client_id=$clientId" +
            "&redirect_uri=$REDIRECT_URI" +
            "&scope=$SCOPE" +
            "&response_type=code" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&v=5.199"
    }

    @Suppress("ReturnCount")
    fun handleCodeRedirect(redirectUrl: String): String? {
        val query = redirectUrl.substringAfter("?", "")
        if (query.isBlank()) return null

        val params = query.split("&").associate { part ->
            val (k, v) = part.split("=", limit = 2)
            k to v
        }
        return params["code"]
    }

    fun buildTokenExchangeParams(code: String, clientId: String): Map<String, String> {
        val params = mutableMapOf(
            "client_id" to clientId,
            "code" to code,
            "redirect_uri" to REDIRECT_URI,
        )
        codeVerifier?.let { params["code_verifier"] = it }
        return params
    }

    fun storeTokens(accessToken: String, refreshToken: String? = null) {
        val tokenData = if (refreshToken != null) {
            "$accessToken\n$refreshToken"
        } else {
            accessToken
        }
        tokenVault.store(tokenData.toByteArray(Charsets.UTF_8))
        logger.event(AppEvent.VK_OAUTH_COMPLETED, mapOf("pkce" to usePkce))
    }

    // --- Implicit Flow (legacy fallback) ---

    fun buildImplicitAuthUrl(clientId: String): String =
        "$AUTH_URL?client_id=$clientId" +
            "&display=page" +
            "&redirect_uri=$REDIRECT_URI" +
            "&scope=$SCOPE" +
            "&response_type=token" +
            "&v=5.199"

    @Suppress("ReturnCount")
    fun handleImplicitRedirect(redirectUrl: String): String? {
        val fragment = redirectUrl.substringAfter("#", "")
        if (fragment.isBlank()) return null
        val params = fragment.split("&").associate { part ->
            val (k, v) = part.split("=", limit = 2)
            k to v
        }
        val token = params["access_token"] ?: return null
        tokenVault.store(token.toByteArray(Charsets.UTF_8))
        logger.event(AppEvent.VK_OAUTH_COMPLETED)
        return token
    }

    // --- Convenience ---

    fun buildAuthUrl(clientId: String): String =
        if (usePkce) buildPkceAuthUrl(clientId)
        else buildImplicitAuthUrl(clientId)

    fun getStoredToken(): String? {
        val bytes = tokenVault.get() ?: return null
        return String(bytes, Charsets.UTF_8).lines().first()
    }

    fun getStoredRefreshToken(): String? {
        val bytes = tokenVault.get() ?: return null
        val lines = String(bytes, Charsets.UTF_8).lines()
        return if (lines.size >= 2) lines[1] else null
    }

    fun shouldRefresh(): Boolean = getStoredRefreshToken() != null

    // --- PKCE helpers ---

    internal fun generateCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    internal fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
