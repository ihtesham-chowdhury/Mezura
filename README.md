# Mezura — Precision Mechanical Instruments for Android

<p align="center">
  <strong>Tactile, mechanical, offline instruments for measuring time, rhythm, reading, and habits.</strong>
</p>

---

## Overview

**Mezura** is an Android application designed around the physical metaphors of vintage instruments. Every screen avoids standard flat Material Design UI; instead, every tool is rendered as high-precision, tactile hardware:

- **Split-flap page counters, lathe-turned brushed steel jog wheels, and sunray antique brass gauges.**
- **Conical metallic shaders, molten neon illuminated light bars, and phosphor digital LEDs.**
- **Fluid spring physics** throughout navigation sheets and floating controls (`spring(dampingRatio = 0.72f, stiffness = 500f)`).
- **100% Offline & Private:** Zero network tracking, zero external backends. All library data, reading sessions, routines, and habit matrices live on your device with JSON backup/restore and Excel export.

---

## The Instruments

### 1. The Counter (Reading & Focus Instrument)
A precision mechanical reading instrument inspired by industrial analog measuring tools:
- **3-Digit Split-Flap Display:** Large condensed bold numerals with realistic stacked card reels, top alignment notches, and 3.2px split axle hinges.
- **Total Readout:** Clear external `/ 300 PAGES` (or `%` for e-readers and Kindle progress).
- **Lathe-Turned Conical Platter:** Anisotropic 4-lobe brushed stainless steel reflection at 45°, 135°, 225°, 315° with concentric machine grooves and a deeply sculpted liquid-metal kidney thumb dimple.
- **Rotary Jog-Shuttle Wheel:** 120 sharp polygon steel gear teeth, compact `-44°` bolted brake ear-lug, and `+45°` spring-loaded knurled lever for quick `+1 page` increments.
- **Micro Chrome Toggles:** Authentic bat-handle switches with laser-etched `HAPTIC` (with Electric Cyan `#1AF2FF` LED) and `SOUND` (with Emerald Green `#26FF78` LED).
- **Molten Neon-Orange Fader:** Fast-scrub vertical fader with a blazing molten orange-gold illuminated light bar below the handle and glowing pointer arrow. Strict drag-only routing prevents accidental jumps.

### 2. Time Family
- **Mechanical Stopwatch:** Vintage chronograph with top winding crown, arched looped bow, and dual knurled pushers at 10:30 and 1:30. Features dual dials: Ivory Archival and Matte Smoked.
- **Pomodoro Timer:** Three distinct physical devices—*Module* (mechanical plastic), *Slab* (machined aluminium), and *Pocket* (tactile rubber membrane). Supports both traditional countdown focus timers and continuous count-up sessions.
- **Hourglass:** Physics-based simulation of sand granular flow across four distinct profiles (Vessel, Fluted, Teardrop, Cylinder) and a mechanical flip clock.

### 3. Rhythm & Breath
- **Metronome:** Sample-accurate audio engine with Studio (rotary dial) and Pendulum interfaces.
- **Breathing Instrument:** Multi-mode guided breath pacer (Box Breathing, 4-7-8, Cyclic Sighing) with dynamic harmonic auditory cues.

### 4. Habits & Living City
- **Habit Ledger:** Track core habits, long-term goals, milestones, and dreams. Full detail editor allows setting target completion dates, descriptions, and progress.
- **The Living City:** An isometric voxel city canvas where completing habits and reading sessions awards building materials to construct towers and landmarks.

### 5. Yearline (Almanac)
- **365/366-Day Matrix:** Heat-map matrix tracking year-long show-up streaks.
- **Lens Filtering:** Instant toggle between all habits, specific dreams, and focus logs.

### 6. Home Hub (Sequence & Trace)
- **Sequence Engine:** Real-time guided daily routine tracker. Fully customizable: add, edit, reorder, or delete routines (Deep Work, Study, Morning Ritual) with direct links to the relevant instruments.
- **Trace Ledger:** Immutable, local audit log recording every completed session and reading page count.

### 7. Android Home-Screen Widgets
- **Calendar Widgets:** Clean glass and archival paper month/week widgets.
- **Hourglass & Timer Widgets:** Active live countdowns and glanceable progress directly on your home screen.

---

## Architecture & Tech Stack

- **Platform:** Android 8.0+ (API 26+) · Target SDK 36
- **Language:** Kotlin 2.0.21
- **UI Framework:** Jetpack Compose 1.7.0 (with zero Material3 dependencies)
- **Audio Engine:** Pre-baked PCM `AudioTrack` synthesizers with zero decode latency
- **Haptic Engine:** Device-native vibration actuators (`VibrationEffect`)
- **Persistence:** Local JSON file storage + SharedPreferences, with Excel `.xls` export and JSON backup/restore

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/ihtesham-chowdhury/Mezura.git
cd Mezura

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

Compiled APKs will be output to `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.

---

## License

Personal project by [Ihtesham Chowdhury](https://github.com/ihtesham-chowdhury). All rights reserved.
