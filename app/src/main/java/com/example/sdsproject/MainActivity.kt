package com.example.sdsproject

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.datastore.tink.AeadSerializer
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.sdsproject.ui.theme.PostechRed
import com.example.sdsproject.ui.theme.PostechRedLight
import com.example.sdsproject.ui.theme.SDSProjectTheme
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SDSProjectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(this)
                }
            }
        }
    }
}

sealed class UiState {
    data object Unauthenticated : UiState()
    data object Authenticated : UiState()
    data object Loading : UiState()
    data class Dialog(val body: String, val isSuccess: Boolean) : UiState()
}

interface UserInfoRepository {
    suspend fun getUserName(): String
    suspend fun getUserEmail(): String
    suspend fun getUserPhoneNumber(): String
    suspend fun getUserProfileImageUri(): String
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

    suspend fun loginViaNaver(): Pair<Boolean, String> = suspendCoroutine { continuation ->
        val callback = object : NidOAuthCallback {
            override fun onSuccess() =
                continuation.resume(Pair(true, "네이버를 통한 로그인에 성공하였습니다."))

            override fun onFailure(errorCode: String, errorDesc: String) =
                continuation.resume(
                    Pair(false, "네이버를 통한 로그인에 실패하였습니다: $errorDesc")
                )
        }

        NidOAuth.requestLogin(context, callback)
    }

    override suspend fun getUserName(): String = deferredProfile.await().profile.name
    override suspend fun getUserPhoneNumber(): String = deferredProfile.await().profile.mobile
    override suspend fun getUserEmail(): String = deferredProfile.await().profile.email
    override suspend fun getUserProfileImageUri(): String =
        deferredProfile.await().profile.profileImage
}

@Serializable
data class Token(val token: String? = null)

object TokenSerializer : Serializer<Token> {
    override val defaultValue = Token()

    override suspend fun readFrom(input: InputStream): Token =
        Json.decodeFromString(input.readBytes().decodeToString())

    override suspend fun writeTo(t: Token, output: OutputStream) =
        output.write(Json.encodeToString(Token.serializer(), t).encodeToByteArray())
}

fun createTokenDataStore(context: Context): DataStore<Token> {
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
        produceFile = { context.dataDir.resolve("datastore/token.json") })
}

class TokenRepository(private val dataStore: DataStore<Token>) {
    val token: Flow<String?> = dataStore.data.map { it.token }

    suspend fun saveToken(token: String) {
        dataStore.updateData { it.copy(token = token) }
    }

    suspend fun clearToken() {
        dataStore.updateData { it.copy(token = null) }
    }
}

@Composable
fun MainScreen(context: Context) {
    var state by remember { mutableStateOf<UiState>(UiState.Unauthenticated) }
    val coroutineScope = rememberCoroutineScope()
    val userInfoRepository = remember { NidUserInfoRepository(context) }
    val tokenRepository = remember { TokenRepository(createTokenDataStore(context)) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val currentState = state) {
            is UiState.Unauthenticated -> {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NaverLoginButton(onClick = {
                        state = UiState.Loading
                        coroutineScope.launch {
                            val (isSuccess, message) = userInfoRepository.loginViaNaver()

                            if (!isSuccess) {
                                state = UiState.Dialog(message, false)
                                return@launch
                            }

                            val token = fetchToken()

                            if (token == null) {
                                state = UiState.Dialog(
                                    "JWT를 가져오는데 실패하였습니다.", false
                                )
                                return@launch
                            }

                            tokenRepository.saveToken(token)
                            state = UiState.Dialog(token, true)
                        }
                    })
                }
            }

            is UiState.Authenticated -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UserInfoCard(userInfoRepository)
                    SendRequestButton {
                        state = UiState.Loading
                        coroutineScope.launch {
                            val token = tokenRepository.token.first()

                            if (token == null) {
                                state = UiState.Dialog("발급받은 JWT가 존재하지 않습니다.", false)
                                return@launch
                            }

                            val message = verifyToken(token)

                            if (message == null) {
                                state = UiState.Dialog("서버로부터 JWT를 검증하는데 실패하였습니다.", false)
                                return@launch
                            }

                            state = UiState.Dialog(message, true)
                        }
                    }
                }
            }

            is UiState.Loading -> {
                LoadingSpinner()
            }

            is UiState.Dialog -> {
                val (body, isSuccess) = currentState

                ResponseDialog(
                    body = body,
                    isSuccess = isSuccess,
                    onClose = { state = UiState.Authenticated }
                )
            }
        }
    }
}

@Composable
fun SendRequestButton(onClick: () -> Unit) {
    val indigo = Color(0xFF3F51B5)

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = indigo),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .height(56.dp)
            .width(220.dp)
    ) {
        Text(
            text = "요청 보내기",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview
@Composable
fun SendRequestButtonPreview() {
    SendRequestButton(onClick = {})
}

@Composable
fun NaverLoginButton(onClick: () -> Unit) {
    val naverGreen = Color(0xFF03A94D)

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = naverGreen),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .height(56.dp)
            .width(220.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.naver_login),
            contentDescription = "네이버 로그인 버튼",
            modifier = Modifier.fillMaxHeight(),
            contentScale = ContentScale.FillHeight,
        )
    }
}

@Preview
@Composable
fun NaverLoginButtonPreview() {
    NaverLoginButton(onClick = {})
}

@Composable
fun LoadingSpinner() {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val indigo = Color(0xFF3F51B5)
    val indigoLight = Color(0xFFC5CAE9)

    Box(
        modifier = Modifier
            .size(64.dp)
            .rotate(rotation)
            .drawBehind {
                // Background track
                drawArc(
                    color = indigoLight,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
                // Spinning arc
                drawArc(
                    color = indigo,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
    )
}

@Composable
fun ResponseDialog(body: String, isSuccess: Boolean, onClose: () -> Unit) {
    val successBg = Color(0xFFDCEDC8) // light green
    val errorBg = Color(0xFFFFCDD2)   // light red
    val successIcon = Color(0xFF4CAF50) // green
    val errorIcon = Color(0xFFF44336)   // red

    val bgColor = if (isSuccess) successBg else errorBg
    val iconColor = if (isSuccess) successIcon else errorIcon
    val iconText = if (isSuccess) "\u2713" else "\u2717"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Response box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .padding(16.dp)
            ) {
                Column {
                    // Icon row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(iconColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = iconText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Text(
                        text = body,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF333333),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Close button
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF333333)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .width(160.dp)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFBDBDBD),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Text(
                    text = "Close",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun UserInfoCard(repository: UserInfoRepository) {
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var userPhoneNumber by remember { mutableStateOf("") }
    var userProfileImageUri by remember { mutableStateOf("") }

    LaunchedEffect(repository) {
        userName = repository.getUserName()
        userEmail = repository.getUserEmail()
        userPhoneNumber = repository.getUserPhoneNumber()
        userProfileImageUri = repository.getUserProfileImageUri()
    }

    Box(contentAlignment = Alignment.Center) {
        ElevatedCard(
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.6f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(PostechRed, PostechRedLight))),
                contentAlignment = Alignment.TopStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(userProfileImageUri)
                            .build(),
                        contentDescription = "Profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = userName,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = userEmail,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            letterSpacing = 0.2.sp,
                        )
                        Text(
                            text = userPhoneNumber,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            letterSpacing = 0.2.sp,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun UserInfoCardPreview() {
    val repository = object : UserInfoRepository {
        override suspend fun getUserName(): String = "홍길동"
        override suspend fun getUserEmail(): String = "foo@bar.com"
        override suspend fun getUserPhoneNumber(): String = "010-1234-5678"
        override suspend fun getUserProfileImageUri(): String = ""
    }
    UserInfoCard(repository)
}

suspend fun fetchToken(): String? {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("http://10.0.2.2:3000/auth/login")
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = Json.parseToJsonElement(body).jsonObject

            json["token"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }
}

suspend fun verifyToken(token: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("http://10.0.2.2:3000/me")
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = Json.parseToJsonElement(body).jsonObject

            json["message"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }
}

