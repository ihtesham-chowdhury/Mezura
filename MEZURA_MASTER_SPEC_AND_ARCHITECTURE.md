# MEZURA — Master Technical Architecture & Specification
**Application ID:** `com.instrument.mezura`  
**Version:** `1.0.3` (VersionCode `4`)  
**Platform:** Android (Min SDK 26, Target SDK 36, Compiled with Android SDK 36, Jetpack Compose 1.7.0, Kotlin 2.0.21)

---

## 1. Executive Summary & Design Ethos

**Mezura** (formerly Ndot) is a set of tactile mechanical instruments designed for measuring time, rhythm, reading progress, habits, and yearlines. 
Every instrument avoids generic Material Design controls; instead, every screen is rendered as high-precision physical hardware:
- Cast-metal dials, engraved bezels, split-flap counters, milled jog wheels, and phosphor LEDs.
- Fluid Apple-like spring dynamics (`slideInVertically`, `scaleIn`, `spring(dampingRatio = 0.72f, stiffness = 500f)`).
- Zero external backend dependencies: 100% local-first data storage on device with JSON backup/restore and Excel export.

---

## 2. Core Architecture & Navigation

```
                       ┌────────────────────────┐
                       │      InstrumentsApp    │
                       └───────────┬────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         ▼                         ▼                         ▼
   ┌───────────┐             ┌───────────┐             ┌───────────┐
   │ HomeScreen│             │ DrawerBar │             │Settings/  │
   │(Seq/Trace)│             │(Navigation│             │Backup/    │
   └───────────┘             └─────┬─────┘             │About      │
                                   │                   └───────────┘
         ┌────────────┬────────────┼────────────┬────────────┬────────────┐
         ▼            ▼            ▼            ▼            ▼            ▼
     [ TIME ]     [ RHYTHM ]  [ CALENDAR ]  [ HABITS ]   [ READING ]  [ YEARLINE ]
    • Stopwatch  • Metronome  • Apple Glass • Today/Habits• Shelf      • Full Year
    • Pomodoro                • Archival    • City/Towers• Counter     • Streak Flame
    • Hourglass                 Paper       • Goals/Dreams• Stats/Log   • Lens Filter
```

### Navigation & Fluid Spring Physics
- **DrawerBar:** A floating pill navigation bar with Apple-like spring physics. Tapping a multi-tool family (e.g. `TIME`) expands a secondary pill above the bar with fluid spring dampening (`dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow`).
- **Sheets (`NdotSheet`):** Custom bottom sheet with Apple spring physics (`slideInVertically(spring(dampingRatio = 0.72f, stiffness = 500f)) + fadeIn() + scaleIn(initialScale = 0.93f)`).

---

## 3. Instrument Specifications

### 3.1. Home Hub (Sequence & Trace)
- **Sequence Engine:** Real-time guided daily routine tracker.
  - Supports preset routines (Deep Work, Study, Morning Ritual).
  - Full interactive customization: create new custom routines, edit steps (reorder, duration, instrument links), and delete routines.
  - Links directly to the real instrument for active steps (no redundant duplicated timers on Home).
- **Trace Ledger:** Evidence-based audit log recording all completed focus sessions, reading logs, and sequence steps.

### 3.2. Time Family
1. **Stopwatch (`StopwatchScreen`):**
   - Faithful recreation of vintage mechanical chronographs: top winding crown with knurled collar and arched hanging looped bow.
   - Dual knurled pushers at 10:30 (-126°) and 1:30 (-54°) with stepped collars.
   - Dual dial options: Ivory Archival dial and Matte Smoked dial with "MEZURA" branding.
2. **Pomodoro (`PomodoroScreen`):**
   - Countdown presets: 10, 25, 45, 60, 90 mins focus; 5, 10, 15, 30 mins breaks.
   - **Count-Up / Unlimited Reading Mode:** Stopwatch-style count-up from 0. On stop, it opens the session logging sheet, automatically recording minutes elapsed into the active reading book with page/percentage tracking.
3. **Hourglass (`HourglassScreen`):**
   - Realistic granular sand physics simulation with tilting physics.

### 3.3. Rhythm Family
- **Metronome (`MetronomeScreen`):**
  - Mechanical pendulum oscillation with weighted bob positioning.
  - Audio detents with microsecond precision, Italian tempo markings (Largo, Moderato, Presto).

### 3.4. Calendar & Widgets
- **In-App Calendar (`CalendarScreen`):**
  - **Apple Glass:** Smoked glass plate floating over moving ambient room blobs. Title-case month title (`"October 2026"`), circular pill chevrons (`<` `>`), 3-letter uppercase weekdays (`SUN`, `MON`...), solid `#FF3B30` red today circle, and 2-line bottom info (`Sunday, 18 October 2026` + `DAY 291 OF 365 · WEEK 43 · TODAY`).
  - **Archival Paper:** Warm textured desk card with spiral binder holes.
- **Home Screen Widgets (`CalendarWidgetProvider`):**
  - 4x4 Month Calendar Widget and 4x2 Week Widget.
  - Configurable finish (Apple Glass / Archival Paper), week start day (Sat, Sun, Mon), and activity dots.
  - **Bottom Info Toggle:** Users can choose whether to display the bottom date and yearline info. When disabled, the card plate expands to fill the entire widget bounds cleanly.
  - Full horizontal and vertical widget resizing enabled.

### 3.5. Habits & Living City
- **HabitMasterStore:** Local transactional storage for daily habits, goals, milestones, and dreams.
- **Goal & Dream Detail Editor:** Full interactive sheet allowing users to set target completion dates, rename goals/dreams, view progress, and update status.
- **Living City (`CityCanvas`):** Isometric city view where completing habits and reading sessions awards bricks to build towers and landmarks. Streamlined header title bar without awkward village clutter.

### 3.6. Reading & Counter (`FolioScreen` & `VernierCaliperView`)
- **The Counter (Caliper V3 Complete Video Bug-Fix & Photorealistic Hardware UI):**
  - **Recessed Split-Flap Display:** `204 x 115` deep cavity containing strictly the 3 large split-flap digit cards (`0`, `0`, `3`) filling 94% of the cavity. Rendered with authentic condensed mechanical split-flap typography (`sans-serif-condensed`, Bold), 3 top metallic pointer notches, stacked bottom card edges, and 3.2px horizontal split hinge with mechanical axle pins.
  - **Outside Total Pages Readout:** `/ 300 PAGES` sits cleanly outside to the right of the split-flap cavity on the gunmetal faceplate below the brass gauge (`/ 300` at `x=248, y=119` in warm engraved amber `#C98F47`, `PAGES` at `x=259, y=138` in `#AE7B3B`).
  - **Top-Right Antique Brass Progress Gauge:** Recessed stepped bezel with 21 hairline calibration ticks (-135° to +135°), lathe-turned conical sunray brass dial (`Brush.sweepGradient` with 9 brass color stops), radial light depth, tapered crimson sweep needle, and compact watch pivot bushing.
  - **Central Rotary Jog Wheel:** 
    - 120 sharp polygon steel gear teeth (`outerR=100.5, innerR=97.2`).
    - Compact `-44°` gunmetal bolted brake ear-lug with complete clearance away from the `SOUND` switch.
    - `+45°` machined steel spring lever with 6 high-contrast knurled ridges (`+1 page` tap with spring-back animation).
    - Laser-etched `+1` with sculpted directional arrow and `+1 page` labels positioned cleanly outside the gear teeth.
    - 3D beveled champagne-brass collar with dual glowing neon-orange-red 12 o'clock index slits (`#FF4D1A` outer with `#FFF2B8` hot core) and ambient warm light spill on top of collar.
    - Warm satin-titanium conical brushed platter with 4-lobe anisotropic specular highlight (`Brush.sweepGradient` with 9 color stops at 45°, 135°, 225°, 315°) and repeating concentric lathe grooves.
    - **Liquid-Metal Concave Kidney Thumb Dimple:** Seamless 3D liquid-metal depression with zero cartoon strokes or outline lines, soft outer cushion bevel, radial gradient bowl, and diffused specular sheen.
    - **Permanent Gesture Persistence:** Keyless pointer detection prevents mid-drag gesture cancellation; rotation angle is persisted continuously so the wheel never snaps back on release.
  - **Micro Toggles with Laser-Etched Labels & Status LEDs:**
    - Left toggle at `(55, 198)` with laser-etched `HAPTIC` label and glowing **Electric Cyan (`#1AF2FF`)** LED pip at `(78, 233)`.
    - Right toggle at `(333, 166)` with glowing **Emerald Green (`#26FF78`)** LED pip at `(309, 167)` and laser-etched `SOUND` label cleanly centered below the green LED at `(309, 190)`.
  - **Fast-Scrub Glowing Fader:**
    - Slot track extends down to `Y=374` so the intense molten neon-orange bar (`#FFF5B8` core -> `#FF4D00` bloom) is **always visible** beneath the handle even at Page 0!
    - Heavy 3D chamfered gunmetal knob (`36 x 23`) with glowing orange slit (`#FFF5B8` core) and glowing pointer arrow.
    - Generous drag target (`vx in 280..360, |vy - knobY| <= 32`) with strict drag-only routing.
  - **Clean Chassis:** Chassis plate matches target reference (clean bottom-left without rocker switch).
- **Book Metadata Engine (`BookMetadataFetcher`):**
  - Dual lookup: Google Books API + Open Library API.
  - Comprehensive Unicode support boosting Bengali script (`\u0980..\u09FF`) and South Asian literature.
  - Fallback logic for `printedPageCount` and `number_of_pages`.
- **Status & Tracking:**
  - Modes: Pages or Percentage (Kindle/e-reader friendly).
  - Status: Focused strictly on `Reading` and `Completed` (removed deprecated "Wish" status).
  - Excel `.xls` library export and full backup archive support.

### 3.7. Yearline (`AlmanacScreen`)
- Full 365/366-day visual heat matrix.
- Show-up streak flame badge.
- Interactive lens filters (`All`, `Habit`, `Dream`).
- Status bar inset padded to ensure zero header collision.

---

## 4. Build & Release Artifacts

- **Release APK:** `C:\Users\The System Admin\Downloads\Mezura-v1.0.3-release.apk`
- **Debug APK:** `C:\Users\The System Admin\Downloads\Mezura-v1.0.3-debug.apk`
- **Direct Copy:** `C:\Users\The System Admin\Downloads\Mezura.apk`
- **Application ID:** `com.instrument.mezura`
- **Version:** `1.0.3` (VersionCode `4`)
- **All unit tests:** Executed, 100% passed.
