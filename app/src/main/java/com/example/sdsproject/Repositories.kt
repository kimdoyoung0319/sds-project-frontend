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
import com.navercorp.nid.NidOAuth
import com.navercorp.nid.oauth.domain.enum.LoginBehavior
import com.navercorp.nid.oauth.util.NidOAuthCallback
import com.navercorp.nid.profile.domain.vo.NidProfile
import com.navercorp.nid.profile.util.NidProfileCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

class NidUserInfoRepository(private val context: Context) : UserInfoRepository {
    private val deferredProfile: Deferred<NidProfile> by lazy {
        CoroutineScope(Dispatchers.IO).async { fetchUserProfile() }
    }

    init {
        NidOAuth.initialize(
            context = context,
            clientId = "mZgJxaEMILClmQ4Z9Z4n",
            clientSecret = "lGnr0KNcb8",
            clientName = "테스트 어플리케이션",
        )
        NidOAuth.behavior = LoginBehavior.DEFAULT
    }

    private suspend fun fetchUserProfile(): NidProfile =
        suspendCoroutine<NidProfile> { continuation ->
            val callback = object : NidProfileCallback<NidProfile> {
                override fun onSuccess(result: NidProfile) {
                    continuation.resume(result)
                }

                override fun onFailure(errorCode: String, errorDesc: String) {
                    continuation.resumeWithException(Exception("$errorCode:$errorDesc"))
                }
            }

            NidOAuth.getUserProfile(callback)
        }

    suspend fun loginViaNaver(): OAuthToken? = suspendCoroutine { continuation ->
        val callback = object : NidOAuthCallback {
            override fun onSuccess() {
                val token = NidOAuth.getAccessToken()

                if (token == null)
                    continuation.resume(null)
                else
                    continuation.resume(OAuthToken(token, OAuthProvider.Naver))
            }

            override fun onFailure(errorCode: String, errorDesc: String) =
                continuation.resume(null)
        }

        NidOAuth.requestLogin(context, callback)
    }

    override suspend fun getUserName(): String = deferredProfile.await().profile.name
    override suspend fun getUserPhoneNumber(): String = deferredProfile.await().profile.mobile
    override suspend fun getUserEmail(): String = deferredProfile.await().profile.email
    override suspend fun getUserProfileImageUri(): String =
        deferredProfile.await().profile.profileImage
}