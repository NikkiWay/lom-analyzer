package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.security.TokenVault

// TODO(Prompt-21): Migrate from OAuth Implicit Flow to Authorization Code + PKCE
//   per architecture v6 §29.1 / §33 technical debt plan.
//   VK API 5.199+ currently supports Implicit Flow but it is deprecated in OAuth 2.1.

class OAuthFlow(
    private val tokenVault: TokenVault,
    private val logger: Logger,
) {
    companion object {
        private const val AUTH_URL = "https://oauth.vk.com/authorize"
        private const val REDIRECT_URI = "https://oauth.vk.com/blank.html"
        private const val DISPLAY = "page"
        private const val SCOPE = "wall,friends,groups,stats"
        private const val RESPONSE_TYPE = "token"
    }

    fun buildAuthUrl(clientId: String): String =
        "$AUTH_URL?client_id=$clientId" +
            "&display=$DISPLAY" +
            "&redirect_uri=$REDIRECT_URI" +
            "&scope=$SCOPE" +
            "&response_type=$RESPONSE_TYPE" +
            "&v=5.199"

    @Suppress("ReturnCount")
    fun handleRedirect(redirectUrl: String): String? {
        // Extract access_token from URL fragment
        // e.g. https://oauth.vk.com/blank.html#access_token=XXX&expires_in=0&user_id=123
        val fragment = redirectUrl.substringAfter("#", "")
        if (fragment.isBlank()) return null

        val params = fragment.split("&").associate { part ->
            val (key, value) = part.split("=", limit = 2)
            key to value
        }

        val token = params["access_token"] ?: return null

        // Store in vault
        tokenVault.store(token.toByteArray(Charsets.UTF_8))
        logger.event(AppEvent.VK_OAUTH_COMPLETED)

        return token
    }

    fun getStoredToken(): String? {
        val bytes = tokenVault.get() ?: return null
        return String(bytes, Charsets.UTF_8)
    }
}
