package com.falahpro.app.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falahpro.app.BuildConfig
import com.falahpro.app.WebsiteLinks
import com.falahpro.app.auth.DeleteAccountResult
import com.falahpro.app.auth.SupabaseAuthManager
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope

private const val TAG = "FalahProProfile"

private val Gold = Color(0xFFE2C07A)
private val Ink = Color(0xFF1A120F)
private val CardBg = Color.White.copy(alpha = 0.06f)
private val DividerColor = Color.White.copy(alpha = 0.08f)
private val Muted = Color.White.copy(alpha = 0.55f)
private val Danger = Color(0xFFE57373)

private fun openWebsite(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
    }
}

private fun openPlayStoreListing(context: Context) {
    val packageName = "com.falahpro.app"
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            )
        )
    } catch (_: ActivityNotFoundException) {
        openWebsite(
            context,
            "https://play.google.com/store/apps/details?id=$packageName"
        )
    }
}

private fun shareApp(context: Context) {
    val shareText =
        "Check out Falah Pro – Built for Every Muslim.\n" +
            "https://play.google.com/store/apps/details?id=com.falahpro.app"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
fun ProfileScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<UserInfo?>(null) }

    LaunchedEffect(Unit) {
        user = SupabaseAuthManager.currentUser()
    }

    val name = SupabaseAuthManager.displayName(user)
    val email = SupabaseAuthManager.email(user)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A120F),
            Color(0xFF2A1C18),
            Color(0xFF3E2A24)
        )
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Account",
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
            },
            text = {
                Text(
                    text = "This will permanently delete your Falah Pro account. This action cannot be undone.",
                    color = Ink.copy(alpha = 0.75f)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        if (!SupabaseAuthManager.isLoggedIn()) {
                            showDeleteDialog = false
                            onLogout()
                            return@TextButton
                        }
                        isDeleting = true
                        scope.launch {
                            when (SupabaseAuthManager.deleteAccount()) {
                                DeleteAccountResult.NotImplemented -> {
                                    // TODO(SECURE_DELETE): Invoke Edge Function `delete-account`
                                    // with Authorization: Bearer <access_token>. Backend must use
                                    // the Supabase service role to admin.deleteUser(uid).
                                    Log.w(TAG, "Delete account blocked: secure backend endpoint not configured")
                                    Toast.makeText(
                                        context,
                                        "Account deletion is temporarily unavailable. Please contact support.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    isDeleting = false
                                    showDeleteDialog = false
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (isDeleting) "Deleting…" else "Delete",
                        color = Danger,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel", color = Ink.copy(alpha = 0.7f))
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // —— Profile header ——
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Gold),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "F",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Gold
            )

            Text(
                text = email,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // —— Account ——
            ProfileSection(title = "Account") {
                ProfileMenuItem(
                    title = "Sign Out",
                    onClick = {
                        scope.launch {
                            try {
                                SupabaseAuthManager.signOut()
                            } catch (e: Exception) {
                                Log.e(TAG, "Supabase signOut failed: ${e.message}", e)
                            }
                            Toast.makeText(
                                context,
                                "Signed out successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            onLogout()
                        }
                    }
                )
                ProfileMenuItem(
                    title = "Delete Account",
                    titleColor = Danger,
                    showDivider = false,
                    onClick = { showDeleteDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // —— Legal ——
            ProfileSection(title = "Legal") {
                ProfileMenuItem(
                    title = "Privacy Policy",
                    onClick = { openWebsite(context, WebsiteLinks.PRIVACY) }
                )
                ProfileMenuItem(
                    title = "Terms & Conditions",
                    showDivider = false,
                    onClick = { openWebsite(context, WebsiteLinks.TERMS) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // —— Support ——
            ProfileSection(title = "Support") {
                ProfileMenuItem(
                    title = "Contact Support",
                    onClick = { openWebsite(context, WebsiteLinks.CONTACT) }
                )
                ProfileMenuItem(
                    title = "Rate App",
                    onClick = { openPlayStoreListing(context) }
                )
                ProfileMenuItem(
                    title = "Share App",
                    showDivider = false,
                    onClick = { shareApp(context) }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // —— Footer ——
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                fontSize = 13.sp,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Built for Every Muslim",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Gold.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gold.copy(alpha = 0.9f),
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
        ) {
            content()
        }
    }
}

@Composable
private fun ProfileMenuItem(
    title: String,
    titleColor: Color = Color.White,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            Text(
                text = "›",
                fontSize = 20.sp,
                color = Muted
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                thickness = 1.dp,
                color = DividerColor
            )
        }
    }
}
