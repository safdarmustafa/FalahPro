package com.falahpro.app.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falahpro.app.BuildConfig
import com.falahpro.app.WebsiteLinks
import com.falahpro.app.auth.DeleteAccountResult
import com.falahpro.app.auth.SupabaseAuthManager
import com.falahpro.app.ui.theme.FalahColors
import com.falahpro.app.ui.theme.FalahShapes
import com.falahpro.app.ui.theme.FalahSpacing
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.launch

private const val TAG = "FalahProProfile"

// ── Utility functions — UNCHANGED ─────────────────────────────────────────────

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
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
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

// ─────────────────────────────────────────────────────────────────────────────
// ProfileScreen — all Supabase logic UNCHANGED
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<UserInfo?>(null) }

    // ── ALL SUPABASE LOGIC UNCHANGED ─────────────────────────────────────────
    LaunchedEffect(Unit) {
        user = SupabaseAuthManager.currentUser()
    }

    val name = SupabaseAuthManager.displayName(user)
    val email = SupabaseAuthManager.email(user)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    // ── END LOGIC ────────────────────────────────────────────────────────────

    // Delete account dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Account",
                    fontWeight = FontWeight.SemiBold,
                    color = FalahColors.InkBrown
                )
            },
            text = {
                Text(
                    text = "This will permanently delete your Falah Pro account. This action cannot be undone.",
                    color = FalahColors.InkBrown.copy(alpha = 0.75f)
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
                        color = FalahColors.Danger,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel", color = FalahColors.InkBrown.copy(alpha = 0.7f))
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FalahColors.Ivory)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FalahSpacing.screenRegular)
                .padding(bottom = FalahSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(FalahSpacing.xl))

            // ── Profile header ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(4.dp, CircleShape, ambientColor = FalahColors.Forest.copy(0.2f))
                    .clip(CircleShape)
                    .background(FalahColors.Forest)
                    .border(2.dp, FalahColors.Brass.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "F",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = FalahColors.SoftBrass
                )
            }

            Spacer(modifier = Modifier.height(FalahSpacing.md))

            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = FalahColors.Forest,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(FalahSpacing.xxs))

            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = FalahColors.WarmBrown,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(FalahSpacing.xl))

            // ── Account ──────────────────────────────────────────────────────
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
                    titleColor = FalahColors.Danger,
                    showDivider = false,
                    onClick = { showDeleteDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(FalahSpacing.md))

            // ── Legal ─────────────────────────────────────────────────────────
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

            Spacer(modifier = Modifier.height(FalahSpacing.md))

            // ── Support ───────────────────────────────────────────────────────
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

            Spacer(modifier = Modifier.height(FalahSpacing.xl))

            // ── Footer ────────────────────────────────────────────────────────
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = FalahColors.WarmBrown.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(FalahSpacing.xxs))
            Text(
                text = "Built for Every Muslim",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = FalahColors.Brass.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section — Butter Cream card, WarmBrown label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = FalahColors.WarmBrown.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = FalahSpacing.xxs, bottom = FalahSpacing.xs)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FalahShapes.Card)
                .background(FalahColors.ButterCream)
                .border(1.dp, FalahColors.WarmSand, FalahShapes.Card)
        ) {
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Menu item — all click behavior UNCHANGED
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileMenuItem(
    title: String,
    titleColor: Color = FalahColors.InkBrown,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = FalahSpacing.md, vertical = FalahSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = FalahColors.WarmBrown.copy(alpha = 0.6f)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = FalahSpacing.md),
                thickness = 1.dp,
                color = FalahColors.WarmSand
            )
        }
    }
}

