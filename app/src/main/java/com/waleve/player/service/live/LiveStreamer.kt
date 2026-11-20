package com.waleve.player.service.live

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.waleve.player.data.remote.SupabaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioBufferCallback
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live Listen Together over WebRTC (LiveKit).
 *  • Host: publishes what Olive is playing (via [LiveAudioTap]) — guests hear it with ~0.3 s delay,
 *    no downloading or uploading, perfectly in sync, local files included.
 *  • Guest: just listens to the host's audio.
 * Tokens come from the olive-api edge function (only the host + invited friends get one).
 */
@Singleton
class LiveStreamer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SupabaseStorage,
) {
    enum class Role { HOST, GUEST }
    sealed interface State {
        data object Off : State
        data object Connecting : State
        data class Live(val role: Role, val listeners: Int) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lock = Mutex()
    private var room: Room? = null
    private var eventsJob: Job? = null
    private var role: Role? = null

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Replace the (silent) microphone audio with Olive's music. */
    private val musicFeed = object : AudioBufferCallback {
        override fun onBuffer(buffer: ByteBuffer, audioFormat: Int, channelCount: Int, sampleRate: Int, bytesRead: Int, captureTimeNs: Long): Long {
            LiveAudioTap.read(buffer, bytesRead, channelCount.coerceAtLeast(1), sampleRate)
            return captureTimeNs
        }
    }

    private fun newRoom(): Room = LiveKit.create(
        appContext = context,
        options = RoomOptions(
            adaptiveStream = false,
            dynacast = false,
            // Music must not go through voice processing
            audioTrackCaptureDefaults = LocalAudioTrackOptions(
                noiseSuppression = false, echoCancellation = false, autoGainControl = false,
                highPassFilter = false, typingNoiseDetection = false,
            ),
            audioTrackPublishDefaults = AudioTrackPublishDefaults(audioBitrate = 96_000, dtx = false, red = true),
        ),
        overrides = LiveKitOverrides(
            audioOptions = AudioOptions(
                audioOutputType = AudioType.MediaAudioType(),          // plays like music, not a call
                disableCommunicationModeWorkaround = true,
                javaAudioDeviceModuleCustomizer = { b ->
                    b.setAudioSource(MediaRecorder.AudioSource.MIC)
                    b.setUseHardwareAcousticEchoCanceler(false)
                    b.setUseHardwareNoiseSuppressor(false)
                },
            ),
        ),
    )

    private suspend fun token(hostUid: String): Pair<String, String> {
        val r: JSONObject = api.callApi(JSONObject().put("action", "livekit-token").put("hostUid", hostUid))
        return r.getString("url") to r.getString("token")
    }

    /** Host: go live. Needs the microphone permission (the mic itself is never sent). */
    suspend fun startHost(hostUid: String): Result<Unit> = start(Role.HOST, hostUid)

    /** Guest: listen to the host. */
    suspend fun startGuest(hostUid: String): Result<Unit> = start(Role.GUEST, hostUid)

    private suspend fun start(r: Role, hostUid: String): Result<Unit> = lock.withLock {
        runCatching {
            stopLocked()
            _state.value = State.Connecting
            val (url, tok) = token(hostUid)
            LiveSessionService.start(context, r == Role.HOST)
            val rm = newRoom()
            room = rm; role = r
            eventsJob = scope.launch {
                rm.events.collect { e ->
                    when (e) {
                        is RoomEvent.Disconnected -> if (room === rm) _state.value = State.Failed("Live stream disconnected")
                        is RoomEvent.Reconnecting -> _state.value = State.Connecting
                        is RoomEvent.Reconnected, is RoomEvent.ParticipantConnected, is RoomEvent.ParticipantDisconnected ->
                            _state.value = State.Live(r, rm.remoteParticipants.size)
                        else -> Unit
                    }
                }
            }
            rm.connect(url, tok)
            if (r == Role.HOST) {
                LiveAudioTap.active = true
                rm.localParticipant.setMicrophoneEnabled(true)
                val track = rm.localParticipant.getTrackPublication(Track.Source.MICROPHONE)?.track as? LocalAudioTrack
                    ?: error("Couldn't start the live audio")
                track.setAudioBufferCallback(musicFeed)
            }
            _state.value = State.Live(r, rm.remoteParticipants.size)
        }.onFailure {
            Log.e("LiveStreamer", "start failed", it)
            stopLocked()
            _state.value = State.Failed(it.message ?: "Couldn't go live")
        }
    }

    suspend fun stop() = lock.withLock { stopLocked() }

    private fun stopLocked() {
        eventsJob?.cancel(); eventsJob = null
        LiveAudioTap.active = false
        room?.let { rm ->
            runCatching {
                (rm.localParticipant.getTrackPublication(Track.Source.MICROPHONE)?.track as? LocalAudioTrack)
                    ?.setAudioBufferCallback(null)
            }
            runCatching { rm.disconnect() }
            runCatching { rm.release() }
        }
        room = null; role = null
        LiveSessionService.stop(context)
        _state.value = State.Off
    }

    val isLive: Boolean get() = _state.value is State.Live
}
