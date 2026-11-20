package com.waleve.player.presentation.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.mutableLongStateOf
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

private const val PERM_PREFS = "olive_permissions"
private const val KEY_ASKED_AUDIO = "asked_audio"

/** Remember that Android has asked for music access once (needed to tell "never asked" from "blocked"). */
fun markAudioPermissionAsked(context: Context) {
    context.getSharedPreferences(PERM_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED_AUDIO, true).apply()
}

private fun audioPermissionAsked(context: Context): Boolean =
    context.getSharedPreferences(PERM_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED_AUDIO, false)

/** Olive's page in Android Settings, where Permissions → Music and audio can be turned on. */
fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
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
 * When Android won't show its permission dialog (turned off earlier, or the request gets
 * dropped), the button takes you to Olive's page in Settings instead of doing nothing.
 */
@Composable
fun StoragePermissionCard(onGranted: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val granted = rememberAudioPermissionState(onGranted)
    val act = remember(context) { context.activity() }
    // Asked before and Android won't ask again → only Settings can turn it on
    var blocked by remember {
        mutableStateOf(audioPermissionAsked(context) && act != null && !act.shouldShowRequestPermissionRationale(audioPermission))
    }
    var askedAt by remember { mutableLongStateOf(0L) }
    var waiting by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }   // Android's dialog covers the app → we get paused

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) paused = true }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun goToSettings() {
        blocked = true
        Toast.makeText(context, "Tap Permissions → Music and audio → Allow", Toast.LENGTH_LONG).show()
        openAppSettings(context)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        waiting = false
        markAudioPermissionAsked(context)
        if (!ok) {
            // Answered "no" without any dialog on screen = Android refused by itself
            val noDialog = !paused && SystemClock.elapsedRealtime() - askedAt < 1_500
            val cantAskAgain = act == null || !act.shouldShowRequestPermissionRationale(audioPermission)
            if (noDialog) goToSettings() else if (cantAskAgain) blocked = true
        }
        // when ok, the resume re-check flips the state and onGranted() runs
    }

    // Some phones drop the request without an answer: don't leave the button dead
    LaunchedEffect(waiting) {
        if (waiting) {
            kotlinx.coroutines.delay(1_500)
            if (waiting && !paused) { waiting = false; goToSettings() }
        }
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
                    if (blocked) "Access is off. Open Settings → Permissions → Music and audio → Allow."
                    else "Olive needs permission to find the songs and audiobooks saved on this phone.",
                    color = GlassSub, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (blocked) {
                GradientButton("Open settings", { openAppSettings(context) }, Modifier.weight(1f), icon = Icons.Filled.Settings, height = 44.dp)
            } else {
                GradientButton("Allow access", {
                    paused = false
                    askedAt = SystemClock.elapsedRealtime()
                    waiting = true
                    launcher.launch(audioPermission)
                }, Modifier.weight(1f), height = 44.dp)
            }
        }
    }
}
