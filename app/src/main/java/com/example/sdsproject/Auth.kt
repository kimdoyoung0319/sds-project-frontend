package com.example.sdsproject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Serializable
sealed class OAuthProvider {
    object Naver : OAuthProvider()

    override fun toString(): String = when (this) {
        Naver -> "naver"
    }
}

@Serializable
data class OAuthToken(val value: String, val provider: OAuthProvider)

@Serializable
data class Token(val value: String)

suspend fun fetchToken(token: OAuthToken): Pair<Token, Token>? =
    withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val requestBody = buildJsonObject {
                put("oauthProvider", token.provider.toString())
                put("accessToken", token.value)
            }

            val request = Request.Builder()
                .url("http://10.0.2.2:3000/auth/login")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val body = client.newCall(request).execute().body?.string() ?: ""
            val json = Json.parseToJsonElement(body).jsonObject

            val accessToken = json["accessToken"]?.jsonPrimitive?.content
            val refreshToken = json["refreshToken"]?.jsonPrimitive?.content

            if (accessToken == null || refreshToken == null)
                null
            else
                Pair(Token(accessToken), Token(refreshToken))
        } catch (_: Exception) {
            null
        }
    }

suspend fun verifyToken(accessToken: Token): String? =
    withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("http://10.0.2.2:3000/me")
                .get()
                .header("Authorization", "Bearer ${accessToken.value}")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = Json.parseToJsonElement(body).jsonObject

            json["message"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }