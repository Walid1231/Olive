package com.waleve.player.service

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes audio files from the phone the way each Android version allows it:
 *  • Android 11+  : one system dialog for all files (MediaStore.createDeleteRequest)
 *  • Android 10   : direct delete; if the file belongs to another app, the system asks per file
 *  • Android 8–9  : direct delete (storage permission)
 */
@Singleton
class MediaDeleteHelper @Inject constructor(@ApplicationContext private val context: Context) {

    sealed class Outcome {
        /** Files are gone (or weren't on the phone) — remove these paths from the library now. */
        data class Deleted(val paths: List<String>) : Outcome()
        /** The system must ask the user first. Launch [sender]; on RESULT_OK call [onApproved]. */
        data class NeedsConsent(val sender: IntentSender, val paths: List<String>) : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    @Suppress("DEPRECATION")
    private fun uriFor(path: String): Uri? {
        val collections = buildList {
            add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
        for (col in collections) {
            context.contentResolver.query(col, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DATA} = ?", arrayOf(path), null)?.use { c ->
                if (c.moveToFirst()) return ContentUris.withAppendedId(col, c.getLong(0))
            }
        }
        return null
    }

    suspend fun delete(paths: List<String>): Outcome = withContext(Dispatchers.IO) {
        try {
            // Streams / remote items have no file on the phone — nothing to delete, just drop them.
            val local = paths.filter { !it.startsWith("http") && !it.startsWith("ytm://") }
            val remote = paths - local.toSet()
            val withUri = local.mapNotNull { p -> uriFor(p)?.let { p to it } }
            val noUri = local.filter { p -> withUri.none { it.first == p } }

            // Files MediaStore doesn't know about: delete directly if we can
            noUri.forEach { runCatching { File(it).takeIf { f -> f.exists() }?.delete() } }

            if (withUri.isEmpty()) return@withContext Outcome.Deleted(paths)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, withUri.map { it.second })
                return@withContext Outcome.NeedsConsent(pi.intentSender, paths)
            }

            val done = mutableListOf<String>()
            for ((p, uri) in withUri) {
                try {
                    context.contentResolver.delete(uri, null, null)
                    runCatching { File(p).takeIf { it.exists() }?.delete() }
                    done += p
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        return@withContext Outcome.NeedsConsent(e.userAction.actionIntent.intentSender, listOf(p))
                    }
                    return@withContext Outcome.Failed("Android didn't allow deleting ${File(p).name}.")
                }
            }
            Outcome.Deleted(done + noUri + remote)
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: "Couldn't delete")
        }
    }

    /** Android 10 only: after the user approved one file, delete it for real. */
    suspend fun finishAfterConsent(paths: List<String>) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return@withContext // system already deleted
        paths.forEach { p -> uriFor(p)?.let { runCatching { context.contentResolver.delete(it, null, null) } } }
    }
}
