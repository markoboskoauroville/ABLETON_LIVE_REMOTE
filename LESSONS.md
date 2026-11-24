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
