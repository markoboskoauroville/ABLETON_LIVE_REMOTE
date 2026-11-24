# Ableton Live Remote (Mantra): takeover

**What it is.** An Android remote for Ableton Live (Stream Deck look, scenario tabs) and its Mac helper. The phone shows
and controls the set that is open in Live; the helper on the Mac is the only thing that talks to Live.

    phone (android/)  ⇄  one port, 8966, HTTP + WebSocket  ⇄  Mac helper (mac/)  ⇄  UDP 11010/11011  ⇄  AbletonOSC in Live

## The pieces

- **AbletonOSC** (`~/Music/Ableton/User Library/Remote Scripts/AbletonOSC`) got two Mantra handlers on 24.11.2026,
  `/live/mantra/remote/state` (transport + every track in ONE answer) and `/live/mantra/remote/clips <track>` (every
  audio clip on a track's timeline with file path, markers, warp markers). Kept in
  `ABLETON_LIVE_ACTIONS/abletonosc/mantra-application.patch` (README there).
- **Mac helper** `mac/app`: `Live.swift` asks the state 3–4 times a second through the shared turn
  (`LiveTurn.swift`: flock ~/.config/liveosc.lock, bind 11011, ask, let go; the same as A1, OXI and every Mantra app),
  only while a phone is connected; orders go to 11010 with no turn. `Server.swift`: NWListener on 8966 (Ports.record
  `ableton-live-remote`), Bonjour `_mantralive._tcp`, a hand-written WebSocket (`/ws`), `/hello`, `/state`, `/do?c=…`,
  `/wave?t=N`, `/window`; `Cable` sets `adb reverse tcp:8966` on every adb phone. `Peaks.swift`: AVAudioFile → loudest
  sample per 5 ms per file (cached in ~/Library/Caches/community.auroville.abletonliveremote/peaks), laid on the timeline
  at 32 columns per beat (unwarped: start marker in seconds + beats at the tempo; warped: warp markers).
- **Android** `android/app/src/main/java/com/mantra/abletonliveremote`: `Link.kt` (discovery cable → last → Bonjour →
  typed → Tailscale 100.79.251.19, WebSocket with OkHttp, reconnects), `Model.kt` (pure Kotlin: state, beat maths,
  position text), `MainActivity.kt` (tabs REC / SESSION, the REC keys, waveform, track list), `AppUpdater.kt` (kit).

## The orders (text over the WebSocket, or GET /do?…)

`c=play` · `c=stop` (also ends arrangement record) · `c=rec&v=1|0` (arrangement record; starts playing like Live's
button) · `c=metro&v=1|0` · `c=loop&v=1|0` · `c=arm&t=N&v=1|0` · `c=mute&t=N&v=…` · `c=solo&t=N&v=…` · `c=jump&b=<beats>`
· `c=new&k=audio|midi` (a track at the end of the set).

## Install

    gh release download v<N> --repo markoboskoauroville/ABLETON_LIVE_REMOTE --pattern '*.apk' -D /tmp/alr --clobber
    adb install -r /tmp/alr/<N>-ableton-live-remote-v<N>.apk
    gh release download mac-v<N> --repo markoboskoauroville/ABLETON_LIVE_REMOTE --pattern app.zip -D /tmp/alrm --clobber
    ditto -x -k /tmp/alrm/app.zip /Applications && codesign --force -s "Mantra Apps" "/Applications/Ableton Live Remote (Mantra).app"

## State

See the dated lines at the end (newest last).

## SESSION (Android v3+, Mac v4+)

- `Viewer.kt` + `Rfb.kt` (copied from MANTRA_REMOTE_VIEWING; changed: only the crop is a Bitmap, each frame logged as
  `ALRvnc frame <wall ms> <rect> <bytes>` for delay tests). View only: nothing but frame requests is sent.
- Route: the helper's own route. Over the cable the helper sets `adb reverse tcp:15900 tcp:5900` (the phone's own 5900 may
  be a VNC server app); on Wi-Fi / Tailscale the Mac's 5900 directly.
- Auth: the Mac's Screen Sharing VNC password (security type 2; the Mac offers 30, 33, 36, 2, 35), asked once on the
  phone, kept encrypted by the Android Keystore (`Secrets.kt`), never in the repo or a log. PASSWORD key changes it.
- Crop: `/window` gives the Live window in VNC pixels (each screen at its top-left in points, drawn at its pixel size).
  The phone asks every second and follows a move/resize. Pinch zoom 1–8x, drag to pan, double tap fits, FULL SCREEN
  hides the bars (✕ or Back leaves). The tab may turn sideways; REC stays portrait.
- Honest limits: Apple's server sends the SCREEN, not the window. When other windows lie over Live (`covered`, `by`),
  or Live is on another Space / minimised / hidden (`onScreen` false), the phone shows a note instead of a wrong
  picture (SHOW ANYWAY for the covered case). Nothing on the Mac is moved or raised from the phone. A capture of only the
  Live window (ScreenCaptureKit in the helper) would see through covering windows; it needs Screen Recording
  permission for the helper and is not built.

## Switches

- `~/.config/ableton-live-remote/no-cable` (Mac): the helper makes no adb reverse and removes its own; for Wi-Fi tests.

## Next

- MIX tab: solo and mute for every track (the state already carries them; the orders `c=mute`, `c=solo` exist). The
  tab bar takes it as a third pair in `TABS` and a branch in `RemoteScreen`.

- **24.11.2026:** Android v2 (Latest) and Mac helper v3, all runs green. Verified on the Pixel 7 emulator over the cable
  route (adb reverse) against Live 12.4.6: metronome, PLAY, STOP, RECORD (record_mode on + playing, STOP ends both),
  ARM on a MIDI track, NEW TRACK audio and MIDI (both appeared, both deleted again), tap-to-jump exact to 0.01 beat in
  WHOLE and LOOP, LOOP view follows the brace moved in Live, beat light red on the downbeat and green on the others,
  changes made in Live (metronome, loop) shown on the phone. Audio tracks do not arm: Live 12.4.6's log says its audio
  input device is "No Device" (the RØDE NT1 used under 12.4.16 is not connected); the phone says so (v2 / Mac v3).
  NOT yet on the real Pixel 7: it was rebooted and not unlocked since, so no app can start there (needs Marko's PIN once).

- **24.11.2026 (later), Android v6 (Latest) and Mac helper v5:** SESSION tab built (password asked on the phone; the
  first Screen Sharing sign-in is Marko's, so no picture was seen yet and frame rate / delay are not measured). On the
  real Pixel 7 (unlocked by Marko) in the copy "STRESS TEST 24.11.2026.als" (beside the untouched
  "Korak_u_Nepoznato.als" in ~/Music/Ableton/Korak_u_Nepoznato Project): every REC control both ways, a real take
  through the RØDE NT1 started and stopped from the phone (clip on the timeline, file in Samples/Recorded, its waveform
  on the phone without a restart), stress cases (rapid taps, transport hammering, arm storms, 25 tracks created and
  deleted, the shown track deleted and renamed, tempo/signature/loop changes while playing, 11 min play with no drift,
  helper killed and restarted, background/foreground, Wi-Fi by Bonjour and by a typed address). Fixed: v5 rename keeps
  the waveform, heartbeat; v6 Wi-Fi timeouts. Live left with the copy open, stopped, metronome off, saved.
