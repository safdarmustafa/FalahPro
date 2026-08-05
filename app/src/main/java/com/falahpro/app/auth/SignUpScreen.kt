package com.falahpro.app.auth

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val AUTH_TAG = "FalahProAuth"

@Composable
fun SignUpScreen(
    onAccountCreated: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val darkTheme = isSystemInDarkTheme()

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val contentAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = EaseOutCubic),
        label = "signupFadeIn"
    )
    val contentSlide by animateFloatAsState(
        targetValue = if (entered) 0f else 20f,
        animationSpec = tween(durationMillis = 700, easing = EaseOutCubic),
        label = "signupSlideUp"
    )

    val cardColor = if (darkTheme) LoginColors.DarkCard else Color(0xF7FFFCF0)
    val mutedColor = if (darkTheme) LoginColors.DarkMuted else LoginColors.TextMuted
    val fieldColors = authTextFieldColors(darkTheme)

    fun attemptSignUp() {
        val name = fullName.trim()
        val trimmedEmail = email.trim()
        when {
            trimmedEmail.isEmpty() -> {
                Toast.makeText(context, "Email is required", Toast.LENGTH_SHORT).show()
            }
            password.length < 8 -> {
                Toast.makeText(
                    context,
                    "Password must be at least 8 characters",
                    Toast.LENGTH_SHORT
                ).show()
            }
            password != confirmPassword -> {
                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
            }
            isCreating -> Unit
            else -> {
                isCreating = true
                focusManager.clearFocus()
                scope.launch {
                    try {
                        SupabaseAuthManager.signUpWithEmail(
                            fullName = name.ifBlank { trimmedEmail.substringBefore("@") },
                            email = trimmedEmail,
                            password = password
                        )
                        isCreating = false
                        Toast.makeText(
                            context,
                            "Account created successfully.",
                            Toast.LENGTH_SHORT
                        ).show()
                        // With email confirmation disabled, Sign Up creates a session and
                        // AuthGate navigates to Home. Do not route through Login (avoids
                        // showing the Login success toast).
                        if (!SupabaseAuthManager.isLoggedIn()) {
                            onAccountCreated()
                        }
                    } catch (e: Exception) {
                        isCreating = false
                        Log.e(AUTH_TAG, "Sign up failed: ${e.message}", e)
                        Toast.makeText(
                            context,
                            e.message ?: "Could not create account",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

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
                .padding(top = 36.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Create Account",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (darkTheme) LoginColors.DarkText else LoginColors.EmeraldDeep,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Join Falah Pro",
                style = MaterialTheme.typography.bodyMedium,
                color = mutedColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

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
                        .padding(horizontal = 26.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating,
                        singleLine = true,
                        label = { Text("Full Name") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating,
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
                        enabled = !isCreating,
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
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating,
                        singleLine = true,
                        label = { Text("Confirm Password") },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { attemptSignUp() }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = { attemptSignUp() },
                        enabled = !isCreating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LoginColors.Emerald,
                            contentColor = Color.White,
                            disabledContainerColor = LoginColors.Emerald.copy(alpha = 0.55f)
                        )
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Creating…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Create Account", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onBackToLogin,
                        enabled = !isCreating
                    ) {
                        Text(
                            text = "Back to Login",
                            color = if (darkTheme) LoginColors.GoldSoft else LoginColors.EmeraldMid,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
