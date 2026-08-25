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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falahpro.app.ui.theme.FalahColors
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.statement.request
import kotlinx.coroutines.launch

private const val AUTH_TAG = "FalahProAuth"

// ── Exception formatter — UNCHANGED ────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
// LoginScreen — all authentication logic UNCHANGED
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onCreateAccount: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val darkTheme = isSystemInDarkTheme()

    // ── State — UNCHANGED ─────────────────────────────────────────────────────
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SupabaseAuthManager.awaitInitialization()
        if (SupabaseAuthManager.isLoggedIn()) {
            onLoginSuccess()
        }
    }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    // ── Animations — UNCHANGED ────────────────────────────────────────────────
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

    // ── Colors — UNCHANGED ────────────────────────────────────────────────────
    val titleColor = if (darkTheme) LoginColors.DarkText else LoginColors.EmeraldDeep
    val taglineColor = if (darkTheme) {
        LoginColors.GoldSoft.copy(alpha = 0.72f)
    } else {
        LoginColors.Gold.copy(alpha = 0.82f)
    }
    val cardColor = if (darkTheme) LoginColors.DarkCard else LoginColors.Cream
    val mutedColor = if (darkTheme) LoginColors.DarkMuted else LoginColors.TextMuted

    // ── Login action — UNCHANGED ──────────────────────────────────────────────
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

    // ── UI ────────────────────────────────────────────────────────────────────
    // needsScroll uses LocalConfiguration so no BoxWithConstraints is required.
    // screenHeightDp equals the available layout height when the composable is
    // fillMaxSize without any applied padding (same value as BoxWithConstraints.maxHeight
    // would have provided at this level).
    val screenConfig = LocalConfiguration.current
    val needsScroll = screenConfig.screenHeightDp.dp < 720.dp || screenConfig.fontScale > 1.15f

    Box(modifier = Modifier.fillMaxSize()) {
        IslamicPatternBackground(modifier = Modifier.fillMaxSize(), darkTheme = darkTheme)

        Box(modifier = Modifier.fillMaxSize()) {
            val baseModifier = Modifier
                .alpha(contentAlpha)
                .offset(y = contentSlide.dp)
                .padding(horizontal = 24.dp)

            if (needsScroll) {
                // ── Emergency scroll layout (landscape / compact phone / large font) ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .then(baseModifier)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(24.dp))
                    LoginBrandMark(
                        floatOffset = floatOffset,
                        glowPulse = glowPulse,
                        darkTheme = darkTheme,
                        titleColor = titleColor,
                        taglineColor = taglineColor
                    )
                    Spacer(Modifier.height(20.dp))
                    LoginFormCard(
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        passwordVisible = passwordVisible,
                        onPasswordVisibleChange = { passwordVisible = it },
                        isSigningIn = isSigningIn,
                        onAttemptLogin = { attemptLogin() },
                        interactionSource = interactionSource,
                        buttonScale = buttonScale,
                        darkTheme = darkTheme,
                        cardColor = cardColor
                    )
                    Spacer(Modifier.height(14.dp))
                    LoginSignUpRow(
                        onCreateAccount = onCreateAccount,
                        isSigningIn = isSigningIn,
                        mutedColor = mutedColor,
                        darkTheme = darkTheme
                    )
                    Spacer(Modifier.height(12.dp))
                    LoginFooter(darkTheme = darkTheme)
                    Spacer(Modifier.height(20.dp))
                }
            } else {
                // ── Normal layout — no scroll, optically centered ─────────────
                //
                // Two elastic spacers: 35% top / 65% bottom.
                // This places the card slightly below the geometric center,
                // which is the standard optical composition for login screens.
                //
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .then(baseModifier),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top elastic spacer — takes 35% of remaining vertical space
                    Spacer(Modifier.weight(0.35f))

                    // Brand — compact mark above the card
                    LoginBrandMark(
                        floatOffset = floatOffset,
                        glowPulse = glowPulse,
                        darkTheme = darkTheme,
                        titleColor = titleColor,
                        taglineColor = taglineColor
                    )

                    Spacer(Modifier.height(20.dp))

                    // ── LOGIN CARD — primary focal point ─────────────────────
                    LoginFormCard(
                        email = email,
                        onEmailChange = { email = it },
                        password = password,
                        onPasswordChange = { password = it },
                        passwordVisible = passwordVisible,
                        onPasswordVisibleChange = { passwordVisible = it },
                        isSigningIn = isSigningIn,
                        onAttemptLogin = { attemptLogin() },
                        interactionSource = interactionSource,
                        buttonScale = buttonScale,
                        darkTheme = darkTheme,
                        cardColor = cardColor
                    )

                    Spacer(Modifier.height(14.dp))

                    LoginSignUpRow(
                        onCreateAccount = onCreateAccount,
                        isSigningIn = isSigningIn,
                        mutedColor = mutedColor,
                        darkTheme = darkTheme
                    )

                    Spacer(Modifier.height(10.dp))

                    LoginFooter(darkTheme = darkTheme)

                    // Bottom elastic spacer — takes 65% of remaining vertical space
                    Spacer(Modifier.weight(0.65f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Brand mark — compact 96dp hero, animated float preserved
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoginBrandMark(
    floatOffset: Float,
    glowPulse: Float,
    darkTheme: Boolean,
    titleColor: Color,
    taglineColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Compact hero — 96dp. The float animation is preserved.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .offset(y = floatOffset.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                LoginColors.GoldSoft.copy(alpha = glowPulse * 0.72f),
                                LoginColors.EmeraldSoft.copy(alpha = glowPulse * 0.20f),
                                Color.Transparent
                            )
                        )
                    )
            )
            PremiumIslamicHero(
                modifier = Modifier.size(78.dp),
                darkTheme = darkTheme,
                glowPulse = glowPulse
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Falah Pro",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            ),
            color = titleColor,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = "Built for Every Muslim",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.Normal
            ),
            color = taglineColor,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        // Brass accent line
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(1.5.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            LoginColors.Gold.copy(alpha = 0.72f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Login form card — refined paper surface, all field/button logic UNCHANGED
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoginFormCard(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    isSigningIn: Boolean,
    onAttemptLogin: () -> Unit,
    interactionSource: MutableInteractionSource,
    buttonScale: Float,
    darkTheme: Boolean,
    cardColor: Color
) {
    val focusManager = LocalFocusManager.current
    // Login-specific field colors using the full Falah palette
    val fieldColors = loginFieldColors(darkTheme)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (darkTheme) {
                Color.White.copy(alpha = 0.10f)
            } else {
                FalahColors.WarmSand
            }
        ),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Card header ───────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Welcome to Falah Pro",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (darkTheme) LoginColors.DarkText else FalahColors.InkBrown
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Sign in to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (darkTheme) LoginColors.DarkMuted else FalahColors.WarmBrown
                )
            }

            Spacer(Modifier.height(4.dp))

            HorizontalDivider(
                thickness = 1.dp,
                color = if (darkTheme) Color.White.copy(0.07f) else FalahColors.WarmSand.copy(0.6f)
            )

            Spacer(Modifier.height(16.dp))

            // ── Email field — logic UNCHANGED ─────────────────────────────────
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
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
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )

            Spacer(Modifier.height(10.dp))

            // ── Password field — logic UNCHANGED ──────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
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
                    TextButton(
                        onClick = { onPasswordVisibleChange(!passwordVisible) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = if (passwordVisible) "Hide" else "Show",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (darkTheme) LoginColors.EmeraldSoft else FalahColors.OldMoneyGreen
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onAttemptLogin() }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )

            Spacer(Modifier.height(18.dp))

            // ── Sign In button — logic UNCHANGED ─────────────────────────────
            Button(
                onClick = onAttemptLogin,
                enabled = !isSigningIn,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .scale(buttonScale)
                    .semantics { contentDescription = "Login" },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LoginColors.Forest,
                    contentColor = LoginColors.Ivory,
                    disabledContainerColor = LoginColors.Forest.copy(alpha = 0.5f),
                    disabledContentColor = LoginColors.Ivory.copy(alpha = 0.75f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                )
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LoginColors.Ivory
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Signing in…",
                        color = LoginColors.Ivory,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                } else {
                    Text(
                        text = "Sign In",
                        color = LoginColors.Ivory,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sign Up row — callback UNCHANGED
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoginSignUpRow(
    onCreateAccount: () -> Unit,
    isSigningIn: Boolean,
    mutedColor: Color,
    darkTheme: Boolean
) {
    TextButton(
        onClick = onCreateAccount,
        enabled = !isSigningIn,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = mutedColor.copy(alpha = 0.85f), fontSize = 13.sp)) {
                    append("Don't have an account?  ")
                }
                withStyle(
                    SpanStyle(
                        color = if (darkTheme) LoginColors.GoldSoft else FalahColors.OldMoneyGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                ) {
                    append("Sign Up")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Footer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoginFooter(darkTheme: Boolean) {
    Text(
        text = "Assalamu Alaikum",
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
        color = (if (darkTheme) LoginColors.GoldSoft else LoginColors.EmeraldMid).copy(alpha = 0.32f),
        textAlign = TextAlign.Center
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Login-specific field colors — Falah palette for light, shared dark for dark.
// Inlined into a single OutlinedTextFieldDefaults.colors() call so darkTheme
// is evaluated per-parameter and never "always true" in a nested branch.
// Does NOT modify authTextFieldColors (still used by SignUpScreen).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun loginFieldColors(darkTheme: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedTextColor     = if (darkTheme) LoginColors.DarkText else FalahColors.InkBrown,
    unfocusedTextColor   = if (darkTheme) LoginColors.DarkText else FalahColors.InkBrown,
    focusedBorderColor   = if (darkTheme) LoginColors.EmeraldMid else FalahColors.Forest,
    unfocusedBorderColor = if (darkTheme) Color.White.copy(alpha = 0.18f) else FalahColors.WarmSand,
    focusedLabelColor    = if (darkTheme) LoginColors.EmeraldMid else FalahColors.Forest,
    unfocusedLabelColor  = if (darkTheme) LoginColors.DarkMuted else FalahColors.WarmBrown,
    cursorColor          = if (darkTheme) LoginColors.Emerald else FalahColors.Forest,
    focusedContainerColor   = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)

// ─────────────────────────────────────────────────────────────────────────────
// ForgotPasswordDialog — UNCHANGED (Release 2, hidden in R1)
// ─────────────────────────────────────────────────────────────────────────────

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
                    text = "Enter your email and we'll send a reset link.",
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
            TextButton(enabled = !isSending, onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = if (darkTheme) LoginColors.DarkMuted else LoginColors.TextMuted
                )
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// authTextFieldColors — UNCHANGED (also used by SignUpScreen)
// ─────────────────────────────────────────────────────────────────────────────

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
