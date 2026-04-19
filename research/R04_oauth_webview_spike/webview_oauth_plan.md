# R04 – VK OAuth Implicit Flow via Compose Desktop WebView

Design spike — no code. Based on LOM v6 Architecture §9.1, §26.

## Goal

Authenticate the user with VK and obtain an `access_token` for VK API 5.199+
using the OAuth 2.0 Implicit Flow, embedded in a Compose Desktop WebView.

## Background

The LOM application is a Compose Desktop (JVM) app. VK's Implicit Flow returns
the token in the URL **fragment** (`#access_token=...`), which means a redirect
to a controlled URI is required and the fragment must be read client-side.

Per §9.1, Implicit Flow is deprecated in OAuth 2.1 but still supported by
VK API 5.199+. Migration to Authorization Code Flow + PKCE is planned for v1.1
(§33).

## Flow

```
┌──────────────────────────────────────┐
│  OAuthScreen (Compose)               │
│  ┌────────────────────────────────┐  │
│  │  WebView (JCEF / KCef)        │  │
│  │                                │  │
│  │  1. Load VK authorize URL:     │  │
│  │     https://oauth.vk.com/      │  │
│  │       authorize?               │  │
│  │       client_id=APP_ID         │  │
│  │       &redirect_uri=           │  │
│  │         https://oauth.vk.com/  │  │
│  │         blank.html             │  │
│  │       &display=page            │  │
│  │       &scope=wall,friends,     │  │
│  │         groups,offline         │  │
│  │       &response_type=token     │  │
│  │       &v=5.199                 │  │
│  │                                │  │
│  │  2. User logs in / approves    │  │
│  │                                │  │
│  │  3. VK redirects to:           │  │
│  │     https://oauth.vk.com/      │  │
│  │       blank.html               │  │
│  │       #access_token=TOKEN      │  │
│  │       &expires_in=0            │  │
│  │       &user_id=12345           │  │
│  │                                │  │
│  └────────────────────────────────┘  │
│                                      │
│  4. Navigation listener detects      │
│     redirect_uri in URL.             │
│  5. Parse fragment → extract token.  │
│  6. Pass token to TokenVault.        │
│  7. Close WebView, proceed.          │
└──────────────────────────────────────┘
```

## Capturing the token from the URL fragment

The key challenge is that the `#fragment` part of the URL is **not** sent to
any server — it only exists in the browser. In a desktop WebView we capture it
via a navigation/URL-change listener.

### Approach: JCEF / KCef navigation handler

1. **Register a `CefLoadHandler` or equivalent** that fires on every
   navigation / URL change.
2. On each callback, check whether the current URL starts with the
   `redirect_uri` (`https://oauth.vk.com/blank.html`).
3. If it does, extract everything after `#` and parse it as
   `application/x-www-form-urlencoded` key-value pairs.
4. Extract `access_token`, `expires_in`, `user_id`.
5. Immediately pass the token to `TokenVault` for AES-GCM encryption (§26.2).
6. Clear the WebView session/cookies to avoid leaking the token.
7. Dismiss the `OAuthScreen` and signal success to the caller.

### Fragment parsing (pseudocode)

```
val fragment = url.substringAfter("#", "")
val params = fragment.split("&").associate {
    val (k, v) = it.split("=", limit = 2)
    k to v
}
val accessToken = params["access_token"] ?: error("No token in redirect")
val expiresIn   = params["expires_in"]?.toLongOrNull() ?: 0L
val userId      = params["user_id"]?.toLongOrNull()
```

## Security considerations

| Concern | Mitigation |
|---------|-----------|
| Token visible in WebView URL bar | Compose Desktop WebView has no user-visible URL bar by default |
| Token in memory | Encrypt immediately via `TokenVault`; zero-fill plaintext array after use (§26.2) |
| Token on disk | Stored only as AES-GCM ciphertext in `token_vault.bin`; key derived from master password via PBKDF2 ≥100k iterations |
| WebView cookie/session leak | Clear cookies and browsing data after token capture |
| MITM | HTTPS enforced; JCEF uses Chromium's TLS stack |
| Token replay | `offline` scope gives a non-expiring token; revocation only via VK settings |

## Required scopes

Per §9.1 (VK API methods used by LOM):

- `wall` – `wall.get`, `wall.search`, `wall.getComments`
- `friends` – for discovery graph expansion
- `groups` – `groups.getById`, `groups.getMembers`
- `offline` – non-expiring token (no refresh flow needed)

## WebView library options for Compose Desktop

| Library | Pros | Cons |
|---------|------|------|
| **KCef** (Kotlin CEF wrapper) | Compose-friendly API; actively maintained | Bundles ~200 MB Chromium; platform-specific binaries |
| **JCEF** (JetBrains CEF fork) | Used in IntelliJ; proven | Lower-level API; requires manual Compose bridge |
| **JavaFX WebView** | Lightweight (~5 MB) | WebKit-based; fragment interception less reliable; extra JavaFX dependency |

**Recommendation:** KCef — best balance of Compose integration and reliability
for fragment-based token capture.

## Error handling

- **User cancels login:** detect navigation to `https://oauth.vk.com/blank.html#error=access_denied` → show user-friendly message, allow retry.
- **Network failure:** WebView shows Chromium error page → detect via `onLoadError` callback → offer retry or offline mode.
- **Token not found in fragment:** log warning via `AuditLog`, show error dialog, allow retry.

## Open questions for Sprint 2

1. Exact KCef version and Gradle dependency coordinates.
2. Whether `redirect_uri` must be registered in the VK app settings or if
   `https://oauth.vk.com/blank.html` works universally.
3. Cookie/session clearing API in KCef.
