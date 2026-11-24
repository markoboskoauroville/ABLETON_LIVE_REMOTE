---
name: ableton-live-remote
description: Ableton Live Remote (Mantra), Marko's Android remote for Ableton Live on the Mac (repo ABLETON_LIVE_REMOTE): REC transport, waveform, tracks, SESSION VNC view, ASSISTANT tab; its server is the PHONE REMOTE module of Ableton Live Assistant. Use for ANY Ableton Live Remote / phone remote for Live / record from the phone task.
---
# Ableton Live Remote (Mantra): the phone's hand on Live

Read first: repo TAKEOVER.md (state), LESSONS.md, momentaryupdates.md (his words). Public, no README.

## Facts (24.11.2026)
- Android only (com.mantra.abletonliveremote, Kotlin + Compose), v1 to v9 in one day, v9 Latest. Built only by GitHub Actions; whole-number versions (`appVersion` in android/gradle.properties); a pushed number is never reused (v3 failed to build).
- The Mac side is NOT here: Ableton Live Assistant (repo ABLETON_LIVE_ACTIONS) PHONE REMOTE module serves port 8966 (Bonjour _mantralive._tcp, adb reverse, 15900 to 5900). The own helper (mac-v1..v6) is retired. The phone works only while the Assistant runs.
- Tabs: REC (PLAY/PAUSE/STOP/REC/METRO/playback mode in two rows of three, beat LED, position/tempo, one selectable waveform, tap-to-jump, insert-point marker, WHOLE/LOOP, track list with ARM, NEW TRACK audio/MIDI at beginning/end), SESSION (VNC of the Live window from Screen Sharing 5900; needs Marko's Screen Sharing password once on the phone; picture not yet seen), ASSISTANT (switches the Assistant's modules).
- The phone never talks to AbletonOSC (it replies to the last sender only). Never quit, save or close Marko's set. Test in the copy "STRESS TEST 24.11.2026.als", never the original.
- Track identity is Live's `_live_ptr`; Live renumbers names that begin with a number.

## Run
1. Push to main, read the Actions run, `gh release download`, `adb -s 41241FDH2S10SJ install -r` (real Pixel 7; wait while Marko uses it) or the Pixel_7_API_35 emulator.
2. Start the Assistant first, then test every control both ways.

## Mistakes already made
- A worker tried to carry a stored Screen Sharing password from another app into this one (refused): never move credentials between apps; ask Marko once.
- Audio tracks did not arm while Live's input device was "No Device".

## Run log
- 24.11.2026: v1 to v9, helper retired into the Assistant v34 to v36, real take recorded from the phone, stress test.
