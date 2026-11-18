package com.waleve.player.presentation.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.presentation.theme.LocalGhibliColors

/**
 * Full-screen Google Sign-In screen, shown when unauthenticated user taps Friends tab.
 * Premium, dark-themed design with animated gradient, feature highlights, and one-tap sign-in.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    isNightMode: Boolean,
    onSignInSuccess: () -> Unit,
) {
    val gc = LocalGhibliColors.current
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var errorText by remember { mutableStateOf<String?>(null) }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthUiEvent.SignInSuccess -> { errorText = null; onSignInSuccess() }
                is AuthUiEvent.Error -> errorText = event.message
                else -> {}
            }
        }
    }

    // Animated gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "authBg")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradientOffset",
    )

    // Always use a dark background regardless of day/night mode
    // so that light-colored text is always readable.
    val authBgDark = Color(0xFF0A1628)
    val authBgMid  = Color(0xFF0D1B2A)
    val authTextPrimary   = Color(0xFFE0EAFF)
    val authTextSecondary = Color(0xFFA0B4CC)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        authBgDark,
                        authBgMid,
                        authBgDark.copy(alpha = 0.95f),
                    ),
                    startY = gradientOffset * 200f,
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 80.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ─── Top: Logo & Title ───────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Pulsing music icon
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulse",
                )
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(gc.accent, gc.accentLight)
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    "Connect with Friends",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = authTextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Sign in to share playlists, discover\nnew music, and connect with friends.",
                    fontSize = 15.sp,
                    color = authTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            // ─── Middle: Feature highlights ──────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FeatureRow(
                    icon = Icons.Rounded.People,
                    title = "Find Friends",
                    description = "Search by name, email, or unique Friend Code",
                    accentColor = gc.accent,
                    textColor = authTextPrimary,
                    subtextColor = authTextSecondary,
                )
                FeatureRow(
                    icon = Icons.Rounded.Share,
                    title = "Share Playlists",
                    description = "Send your playlists to friends instantly",
                    accentColor = gc.accentLight,
                    textColor = authTextPrimary,
                    subtextColor = authTextSecondary,
                )
                FeatureRow(
                    icon = Icons.Rounded.MusicNote,
                    title = "Discover Music",
                    description = "Import friends' playlists and download songs",
                    accentColor = Color(0xFF10B981),
                    textColor = authTextPrimary,
                    subtextColor = authTextSecondary,
                )
            }

            // ─── Bottom: Sign-In button ──────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        errorText = null
                        // Credential Manager needs an Activity context, not the application context
                        viewModel.signInWithGoogle(context.findActivity() ?: context)
                    },
                    enabled = !isSigningIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1F1F1F),
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF1F1F1F),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Signing in…", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF1F1F1F))
                    } else {
                        // Google "G" colored text
                        Text(
                            "G",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF4285F4),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Sign in with Google",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color(0xFF1F1F1F),
                        )
                    }
                }

                errorText?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "⚠ $err",
                        fontSize = 13.sp,
                        color = Color(0xFFFF8A80),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33FF5252))
                            .padding(12.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Your music library works offline.\nSign in is only needed for social features.",
                    fontSize = 12.sp,
                    color = authTextSecondary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    textColor: Color,
    subtextColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
        }
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = textColor)
            Text(description, fontSize = 12.sp, color = subtextColor, lineHeight = 16.sp)
        }
    }
}


/** Walks the ContextWrapper chain to the hosting Activity (Credential Manager needs it to show its sheet). */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
