package com.falahpro.app.auth

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.statement.request
import kotlinx.coroutines.launch

private const val AUTH_TAG = "FalahProAuth"

private fun formatAuthException(e: Exception): String {
    return buildString {
        append(e::class.java.simpleName)
        append(": ")
        append(e.message ?: "(no message)")
        if (e is RestException) {
            append(" | status=")
            append(e.statusCode)
            append(" | error=")
            append(e.error)
            append(" | description=")
            append(e.description ?: "(none)")
            append(" | url=")
            append(e.response.request.url)
        }
        if (e is AuthRestException) {
            append(" | authErrorDescription=")
            append(e.errorDescription)
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onCreateAccount: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val darkTheme = isSystemInDarkTheme()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SupabaseAuthManager.awaitInitialization()
        if (SupabaseAuthManager.isLoggedIn()) {
            // Session already present (e.g. auto-confirm after Sign Up) — no Login toast.
            onLoginSuccess()
        }
    }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 1100, easing = EaseOutCubic),
        label = "loginFadeIn"
    )
    val contentSlide by animateFloatAsState(
        targetValue = if (entered) 0f else 28f,
        animationSpec = tween(durationMillis = 1100, easing = EaseOutCubic),
        label = "loginSlideUp"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "heroFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroFloatOffset"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "buttonPress"
    )

    val titleColor = if (darkTheme) LoginColors.DarkText else LoginColors.EmeraldDeep
    val taglineColor = if (darkTheme) {
        LoginColors.GoldSoft.copy(alpha = 0.72f)
    } else {
        LoginColors.Gold.copy(alpha = 0.82f)
    }
    val subtitleColor = if (darkTheme) {
        LoginColors.DarkMuted.copy(alpha = 0.7f)
    } else {
        LoginColors.TextSecondary.copy(alpha = 0.72f)
    }
    val cardColor = if (darkTheme) LoginColors.DarkCard else Color(0xF7FFFCF0)
    val mutedColor = if (darkTheme) LoginColors.DarkMuted else LoginColors.TextMuted
    val fieldColors = authTextFieldColors(darkTheme)

    fun attemptLogin() {
        val trimmedEmail = email.trim()
        when {
            trimmedEmail.isEmpty() -> {
                Toast.makeText(context, "Email is required", Toast.LENGTH_SHORT).show()
            }
            password.isEmpty() -> {
                Toast.makeText(context, "Password is required", Toast.LENGTH_SHORT).show()
            }
            isSigningIn -> Unit
            else -> {
                isSigningIn = true
                focusManager.clearFocus()
                scope.launch {
                    try {
                        SupabaseAuthManager.signInWithEmail(trimmedEmail, password)
                        isSigningIn = false
                        Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                        onLoginSuccess()
                    } catch (e: Exception) {
                        isSigningIn = false
                        val detail = formatAuthException(e)
                        Log.e(AUTH_TAG, detail, e)
                        Toast.makeText(
                            context,
                            e.message ?: "Login failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // Release 1: Forgot Password entry point hidden (recovery deep links land in Release 2).
    // ForgotPasswordDialog + SupabaseAuthManager.resetPasswordForEmail remain for R2.

    Box(modifier = Modifier.fillMaxSize()) {
        IslamicPatternBackground(
            modifier = Modifier.fillMaxSize(),
            darkTheme = darkTheme
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .alpha(contentAlpha)
                .offset(y = contentSlide.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                AuthHeroBadge(
                    floatOffset = floatOffset,
                    glowPulse = glowPulse,
                    darkTheme = darkTheme
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Falah Pro",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 48.sp
                    ),
                    color = titleColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Built for Every Muslim",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.6.sp
                    ),
                    color = taglineColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    LoginColors.Gold.copy(alpha = 0.75f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Faith. Prayer. Reflection.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.8.sp
                    ),
                    color = subtitleColor,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(30.dp),
                            ambientColor = LoginColors.Forest.copy(alpha = 0.14f),
                            spotColor = LoginColors.Gold.copy(alpha = 0.10f)
                        ),
                    shape = RoundedCornerShape(30.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (darkTheme) 0.12f else 0.65f),
                                LoginColors.GoldSoft.copy(alpha = if (darkTheme) 0.18f else 0.35f),
                                Color.White.copy(alpha = if (darkTheme) 0.06f else 0.25f)
                            )
                        )
                    ),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sign in",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.1.sp
                            ),
                            color = if (darkTheme) LoginColors.DarkText else LoginColors.TextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Your journey of faith begins here",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = mutedColor.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSigningIn,
                            singleLine = true,
                            label = { Text("Email") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSigningIn,
                            singleLine = true,
                            label = { Text("Password") },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Text(
                                        text = if (passwordVisible) "Hide" else "Show",
                                        color = LoginColors.EmeraldMid,
                                        fontSize = 12.sp
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { attemptLogin() }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { attemptLogin() },
                            enabled = !isSigningIn,
                            interactionSource = interactionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .scale(buttonScale)
                                .semantics { contentDescription = "Login" }
                                .shadow(
                                    elevation = if (isPressed) 2.dp else 8.dp,
                                    shape = RoundedCornerShape(18.dp),
                                    ambientColor = Color.Black.copy(alpha = 0.06f),
                                    spotColor = LoginColors.Emerald.copy(alpha = 0.18f)
                                ),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LoginColors.Emerald,
                                contentColor = Color.White,
                                disabledContainerColor = LoginColors.Emerald.copy(alpha = 0.55f),
                                disabledContentColor = Color.White.copy(alpha = 0.8f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp
                            )
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Signing in…",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            } else {
                                Text(
                                    text = "Login",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.15.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = onCreateAccount,
                            enabled = !isSigningIn
                        ) {
                            Text(
                                text = "Create Account",
                                color = if (darkTheme) LoginColors.GoldSoft else LoginColors.EmeraldMid,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "Assalamu Alaikum",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp
                    ),
                    color = (if (darkTheme) LoginColors.GoldSoft else LoginColors.EmeraldMid)
                        .copy(alpha = 0.48f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Password recovery dialog retained for Release 2 (Android deep-link reset).
 * Entry point is hidden on Login in Release 1.
 */
@Suppress("UnusedPrivateMember")
@Composable
private fun ForgotPasswordDialog(
    darkTheme: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resetEmail by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val fieldColors = authTextFieldColors(darkTheme)

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Text(
                text = "Reset Password",
                fontWeight = FontWeight.SemiBold,
                color = if (darkTheme) LoginColors.DarkText else LoginColors.TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter your email and we’ll send a reset link.",
                    color = if (darkTheme) LoginColors.DarkMuted else LoginColors.TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending,
                    singleLine = true,
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSending,
                onClick = {
                    val trimmed = resetEmail.trim()
                    if (trimmed.isEmpty()) {
                        Toast.makeText(context, "Email is required", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    isSending = true
                    scope.launch {
                        try {
                            SupabaseAuthManager.resetPasswordForEmail(trimmed)
                            Toast.makeText(
                                context,
                                "Password reset email sent",
                                Toast.LENGTH_LONG
                            ).show()
                            isSending = false
                            onDismiss()
                        } catch (e: Exception) {
                            isSending = false
                            Log.e(AUTH_TAG, formatAuthException(e), e)
                            Toast.makeText(
                                context,
                                e.message ?: "Could not send reset email",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            ) {
                Text(
                    text = if (isSending) "Sending…" else "Send",
                    color = LoginColors.Emerald,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSending,
                onClick = onDismiss
            ) {
                Text(
                    text = "Cancel",
                    color = if (darkTheme) LoginColors.DarkMuted else LoginColors.TextMuted
                )
            }
        }
    )
}

@Composable
internal fun authTextFieldColors(darkTheme: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = if (darkTheme) LoginColors.DarkText else LoginColors.TextPrimary,
    unfocusedTextColor = if (darkTheme) LoginColors.DarkText else LoginColors.TextPrimary,
    focusedBorderColor = LoginColors.EmeraldMid,
    unfocusedBorderColor = if (darkTheme) {
        Color.White.copy(alpha = 0.18f)
    } else {
        LoginColors.Mist
    },
    focusedLabelColor = LoginColors.EmeraldMid,
    unfocusedLabelColor = if (darkTheme) LoginColors.DarkMuted else LoginColors.TextMuted,
    cursorColor = LoginColors.Emerald,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)
