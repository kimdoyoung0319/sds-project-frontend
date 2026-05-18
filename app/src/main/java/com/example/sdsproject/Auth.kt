package com.example.sdsproject

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

object HttpUrlSerializer : KSerializer<HttpUrl> {
    override val descriptor = PrimitiveSerialDescriptor("HttpUrl", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): HttpUrl = decoder.decodeString().toHttpUrl()
    override fun serialize(encoder: Encoder, value: HttpUrl) =
        encoder.encodeString(value.toString())
}

@Serializable
data class UserInfo(
    val name: String,
    val phoneNumber: String,
    val email: String,
    @Serializable(with = HttpUrlSerializer::class)
    val profileImageUrl: HttpUrl?
)

@Serializable
private data class TokenResponse(
    val id: String,
    val accessToken: String,
    val refreshToken: String,
)

sealed class AuthProvider(
    val url: HttpUrl,
    val clientId: String
) {
    object Naver : AuthProvider(
        url = "https://nid.naver.com/oauth2.0/authorize".toHttpUrl(),
        clientId = "mZgJxaEMILClmQ4Z9Z4n"
    )

    override fun toString(): String = when (this) {
        Naver -> "naver"
    }
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Authenticated(val provider: AuthProvider, val id: String) : AuthState()
    object Error : AuthState()
}

class AuthManager(private val tokenRepository: TokenRepository) {
    companion object {
        private val BACKEND_URL = "http://10.0.2.2:3000".toHttpUrl()

        // Must match BACKEND_CALLBACK_URL in the backend's .env.
        const val OAUTH_CALLBACK_URL = "https://spelling-unwatched-skewer.ngrok-free.dev/auth/callback"
    }

    var state: AuthState = AuthState.Unauthenticated
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(10.seconds.toJavaDuration())
        .readTimeout(10.seconds.toJavaDuration())
        .addInterceptor { chain ->
            val token = runBlocking { tokenRepository.getAccessToken() }
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer ${token?.value}")
                .build()

            chain.proceed(request)
        }
        .authenticator { _, response ->
            runBlocking {
                val refreshToken = tokenRepository.getRefreshToken() ?: return@runBlocking null
                val newAccessToken = refreshAccessToken(refreshToken) ?: return@runBlocking null
                tokenRepository.setAccessToken(newAccessToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newAccessToken.value}")
                    .build()
            }
        }
        .build()

    private fun refreshAccessToken(refreshToken: Token): Token? {
        val request = Request.Builder()
            .url(BACKEND_URL.newBuilder().addPathSegments("auth/refresh").build())
            .post(
                buildJsonObject {
                    put("refreshToken", refreshToken.value)
                }.toString().toRequestBody("application/json".toMediaType())
            )
            .build()

        val tokens = client.newCall(request).execute().decode<TokenResponse>()

        return tokens?.let { Token(it.accessToken) }
    }

    private fun generateNonce(length: Int = 32): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return Base64.UrlSafe.encode(bytes).trimEnd('=')
    }

    private fun buildAuthUrl(provider: AuthProvider): Pair<HttpUrl, String> {
        if (state !is AuthState.Unauthenticated)
            throw IllegalStateException()

        val nonce = generateNonce()
        val url = provider.url.newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", provider.clientId)
            .addQueryParameter("redirect_uri", OAUTH_CALLBACK_URL)
            .addQueryParameter("state", nonce)
            .build()

        return Pair(url, nonce)
    }

    // Parses the JWT tokens the backend embedded in the final custom-scheme redirect.
    private fun parseTokensFromRedirect(redirectUrl: Uri, expectedNonce: String): TokenResponse? {
        val nonce = redirectUrl.getQueryParameter("state")
        val id = redirectUrl.getQueryParameter("id")
        val accessToken = redirectUrl.getQueryParameter("accessToken")
        val refreshToken = redirectUrl.getQueryParameter("refreshToken")

        if (nonce != expectedNonce || id == null || accessToken == null || refreshToken == null)
            return null

        return TokenResponse(id, accessToken, refreshToken)
    }

    private inline fun <reified T> Response.decode(): T? = use {
        if (!isSuccessful)
            return null

        return try {
            body?.string()?.let { Json.decodeFromString<T>(it) }
        } catch (e: Exception) {
            null
        }
    }

    // Opens the provider's authorization page via [block], then reads the JWT tokens
    // the backend embeds in the final redirect back to the custom scheme.
    suspend fun authorize(provider: AuthProvider, block: suspend (HttpUrl) -> Uri) {
        if (state !is AuthState.Unauthenticated)
            throw IllegalStateException()

        val (authUrl, nonce) = buildAuthUrl(provider)
        val redirectUri = block(authUrl)
        val tokens = parseTokensFromRedirect(redirectUri, nonce)

        if (tokens == null) {
            state = AuthState.Error
            return
        }

        tokenRepository.setAccessToken(Token(tokens.accessToken))
        tokenRepository.setRefreshToken(Token(tokens.refreshToken))
        state = AuthState.Authenticated(provider, tokens.id)
    }

    suspend fun fetchUserInfo(): UserInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BACKEND_URL.newBuilder().addPathSegments("me").build())
            .get()
            .build()

        client.newCall(request).execute().decode<UserInfo>()
    }

    fun reset() {
        state = AuthState.Unauthenticated
    }
}
