# Ndot Widgets — native Android project

Four widgets: a **sand timer** (the focus timer), a **mechanical stopwatch**, **Age in
Motion**, and a **Sun Dial**. Colour and transparency are set once in the app and apply to all
of them.

I have not compiled this myself; Gradle's JVM can't spawn child processes in the sandbox this
was written in. Every resource reference is cross-checked by script in both directions, and
both new faces were drawn in a browser port first so the artwork could actually be looked at
before shipping - but "cross-checks cleanly" is not "compiles", so the build is the real test.

## Building it

**Android Studio (simplest):** open this folder, let it sync, run on a device or emulator.

**On-device with Termux**, using the root access you mentioned:
```bash
pkg install openjdk-17
# Android SDK command-line tools, if you don't already have them from the PWA setup:
# https://developer.android.com/studio#command-line-tools-only
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
export ANDROID_HOME=$HOME/android-sdk   # wherever sdkmanager put it
./gradlew assembleDebug
pm install -r app/build/outputs/apk/debug/app-debug.apk
```

## About the toolchain versions

Written in September 2026 against AGP 9.0.1 / Gradle 9.1.0 / Kotlin 2.4.0 - I verified these
against current release notes rather than assuming what I trained on (AGP had jumped a full
major version, 8→9, since my knowledge cutoff). AGP 9.0 made Kotlin support built-in and
technically deprecated the separate Kotlin Android plugin this project still applies - I
deliberately kept the classic setup (via `android.builtInKotlin=false` in `gradle.properties`,
a real, documented compatibility flag, not a hack) because I could verify its syntax with
total confidence, and couldn't fully verify the brand-new built-in-Kotlin DSL from here.
Google's own notes say that flag stops working in AGP 10.0. If you're building this on AGP 10+,
delete `android.builtInKotlin=false` and `android.newDsl=false` from `gradle.properties` and
the `kotlin("android")` line from `app/build.gradle.kts`, and follow AGP's own built-in-Kotlin
migration guide for the couple of DSL changes that need.

## The two clocks, and why they're different now

The focus timer and the sand timer used to be the same countdown wearing two faces, which is
exactly why it felt redundant. Now there is one of each kind:

- **Sand timer** - counts *down*. Tap to start or pause, tap the time underneath to reset.
  When a session ends it buzzes, posts an alert and rolls into a break; pick focus and break
  lengths in the app. Glass, brass caps, a sand surface that dips toward the neck, a mound
  that builds underneath, and loose grains scattering around the stream.
- **Stopwatch** - counts *up*, with laps. Tap the dial to start and stop; tap the crown to
  open the full-size face.

## About the smooth sweep

A widget tile cannot sweep a second hand. RemoteViews updates are IPC calls to the launcher,
and roughly one a second is the responsible ceiling - nowhere near the sixty a smooth sweep
needs. That is true of every app on Android, not just this one.

So the stopwatch exists twice. On the home screen it ticks once a second, which is how a
quartz stopwatch behaves anyway. Tapping the crown opens `StopwatchActivity`, a normal window
with no such limit: there the hand sweeps at full frame rate, the pusher clicks, and laps
accumulate. Both faces are drawn by the same `StopwatchRenderer`, so they are the same watch.

The click is synthesised at runtime rather than shipped as an audio file - a short noise
transient plus a damped tone, which is roughly what a metal pusher sounds like.

## Layout

```
app/src/main/kotlin/rocks/ndot/widgets/
  MainActivity.kt          - permission grants + "how to add a widget"
  common/                  - SolarMath (sunrise/sunset), SundialRenderer (the lens bitmap),
                              WidgetPrefs, WidgetSizing, BootReceiver
  common/Appearance.kt        - the app-wide colour + transparency setting
  common/StopwatchRenderer.kt - the watch face, drawn not drawn-over
  common/SandTimerRenderer.kt - glass, brass, sand and falling grains
  common/Clicks.kt            - synthesised pusher clicks
  widgets/TimerEngine.kt      - the countdown
  widgets/StopwatchEngine.kt  - the count-up, with laps
  widgets/TimerService.kt     - repaints whichever face is moving
  ui/StopwatchActivity.kt     - the full-size face, at 60fps
```
