# Lessons

- **AbletonOSC answers to the last sender's IP, at port 11011, and many Mantra apps share it.** The registry rows
  `abletonosc-router` (11002) and `abletonosc-answers` (11011) in ~/.config/mantra-ports.json are from before 31.10.2026
  (pid gone, nothing binds them): since then nobody keeps 11011, a question takes the turn (LiveTurn.swift / liveosc.py).
  So the helper asks ONE combined question per look (`/live/mantra/remote/state`) instead of dozens of small ones, and
  never uses `start_listen` (a listener pushes to whoever asked last). 24.11.2026.
- **`/live/song/get/track_data` cannot be read through liveosc.py's `ask`**: its answer's first value is not the track
  index, which `ask` checks for track questions; and `arm` raises on group tracks. Hence the Mantra handler. 24.11.2026.
- **Clip markers are seconds when a clip is not warped, beats when it is** (Live 12.4.6, checked on the test set: the
  Vasyl stems unwarped end_marker 344.52 s; the Suno imports warped end_marker 721.04 beats). 24.11.2026.
- **AbletonOSC works once per tick (100 ms)**: one question costs 50–100 ms of the shared turn. 24.11.2026.
- **A Kotlin `var full` in an Activity has a JVM setter `setFull(Boolean)`**: a `fun setFull(on: Boolean)` beside it is a
  "Platform declaration clash" (Android v3 failed on it; the fix went out as v4, the number is never reused). 24.11.2026.
- **`pkill -f AbletonLiveRemote.app` matches nothing**: the bundle is "Ableton Live Remote (Mantra).app"; the process is
  `AbletonLiveRemote` (`pkill -x AbletonLiveRemote`). A first "kill the helper" stress run killed nothing and was read as
  "the phone shows a dead helper as connected"; the real kill was seen within 2 s. 24.11.2026.
- **A typed LAN address must get more than 0.9 s**: the phone on 2.4 GHz Wi-Fi with Tailscale on missed the first
  /hello and fell through to Tailscale; v6 gives Wi-Fi 2.5 s and Bonjour 4 s. 24.11.2026.
- **The Mac's LAN address is 192.168.1.102 (en17, Ethernet) on 24.11.2026**, not 192.168.1.100; en0 has 10.250.79.41.
  Bonjour advertises all three; the phone found the right one. 24.11.2026.
