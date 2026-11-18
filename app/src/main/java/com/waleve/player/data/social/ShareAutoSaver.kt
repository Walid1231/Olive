package com.waleve.player.data.social

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.waleve.player.data.extractor.DownloadCategory
import com.waleve.player.data.extractor.MediaExtractor
import com.waleve.player.data.local.dao.SongDao
import com.waleve.player.data.remote.SupabaseStorage
import com.waleve.player.domain.usecase.ScanMusicUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Songs a friend sends you save themselves onto your phone (Music/Olive or Audiobooks/Olive)
 * as soon as Olive is open. Each upload remembers who still has to save it
 * (`transfers/{id}.pending`); the last friend to save it deletes the file from Supabase,
 * so the free storage never fills up. Uploads nobody picked up are removed by the sender
 * after 14 days.
 */
@Singleton
class ShareAutoSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: SupabaseStorage,
    private val extractor: MediaExtractor,
    private val songDao: SongDao,
    private val scanMusic: ScanMusicUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { context.getSharedPreferences("auto_save", Context.MODE_PRIVATE) }
    private val lock = Mutex()
    private val tried = mutableSetOf<String>()

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** notifId → 0..100 while a shared file is being saved. */
    private val _saving = MutableStateFlow<Map<String, Float>>(emptyMap())
    val saving: StateFlow<Map<String, Float>> = _saving.asStateFlow()

    private var listener: ListenerRegistration? = null
    private var started = false

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean("enabled", on).apply()
        if (on) { tried.clear(); restartListener() }
    }

    fun start() {
        if (started) return
        started = true
        _enabled.value = prefs.getBoolean("enabled", true)
        auth.addAuthStateListener { restartListener(); cleanupMyOldUploads() }
    }

    private fun restartListener() {
        listener?.remove(); listener = null
        val me = auth.currentUser?.uid ?: return
        listener = firestore.collection("users").document(me).collection("inbox")
            .whereEqualTo("type", "SONG_SHARED")
            .addSnapshotListener { snap, _ ->
                if (!_enabled.value) return@addSnapshotListener
                val todo = snap?.documents.orEmpty().filter { d ->
                    d.getString("fileUrl") != null && d.getString("savedPath") == null &&
                        d.getBoolean("saveFailed") != true && d.id !in tried
                }
                todo.forEach { d -> tried += d.id; scope.launch { save(me, d) } }
            }
    }

    private suspend fun save(me: String, d: DocumentSnapshot) = lock.withLock {
        val url = d.getString("fileUrl") ?: return@withLock
        val title = d.getString("songTitle") ?: "Shared song"
        val audiobook = d.getString("mediaType") == "audiobook"
        val name = d.getString("fileName") ?: "$title.${d.getString("mimeType")?.substringAfter('/') ?: "mp3"}"
        val ref = d.reference
        _saving.update { it + (d.id to 0f) }
        try {
            val file = storage.downloadToCache(url, name) { p -> _saving.update { m -> m + (d.id to p) } }.getOrElse {
                // Gone from Supabase (already cleaned up) or no internet → try again next time unless it's 404-like
                if (it.message?.contains("(404)") == true || it.message?.contains("(400)") == true) {
                    runCatching { ref.update("saveFailed", true).await() }
                } else tried -= d.id
                return@withLock
            }
            val saved = runCatching {
                extractor.saveToSharedStorage(file, if (audiobook) DownloadCategory.AUDIOBOOK else DownloadCategory.SONG)
            }.getOrNull()
            file.delete()
            if (saved == null) { tried -= d.id; return@withLock }
            runCatching { scanMusic() }
            songDao.getSongByPath(saved)?.let { song ->
                d.getString("thumbnailUrl")?.takeIf { it.isNotBlank() }?.let { songDao.updateAlbumArtUri(song.id, it) }
            }
            runCatching { ref.update(mapOf("savedPath" to saved, "savedAt" to System.currentTimeMillis())).await() }
            d.getString("transferId")?.let { finishTransfer(me, it) }
        } finally {
            _saving.update { it - d.id }
        }
    }

    /** Saved by hand (Download button): record it and clean up the upload the same way. */
    fun markSaved(notifId: String, transferId: String?, path: String) {
        val me = auth.currentUser?.uid ?: return
        tried += notifId
        scope.launch {
            runCatching {
                firestore.collection("users").document(me).collection("inbox").document(notifId)
                    .update(mapOf("savedPath" to path, "savedAt" to System.currentTimeMillis())).await()
            }
            transferId?.let { finishTransfer(me, it) }
        }
    }

    /** I've saved it → take me off the list; if I was the last one, delete the upload. */
    private suspend fun finishTransfer(me: String, id: String) {
        val ref = firestore.collection("transfers").document(id)
        val left = runCatching {
            firestore.runTransaction { tx ->
                val doc = tx.get(ref)
                if (!doc.exists()) return@runTransaction -1
                @Suppress("UNCHECKED_CAST")
                val pending = (doc.get("pending") as? List<String>).orEmpty()
                if (me in pending) tx.update(ref, "pending", FieldValue.arrayRemove(me))
                (pending - me).size
            }.await()
        }.getOrDefault(-1)
        if (left == 0) {
            val url = runCatching { ref.get().await().getString("url") }.getOrNull()
            if (url != null && storage.deleteByPublicUrl(url)) runCatching { ref.delete().await() }
        }
    }

    /** Sender side: remove my uploads that friends never picked up (older than 14 days). */
    private fun cleanupMyOldUploads() {
        val me = auth.currentUser?.uid ?: return
        scope.launch {
            runCatching {
                val cutoff = System.currentTimeMillis() - 14L * 86_400_000
                firestore.collection("transfers").whereEqualTo("fromUid", me).get().await().documents
                    .filter { (it.getLong("createdAt") ?: 0L) < cutoff || (it.get("pending") as? List<*>)?.isEmpty() == true }
                    .forEach { doc ->
                        val url = doc.getString("url")
                        if (url == null || storage.deleteByPublicUrl(url)) runCatching { doc.reference.delete().await() }
                    }
            }
        }
    }
}
