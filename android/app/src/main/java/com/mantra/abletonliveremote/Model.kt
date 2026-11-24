package com.mantra.abletonliveremote

// The set as the Mac helper describes it, and the arithmetic on it. Nothing here imports Android (MANTRA_MANIFEST
// android-app.md §1: the mechanism is testable on a desk).

import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

/** One track of the set. kind bits: 1 can be armed, 2 audio track, 4 group, 8 inside a group. arm -1: cannot be armed. */
data class Track(
    val i: Int, val name: String, val color: Int, val kind: Int, val arm: Int, val mute: Boolean, val solo: Boolean,
    val clips: Int, val sum: Double
) {
    val armable get() = (kind and 1) != 0 && arm >= 0
    val group get() = (kind and 4) != 0
    val grouped get() = (kind and 8) != 0
    val armed get() = arm == 1
    /** A track with audio clips on the Arrangement timeline: one of the waveforms that can be shown. */
    val hasAudio get() = clips > 0
}

/**
 * Live's transport and tracks at the moment the phone received them (`at`, ms on the phone's monotonic clock). The
 * mute and solo of every track are already here, for the MIX tab that comes next.
 */
data class LiveState(
    val v: Long, val live: Boolean, val playing: Boolean, val time: Double, val tempo: Double, val metro: Boolean,
    val loop: Boolean, val loopStart: Double, val loopLen: Double, val rec: Boolean, val sigN: Int, val sigD: Int,
    val end: Double, val tracks: List<Track>, val at: Long, val noInput: Boolean = false,
    /** Live's insert point (Song.start_time): where Play and Space start. */
    val insert: Double = 0.0,
    /** After a stop: "return" (the DAW way, back to the insert point) or "continue" (the editing way, DaVinci Resolve). */
    val mode: String = "return"
) {
    val beatLen get() = 4.0 / max(1, sigD)
    val barLen get() = beatLen * max(1, sigN)
    val loopEnd get() = loopStart + loopLen

    /** The song time now: while playing, carried on at the tempo from the last reading (wrapping at the loop's end). */
    fun now(ms: Long): Double {
        if (!playing) return time
        var t = time + (ms - at) / 1000.0 * tempo / 60.0
        if (loop && loopLen > 0 && time < loopEnd && t >= loopEnd) t = loopStart + (t - loopEnd) % loopLen
        return t
    }

    /** The far end of the WHOLE view: the song's last event, or the loop's end if that is later. */
    val wholeEnd get() = max(max(end, loopEnd), 4.0)
}

object Clock {
    /** Live's own position text: bar.beat.sixteenth, from 1.1.1. */
    fun position(beats: Double, sigN: Int, sigD: Int): String {
        val bl = 4.0 / max(1, sigD)
        val bar = bl * max(1, sigN)
        val b = max(0.0, beats) + 1e-6
        val barN = floor(b / bar).toInt()
        val inBar = b - barN * bar
        val beatN = floor(inBar / bl).toInt()
        val six = floor((inBar - beatN * bl) / 0.25).toInt()
        return "${barN + 1}.${beatN + 1}.${six + 1}"
    }

    /** The same moment in minutes and seconds (the set's tempo held constant): 1:05.25 */
    fun seconds(beats: Double, tempo: Double): String {
        val s = max(0.0, beats) * 60.0 / (if (tempo > 0) tempo else 120.0)
        val m = floor(s / 60).toInt()
        return String.format(Locale.US, "%d:%05.2f", m, s - m * 60)
    }

    fun tempo(t: Double): String = String.format(Locale.US, "%.2f", t)

    /** The beat number (from 0) a song time falls in, and whether it is the bar's first. */
    fun beat(t: Double, beatLen: Double, sigN: Int): Pair<Long, Boolean> {
        val n = floor((t + 1e-6) / beatLen).toLong()
        return n to (Math.floorMod(n, max(1, sigN).toLong()) == 0L)
    }

    /**
     * A new reading while playing: a small difference from what the phone was already showing is eased in, so the
     * playhead and the beat light do not jump back and forth by a few milliseconds; a jump or a loop wrap is taken as is.
     */
    fun smooth(prev: LiveState?, next: LiveState): LiveState {
        if (prev == null || !prev.playing || !next.playing || prev.tempo != next.tempo) return next
        val mine = prev.now(next.at)
        val d = next.time - mine
        return if (kotlin.math.abs(d) < 0.08) next.copy(time = mine + d * 0.3) else next
    }
}

/** A track's waveform laid on the timeline by the helper: `perBeat` columns per beat from beat 0, each 0…255. */
class Wave(
    val t: Int, val perBeat: Int, val end: Double, val peaks: ByteArray, val clips: List<Triple<Double, Double, Boolean>>,
    val key: String
) {
    /** The loudest column between two beats, 0…1. */
    fun peak(b0: Double, b1: Double): Float {
        if (peaks.isEmpty()) return 0f
        val i0 = max(0, floor(b0 * perBeat).toInt())
        val i1 = minOf(peaks.size, max(i0 + 1, kotlin.math.ceil(b1 * perBeat).toInt()))
        var m = 0
        for (i in i0 until i1) { val v = peaks[i].toInt() and 0xFF; if (v > m) m = v }
        return m / 255f
    }
}

/**
 * Where the Live window is, as the helper sees it: found at all, on the screen now (not another Space, not minimised),
 * its place in the VNC picture's pixels (fx, fy, fw, fh), how much of it other apps' windows cover and which.
 */
data class WinInfo(
    val found: Boolean, val onScreen: Boolean, val front: Boolean, val covered: Double, val by: List<String>,
    val fx: Int, val fy: Int, val fw: Int, val fh: Int
)
