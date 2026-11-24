package com.mantra.abletonliveremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

/** The Mac's helper, Ableton Live Remote (Mantra), on port 8966 (MANTRA_PORTS `ableton-live-remote`). */
const val PORT = 8966
const val TAILSCALE_MAC = "100.79.251.19"

/**
 * The line to the Mac. The phone never talks to Ableton Live itself: the helper on the Mac does, and pushes the set to
 * the phone over a WebSocket whenever it changes; the phone's presses go back the same way. The Mac is found, in this
 * order: the USB cable or wireless ADB (the helper sets `adb reverse`, so the Mac is at 127.0.0.1 here), the Mac found
 * last time, Bonjour on the same Wi-Fi (_mantralive._tcp), an address typed by hand, Tailscale. A lost line is found
 * again by itself.
 */
class Link(private val ctx: Context, private val scope: CoroutineScope) {
    private val prefs = ctx.getSharedPreferences("remote", Context.MODE_PRIVATE)

    val status = MutableStateFlow("Looking for the Mac…")
    val connected = MutableStateFlow(false)
    val state = MutableStateFlow<LiveState?>(null)
    val wave = MutableStateFlow<Wave?>(null)
    val visible = MutableStateFlow(true)
    val manual = MutableStateFlow(prefs.getString("manual", "") ?: "")

    @Volatile var base: String? = null
        private set
    @Volatile private var bonjour: String? = null
    @Volatile private var ws: WebSocket? = null
    @Volatile private var holdTimeUntil = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .pingInterval(4, TimeUnit.SECONDS)
        .build()

    fun start() {
        startBonjour()
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                visible.first { it }                                  // nothing is asked while the app is not seen
                val b = base ?: find()
                if (b == null) {
                    connected.value = false
                    status.value = if (notRunning) "Ableton Live Assistant is not running on the Mac"
                                   else "Looking for the Mac… (same Wi-Fi, the cable, or Tailscale)"
                    delay(1500)
                    continue
                }
                val closed = CompletableDeferred<Unit>()
                val req = Request.Builder().url(b.replaceFirst("http", "ws") + "/ws").build()
                ws = http.newWebSocket(req, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { connected.value = true }
                    override fun onMessage(webSocket: WebSocket, text: String) { receive(text) }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.complete(Unit) }
                })
                lastMessage = SystemClock.elapsedRealtime()
                while (!closed.isCompleted) {
                    if (!visible.value) { ws?.close(1000, "not seen"); break }
                    // v5: the helper speaks every second; three seconds of silence is a lost line (v4 showed a dead
                    // helper as connected for up to eight seconds, until OkHttp's ping gave up)
                    if (SystemClock.elapsedRealtime() - lastMessage > 3000) {
                        status.value = "The Mac stopped answering…"; break
                    }
                    delay(250)
                }
                ws?.cancel()
                ws = null
                connected.value = false
                if (visible.value) { base = null; delay(700) }       // lost: look for the Mac again
            }
        }
    }

    @Volatile private var lastMessage = 0L

    private fun receive(text: String) {
        lastMessage = SystemClock.elapsedRealtime()
        if (text.startsWith("{\"hb\"")) return                  // the helper's heartbeat (v5): the line is alive
        val s = parse(text) ?: return
        val now = SystemClock.elapsedRealtime()
        val prev = state.value
        // a jump from this phone is shown at once; a reading taken before Live made it is not allowed to undo it
        val next = if (now < holdTimeUntil && prev != null) s.copy(time = prev.now(now), at = now, insert = prev.insert)
                   else Clock.smooth(prev, s)
        state.value = next
    }

    /** One order to Live through the helper: "c=play", "c=arm&t=3&v=1" … */
    fun send(order: String) {
        val w = ws
        if (w != null && w.send(order)) return
        val b = base ?: return
        scope.launch(Dispatchers.IO) { get("$b/do?$order", 3000) }
    }

    /** The playhead to this beat: shown here at once, sent to Live. */
    fun jump(beat: Double, final: Boolean = true) {
        val b = maxOf(0.0, beat)
        val now = SystemClock.elapsedRealtime()
        state.value?.let { state.value = it.copy(time = b, at = now, insert = b, paused = false) }
        holdTimeUntil = now + if (final) 450 else 700
        send("c=jump&b=" + String.format(java.util.Locale.US, "%.4f", b))
    }

    /** The Assistant's switches (v8): GET /features; null when the Mac does not answer. */
    fun features(): List<Feature>? {
        val b = base ?: return null
        val d = get("$b/features", 3000) ?: return null
        return try {
            val a = JSONObject(String(d)).optJSONArray("features") ?: return emptyList()
            (0 until a.length()).map { i ->
                val f = a.getJSONObject(i)
                Feature(f.optString("id"), f.optString("group"), f.optString("name"), f.optString("about"), f.optBoolean("on"), f.optString("state"))
            }
        } catch (e: Exception) { null }
    }

    /** Track t's waveform, fetched once per change of its clips (key). */
    fun loadWave(t: Int, key: String) {
        val b = base ?: return
        if (wave.value?.key == key) return
        scope.launch(Dispatchers.IO) {
            val d = get("$b/wave?t=$t", 20000) ?: return@launch
            try {
                val j = JSONObject(String(d))
                if (j.has("error")) return@launch
                val cs = j.optJSONArray("clips")
                val clips = (0 until (cs?.length() ?: 0)).map { i ->
                    val c = cs!!.getJSONArray(i)
                    Triple(c.optDouble(0), c.optDouble(1), c.optInt(2) != 0)
                }
                wave.value = Wave(j.optInt("t"), j.optInt("perBeat", 32), j.optDouble("end", 0.0),
                    Base64.decode(j.optString("peaks"), Base64.DEFAULT), clips, key)
            } catch (e: Exception) { }
        }
    }

    fun setManual(a: String) {
        val t = a.trim()
        prefs.edit().putString("manual", t).apply()
        manual.value = t
        ws?.cancel()
        base = null
    }

    var waveTrack: String
        get() = prefs.getString("waveTrack", "") ?: ""
        set(v) { prefs.edit().putString("waveTrack", v).apply() }

    var waveUid: String
        get() = prefs.getString("waveUid", "") ?: ""
        set(v) { prefs.edit().putString("waveUid", v).apply() }

    var waveIndex: Int
        get() = prefs.getInt("waveIndex", -1)
        set(v) { prefs.edit().putInt("waveIndex", v).apply() }

    var tab: String
        get() = prefs.getString("tab", "rec") ?: "rec"
        set(v) { prefs.edit().putString("tab", v).apply() }

    var view: String
        get() = prefs.getString("view", "whole") ?: "whole"
        set(v) { prefs.edit().putString("view", v).apply() }

    /** The Mac's Screen Sharing on the same route as the helper: over the cable 127.0.0.1:15900 (adb reverse to 5900). */
    fun vncTarget(): Pair<String, Int>? {
        val b = base ?: return null
        val host = try { java.net.URI(b).host } catch (e: Exception) { null } ?: return null
        return if (host == "127.0.0.1") host to 15900 else host to 5900
    }

    /** The Live window's place and state, from the helper (null when the Mac does not answer). */
    fun fetchWindow(): WinInfo? {
        val b = base ?: return null
        val d = get("$b/window", 2500) ?: return null
        return try {
            val j = JSONObject(String(d))
            val by = j.optJSONArray("by")
            WinInfo(j.optBoolean("found"), j.optBoolean("onScreen"), j.optBoolean("front"), j.optDouble("covered", 0.0),
                (0 until (by?.length() ?: 0)).map { by!!.getString(it) },
                j.optInt("fx"), j.optInt("fy"), j.optInt("fw"), j.optInt("fh"))
        } catch (e: Exception) { null }
    }

    // MARK: finding the Mac

    private fun find(): String? {
        val tries = mutableListOf<Pair<String, String>>()
        // an address typed by hand is tried first: the person chose it (v3)
        manual.value.takeIf { it.isNotEmpty() }?.let { m ->
            tries += (if (m.startsWith("http")) m else if (m.contains(":")) "http://$m" else "http://$m:$PORT") to "typed address"
        }
        tries += "http://127.0.0.1:$PORT" to "the cable"
        prefs.getString("last", null)?.let { tries += it to "Wi-Fi" }
        var waited = 0
        while (bonjour == null && prefs.getString("last", null) == null && waited < 4000) { Thread.sleep(250); waited += 250 }
        bonjour?.let { tries += it to "Wi-Fi" }
        tries += "http://$TAILSCALE_MAC:$PORT" to "Tailscale"
        notRunning = false
        for ((url, how) in tries.distinctBy { it.first }) {
            // v6: the Wi-Fi gets 2.5 s (a dozing phone's first packet is slow: v5 gave up on a typed LAN address in 0.9 s
            // and fell through to Tailscale); the cable answers at once or not at all
            val (d, closed) = hello("$url/hello", if (how == "the cable") 900 else 2500)
            if (closed) notRunning = true
            if (d == null) continue
            val name = try {
                val j = JSONObject(String(d))
                if (j.optString("app") != "ableton-live-remote") continue
                j.optString("name", "the Mac")
            } catch (e: Exception) { continue }
            if (how == "Wi-Fi") prefs.edit().putString("last", url).apply()      // only the local network is remembered
            status.value = "$name · $how"
            base = url
            return url
        }
        return null
    }

    /**
     * v8: the Mac answered but nothing serves the phone (the connection was refused, or the cable's tunnel closed at once):
     * the Mac is there and Ableton Live Assistant, which serves the phone since 24.11.2026, is not running.
     */
    @Volatile var notRunning = false
        private set

    private fun hello(url: String, timeout: Int): Pair<ByteArray?, Boolean> = try {
        val c = http.newBuilder().readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(minOf(timeout, 1500).toLong(), TimeUnit.MILLISECONDS).build()
        c.newCall(Request.Builder().url(url).build()).execute().use { r -> (if (r.isSuccessful) r.body?.bytes() else null) to false }
    } catch (e: java.net.SocketTimeoutException) { null to false
    } catch (e: java.net.ConnectException) {
        null to ((e.message ?: "").contains("ECONNREFUSED") || (e.message ?: "").contains("refused", ignoreCase = true))
    } catch (e: java.io.IOException) {
        // through the cable a closed port shows as a tunnel that ends at once
        null to (url.startsWith("http://127.0.0.1") && !(e.message ?: "").contains("timeout", ignoreCase = true))
    } catch (e: Exception) { null to false }

    private fun startBonjour() {
        val nsd = ctx.getSystemService(NsdManager::class.java) ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(si: NsdServiceInfo) = resolve(nsd, si)
            override fun onServiceLost(si: NsdServiceInfo) {}
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        try { nsd.discoverServices("_mantralive._tcp", NsdManager.PROTOCOL_DNS_SD, listener) } catch (e: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun resolve(nsd: NsdManager, si: NsdServiceInfo) {
        try {
            nsd.resolveService(si, object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                override fun onServiceResolved(s: NsdServiceInfo) {
                    val ip = s.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress ?: return
                    bonjour = "http://$ip:${s.port}"
                }
            })
        } catch (e: Exception) {}
    }

    // MARK: the wire

    private fun parse(text: String): LiveState? = try {
        val j = JSONObject(text)
        val ts = j.optJSONArray("tracks")
        val tracks = (0 until (ts?.length() ?: 0)).map { i ->
            val t = ts!!.getJSONObject(i)
            Track(t.optInt("i", i), t.optString("name", ""), t.optInt("color", 0x888888), t.optInt("kind", 0),
                t.optInt("arm", -1), t.optBoolean("mute"), t.optBoolean("solo"), t.optInt("clips"), t.optDouble("sum", 0.0),
                t.optString("id", ""))
        }
        LiveState(j.optLong("v"), j.optBoolean("live"), j.optBoolean("playing"), j.optDouble("time", 0.0),
            j.optDouble("tempo", 120.0), j.optBoolean("metro"), j.optBoolean("loop"), j.optDouble("loopStart", 0.0),
            j.optDouble("loopLen", 0.0), j.optBoolean("rec"), j.optInt("sigN", 4), j.optInt("sigD", 4),
            j.optDouble("end", 0.0), tracks, SystemClock.elapsedRealtime(), j.optBoolean("noInput"),
            j.optDouble("insert", 0.0), j.optString("mode", "return"), j.optBoolean("paused"))
    } catch (e: Exception) { null }

    private fun get(url: String, timeout: Int): ByteArray? = try {
        val c = http.newBuilder().readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(minOf(timeout, 1500).toLong(), TimeUnit.MILLISECONDS).build()
        c.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    } catch (e: Exception) { null }
}
