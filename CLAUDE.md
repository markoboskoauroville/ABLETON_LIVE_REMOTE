# ABLETON_LIVE_REMOTE: how a session works here

- Two apps: `android/` (Ableton Live Remote (Mantra), com.mantra.abletonliveremote, Kotlin + Compose, releases vN marked
  Latest) and `mac/` (the helper "Ableton Live Remote (Mantra).app", Swift, releases mac-vN, not Latest).
- NOTHING compiles on the Mac: every build is the GitHub Actions run on push to main (.github/workflows/android.yml,
  mac.yml). Whole-number versions: `appVersion` in android/gradle.properties, `let VERSION` in mac/app/App.swift.
- Only branch main. Public, unpromoted: no README, no description (TAKEOVER.md says what it is).
- The phone never talks to AbletonOSC: only the helper does, through the shared turn on 11011 (mac/app/LiveTurn.swift,
  copied from ABLETON_LIVE_ACTIONS). Never quit, save or close Marko's set; no destructive OSC.
- Marko's requests word for word: momentaryupdates.md. State: TAKEOVER.md. Mistakes: LESSONS.md.
- Since 24.11.2026 the Mac side is Ableton Live Assistant's PHONE REMOTE module (ABLETON_LIVE_ACTIONS); `mac/` is gone.
