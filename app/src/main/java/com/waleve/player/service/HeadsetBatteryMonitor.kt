package com.waleve.player.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAssignedNumbers
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Battery of the connected Bluetooth earbuds / headphones. Levels are 0–100 or null (unknown). */
data class HeadsetBattery(
    val name: String,
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val leftCharging: Boolean = false,
    val rightCharging: Boolean = false,
    val caseCharging: Boolean = false,
    /** One level for the whole headset (what Android's Bluetooth settings show). */
    val overall: Int? = null,
    /** False → Olive needs the "Nearby devices" (Bluetooth) permission to read the battery. */
    val canRead: Boolean = true,
) {
    val hasSides: Boolean get() = left != null || right != null
    val hasAny: Boolean get() = hasSides || overall != null || case != null

    /** Short text for the widget / notification: "L 80% · R 70% · Case 40%" or "Battery 70%". */
    fun shortText(): String? {
        fun p(v: Int?, c: Boolean) = v?.let { "$it%" + if (c) "⚡" else "" }
        return when {
            hasSides -> listOfNotNull(
                p(left, leftCharging)?.let { "L $it" },
                p(right, rightCharging)?.let { "R $it" },
                p(case, caseCharging)?.let { "Case $it" },
            ).joinToString("  ·  ")
            overall != null -> "Battery $overall%"
            else -> null
        }
    }
}

/**
 * Watches the connected Bluetooth headset and works out its battery from every source Android
 * lets an app use:
 *  1. Android's own Bluetooth battery level (the number in Bluetooth settings) — one level.
 *  2. Battery reports the headset sends over the call profile (+IPHONEACCEV / +XEVENT) — one level.
 *  3. BLE adverts with separate Left / Right / Case levels:
 *     • AirPods & Beats (Apple "proximity pairing" data)
 *     • Google Fast Pair earbuds that share battery (Pixel Buds, many Sony / JBL / OnePlus / Nothing…)
 *  Scanning only runs while earbuds are connected and Olive is open or playing, in short bursts.
 */
@Singleton
class HeadsetBatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: PlaybackController,
) {
    private val _state = MutableStateFlow<HeadsetBattery?>(null)
    /** null = no Bluetooth audio device connected. */
    val state: StateFlow<HeadsetBattery?> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private var started = false
    private var headsetName: String? = null
    private var headsetAddress: String? = null
    private var device: BluetoothDevice? = null
    private var a2dp: BluetoothA2dp? = null
    private var overall: Int? = null

    private data class Sides(
        val left: Int?, val right: Int?, val case: Int?,
        val lc: Boolean, val rc: Boolean, val cc: Boolean, val rssi: Int,
    )
    private var sides: Sides? = null
    private var sidesAt = 0L

    private var foreground = false
    private var playing = false
    private var cycling = false
    private var scanOn = false

    fun start() {
        if (started) return
        started = true
        main.post {
            runCatching { audio?.registerAudioDeviceCallback(deviceCallback, main) }
            registerReceivers()
            onOutputsChanged()
            main.postDelayed(poll, 60_000)
        }
        scope.launch {
            player.playerState.map { it.isPlaying }.distinctUntilChanged().collect { playing = it; updateScan() }
        }
    }

    /** Olive's screen is visible (allowed to scan for Left/Right levels). */
    fun setForeground(visible: Boolean) {
        main.post { foreground = visible; if (visible) refresh() else updateScan() }
    }

    /** Re-read everything (e.g. right after the Bluetooth permission was granted). */
    fun refresh() {
        main.post {
            ensureProfileProxy()
            findDevice()
            readSystemBattery()
            publish()
            updateScan()
        }
    }

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT)

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) granted(Manifest.permission.BLUETOOTH_SCAN)
        else granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    // ── which headset is connected (AudioManager needs no permission) ───────────

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onOutputsChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onOutputsChanged()
    }

    private fun isBtOutput(d: AudioDeviceInfo): Boolean {
        if (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (d.type == AudioDeviceInfo.TYPE_BLE_HEADSET || d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
    }

    private fun onOutputsChanged() {
        val outs = runCatching { audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull().orEmpty()
        val bt = outs.filter(::isBtOutput).let { list -> list.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: list.firstOrNull() }
        if (bt == null) {
            if (headsetName != null) Log.d(TAG, "Headset disconnected")
            headsetName = null; headsetAddress = null; device = null; overall = null; sides = null
            publish(); updateScan(); return
        }
        val name = bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth headphones"
        val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) bt.address else null
        if (name != headsetName) { overall = null; sides = null; device = null }
        headsetName = name; headsetAddress = addr
        ensureProfileProxy()
        findDevice()
        readSystemBattery()
        publish()
        updateScan()
    }

    @SuppressLint("MissingPermission")
    private fun findDevice() {
        if (headsetName == null || !hasConnectPermission()) return
        val ad = adapter ?: return
        device = runCatching {
            headsetAddress?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) && it != "00:00:00:00:00:00" }?.let { ad.getRemoteDevice(it) }
        }.getOrNull()
            ?: runCatching { a2dp?.connectedDevices?.firstOrNull() }.getOrNull()
            ?: runCatching {
                ad.bondedDevices?.firstOrNull { d ->
                    val n = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d.alias ?: d.name else d.name
                    n == headsetName || d.name == headsetName
                }
            }.getOrNull()
            ?: device
    }

    private fun ensureProfileProxy() {
        if (a2dp != null || !hasConnectPermission()) return
        runCatching {
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    a2dp = proxy as? BluetoothA2dp
                    main.post { findDevice(); readSystemBattery(); publish() }
                }
                override fun onServiceDisconnected(profile: Int) { a2dp = null }
            }, BluetoothProfile.A2DP)
        }
    }

    /** Android keeps the last battery level the headset reported (Bluetooth settings shows it). */
    private fun readSystemBattery() {
        val d = device ?: return
        val lvl = runCatching { d.javaClass.getMethod("getBatteryLevel").invoke(d) as? Int }.getOrNull()
        if (lvl != null && lvl in 0..100) overall = lvl
    }

    private val poll = object : Runnable {
        override fun run() {
            if (headsetName != null) { readSystemBattery(); publish() }
            main.postDelayed(this, 60_000)
        }
    }

    // ── broadcasts: battery changes + headset reports ──────────────────────────

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                ACTION_BATTERY_LEVEL_CHANGED -> {
                    val lvl = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                    @Suppress("DEPRECATION")
                    val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (lvl in 0..100 && headsetName != null && (device == null || d == null || d.address == device?.address)) {
                        if (device == null) device = d
                        overall = lvl; publish()
                    }
                }
                BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> parseVendorEvent(intent)
                else -> main.postDelayed({ onOutputsChanged() }, 1_200)
            }
        }
    }

    private fun registerReceivers() {
        runCatching {
            val f = IntentFilter().apply {
                addAction(ACTION_BATTERY_LEVEL_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_EXPORTED)
            val v = IntentFilter(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT).apply {
                addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "." + BluetoothAssignedNumbers.APPLE)
                addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "." + BluetoothAssignedNumbers.PLANTRONICS)
            }
            ContextCompat.registerReceiver(context, receiver, v, ContextCompat.RECEIVER_EXPORTED)
        }.onFailure { Log.w(TAG, "Couldn't register Bluetooth receivers", it) }
    }

    private fun parseVendorEvent(intent: Intent) {
        val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD) ?: return
        @Suppress("DEPRECATION")
        val args = intent.extras?.get(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) as? Array<*> ?: return
        fun num(x: Any?): Int? = when (x) { is Number -> x.toInt(); is String -> x.trim().toIntOrNull(); else -> null }
        when (cmd.uppercase()) {
            "+IPHONEACCEV" -> {
                // count, key, value, key, value… key 1 = battery 0–9
                val n = num(args.getOrNull(0)) ?: return
                for (i in 0 until n) {
                    val k = num(args.getOrNull(1 + 2 * i)); val value = num(args.getOrNull(2 + 2 * i))
                    if (k == 1 && value != null && value in 0..9) overall = (value + 1) * 10
                }
            }
            "+XEVENT" -> {
                if ((args.getOrNull(0) as? String)?.equals("BATTERY", ignoreCase = true) != true) return
                val lvl = num(args.getOrNull(1)); val levels = num(args.getOrNull(2))
                if (lvl != null && levels != null && levels > 1) overall = (lvl * 100 / (levels - 1)).coerceIn(0, 100)
            }
            else -> return
        }
        publish()
    }

    // ── BLE: Left / Right / Case ────────────────────────────────────────────────

    private fun updateScan() {
        val want = headsetName != null && (foreground || playing) && hasScanPermission() &&
            runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        if (want && !cycling) {
            cycling = true
            main.removeCallbacks(cycleOn); main.removeCallbacks(cycleOff)
            main.post(cycleOn)
        } else if (!want && cycling) {
            cycling = false
            main.removeCallbacks(cycleOn); main.removeCallbacks(cycleOff)
            endScan()
        }
    }

    // Scan ~15 s, rest ~1–2 min: fresh numbers without draining the phone.
    private val cycleOn: Runnable = Runnable { beginScan(); main.postDelayed(cycleOff, 15_000) }
    private val cycleOff: Runnable = Runnable {
        endScan()
        if (cycling) main.postDelayed(cycleOn, if (sides != null) 120_000 else 60_000)
    }

    @SuppressLint("MissingPermission")
    private fun beginScan() {
        if (scanOn) return
        val scanner = runCatching { adapter?.bluetoothLeScanner }.getOrNull() ?: return
        val filters = listOf(
            ScanFilter.Builder().setManufacturerData(APPLE_ID, byteArrayOf(0x07), byteArrayOf(-1)).build(),
            ScanFilter.Builder().setServiceData(FAST_PAIR, ByteArray(0)).build(),
        )
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        runCatching { scanner.startScan(filters, settings, scanCallback); scanOn = true }
            .onFailure { Log.w(TAG, "BLE scan failed to start", it) }
    }

    @SuppressLint("MissingPermission")
    private fun endScan() {
        if (!scanOn) return
        scanOn = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) { main.post { handle(result) } }
        override fun onBatchScanResults(results: MutableList<ScanResult>) { main.post { results.forEach(::handle) } }
    }

    private fun handle(r: ScanResult) {
        if (r.rssi < -72) return              // someone else's earbuds across the room
        val rec = r.scanRecord ?: return
        val s = rec.getManufacturerSpecificData(APPLE_ID)?.let { parseApple(it, r.rssi) }
            ?: rec.getServiceData(FAST_PAIR)?.let { parseFastPair(it, r.rssi) }
            ?: return
        val now = SystemClock.elapsedRealtime()
        val cur = sides
        // Keep following the closest earbuds; switch only if the current ones went quiet
        if (cur == null || now - sidesAt > 30_000 || s.rssi >= cur.rssi - 10) {
            sides = s; sidesAt = now; publish()
        }
    }

    /** AirPods / Beats: nibbles 0–10 = 0–100 %, 15 = not available. */
    private fun parseApple(d: ByteArray, rssi: Int): Sides? {
        if (d.size < 9 || d[0] != 0x07.toByte()) return null
        val status = d[5].toInt() and 0xFF
        val flipped = (status and 0x20) == 0
        val pods = d[6].toInt() and 0xFF
        val hi = pods shr 4
        val lo = pods and 0x0F
        val charge = (d[7].toInt() and 0xFF) shr 4
        val caseRaw = d[7].toInt() and 0x0F
        fun lvl(n: Int) = if (n in 0..10) n * 10 else null
        val left = lvl(if (flipped) hi else lo)
        val right = lvl(if (flipped) lo else hi)
        if (left == null && right == null) return null
        return Sides(
            left = left, right = right, case = lvl(caseRaw),
            lc = charge and (if (flipped) 0x02 else 0x01) != 0,
            rc = charge and (if (flipped) 0x01 else 0x02) != 0,
            cc = charge and 0x04 != 0,
            rssi = rssi,
        )
    }

    /** Google Fast Pair "battery values" field: left, right, case; bit 7 = charging, 0x7F = unknown. */
    private fun parseFastPair(d: ByteArray, rssi: Int): Sides? {
        if (d.size <= 3) return null          // pairing mode: only a model id
        var i = 1                             // byte 0 = version / flags
        while (i < d.size) {
            val h = d[i].toInt() and 0xFF
            val len = h shr 4
            val type = h and 0x0F
            if ((type == 0x3 || type == 0x4) && len >= 2 && i + len < d.size) {
                fun v(at: Int): Pair<Int?, Boolean> {
                    val x = d[at].toInt() and 0xFF
                    val l = x and 0x7F
                    return (if (l in 0..100) l else null) to (x and 0x80 != 0)
                }
                val (l, lc) = v(i + 1)
                val (r, rc) = v(i + 2)
                val (c, cc) = if (len >= 3) v(i + 3) else (null to false)
                if (l == null && r == null) return null
                return Sides(l, r, c, lc, rc, cc, rssi)
            }
            i += 1 + len
        }
        return null
    }

    // ── result ──────────────────────────────────────────────────────────────────

    private fun publish() {
        val name = headsetName
        if (name == null) { _state.value = null; return }
        val s = sides?.takeIf { SystemClock.elapsedRealtime() - sidesAt < 6 * 60_000 }
        _state.value = HeadsetBattery(
            name = name,
            left = s?.left, right = s?.right, case = s?.case,
            leftCharging = s?.lc == true, rightCharging = s?.rc == true, caseCharging = s?.cc == true,
            overall = overall ?: listOfNotNull(s?.left, s?.right).minOrNull(),
            canRead = hasConnectPermission(),
        )
    }

    companion object {
        private const val TAG = "HeadsetBattery"
        private const val APPLE_ID = 0x004C
        private val FAST_PAIR = ParcelUuid(UUID.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"))
        private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

        /** Permissions to ask for when the user taps "Show battery". */
        fun permissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
