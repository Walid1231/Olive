package com.waleve.player.presentation.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassSub

/** The permission needed to read music files on this Android version. */
val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/**
 * Live "is music access granted?" state. Re-checked every time the app comes back to the
 * foreground (e.g. after the system dialog or returning from Settings). Calls [onGranted]
 * once whenever access goes from denied → granted, so the caller can scan right away.
 */
@Composable
fun rememberAudioPermissionState(onGranted: () -> Unit): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) granted = hasAudioPermission(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    var wasGranted by remember { mutableStateOf(granted) }
    LaunchedEffect(granted) {
        if (granted && !wasGranted) onGranted()
        wasGranted = granted
    }
    return granted
}

/**
 * Glass card asking for access to music on the phone. Shows nothing when access is granted.
 * If the user chose "Don't ask again", the button opens the app's Settings page instead.
 */
@Composable
fun StoragePermissionCard(onGranted: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val granted = rememberAudioPermissionState(onGranted)
    var blocked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) {
            val act = context.activity()
            blocked = act != null && !act.shouldShowRequestPermissionRationale(audioPermission)
        }
        // when ok, the resume re-check flips the state and onGranted() runs
    }
    if (granted) return

    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), alpha = 0.8f).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(GlassGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Folder, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Allow access to your music", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    if (blocked) "Access was turned off. Open Settings → Permissions → Music and audio → Allow."
                    else "Olive needs permission to find the songs and audiobooks saved on this phone.",
                    color = GlassSub, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (blocked) {
                GradientButton("Open settings", {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }, Modifier.weight(1f), icon = Icons.Filled.Settings, height = 44.dp)
            } else {
                GradientButton("Allow access", { launcher.launch(audioPermission) }, Modifier.weight(1f), height = 44.dp)
            }
        }
    }
}

