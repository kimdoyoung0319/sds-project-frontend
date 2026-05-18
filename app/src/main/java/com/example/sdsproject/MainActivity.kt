package com.example.sdsproject

import android.content.Context
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.sdsproject.ui.theme.PostechRed
import com.example.sdsproject.ui.theme.PostechRedLight
import com.example.sdsproject.ui.theme.SDSProjectTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import android.net.Uri
import okhttp3.HttpUrl

class MainActivity : ComponentActivity() {
    private var authResultDeferred: CompletableDeferred<Uri>? = null

    private suspend fun onAuthorize(url: HttpUrl): Uri {
        val deferred = CompletableDeferred<Uri>()

        authResultDeferred = deferred

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(this, url.toString().toUri())

        return deferred.await()
    }

    private fun validateRedirectUrl(redirectUrl: Uri?): Boolean =
        redirectUrl != null
                && redirectUrl.scheme == "com.example.sdsproject"
                && redirectUrl.host == "oauth2callback"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SDSProjectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(this, ::onAuthorize)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val redirectUrl = intent.data

        if (!validateRedirectUrl(redirectUrl))
            return

        authResultDeferred?.complete(redirectUrl!!)
    }
}

sealed class UiState {
    data object Unauthenticated : UiState()
    data class Authenticating(val provider: AuthProvider) : UiState()
    data class Authenticated(val userInfo: UserInfo) : UiState()
    data object Loading : UiState()
    data class Dialog(val body: String, val isSuccess: Boolean) : UiState()
}

@Composable
fun MainScreen(context: Context, onAuthorize: suspend (HttpUrl) -> Uri) {
    var state by remember { mutableStateOf<UiState>(UiState.Unauthenticated) }
    val coroutineScope = rememberCoroutineScope()
    val tokenRepository = remember { TokenRepository(context) }
    val authManager = remember { AuthManager(tokenRepository) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val currentState = state) {
            is UiState.Unauthenticated -> {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NaverLoginButton(onClick = {
                        state = UiState.Authenticating(AuthProvider.Naver)
                    })
                }
            }

            is UiState.Authenticating -> {
                LaunchedEffect(currentState) {
                    coroutineScope.launch {
                        try {
                            authManager.authorize(currentState.provider, onAuthorize)

                            if (authManager.state !is AuthState.Authenticated)
                                throw IllegalStateException()

                            val userInfo =
                                authManager.fetchUserInfo() ?: throw IllegalStateException()

                            state = UiState.Authenticated(userInfo)
                        } catch (_: IllegalStateException) {
                            state = UiState.Dialog("인증에 실패하였습니다.", false)
                        }
                    }
                }
            }

            is UiState.Authenticated -> {
                UserInfoCard(currentState.userInfo)
            }

            is UiState.Loading -> {
                LoadingSpinner()
            }

            is UiState.Dialog -> {
                val (body, isSuccess) = currentState

                ResponseDialog(
                    body = body,
                    isSuccess = isSuccess,
                    onClose = { state = UiState.Unauthenticated }
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
fun UserInfoCard(userInfo: UserInfo) {
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
                    if (userInfo.profileImageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(userInfo.profileImageUrl)
                                .build(),
                            contentDescription = "Profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = userInfo.name,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = userInfo.email,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            letterSpacing = 0.2.sp,
                        )
                        Text(
                            text = userInfo.phoneNumber,
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

