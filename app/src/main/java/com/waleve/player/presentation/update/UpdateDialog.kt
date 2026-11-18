package com.waleve.player.presentation.update

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.waleve.player.BuildConfig
import com.waleve.player.presentation.components.GlassButton
import com.waleve.player.presentation.components.GlassGradient
import com.waleve.player.presentation.components.GlassProgress
import com.waleve.player.presentation.components.GradientButton
import com.waleve.player.presentation.components.springClick
import com.waleve.player.presentation.theme.GlassAccent
import com.waleve.player.presentation.theme.GlassDanger
import com.waleve.player.presentation.theme.GlassInk
import com.waleve.player.presentation.theme.GlassMuted
import com.waleve.player.presentation.theme.GlassSub
import com.waleve.player.service.AppUpdateChecker
import com.waleve.player.service.DownloadState
import com.waleve.player.service.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

/** Strip the GitHub markdown so the notes read as clean text. */
private fun plainNotes(md: String): String = md.lines()
    .filterNot { it.trim().startsWith(">") }
    .joinToString("\n") { line ->
        line.replace(Regex("^#+\\s*"), "").replace("**", "").replace("`", "")
            .replace(Regex("^\\s*-\\s+"), "•  ").replace(Regex("^(\\s{2,})-\\s+"), "     ◦  ")
    }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

/**
 * "Update available" dialog: notes, size, Download & install with progress, then Android's
 * installer. Handles the one-time "Install unknown apps" permission and reuses a finished download.
 */
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    updateChecker: AppUpdateChecker,
    onDismiss: () -> Unit,
    onLater: () -> Unit = onDismiss,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(updateInfo.version) {
        mutableStateOf<DownloadState>(updateChecker.downloadedFor(updateInfo)?.let { DownloadState.Success(it) } ?: DownloadState.Idle)
    }
    var needsPermission by remember { mutableStateOf(false) }

    fun install(file: File) {
        if (updateChecker.canInstall()) {
            updateChecker.installApk(file)
        } else {
            updateChecker.pendingInstall = file
            needsPermission = true
        }
    }

    // Coming back from Settings with the permission → install right away
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && needsPermission && updateChecker.canInstall()) {
                needsPermission = false
                updateChecker.pendingInstall?.let { updateChecker.installApk(it) }
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val downloading = state is DownloadState.Downloading
    Dialog(onDismissRequest = { if (!downloading) onLater() }, properties = DialogProperties(dismissOnClickOutside = !downloading)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color(0xFFFAF9FD)).padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(GlassGradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Olive ${updateInfo.version} is here", color = GlassInk, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text(
                        "You have ${BuildConfig.VERSION_NAME} · ${Formatter.formatShortFileSize(context, updateInfo.fileSize)}",
                        color = GlassSub, fontSize = 12.5.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (updateInfo.releaseNotes.isNotBlank()) {
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(18.dp))
                        .background(GlassInk.copy(alpha = 0.04f)).verticalScroll(rememberScrollState()).padding(14.dp)
                ) {
                    Text(plainNotes(updateInfo.releaseNotes), color = GlassInk, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(14.dp))
            }

            when (val st = state) {
                is DownloadState.Downloading -> {
                    GlassProgress(st.progress, height = 8.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("Downloading… ${(st.progress * 100).toInt()}%", color = GlassAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Keep Olive open — this takes a minute on mobile data.", color = GlassMuted, fontSize = 12.sp)
                }
                is DownloadState.Error -> {
                    Text("Download failed: ${st.message}", color = GlassDanger, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                }
                else -> {}
            }

            if (needsPermission) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GlassAccent.copy(alpha = 0.1f)).padding(14.dp)) {
                    Text("One-time step", color = GlassAccent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Text("Allow Olive to install updates: tap below, turn on \"Allow from this source\", then come back.", color = GlassInk, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Open settings", { updateChecker.openInstallPermission() }, Modifier.fillMaxWidth(), height = 44.dp)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (!downloading) {
                Spacer(Modifier.height(4.dp))
                when (val st = state) {
                    is DownloadState.Success -> GradientButton("Install now", { install(st.file) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.SystemUpdate, height = 50.dp)
                    else -> GradientButton(
                        if (st is DownloadState.Error) "Try again" else "Download & install",
                        {
                            scope.launch {
                                updateChecker.downloadApk(updateInfo.downloadUrl, updateInfo.version).collect { s ->
                                    state = s
                                    if (s is DownloadState.Success) install(s.file)
                                }
                            }
                        },
                        Modifier.fillMaxWidth(), icon = Icons.Rounded.SystemUpdate, height = 50.dp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Later", onLater, Modifier.weight(1f), tint = GlassSub, height = 44.dp)
                    GlassButton("Skip this version", onDismiss, Modifier.weight(1f), tint = GlassMuted, height = 44.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text("Your songs, playlists and sign-in stay as they are.", color = GlassMuted, fontSize = 11.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
