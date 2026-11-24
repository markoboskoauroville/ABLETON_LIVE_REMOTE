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

## Next

- v2: SESSION tab: the Ableton window over VNC (RFB client from MANTRA_REMOTE_VIEWING), cropped to `/window`.
- MIX tab: solo and mute for every track (the state already carries them; the orders exist).
