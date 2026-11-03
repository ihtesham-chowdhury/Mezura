# Mezura — Precision Mechanical Instruments for Android

<p align="center">
  <strong>Tactile, photorealistic, offline mechanical instruments for measuring time, rhythm, reading, and habits.</strong>
</p>

---

## Overview

**Mezura** (`com.instrument.mezura`, v1.0.10) is an Android application designed around the physical metaphors of authentic mechanical instruments. Every screen completely avoids generic flat Material Design UI; instead, every tool is engineered as high-precision, tactile hardware:

- **Split-flap digit drums, lathe-turned rotary jog wheels, and sunray antique brass gauges.**
- **Liquid Glass Navigation Dock:** Sleek floating liquid glass navigation bar with settled spring-animated amber indicator, single-line tabs with zero clipping, specular refractive highlights, and light haptic feedback.
- **The Grand Library Folio:** Spotless donor-and-feather archival parchment restoration, 3D procedural brass vernier ruler, 365-dot consistency matrix, and carved walnut millimeter tracks with sliding brass clasps.
- **7-Style Master Plate Report Cards:** Curated high-resolution share cards (`01..07`) with mathematical 5-star vectors, zero-bleed cover & header erasures, native Bengali typography (`NotoSansBengali.ttf`), and one-tap social export.
- **Library Shelf Grid:** Fixed 2-line title containers guaranteeing horizontal star alignment across cards, clean cover display for finished books, and progressive liquid glass bottom scrims.
- **Physical 3-pusher chronograph controls** with metallic knurling, chamfered caps, and authentic spring travel animations.
- **Fluid spring physics** throughout navigation sheets and floating controls (`spring(dampingRatio = 0.72f, stiffness = 500f)`).
- **100% Offline & Private:** Zero network tracking, zero external telemetry. All library data, reading sessions, routines, and habit matrices live on your device with JSON backup/restore and Excel export.

---

## The Instruments

### 1. Reading & The Grand Library Folio Subsystem

#### A. The Library Shelf
- **Horizontal Star Alignment:** Book titles are wrapped in a fixed-height 2-line container (`38.dp`), ensuring that single-line and two-line titles reserve identical vertical height so that the 5-star rating footers align to the exact same horizontal baseline across the shelf.
- **Clean Finished Book Covers:** Finished or rated books completely suppress progress lines on the book cover, showcasing pristine artwork alongside gold star ratings.
- **Progressive Liquid Glass Bottom Vignette:** A $92\text{dp}$ progressive vertical gradient scrim behind the floating navigation bar prevents scrolling covers from peeking raw underneath the dock.

#### B. The Counter ("The Caliper V3")
- **Hardware Aspect-Fitting:** Fitted to a `360 x 425` logical canvas with safe clearance above the floating bottom navigation bar.
- **3-Digit Split-Flap Display:** Large condensed bold numerals with realistic stacked card reels, top alignment notches, and 3.2px split axle hinges. Tap to trigger the direct page jump sheet.
- **Total Readout:** Laser-etched `/ 300 PAGES` in warm amber `#C98F47`.
- **Lathe-Turned Conical Platter:** Anisotropic 4-lobe brushed stainless steel reflection at 45°, 135°, 225°, 315° with concentric machine grooves and a sculpted liquid-metal kidney thumb dimple.
- **Rotary Jog-Shuttle Wheel:** 120 sharp polygon steel gear teeth, compact `-44°` bolted brake ear-lug, and `+45°` spring-loaded knurled lever for quick `+1 page` increments.
- **Micro Chrome Toggles:** Authentic bat-handle switches with laser-etched `HAPTIC` (Electric Cyan `#1AF2FF` LED) and `SOUND` (Emerald Green `#26FF78` LED).
- **Molten Neon-Orange Fader:** Fast-scrub vertical fader with a blazing molten orange-gold illuminated light bar below the handle. Strict drag-only routing prevents accidental jumps.

#### C. The Grand Library Folio Stats
- **Seamless Master Parchment Restoration:** Synthesizes spotless archival parchment bands at top (`y = 0.010f..0.124f`) and bottom (`y = 0.814f..0.982f`), cloning authentic paper grain and eliminating baked demo artifacts.
- **Procedural 3D Brass Vernier Ruler:** 4 discrete ranges (`7D`, `30D`, `YEAR`, `ALL`), 68 engraved millimeter graduations, and a smoothly gliding 3D brass carriage with left `<` pointer beak and stepped medallion.
- **Reading Consistency Matrix:** Bounded within `y = 0.737f..0.7886f` with 53 columns $\times$ 7 rows = 371 cells (or 365 days) and strictly labeled `"READING CONSISTENCY (365-DOT MATRIX)"`.
- **Clean Genre Distribution:** Section header matching Reading Velocity Curve, 4 dynamic genre rows, 3D carved walnut millimeter tracks with brass screws, and sliding 3D brass clasps.

#### D. Curated 7-Style Master Plate Report Cards
- Renumbered strictly `01` through `07` with zero gaps:
  1. `01 · ARCHIVAL`: Archival Brass & Split-Flap (`style_1.png`)
  2. `02 · LED MATRIX`: Gunmetal & Dot-Matrix LED (`style_2.png`)
  3. `03 · CLIPBOARD`: Warm Parchment & Charcoal Bento (`style_3.jpg`)
  4. `04 · CELESTIAL`: Astrolabe & Celestial Atlas (`style_4.jpg`)
  5. `05 · BLUEPRINT`: Drafting Blueprint Ledger (`style_5.jpg`)
  6. `06 · GLASS`: Smoked Frosted Glass & Bokeh (`style_6.jpg`)
  7. `07 · OBSIDIAN`: Minimalist Obsidian Editorial (`style_8.png`)
- Mathematical 5-star vectors (`drawCrispFiveStarsLeftToRight`), zero-bleed cover erasure (Coons patch smoothing, vertical smooth interpolation, vector copper paperclips), and high-res bitmap export.

---

### 2. Time Family
- **Mechanical Stopwatch:** Vintage chronograph with top winding crown (Reset All), arched looped bow, and dual knurled pushers at 10:30 (Lap Split) and 1:30 (Start/Stop). Dual dials: Ivory Archival and Matte Smoked.
- **Pomodoro Timer:** Three distinct physical devices—*Module* (mechanical plastic), *Slab* (machined aluminium), and *Pocket* (tactile rubber membrane). Supports traditional countdown focus timers, custom durations, and continuous count-up reading integration sessions.
- **Hourglass:** Physics-based simulation of sand granular flow across four distinct profiles (Classic turned wood, Dusk, Prism iridescent glass, Dot matrix) and a mechanical flip clock.

---

### 3. Rhythm & Breath
- **Metronome:** Sample-accurate audio engine with Studio (rotary dial) and Pendulum (mahogany casing with sliding brass bob) interfaces.
- **Breathing Instrument:** Multi-mode guided breath pacer (Box Breathing, 4-7-8) with dynamic visual ring pacing and sequence engine duration override.

---

### 4. Habits & Living City
- **Habit Ledger:** Track core habits, long-term goals, milestones, and dreams. Quick edit pencil affordance (`✎`) and full detail editor allowing editing and deleting habits, setting target completion dates, descriptions, and progress.
- **Standardized Habit Tiers:** Uniform 48dp heights and 10sp typography across Light, Standard, and Core tiers.
- **The Living City:** An isometric voxel city canvas where completing habits and reading sessions awards building materials to construct towers and landmarks.

---

### 5. Yearline (Almanac)
- **365/366-Day Matrix:** Heat-map matrix tracking year-long show-up streaks.
- **Lens Filtering:** Instant toggle between all habits, specific dreams, and focus logs.

---

### 6. Home Hub (Sequence & Trace)
- **Sequence Engine:** Real-time guided daily routine tracker. Fully customizable: add, edit, reorder, or delete routines (Deep Work, Study, Morning Ritual). Automatically configures and initiates linked instruments with precise custom step durations (e.g., 2m breathing, 45m Pomodoro) and advances automatically.
- **Trace Ledger:** Immutable, local audit log recording every completed session and reading page count, with status bar insets.

---

### 7. Refractive Liquid Glass Navigation Dock
- **Refractive Liquid Glass Body:** Smoked vertical liquid glass gradient (`#E61B1F28` to `#F50C0E13`) encased in a 1dp specular refractive border stroke with soft ambient drop shadow.
- **Settled Spring Amber Indicator:** Thin amber pill bar (`Color(0xCCF5B851)`, 18dp x 2.5dp) settled smoothly below the active icon with Apple-grade spring physics (`spring(dampingRatio = 0.9f, stiffness = 450f)`).
- **Zero-Truncation Typography:** Tab labels rendered on a single line (`maxLines = 1, softWrap = false`) at `11.sp` with compact padding, guaranteeing full visibility of "HABITS", "READING", "BREATHING", and "YEARLINE".
- **Tactile Detents:** Responsive spring press scaling (`0.94f`) paired with an instant light haptic tick and silent tap action.
- **Home Hub Red Beacon:** One-tap navigation to Home Hub routines and trace audit ledger, with long-press startup defaults.

---

## Architecture & Tech Stack

- **Platform:** Android 8.0+ (API 26+) · Target SDK 36 · Compile SDK 36
- **Language:** Kotlin 2.0.21
- **UI Framework:** Jetpack Compose 1.7.0 (Custom Canvas rendering with zero Material3 dependencies)
- **Audio Engine:** Pre-baked PCM `AudioTrack` synthesizers with zero decode latency
- **Haptic Engine:** Device-native vibration actuators (`VibrationEffect`)
- **Persistence:** Local JSON file storage + SharedPreferences, with Excel `.xls` export and full JSON database backup/restore

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/ihtesham-chowdhury/Mezura.git
cd Mezura

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest
```

Compiled APKs will be output to `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.

---

## Verified Build Artifacts (v1.0.10)

| File | Size | SHA-256 Checksum |
| :--- | :--- | :--- |
| **`Mezura-v1.0.10-release.apk`** | 127.84 MB (134,045,504 bytes) | `168b0af4cfcd93063e9a31da615695c6eb9af1daccb89ea86e63dd2bdd95c91f` |
| **`Mezura-v1.0.10-debug.apk`** | 132.44 MB (138,873,665 bytes) | `5471ab6ef2b384d08db446bfa1704a624913650150289fb89902e88aa2106949` |
| **`Mezura.apk`** | 127.84 MB (134,045,504 bytes) | `168b0af4cfcd93063e9a31da615695c6eb9af1daccb89ea86e63dd2bdd95c91f` |

---

## License

Personal project by [Ihtesham Chowdhury](https://github.com/ihtesham-chowdhury). All rights reserved.
