package com.waleve.player.presentation.auth

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waleve.player.presentation.components.GlassGradient
import com.waleve.player.presentation.components.glass
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub

/** Sign in (Figma "08 Sign in"): one glass card, one "Continue with Google" button, clear errors. */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    isNightMode: Boolean,
    onSignInSuccess: () -> Unit,
) {
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthUiEvent.SignInSuccess -> { errorText = null; onSignInSuccess() }
                is AuthUiEvent.Error -> errorText = event.message
                else -> {}
            }
        }
    }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp), alpha = 0.65f, elevation = 12.dp).padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(84.dp).shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = GlassAccent, spotColor = GlassAccent)
                    .clip(RoundedCornerShape(26.dp)).background(GlassGradient),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("Welcome to Olive", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in with Gmail to make a profile, add friends and share songs & audiobooks.",
                color = GlassSub, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Feature(Icons.Rounded.Group, "Add friends with a code")
                Feature(Icons.Rounded.Share, "Share songs & audiobooks")
                Feature(Icons.Rounded.PhotoCamera, "Profile with your own photo")
            }
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth().height(54.dp)
                    .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x1423305A), spotColor = Color(0x1423305A))
                    .clip(RoundedCornerShape(20.dp)).background(com.waleve.player.presentation.theme.GlassMenu)
                    .border(1.dp, GlassInk.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .clickable(enabled = !isSigningIn) {
                        errorText = null
                        // Credential Manager needs an Activity context, not the application context
                        viewModel.signInWithGoogle(context.findActivity() ?: context)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = GlassAccent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Signing in…", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                } else {
                    GoogleG()
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
            errorText?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text(
                    "⚠ $err", color = GlassDanger, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GlassDanger.copy(alpha = 0.08f)).padding(12.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("Your music works offline without signing in.", color = GlassMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(GlassAccent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = GlassAccent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = GlassInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Google "G" drawn with four arcs (no image asset needed). */
@Composable
private fun GoogleG() {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.2f)
        val inset = stroke.width / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(Color(0xFFEA4335), 200f, 110f, false, tl, arcSize, style = stroke)
        drawArc(Color(0xFFFBBC05), 140f, 60f, false, tl, arcSize, style = stroke)
        drawArc(Color(0xFF34A853), 40f, 100f, false, tl, arcSize, style = stroke)
        drawArc(Color(0xFF4285F4), -40f, 80f, false, tl, arcSize, style = stroke)
        drawLine(Color(0xFF4285F4), androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width - inset, size.height / 2), strokeWidth = stroke.width)
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
