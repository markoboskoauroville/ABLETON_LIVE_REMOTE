# Ableton Live Remote (Mantra): takeover

**24.11.2026: the Mac side lives in Ableton Live Assistant.** The separate helper "Ableton Live Remote (Mantra).app"
(this repo's `mac/`, releases mac-v1…mac-v6) is retired: quit, removed from /Applications, its source, workflow and
releases removed here. Its code is the PHONE REMOTE module of Ableton Live Assistant (repo ABLETON_LIVE_ACTIONS,
`app/Remote*.swift`, `app/PlaybackMode.swift`, from v34). The phone works only while Ableton Live Assistant runs (the
star's row opens it); otherwise the phone says "Ableton Live Assistant is not running on the Mac". The sections below
that name `mac/app/...` describe what is now in ABLETON_LIVE_ACTIONS under the Remote* names.

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

## The insert point and the playback mode (Android v7, Mac v6, AbletonOSC 24.11.2026)

- Live's insert point is `Song.start_time`: Play and Space start there. Setting `current_song_time` does NOT move it
  (the cause of "every time I click play, it plays from the beginning"). A tap on the waveform (`c=jump`) now sets both;
  the phone draws the insert point as an amber flag top and bottom with a dotted line, the playhead stays a white line.
- The playback mode is Live's own behaviour after ANY stop, kept in `~/.config/mantra-live-playback-mode`
  (`return` | `continue`) and run inside AbletonOSC (a watcher on its 100 ms tick): RETURN (DAW) puts the playhead back
  on the insert point; CONTINUE (DaVinci Resolve) moves the insert point to where it stopped. Works with the phone
  closed and the helper quit (verified with Live's own Space key). `/live/mantra/playback_mode [mode]`,
  `/live/mantra/start_time [beats]`; the helper's state carries `insert` and `mode`; the phone's fifth transport key is
  the pictogram (curl back = RETURN, run on to a filled second flag = CONTINUE).
- Ableton Live Assistant's own Space logic (Transport.swift, flag "follow": stop and plant the insert point on the
  playhead) is the same idea for the Space key only; with A1 running and "follow" on, RETURN and A1 disagree for a
  Space stop (A1 plants after the watcher put the playhead back, so the result is still RETURN: verified only by
  reading the timing, A1 was not running on 24.11.2026). Step 3 (A1 as the helper) makes A1's switch and this file one.
- NEW TRACK asks AUDIO / MIDI and AT THE BEGINNING (index 0) / AT THE END (-1); the list scrolls to the new track and
  lights it for 3 s. Live renumbers names that start with a track number ("2-01 Drums" → "3-01 Drums") when a track
  goes in front of them, and back when it is removed.

## PAUSE (Android v9, Assistant v36, AbletonOSC 24.11.2026)

- The paused state lives in AbletonOSC (`manager._mantra_paused`): `/live/mantra/pause` (playing: stop exactly here, the
  watcher leaves it alone, a take being recorded ends with the record button off; paused: go on), `/live/mantra/play`
  (paused: Live's continue_playing from the paused place; else Live's Play from the insert point), `/live/mantra/stop`
  (paused: the mode's stop now), `/live/mantra/paused`. A new insert point (a tap) ends a pause; any play ends it.
- The phone: two rows of three keys (PLAY PAUSE STOP / REC METRO mode), 70 dp tall; REC's circle red always, the key
  filled red while recording; PAUSE lit amber while paused. The Assistant has no transport area, so no PAUSE there.

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
