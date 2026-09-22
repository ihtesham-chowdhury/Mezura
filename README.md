# Ndot — instruments for everyday life

An Android app of small, beautiful instruments - a stopwatch, a Pomodoro timer, an hourglass,
a metronome, a calendar and a habit ledger - plus the four home-screen widgets it started as
(sand timer, stopwatch, Age in Motion, Sun Dial), which are unchanged.

Everything is offline: no account, no network, no analytics. State lives in SharedPreferences.

## Status - read this first

The whole Kotlin codebase **type-checks with zero errors** against the real Android, AndroidX
and Compose libraries (`tools/typecheck.py`), and `aapt2` compiles every resource. The complex
drawings - the four hourglasses, three Pomodoro devices, two metronomes, the calendars, the
home screen and the drawer bar - were designed and looked at in a browser canvas first and then
ported number-for-number.

It has **not** been built into an APK or run on a phone yet: Gradle cannot run in the
environment it was written in. So the first Android Studio build is the real test. If
something fails, the error message is enough to fix it.

## Building it

Open this folder in Android Studio, let Gradle sync, then **Build → Generate Signed App Bundle
or APK → APK → release** (the keystore is already set up - see `SIGNING.md`), or just press Run
for a debug build.

`versionCode` is now 2, so a release build installs over the v1 widgets app and keeps your
widgets and settings.

**Toolchain:** AGP 9.0.1, Gradle 9.1.0, Kotlin 2.4.0 (classic `kotlin-android` plugin) plus the
`kotlin.plugin.compose` plugin at the same 2.4.0. Compose is pinned to **1.11.4** and Lifecycle to
2.10.0 because Compose 1.12 and Lifecycle 2.11 require compileSdk 37, which isn't installed here.

## How it's organised

```
MainActivity.kt              the whole app: one Compose activity, edge to edge
WidgetSettingsActivity.kt    the old settings screen, now only for the widgets
app/        Drawer (families -> instruments), InstrumentsApp (routing, system bars),
            Home, Settings, AppPrefs
kit/        Theme (Ink, Fonts, frame ticker), Feedback (synthesised sounds + haptics),
            PixelFont (5x7 dot-matrix alphabet), Components (chips, toggles, dialogs)
instruments/
  stopwatch/   the chronograph: crown = start/stop, pusher = lap/reset
  pomodoro/    DeviceArt (Module, Slab, Pocket) + the screen
  hourglass/   GlassProfile (shape + sand volume maths), GlassRenderer (4 designs), FlipClock
  metronome/   Metronome (sample-accurate audio thread), MetronomeArt (Studio, Pendulum)
  calendar/    Glass and Paper, month and year
  habits/      HabitStore (JSON), the ledger and a habit's page
widgets/    the widget providers and the engines both sides share
common/     widget renderers, appearance, boot handling
```

The engines (`TimerEngine`, `StopwatchEngine`, `HourglassEngine`) are wall-clock based and
shared by the widgets and the app, so the sand-timer widget *is* the in-app Pomodoro session.
`TimerService` finishes any timer on time with the app closed and posts the alert; with the app
open it plays the chime instead, and the two never both fire.

## Checking it without Gradle

```bash
python tools/fetch_deps.py     # once: downloads anything the Gradle cache lacks
python tools/typecheck.py      # runs kotlinc in-process with the Compose plugin
```

## Known limits in this version

- The metronome plays only while it's on screen; leaving it, or the app, stops it.
- The app is portrait-only; every face is composed for a tall screen.
- Shadows under keys and panels need Android 9+; on 8.x they simply don't appear.
- A notification permission prompt appears once, the first time you start a timer (Android 13+).
