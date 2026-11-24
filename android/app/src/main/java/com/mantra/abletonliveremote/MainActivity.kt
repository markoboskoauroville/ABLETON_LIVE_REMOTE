package com.mantra.abletonliveremote

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlin.math.sqrt

private val Bg = Color(0xFF0B0B0C)
private val Body = Color(0xFF1C1C1E)
private val Rim = Color(0xFF2C2C2E)
private val KeyBg = Color(0xFF000000)
private val KeyRim = Color(0xFF26262A)
private val Green = Color(0xFF33D17A)
private val Amber = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)
private val Grey = Color(0xFF8E8E93)
private val White = Color(0xFFF2F2F7)

/**
 * The scenario tabs (Marko, 24.11.2026: "for each scenario ... separate tabs"). A new scenario is one more pair here and
 * one more branch in RemoteScreen: MIX (solo and mute for every track; the state already carries both) comes next.
 */
private val TABS = listOf("rec" to "REC", "session" to "SESSION")

/**
 * Ableton Live Remote (Mantra). Portrait, Stream Deck style keys. REC: the transport, the metronome with its beat
 * light, one track's waveform on the timeline (tap to move the playhead), and every track with its ARM key.
 * SESSION: the Live window from the Mac's screen, view only; this tab may turn sideways and go full screen.
 */
class MainActivity : ComponentActivity() {
    private lateinit var link: Link
    private lateinit var viewer: Viewer
    private var full by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // a remote on the desk never goes dark
        link = Link(applicationContext, lifecycleScope)
        link.start()
        viewer = Viewer(applicationContext, link, lifecycleScope)
        viewer.start()
        onTab(link.tab)
        setContent {
            RemoteScreen(link, viewer, full, { fullScreen(it) }, { onTab(it) }) { openReleases() }
        }
        AppUpdater.checkAtStart(this)
    }

    /** REC is portrait only; SESSION may turn with the phone (the picture is wide). */
    private fun onTab(tab: String) {
        viewer.active.value = tab == "session"
        requestedOrientation = if (tab == "session") ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                               else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (tab != "session") fullScreen(false)
    }

    /** FULL SCREEN: the bars hidden (a swipe from the edge shows them for a moment), the app's own ✕ brings them back. */
    private fun fullScreen(on: Boolean) {
        full = on
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun openReleases() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/markoboskoauroville/ABLETON_LIVE_REMOTE/releases/latest")))
        } catch (e: Exception) { }
    }

    @Deprecated("Back leaves full screen first")
    override fun onBackPressed() {
        if (full) fullScreen(false) else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onResume() { super.onResume(); AppUpdater.resume(this) }
    override fun onStart() { super.onStart(); link.visible.value = true }
    override fun onStop() { link.visible.value = false; super.onStop() }
}

/** The phone's clock while Live plays, one tick a frame: reading it inside a Canvas redraws only that Canvas. */
@Composable
private fun frameClock(playing: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(playing) {
        now.longValue = SystemClock.elapsedRealtime()
        while (playing) withFrameMillis { now.longValue = SystemClock.elapsedRealtime() }
    }
    return now
}

@Composable
private fun RemoteScreen(
    link: Link, viewer: Viewer, full: Boolean, setFull: (Boolean) -> Unit, onTab: (String) -> Unit, openReleases: () -> Unit
) {
    val status by link.status.collectAsState()
    val connected by link.connected.collectAsState()
    var tab by remember { mutableStateOf(link.tab) }
    var ask by remember { mutableStateOf(false) }

    if (full && tab == "session") {
        // FULL SCREEN: only the picture; the camera hole is still kept clear, and the app's own way out top right
        Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.displayCutout)) {
            Picture(viewer, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopEnd).padding(16.dp).size(48.dp).clip(CircleShape)
                    .background(Color(0x99000000)).border(1.dp, Rim, CircleShape).clickable { setFull(false) }
                    .semantics { contentDescription = "exit full screen" },
                contentAlignment = Alignment.Center
            ) { Text("✕", color = White, fontSize = 20.sp) }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
        Column(Modifier.fillMaxSize()) {
            // the line to the Mac (a tap types an address by hand) and the version, top right
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { ask = true }
                        .padding(vertical = 8.dp, horizontal = 4.dp).semantics { contentDescription = "connection" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (connected) Green else Amber))
                    Spacer(Modifier.width(10.dp))
                    Text(if (connected) status else "Looking for the Mac… (same Wi-Fi, the cable, or Tailscale)",
                        color = Grey, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("v${BuildConfig.VERSION_NAME}", color = Grey, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { openReleases() }
                        .padding(horizontal = 10.dp, vertical = 8.dp).semantics { contentDescription = "version" })
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    "session" -> SessionTab(viewer) { setFull(true) }
                    else -> RecTab(link)
                }
            }
            Tabs(tab) { tab = it; link.tab = it; onTab(it) }
        }
    }
    if (ask) AddressDialog(link.manual.value, { link.setManual(it); ask = false }) { ask = false }
}

// MARK: SESSION

@Composable
private fun SessionTab(viewer: Viewer, goFull: () -> Unit) {
    val needPw by viewer.needPassword.collectAsState()
    val refused by viewer.refused.collectAsState()
    val on by viewer.connected.collectAsState()
    val status by viewer.status.collectAsState()
    val w by viewer.win.collectAsState()
    val fps by viewer.fps.collectAsState()
    val ms by viewer.askMs.collectAsState()
    var anyway by remember { mutableStateOf(false) }

    if (needPw) { PasswordCard(refused) { viewer.savePassword(it) }; return }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (on) "LIVE WINDOW · " + String.format(java.util.Locale.US, "%.0f fps · %d ms", fps, ms) else status,
                color = Grey, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    .semantics { contentDescription = "session status" }
            )
            SmallKey("PASSWORD", false, Grey, "change password") { viewer.needPassword.value = true }
            Spacer(Modifier.width(8.dp))
            SmallKey("FULL SCREEN", false, Green, "full screen") { goFull() }
        }
        val note = when {
            w == null -> "Asking the Mac where the Live window is…"
            !w!!.found -> "Ableton Live is not open on the Mac."
            !w!!.onScreen -> "The Live window is not on the Mac's screen now (another Space, minimised or hidden). " +
                "Nothing is moved from here: bring it up on the Mac."
            w!!.covered > 0.02 && !anyway -> "Other windows lie over Live (" + w!!.by.joinToString(", ") + ", " +
                String.format(java.util.Locale.US, "%.0f %%", w!!.covered * 100) + " of it). The picture would show them, " +
                "not Live. Nothing is moved from here."
            !on -> status.ifEmpty { "Connecting to the Mac's screen…" }
            else -> null
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
            if (note == null) Picture(viewer, Modifier.fillMaxSize())
            else Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(note, color = if ((w?.covered ?: 0.0) > 0.02 || w?.onScreen == false) Amber else Grey, fontSize = 15.sp,
                    modifier = Modifier.semantics { contentDescription = "session note" })
                if (w != null && w!!.onScreen && w!!.covered > 0.02 && !anyway) {
                    Spacer(Modifier.height(14.dp))
                    SmallKey("SHOW ANYWAY", false, Amber, "show anyway") { anyway = true }
                }
            }
        }
        Text("View only: nothing on the phone reaches the Mac's mouse or keyboard. Pinch to zoom, drag to move, two taps to fit.",
            color = Grey, fontSize = 11.sp, maxLines = 2)
    }
}

/** The Live window's picture: fitted, then the fingers' zoom (pinch) and pan (drag); two taps fit it again. */
@Composable
private fun Picture(viewer: Viewer, modifier: Modifier) {
    val tick by viewer.tick.collectAsState()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    fun limit(p: Offset, z: Float): Offset {
        val b = viewer.rfb?.crop ?: return Offset.Zero
        if (box.width == 0 || b.width == 0) return Offset.Zero
        val fit = minOf(box.width.toFloat() / b.width, box.height.toFloat() / b.height)
        val mx = maxOf(0f, (b.width * fit * z - box.width) / 2); val my = maxOf(0f, (b.height * fit * z - box.height) / 2)
        return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
    }
    Canvas(
        modifier.clipToBounds().onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, p, z, _ -> zoom = (zoom * z).coerceIn(1f, 8f); pan = limit(pan + p, zoom) }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }) }
            .semantics { contentDescription = "live window picture" }
    ) {
        if (tick < 0) return@Canvas                      // read: every new frame draws again
        val b = viewer.rfb?.crop ?: return@Canvas
        val fit = minOf(size.width / b.width, size.height / b.height)
        val s = fit * zoom
        val dw = b.width * s; val dh = b.height * s
        val p = limit(pan, zoom)
        drawImage(
            b.asImageBitmap(), dstOffset = IntOffset(((size.width - dw) / 2 + p.x).toInt(), ((size.height - dh) / 2 + p.y).toInt()),
            dstSize = IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.Medium
        )
    }
}

@Composable
private fun PasswordCard(refused: Boolean, save: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SESSION", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            (if (refused) "The Mac refused that password. " else "") +
                "The Live window comes from the Mac's Screen Sharing. Its password (System Settings → Sharing → Screen " +
                "Sharing → \"VNC viewers may control screen with password\") is asked once and kept on this phone only, encrypted.",
            color = if (refused) Amber else Grey, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "password note" }
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Screen Sharing password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "vnc password" })
        Spacer(Modifier.height(12.dp))
        SmallKey("SAVE", false, Green, "save password") { if (text.isNotEmpty()) save(text) }
    }
}

// MARK: REC

@Composable
private fun RecTab(link: Link) {
    val st by link.state.collectAsState()
    val connected by link.connected.collectAsState()
    val s = st
    var newTrack by remember { mutableStateOf(false) }
    if (!connected || s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waiting for the Mac…", color = Grey, fontSize = 15.sp)
        }
        return
    }
    val clock = frameClock(s.playing)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Transport(link, s, clock)
        Readout(s, clock)
        WaveSection(link, s, clock)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TRACKS · ${s.tracks.size}", color = Grey, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            SmallKey("+ NEW TRACK", false, Green, "new track") { newTrack = true }
        }
        if (s.noInput) {
            Text("Live has no audio input device (Settings → Audio → Audio Input Device), so it does not arm audio tracks.",
                color = Amber, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = "no input device" })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.tracks, key = { it.i }) { t -> TrackRow(link, t) }
        }
    }
    if (newTrack) NewTrackDialog({ kind -> link.send("c=new&k=$kind"); newTrack = false }) { newTrack = false }
}

/** PLAY, STOP, RECORD, METRONOME: four square keys, the Stream Deck look. */
@Composable
private fun Transport(link: Link, s: LiveState, clock: State<Long>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val k: Dp = minOf((maxWidth - gap * 3) / 4, 96.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DeckKey(k, s.playing, Green, "play", { link.send("c=play") }) {
                KeyFace("▶", "PLAY", if (s.playing) Green else White, k)
            }
            DeckKey(k, false, White, "stop", { link.send("c=stop") }) {
                KeyFace("■", "STOP", White, k)
            }
            DeckKey(k, s.rec, Red, "record", { link.send("c=rec&v=" + if (s.rec) "0" else "1") }) {
                KeyFace("●", "REC", if (s.rec) Red else White, k)
            }
            DeckKey(k, s.metro, Amber, "metronome", { link.send("c=metro&v=" + if (s.metro) "0" else "1") }) {
                MetronomeFace(s, clock, k)
            }
        }
    }
}

@Composable
private fun KeyFace(icon: String, label: String, color: Color, k: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = color, fontSize = (k.value * 0.30f).sp)
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/**
 * The metronome key: its light blinks on every beat Live plays (the bar's first beat red and larger, the others
 * green), only while playing. The phone carries Live's song time on at the tempo between readings, so the light is
 * on the beat, not a reading late.
 */
@Composable
private fun MetronomeFace(s: LiveState, clock: State<Long>, k: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(k * 0.34f).semantics { contentDescription = "beat light" }) {
            val now = clock.value
            var lit = 0                                         // 0 off, 1 beat, 2 the bar's first beat
            if (s.playing) {
                val t = s.now(now)
                val (n, down) = Clock.beat(t, s.beatLen, s.sigN)
                val into = (t - n * s.beatLen) * 60.0 / s.tempo        // seconds since that beat
                if (into in 0.0..0.12) lit = if (down) 2 else 1
            }
            val r = size.minDimension / 2
            drawCircle(Color(0xFF2A2A2E), r)
            when (lit) {
                2 -> drawCircle(Red, r)
                1 -> drawCircle(Green, r * 0.72f)
                else -> {}
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("METRO", color = if (s.metro) Amber else White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp)
    }
}

/** Where the playhead is: bar.beat.sixteenth large, minutes and seconds under it; the tempo and the time signature. */
@Composable
private fun Readout(s: LiveState, clock: State<Long>) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Body).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            val t = s.now(clock.value)
            Text(Clock.position(t, s.sigN, s.sigD), color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.semantics { contentDescription = "position" })
            Text(Clock.seconds(t, s.tempo), color = Grey, fontSize = 14.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Clock.tempo(s.tempo) + " BPM", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${s.sigN}/${s.sigD}" + (if (s.loop) " · LOOP" else "") + (if (!s.live) " · Live silent" else ""),
                color = if (!s.live) Amber else Grey, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/** The waveform: ONE track at a time, chosen from the tracks that have audio; WHOLE or LOOP. */
@Composable
private fun WaveSection(link: Link, s: LiveState, clock: State<Long>) {
    val audio = s.tracks.filter { it.hasAudio }
    var chosen by remember { mutableStateOf(link.waveTrack) }
    var chosenIndex by remember { mutableIntStateOf(link.waveIndex) }
    var view by remember { mutableStateOf(link.view) }
    var menu by remember { mutableStateOf(false) }
    // v5: by name, and when the name is gone (renamed in Live) by its place, so a rename does not switch the waveform
    val track = audio.firstOrNull { it.name == chosen } ?: audio.firstOrNull { it.i == chosenIndex } ?: audio.firstOrNull()
    LaunchedEffect(track?.i, track?.name) {
        if (track != null && (track.name != chosen || track.i != chosenIndex)) {
            chosen = track.name; chosenIndex = track.i; link.waveTrack = track.name; link.waveIndex = track.i
        }
    }
    val wave by link.wave.collectAsState()
    val key = track?.let { "${it.i}|${it.name}|${it.clips}|${it.sum}|${s.tempo}" }
    LaunchedEffect(key) { if (track != null && key != null) link.loadWave(track.i, key) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(KeyBg)
                    .border(1.dp, KeyRim, RoundedCornerShape(10.dp)).clickable(enabled = audio.isNotEmpty()) { menu = true }
                    .padding(horizontal = 10.dp).semantics { contentDescription = "waveform track" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(liveColor(track?.color ?: 0x555555)))
                Spacer(Modifier.width(8.dp))
                Text(track?.name ?: "No track with audio", color = White, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("▾", color = Grey, fontSize = 14.sp)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                for (t in audio) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(liveColor(t.color)))
                                Spacer(Modifier.width(8.dp))
                                Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (t.i == track?.i) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        onClick = { chosen = t.name; chosenIndex = t.i; link.waveTrack = t.name; link.waveIndex = t.i; menu = false }
                    )
                }
            }
        }
        SmallKey("WHOLE", view == "whole", Green, "whole view") { view = "whole"; link.view = "whole" }
        SmallKey("LOOP", view == "loop", Amber, "loop view") { view = "loop"; link.view = "loop" }
    }
    val w = wave?.takeIf { track != null && it.t == track.i }
    WaveStrip(link, s, w, track, view, clock)
}

@Composable
private fun WaveStrip(link: Link, s: LiveState, w: Wave?, track: Track?, view: String, clock: State<Long>) {
    val r0 = if (view == "loop" && s.loopLen > 0) s.loopStart else 0.0
    val r1 = if (view == "loop" && s.loopLen > 0) s.loopEnd else maxOf(s.wholeEnd, w?.end ?: 0.0)
    val range by rememberUpdatedState(r0 to r1)
    val tint = liveColor(track?.color ?: 0x888888)
    var lastSent by remember { mutableLongStateOf(0L) }
    fun beatAt(x: Float, width: Int): Double {
        val (a, b) = range
        return a + (x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * (b - a)
    }
    Canvas(
        Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(12.dp)).background(KeyBg)
            .border(1.dp, KeyRim, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "waveform" }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { o -> link.jump(beatAt(o.x, size.width)) })
            }
            .pointerInput(Unit) {
                var lastX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        lastX = o.x; lastSent = SystemClock.elapsedRealtime(); link.jump(beatAt(o.x, size.width), false)
                    },
                    // v2: the finger's last place is always sent (v1 lost it when the last move fell inside the 70 ms)
                    onDragEnd = { link.jump(beatAt(lastX, size.width)) },
                    onDragCancel = { link.jump(beatAt(lastX, size.width)) },
                    onHorizontalDrag = { change, _ ->
                        lastX = change.position.x
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastSent > 70) { lastSent = now; link.jump(beatAt(lastX, size.width), false) }
                    }
                )
            }
    ) {
        val (a, b) = range
        val span = (b - a).coerceAtLeast(1e-6)
        fun x(beat: Double) = ((beat - a) / span * size.width).toFloat()
        val h = size.height
        // the bars, faint, when there is room for them
        val bar = s.barLen
        if (bar > 0 && size.width / (span / bar) >= 8) {
            var g = kotlin.math.ceil(a / bar) * bar
            while (g <= b) { val gx = x(g); drawLine(Color(0xFF1E1E22), Offset(gx, 0f), Offset(gx, h), 1f); g += bar }
        }
        // the loop brace, shaded
        if (s.loopLen > 0) {
            val lx0 = x(s.loopStart).coerceIn(0f, size.width); val lx1 = x(s.loopEnd).coerceIn(0f, size.width)
            if (lx1 > lx0) {
                drawRect(Amber.copy(alpha = if (s.loop) 0.16f else 0.07f), Offset(lx0, 0f), Size(lx1 - lx0, h))
                drawRect(Amber.copy(alpha = if (s.loop) 0.9f else 0.35f), Offset(lx0, 0f), Size(lx1 - lx0, 3f))
            }
        }
        if (w != null) {
            // the clips where they sit on the timeline
            for ((c0, c1, muted) in w.clips) {
                val cx0 = x(c0).coerceIn(0f, size.width); val cx1 = x(c1).coerceIn(0f, size.width)
                if (cx1 > cx0) drawRect((if (muted) Grey else tint).copy(alpha = 0.16f), Offset(cx0, 8f), Size(cx1 - cx0, h - 16f))
            }
            // the waveform, one column every 2 px
            val step = 2f
            var px = 0f
            val mid = h / 2
            val color = tint.copy(alpha = 0.95f)
            while (px < size.width) {
                val p = w.peak(a + px / size.width * span, a + (px + step) / size.width * span)
                if (p > 0f) {
                    val hh = sqrt(p) * (h / 2 - 10f)
                    drawLine(color, Offset(px + 0.5f, mid - hh), Offset(px + 0.5f, mid + hh), step * 0.8f)
                }
                px += step
            }
        }
        // the playhead
        val t = s.now(clock.value)
        if (t >= a && t <= b) {
            val tx = x(t)
            drawLine(White, Offset(tx, 0f), Offset(tx, h), 3f)
        }
    }
    if (w == null && track != null) {
        Text("Reading the waveform of ${track.name}…", color = Grey, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackRow(link: Link, t: Track) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(Body)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(8.dp).fillMaxHeight().background(liveColor(t.color)))
        Spacer(Modifier.width(if (t.grouped) 22.dp else 12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, color = White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (t.group) FontWeight.Bold else FontWeight.Normal)
            val notes = listOfNotNull(if (t.group) "GROUP" else null, if (t.mute) "MUTED" else null, if (t.solo) "SOLO" else null)
            if (notes.isNotEmpty()) Text(notes.joinToString(" · "), color = Grey, fontSize = 11.sp, maxLines = 1)
        }
        if (t.armable) {
            Spacer(Modifier.width(8.dp))
            val haptic = LocalHapticFeedback.current
            Box(
                Modifier.size(width = 72.dp, height = 44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (t.armed) Red else KeyBg)
                    .border(1.dp, if (t.armed) Red else KeyRim, RoundedCornerShape(10.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        link.send("c=arm&t=${t.i}&v=" + if (t.armed) "0" else "1")
                    }
                    .semantics { contentDescription = "arm ${t.i}" },
                contentAlignment = Alignment.Center
            ) {
                Text("ARM", color = if (t.armed) White else Grey, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

// MARK: pieces

@Composable
private fun DeckKey(size: Dp, active: Boolean, accent: Color, name: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(size * 0.16f)
    Box(
        Modifier.size(size).clip(shape).background(KeyBg)
            .border(if (active) 3.dp else 1.dp, if (active) accent else KeyRim, shape)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun SmallKey(label: String, active: Boolean, accent: Color, name: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(44.dp).widthIn(min = 72.dp).clip(shape).background(KeyBg)
            .border(if (active) 2.dp else 1.dp, if (active) accent else KeyRim, shape)
            .clickable { onClick() }.padding(horizontal = 12.dp).semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (active) accent else White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
    }
}

private fun liveColor(c: Int): Color = Color(0xFF000000.toInt() or (c and 0xFFFFFF))

@Composable
private fun Tabs(current: String, pick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((id, name) in TABS) {
            val sel = id == current
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) Body else Color.Transparent)
                    .clickable { pick(id) }.padding(vertical = 12.dp).semantics { contentDescription = "tab $name" },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(name, color = if (sel) White else Grey, fontSize = 15.sp, letterSpacing = 1.5.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (sel) Green else Color.Transparent))
            }
        }
    }
}

@Composable
private fun NewTrackDialog(make: (String) -> Unit, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("New track") },
        text = { Text("A new track at the end of the set; it appears in the list and can be armed.", fontSize = 14.sp) },
        confirmButton = {
            Row {
                TextButton({ make("audio") }, Modifier.semantics { contentDescription = "new audio" }) { Text("AUDIO") }
                TextButton({ make("midi") }, Modifier.semantics { contentDescription = "new midi" }) { Text("MIDI") }
            }
        },
        dismissButton = { TextButton(close) { Text("✕") } }
    )
}

@Composable
private fun AddressDialog(now: String, save: (String) -> Unit, close: () -> Unit) {
    var text by remember { mutableStateOf(now) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("The Mac's address") },
        text = {
            Column {
                Text("Found by itself on the same Wi-Fi, over the cable, or over Tailscale. Only if it is not: the Mac's " +
                    "address, e.g. 192.168.1.20 (Ableton Live Remote (Mantra) on the Mac shows it).", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(text, { text = it }, singleLine = true)
            }
        },
        confirmButton = { TextButton({ save(text) }) { Text("SAVE") } },
        dismissButton = { TextButton(close) { Text("✕") } }
    )
}
