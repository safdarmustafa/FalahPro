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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.falahpro.app.BuildConfig
import com.falahpro.app.R
import com.falahpro.app.WebsiteLinks
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

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

private fun googleSignInClient(context: Context) =
    GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    )

private fun signOutLocalSessions(context: Context, onComplete: () -> Unit) {
    val client = googleSignInClient(context)
    client.signOut().addOnCompleteListener {
        FirebaseAuth.getInstance().signOut()
        onComplete()
    }
}

/**
 * Firebase requires a recent login before [FirebaseUser.delete].
 * Reauthenticate with the current Google session (silent), then delete.
 */
private fun deleteAccountWithGoogleReauth(
    context: Context,
    user: FirebaseUser,
    onSuccess: () -> Unit,
    onFinished: () -> Unit
) {
    val client = googleSignInClient(context)

    fun proceedWithIdToken(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        user.reauthenticate(credential)
            .addOnCompleteListener { reauthTask ->
                if (!reauthTask.isSuccessful) {
                    val err = reauthTask.exception
                    Log.e(TAG, "Delete account reauth failed: ${err?.message}", err)
                    Toast.makeText(
                        context,
                        "Could not verify account: ${err?.localizedMessage ?: "unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                    onFinished()
                    return@addOnCompleteListener
                }

                user.delete()
                    .addOnCompleteListener { deleteTask ->
                        if (deleteTask.isSuccessful) {
                            client.signOut().addOnCompleteListener {
                                FirebaseAuth.getInstance().signOut()
                                Toast.makeText(
                                    context,
                                    "Account deleted successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSuccess()
                                onFinished()
                            }
                        } else {
                            val err = deleteTask.exception
                            Log.e(TAG, "Delete account failed: ${err?.message}", err)
                            Toast.makeText(
                                context,
                                "Could not delete account: ${err?.localizedMessage ?: "unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                            onFinished()
                        }
                    }
            }
    }

    client.silentSignIn()
        .addOnCompleteListener { silentTask ->
            val account = try {
                silentTask.getResult(ApiException::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "silentSignIn failed, trying last signed-in account: ${e.message}")
                GoogleSignIn.getLastSignedInAccount(context)
            }

            val idToken = account?.idToken
            if (idToken.isNullOrBlank()) {
                val err = silentTask.exception
                Log.e(TAG, "Delete account failed: no Google idToken. ${err?.message}", err)
                Toast.makeText(
                    context,
                    "Could not delete account: Google session expired. Please sign in again.",
                    Toast.LENGTH_LONG
                ).show()
                onFinished()
                return@addOnCompleteListener
            }

            proceedWithIdToken(idToken)
        }
}

@Composable
fun ProfileScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser

    val name = user?.displayName ?: "Falah Pro User"
    val email = user?.email ?: "No Email"
    val photoUrl = user?.photoUrl

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
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser == null) {
                            showDeleteDialog = false
                            onLogout()
                            return@TextButton
                        }
                        isDeleting = true
                        deleteAccountWithGoogleReauth(
                            context = context,
                            user = currentUser,
                            onSuccess = { onLogout() },
                            onFinished = {
                                isDeleting = false
                                showDeleteDialog = false
                            }
                        )
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

            // —— Existing profile header ——
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Gold),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.first().toString(),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                }
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
                        signOutLocalSessions(context) {
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
