package com.example.sdsproject

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class Token(val value: String)

@Serializable
data class TokenPair(val accessToken: Token, val refreshToken: Token)

class TokenRepository(context: Context) {
    private val accessTokenStore: DataStore<Token?>
    private val refreshTokenStore: DataStore<Token?>

    object TokenSerializer : Serializer<Token?> {
        override val defaultValue = null

        override suspend fun readFrom(input: InputStream): Token =
            Json.decodeFromString(input.readBytes().decodeToString())

        override suspend fun writeTo(t: Token?, output: OutputStream) {
            if (t == null)
                return
            output.write(Json.encodeToString(Token.serializer(), t).encodeToByteArray())
        }
    }

    private fun createTokenDataStore(context: Context, name: String): DataStore<Token?> {
        AeadConfig.register()

        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "keyset", "keyset_prefs")
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri("android-keystore://master_key")
            .build()
            .keysetHandle

        val serializer = AeadSerializer(
            aead = handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java),
            wrappedSerializer = TokenSerializer,
            associatedData = "token.json".encodeToByteArray()
        )

        return DataStoreFactory.create(
            serializer = serializer,
            produceFile = { context.dataDir.resolve("datastore/$name.json") })
    }

    init {
        accessTokenStore = createTokenDataStore(context, "access")
        refreshTokenStore = createTokenDataStore(context, "refresh")
    }

    suspend fun getAccessToken(): Token? = accessTokenStore.data.first()
    suspend fun setAccessToken(token: Token) =
        accessTokenStore.updateData {
            it?.copy(value = token.value) ?: token
        }

    suspend fun getRefreshToken(): Token? = refreshTokenStore.data.first()
    suspend fun setRefreshToken(token: Token) =
        refreshTokenStore.updateData {
            it?.copy(value = token.value) ?: token
        }
}
