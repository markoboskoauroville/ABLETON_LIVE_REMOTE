package com.mantra.abletonliveremote

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The SESSION tab (Marko, 24.11.2026: "a session view where I can see exactly the screen as it is on my computer ...
 * a remote viewer for the Ableton only ... We're using VNC for this"): the Mac's own Screen Sharing over RFB (Rfb.kt,
 * from Mantra Remote Viewing), VIEW ONLY (nothing is ever sent but frame requests), cropped to the Live window that the
 * helper reports at /window, and following it when it moves or changes size. The same route as the helper: the cable
 * (the helper sets adb reverse 15900 → 5900), the Wi-Fi, or Tailscale.
 *
 * The password is the Mac's Screen Sharing ("VNC viewers may control screen with password") password, asked once on
 * this phone and kept only here, encrypted by the Android Keystore (Secrets.kt). Never in the repository, never logged.
 */
class Viewer(ctx: Context, private val link: Link, private val scope: CoroutineScope) : Rfb.Listener {
    private val prefs = ctx.getSharedPreferences("remote", Context.MODE_PRIVATE)
    private val KEY = "vnc-password"

    /** The SESSION tab is on the screen: only then is the Mac's screen asked for. */
    val active = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val needPassword = MutableStateFlow(false)
    val refused = MutableStateFlow(false)
    val connected = MutableStateFlow(false)
    val win = MutableStateFlow<WinInfo?>(null)
    val tick = MutableStateFlow(0L)
    val fps = MutableStateFlow(0.0)
    val askMs = MutableStateFlow(0L)

    @Volatile var rfb: Rfb? = null
        private set
    private var lastRegion: List<Int>? = null
    private val frameTimes = ArrayDeque<Long>()
    @Volatile private var retryAfter = 0L

    init {
        needPassword.value = Secrets.pin(prefs, KEY).isEmpty()
    }

    fun savePassword(pw: String) {
        Secrets.putPin(prefs, KEY, pw)
        needPassword.value = pw.isEmpty()
        refused.value = false
        retryAfter = 0
        close()
    }

    fun start() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (!active.value || !link.visible.value) {
                    if (rfb != null) close()
                    delay(300)
                    continue
                }
                val w = link.fetchWindow()
                win.value = w
                applyRegion()
                if (rfb == null && !needPassword.value && SystemClock.elapsedRealtime() > retryAfter) connect()
                // the picture rate, from the frames of the last two seconds
                synchronized(frameTimes) {
                    val now = SystemClock.elapsedRealtime()
                    while (frameTimes.isNotEmpty() && now - frameTimes.first() > 2000) frameTimes.removeFirst()
                    fps.value = frameTimes.size / 2.0
                }
                delay(1000)
            }
        }
    }

    private fun applyRegion() {
        val r = rfb ?: return
        val w = win.value ?: return
        if (!w.found || !w.onScreen || w.fw <= 0 || w.fh <= 0) return
        val want = listOf(w.fx, w.fy, w.fw, w.fh)
        if (want != lastRegion && r.width > 0) {
            lastRegion = want
            r.setRegion(w.fx, w.fy, w.fw, w.fh)
        }
    }

    private fun connect() {
        val t = link.vncTarget()
        if (t == null) { status.value = "Waiting for the Mac…"; return }
        val pw = Secrets.pin(prefs, KEY)
        if (pw.isEmpty()) { needPassword.value = true; return }
        status.value = "Connecting to the Mac's screen…"
        val r = Rfb(this)
        r.viewOnly = true
        r.minIntervalMs = 33
        lastRegion = null
        rfb = r
        r.connect(t.first, t.second, pw)
    }

    fun close() {
        rfb?.close()
        rfb = null
        connected.value = false
        lastRegion = null
    }

    override fun onConnected(name: String, width: Int, height: Int) {
        connected.value = true
        status.value = "The Mac's screen"
        lastRegion = null
        applyRegion()
    }

    override fun onFrame() {
        synchronized(frameTimes) { frameTimes.addLast(SystemClock.elapsedRealtime()) }
        tick.value = tick.value + 1
    }

    override fun onClosed(reason: String) {
        rfb = null
        connected.value = false
        if (reason.contains("LOGIN REFUSED") || reason.contains("REFUSED")) {
            refused.value = true
            needPassword.value = true                 // never tried again by itself: Apple blocks repeated failures
            status.value = "The Mac refused the password"
        } else {
            status.value = "The Mac's screen closed (${reason.take(80)}); trying again…"
            retryAfter = SystemClock.elapsedRealtime() + 3000
        }
    }

    override fun onStats(bytesIn: Long, frames: Long, lastFrameMs: Long) { askMs.value = lastFrameMs }
}
