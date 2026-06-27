package com.waleve.player.presentation.update

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waleve.player.presentation.theme.GhibliColors
import com.waleve.player.presentation.theme.LocalGhibliColors
import com.waleve.player.service.AppUpdateChecker
import com.waleve.player.service.DownloadState
import com.waleve.player.service.UpdateInfo
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    updateChecker: AppUpdateChecker,
    onDismiss: () -> Unit
) {
    val gc = LocalGhibliColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    
    AlertDialog(
        onDismissRequest = { 
            // Don't allow dismiss while downloading
            if (downloadState !is DownloadState.Downloading) {
                onDismiss()
            }
        },
        containerColor = gc.surface,
        titleContentColor = gc.textPrimary,
        textContentColor = gc.textSecondary,
        title = {
            Text(
                text = "New Update Available!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Version ${updateInfo.version} is ready to install.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = gc.textPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Size: ${Formatter.formatShortFileSize(context, updateInfo.fileSize)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = "What's New:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = gc.textPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Surface(
                        color = gc.surfaceCard,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                when (val state = downloadState) {
                    is DownloadState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Downloading... ${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = gc.accent,
                                trackColor = gc.surfaceCard
                            )
                        }
                    }
                    is DownloadState.Error -> {
                        Text(
                            text = "Download failed: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is DownloadState.Success -> {
                        Text(
                            text = "Download complete! Starting installer...",
                            color = gc.accent,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    DownloadState.Idle -> {
                        // Show nothing extra
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (downloadState is DownloadState.Idle || downloadState is DownloadState.Error) {
                        coroutineScope.launch {
                            updateChecker.downloadApk(updateInfo.downloadUrl)
                                .collect { state ->
                                    downloadState = state
                                    if (state is DownloadState.Success) {
                                        updateChecker.installApk(state.file)
                                        onDismiss()
                                    }
                                }
                        }
                    }
                },
                enabled = downloadState !is DownloadState.Downloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = gc.accent,
                    contentColor = gc.surface
                )
            ) {
                Text(
                    text = if (downloadState is DownloadState.Error) "Retry" else "Update Now",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = downloadState !is DownloadState.Downloading
            ) {
                Text(
                    text = "Later",
                    color = gc.textSecondary
                )
            }
        }
    )
}
