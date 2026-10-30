# Ndot (NdotWidgets) — Comprehensive Master Architecture, System Specification & State of the Project

> **Target Audience:** LLMs (ChatGPT, Claude, Gemini, DeepSeek), Senior Android Systems Architects, and Core Maintainers.  
> **Document Purpose:** Provide a single, fully exhaustive, self-contained source of truth describing the entire Ndot codebase architecture, design system, data models, implemented instruments, completed master build stages (Part 1 & Part 2), recent feature additions, strict architectural constraints, and open future upgrade vectors so that any future LLM can immediately propose and implement high-leverage upgrades without prior context.

---

## 1. Executive Summary & Vision

**Ndot** is an offline-first, native Android suite of tactile, hardware-inspired digital instruments and companion home-screen launcher widgets built with **Kotlin 2.4.0** and **Jetpack Compose 1.11.4** targeting **Android SDK 36 (targetSdk 35, minSdk 26)**.

### Product Philosophy
Ndot rejects the noisy, hyperactive visual conventions of modern mobile software. It treats digital tools as physical, machined instruments:
- **Teenage Engineering & Braun / Dieter Rams:** Machined metal faceplates, recessed channels, knurled knobs, mechanical detents, calibrated hairlines, and analog readouts.
- **Nothing OS:** Dot-matrix LED typography, monochrome graphite base layouts, and punchy, selective amber/brass and vermilion accents.
- **Precision Mechanical Instruments:** Vernier calipers, mechanical chronographs, phyllotaxis orbs, and daylight timeline ribbons.
- **Honest Evidence Layer:** Ndot never displays fictitious productivity forecasts, fake bio-energy curves, or duplicate running clocks. It provides tools for deliberate action and records only factual, measured history.

### Core Non-Negotiable Invariants
1. **Zero Material 3 Dependencies:** There is strictly **no reliance** on `androidx.compose.material3`. All interactive controls, inputs, sheets, segmented controls, glass panels, and typography are hand-crafted inside `rocks.ndot.widgets.kit.*`.
2. **Deterministic & Local-First:** All state is persisted in clean, lightweight SharedPreferences JSON representations (`BookStore`, `HabitMasterStore`, `AppPrefs`, `TraceLedger`, `SequenceStore`). No cloud account, telemetry, tracking service, or external network dependence is required for core operation.
3. **Synthesized Real-Time Audio & Haptics (`Feedback.kt`):** Zero static audio asset files (.wav/.mp3) are shipped in the APK. 11 physical switch sounds (plastic keys, aluminum slabs, rubber membranes, detent clicks, chimes) are synthesized in real-time at 44.1 kHz via static `AudioTrack` buffers and paired with hardware vibration waveforms.
4. **Unified Canvas Rendering:** In-app visualisations and launcher home-screen widgets share exact vector drawing routines via Jetpack Compose `DrawScope` and `CanvasDrawScope`.
5. **No Second Timer on Home:** Time belongs authoritative only to active instruments (Pomodoro, Hourglass, Breathing, Folio reading). Home only reflects state, step progress, and estimated durations.
6. **Bengali / Indic Typography Safeguard:** Any text containing Bengali Unicode (`U+0980..U+09FF`) strictly bypasses custom dot-matrix pixel fonts and renders using system default typography with 1.35 line height and zero letter-spacing to avoid matra clipping or conjunct shaping bugs.

---

## 2. Technology Stack & Build Environment

| Component | Specification |
| :--- | :--- |
| **Language & Tooling** | Kotlin 2.4.0, Compose Compiler 2.4.0, Gradle 9.1.0, AGP 9.0.0 |
| **Runtime Environment** | Java 17+ (Android Studio JBR: `C:\Program Files\Android\Android Studio\jbr`) |
| **SDK Targets** | `compileSdk = 36`, `targetSdk = 35`, `minSdk = 26` |
| **Dependencies** | Jetpack Compose Foundation, UI, Runtime; Core-KTX; Activity-Compose; Coil (Covers); Coroutines |
| **Verification & Tests** | Unit tests: `./gradlew testDebugUnitTest` (`ScoreEngineTest.kt` passes with 100% vector accuracy)<br>Typecheck: `python tools/typecheck.py` (0 errors) |
| **Build Artifacts** | `app/build/outputs/apk/debug/app-debug.apk` (~16.4 MB)<br>Copies deployed to `~/Downloads/Ndot-v5.apk` and `ndot-v5-debug.apk` |
| **Key In-Tree Packages** | `rocks.ndot.widgets.kit` (Foundations, Theme, Audio, Components)<br>`rocks.ndot.widgets.app` (Navigation, Home, Drawer, Settings)<br>`rocks.ndot.widgets.instruments.*` (All 9 instruments)<br>`rocks.ndot.widgets.widgets` (Launcher App Widgets) |

---

## 3. Design System & Foundation Tokens (`kit/`)

All UI foundations are centralized in `app/src/main/kotlin/rocks/ndot/widgets/kit/`:

### 3.1 Color Palette (`Theme.kt`)
- **Ground / Base Surfaces:** Pitch black (`Ink.Black` / `Ink.GROUND = #0A0A0C`), Surface 1 (`#141417`), Surface 2 (`#1B1C20`), Surface 3 (`#222328`), Hairline (`#2D2E35`).
- **Accent Tokens:**
  - **Brass / Primary:** `Ink.BRASS = #E8B25C` (used for active sequence steps, caliper accents, progress curves).
  - **LED Amber / Gold:** `Ink.AMBER = #FFD08A` (used for dot-matrix readouts, Gold tier tags, gilded accents, night illumination).
  - **Sage / Success:** `Ink.SAGE = #9CC5A1` (used for completed rungs, ahead-of-pace status, finished books).
  - **Signal Vermilion:** `Ink.RED = #E5533D` (seconds hand, caliper hairline, warning tags).
  - **Brick Orange:** `#C9694A` (Habit city particles and brick icons).
- **Text Hierarchies:** `Ink.Text` (`#F4F1EA`), `Ink.TEXT_WARM` (`#E6E1D8`), `Ink.TEXT_MUTED_V4` (`#8A8D98`), `Ink.TEXT_FAINT_V4` (`#555862`).

### 3.2 Typography & Systems
- **`PixelFont.kt` & `DotMatrixText.kt`:** 5×7 physical LED matrix font with lit amber/white dots and optional unlit background dots (`#2A2018`) mimicking genuine physical LED displays. Used strictly for short technical labels, page counters, and timers.
- **System Typography (`Fonts.kt`):** Monospace (`Fonts.mono`), Condensed (`Fonts.condensed`), and Sans. Titles and body text preserve readability with high-contrast system fonts.

### 3.3 Audio & Haptics Engine (`Feedback.kt`)
Real-time programmatic sound synthesis via low-latency `AudioTrack` buffers at 44.1 kHz:
- `tick(context)`: Sub-10ms mechanical rotary detent click.
- `key(context)`: Tactile plastic key press.
- `pop(context)`: Flying brick particle landing sound.
- `lock(context)`: Heavy metal latch engagement.
- `success(context)` / `chime(context)`: Multi-tone harmonious bell chime.
- `scrollTick(context)`: Lightweight haptic and audio tick during high-speed Vernier scrolling.

### 3.4 UI Primitives (`Components.kt`)
- **`NdotSegmented`:** 40dp pill segmented control (`SURFACE_1` track, accent-filled active pill, click to select, 500ms long-press to save user default via `AppPrefs`).
- **`NdotSheet`:** 28dp rounded-corner bottom modal sheet on `SURFACE_2` with centered top drag handle and system navigation insets.
- **`GlassPanel`:** VisionOS/Apple-style frosted glass card with 1.25dp specular gradient rim and layered translucent backdrop.
- **`StepKey` & `TextAction`:** Tactile mechanical minus/plus buttons and round action pills with built-in detent audio/haptic responses.
- **`LightChrome(isLight)`:** Manages status bar icon contrast dynamically.
- **`Modifier.ndotScreenInsets()`:** Edge-to-edge window insets handling status bar padding.
- **`NavSpacer()`:** 100dp clearance at screen bottom for the floating navigation pill.

### 3.5 Navigation Drawer (`Drawer.kt`)
- Floating 64dp high pill navigation drawer pinned at the bottom of the screen.
- Auto-centers the selected instrument horizontally with smooth scrolling.
- 24dp horizontal edge alpha fade masks preventing harsh clipping on smaller screens.
- Houses root instruments: **HOME**, **FOLIO**, **POMODORO**, **HOURGLASS**, **BREATHING**, **RHYTHM**, **HABITS**, **ALMANAC**, **CALENDAR**.

---

## 4. Architectural Deep-Dive into Instruments & Subsystems

```
┌────────────────────────────────────────────────────────────────────────┐
│                              ROOT APP                                  │
│ ┌───────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────┐ │
│ │   HOME HUB    │ │     FOLIO      │ │    POMODORO    │ │  BREATHING │ │
│ │ (Seq + Trace) │ │(Pages / Pct %) │ │(Down / CountUp)│ │(Bloom Orb) │ │
│ └───────┬───────┘ └───────┬────────┘ └────────┬───────┘ └────────────┘ │
│         │                 │                   │                        │
│         ▼                 ▼                   ▼                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │                           TRACE LEDGER                             │ │
│ │  (Canonical Activity Facts: Focus, Reading, Breathing, Habits)     │ │
│ └─────────────────────────────────┬──────────────────────────────────┘ │
│                                   │                                    │
│         ┌─────────────────────────┴────────────────────────┐           │
│         ▼                                                  ▼           │
│ ┌───────────────────────────────┐        ┌───────────────────────────┐ │
│ │       HABITS & CITY           │        │         ALMANAC           │ │
│ │  ScoreEngine, Today, Dreams,  │        │  12x31 Dot Grid, Lenses,  │ │
│ │  Gold Gilding, Dream Towers   │        │  The Lines (Bullet Bars)  │ │
│ └───────────────────────────────┘        └───────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 4.1 Home Hub & Sequence Engine (`HomeScreen.kt`, `instruments/sequence/`)

The first page of Ndot is the **Home Hub**, fusing sequence-based intentional action with factual evidence.
- **No Second Timer on Home:** The Home screen never displays a running countdown, count-up clock, or circular dial. The authoritative clock lives solely inside the launched instrument.
- **Resting State:**
  - Compact header: Ndot wordmark, current date, settings cog.
  - Hero routine module: Displays active sequence (e.g. `Deep Work`), descriptor (`Breathe · Focus · Recover`), linear step nodes, estimated duration (`3 steps · about 52 min`), and primary action **`START SEQUENCE`**.
  - Routine presets selector: Editable starting templates (Deep Work, Study, Reading, Reset, Custom).
  - Compact Trace snapshot: Horizontal segmented activity strip + summary metrics (`Focus 1h 20m · 4 sessions`), with **`VIEW TRACE`** link.
  - Quiet practice line: `PRACTICE · X OF Y MET` (mono 11sp muted), tapping directly navigates to Habits ▸ TODAY.
- **Active Sequence State:**
  - Module shifts to **`SEQUENCE IN PROGRESS`**: shows routine name, current step index (`STEP 2 OF 4`), instrument name (`Pomodoro · Focus session`), slim progress rail, and primary button **`RETURN TO CURRENT STEP`**.
- **Automatic Instrument Handoff:**
  - One durable run record in `SequenceStore`.
  - When an instrument completes naturally, it emits a completion result. Sequence automatically transitions and launches the next instrument (e.g. Breathing ends $\to$ Pomodoro opens and starts $\to$ Hourglass break begins).
  - Folio reading steps hold at `AWAITING_USER_LOG` until the user enters page/percentage reached or explicitly finishes without log.

---

### 4.2 Trace — Trustworthy Activity Record (`TraceScreen.kt`, `instruments/trace/`)

Trace is the factual ledger of everything the user did in Ndot, replacing speculative productivity forecasts with unassailable data.
- **Canonical Model (`TraceLedger.kt`):**
  - Fields: `eventId`, `activityType` (Focus, Break, Breathing, Reading, Metronome, Habit), `sourceInstrument`, `startedAt`, `endedAt`, `durationSeconds`, `outcome` (`COMPLETED`, `INTERRUPTED`, `SKIPPED`), `metadata`.
  - Idempotent writes keyed by stable event ID. A sequence-launched session annotates the canonical event without double-counting duration.
- **Trustworthy Duration Rule:**
  - Tracked time counts **only instrument-measured durations**. A manually logged habit entry (e.g. `15 min`) appears in the event list as `Handwriting · 15 min logged` with a `—` duration and is **never added to tracked time**.
- **Trace Detail Screen:**
  - Day selector (today + historical horizontal strip).
  - Day summary total tracked time and session counts.
  - Clean segmented activity timeline.
  - Chronological event list with book names, protocols, and routine tags.
  - Category filters: `ALL`, `FOCUS`, `READING`, `BREATHING`, `BREAKS`, `HABITS`.
  - Export CSV / JSON and safe clear history controls.

---

### 4.3 Almanac — The Year of Days (`AlmanacScreen.kt`, `instruments/almanac/`)

Almanac occupies the root slot vacated by Horizon. It answers: *How is my year going, and what have I finished?*
- **Dot Grid (12 Rows × 31 Columns):**
  - Displays all 365/366 days. Columns 1..31 across width, 12 month rows. Months shorter than 31 days leave trailing cells blank.
  - Entrance sweep animation (900ms) sweeps from Jan 1 to today upon year switch.
- **Mathematical Dot Encoding (§5.2):**
  - Scheduled DAY habits on epoch day $d$:
    $$\text{completion} = \frac{\sum (\text{weight} \times \min(\text{credit}, 1))}{\sum \text{weight}}, \quad \text{overall} = \frac{\sum (\text{weight} \times \text{credit})}{\sum \text{weight}}$$
  - **Future day:** Very dim graphite solid (`#1B1C20`).
  - **Before tracking started:** Hollow hairline ring (`#2B2E35`).
  - **Tracked with no logs:** Dim graphite solid (`#2A2C32`).
  - **Rest day:** Hollow brass-dim ring.
  - **Partial/Met ($0 < \text{completion} \le 1$):** Brass in 5 steps ($\le 0.25$ [30%], $\le 0.50$ [50%], $\le 0.75$ [70%], $< 1.0$ [85%], $= 1.0$ [100%]).
  - **Gold Day ($\text{overall} \ge 1.25$):** Full brass dot with `#FFD08A` LED halo ring and soft ambient glow.
  - **Mended day (forgiven miss):** Hollow brass ring with heavier stroke.
  - **Today:** Static amber accent outer ring.
  - **Overlays:** Achievement four-point gold stars, milestone/rung sage diamonds, deadline flags, period-habit ticks.
- **Horizontal Lens Chips:**
  - `ALL` + one chip per habit and per dream. Selecting a habit recalculates the grid for that habit alone. Selecting a dream aggregates linked habits and overlays rungs.
- **The Lines (Bullet Bars & Rung Timelines):**
  - Horizontal timeline Jan to Dec with a vertical NOW hairline.
  - Goals render as bullet bars: track from start to target with a brass node at $\text{start} + \text{progress} \times \text{span}$. If node is right of NOW line $\to$ ahead; left $\to$ behind. Status labels: `AHEAD 6D`, `ON TRACK`, `BEHIND 9D`.
  - Dreams render as thin lines with sage diamonds for achieved rungs and hollow diamonds for future rungs.
- **Day Sheet:** Tapping any dot opens the Day Sheet showing exact habit quantities, credits, bricks earned, and Trace sessions for that date. Long-press opens Trace filtered to that date.

---

### 4.4 Habits, Goals & Dreams System (`ScoreEngine.kt`, `HabitMasterStore.kt`, `HabitsScreen.kt`)

Hosted under the **HABITS** root navigation entry with a 3-segment control: **`CITY | TODAY | DREAMS`**.

#### The Core Scoring Engine (`ScoreEngine.kt`)
- **Credit Formula (Diminishing Returns):**
  Let $r = \text{periodTotal} / \text{target}$.
  $$\text{credit}(r) = \begin{cases} 0.0 & r \le 0 \\ r & 0 < r < 1.0 \\ \min(2.0, 1.0 + 0.6 \log_2(r)) & r \ge 1.0 \end{cases}$$
- **Tier Tags:** `NONE` ($0$), `PARTIAL` ($0 < r < 1$), `MET` ($1.0 \le r < 1.5$), `STRONG` ($1.5 \le r < 2.0$), `GOLD` ($r \ge 2.0$).
- **Streaks ("Never Miss Twice"):**
  - Consecutive scheduled periods qualifying ($q \ge \text{minimum} \text{ or target}$).
  - One missed period is forgiven if the next qualifies; streak keeps count without incrementing and displays a mended dot. Two consecutive misses reset it to 0. Rest days are neutral.
  - Show-up streak (global flame): Consecutive days with $\ge 1$ qualifying entry across any habit.
- **Brick Economy:**
  $$\text{periodBricks} = \lfloor \text{BASE (10)} \times wf \times \text{credit} \rfloor$$
  $$\text{entryBricks} = \text{periodBricks}(\text{after}) - \text{periodBricks}(\text{before}) \quad \text{(marginal, order-independent)}$$
  - Bonuses: Streak bonus $+ \min(\text{streak}, 10)$; Perfect day $+15$; Milestone $+20$; Goal achieved $100 + \min(\text{plannedDays}, 200)$; Early finish $+ \lfloor \text{goalBase} \times \min(0.5, \text{deltaDays} / \text{plannedDays}) \rfloor$; Dream rung $+40$; Dream achieved $+500$.
  - Lifetime bricks derived by replaying history day-by-day.

#### Auto-Sourced Habits (`AutoQuantityProvider`)
- Automatically populated from committed canonical sessions without manual duplicate entry:
  - `FOLIO_PAGES`: Sum of committed reading session pages.
  - `FOLIO_MINUTES`: Measured reading duration.
  - `FOLIO_BOOKS_COMPLETED`: Count of books marked completed in the period.
  - `FOCUS_MINUTES`: Duration of Pomodoro focus sessions (`COMPLETED` or `INTERRUPTED`).
  - `BREATHING_MINUTES`: Duration of finished breathing sessions.
  - `METRONOME_MINUTES`: Measured practice duration.
- Manual entries can be added on top of auto-sourced quantities.

#### Today Tab
- Week ribbon with day selector.
- Quantified habit rows with live credit calculation, quick-add chips, rest-day option, and 4-second expiration `UNDO` snackbar.
- Period habits (Week/Month) with pace indicators (`N TO GO · N DAYS LEFT`).
- Feedback hygiene: Audio/haptic completion moments fire only when Habits tab is in foreground; quiet chips otherwise.

#### Dreams & Goals Tab
- **Goals:** Checklist (course modules), Count total (memorize 100 items), or Metric target (IELTS band 8, higher/lower is better).
  - Pace module: Calculates `daysAhead` against planned timeline.
  - Achievement Ceremony: Plaque, delta chip (`12 DAYS EARLY`, `ON TIME`), bricks award, evidence attachments (certificate photo/note), and landmark preview.
- **Dreams:** Ladder of rungs (`TICK`, `COUNT_AT`, `METRIC_AT`, `GOAL`, `EVIDENCE`).
  - Checkpoint series: Monthly prompts with side-by-side **THEN / NOW** photo comparison with draggable divider.
  - Dream Fuel Meter: Average credit of linked habits over the last 30 days.
  - The Hall: Archive shelf of achievement plaques.

---

### 4.5 The 2:1 Isometric Vector City (`CityModel.kt`, `CityCanvas.kt`)

Extends the deterministic city simulation into an architectural landmark visualization:
- **Base Economy & Layout:**
  - $\text{Cost}(n) = 20 + 6n$.
  - 60-slot master plan radiating outward from the central plaza.
  - $\text{buildingsBuilt} = \max(\text{storedBuildingsBuilt}, \text{buildingsUnlocked}(\text{lifetimeBricks}))$. Buildings never disappear if entries are edited.
  - Titles: Camp $\to$ Hamlet $\to$ Village $\to$ Town $\to$ City $\to$ Metropolis.
- **Gold Days & Gilding (§6.2):**
  - Unlocking a building on a Gold day ($\text{overall} \ge 1.25$) permanently gilds it with an engraved brass roof edge and central gold crest (`gilded = true` in `buildLog`).
- **Dream Towers (§6.3):**
  - 8 reserved plots in the Dream District (plots 1..8).
  - Silhouette mapped to dream's `LandmarkStyle`: `TOWER` (slender glass-blue), `ACADEMY` (cream stone with pediment and clock), `LIBRARY` (warm brick with arched windows), `OBSERVATORY` (slate cylinder with brass dome), `ATELIER` (gabled studio with north-light window), `DOMED_HALL` (sand-toned dome).
  - Floors: $n$ rungs $\to$ $\text{clamp}(n, 3, 10)$ floors. Achieved rungs render solid with warm glowing windows at night. Unachieved floors render as **scaffolding** (thin wireframe at 40% alpha with diagonal cross-braces).
  - **Animated Crane:** Active, incomplete dreams feature a 3D construction crane with its boom gently swinging $\pm 12^\circ$ over a 6-second cycle.
  - **Crowning Flag:** Achieved dreams lift the crane, crown the tower with a brass flag, and radiate a soft gold ambient halo.
- **Achievement Landmarks (§6.4):**
  - 24 outer-ring plots (36..59) for achieved goals. Floors scale with milestone count (1..3). Early finishes are gilded.
- **Header & Inspector Card:**
  - Header displays **`GOLD DAYS n`** chip for current year.
  - Tapping an ordinary building shows archetype, slot number, build date, and `✦ GILDED` badge.
  - Tapping a Dream Tower shows rungs progress, fuel, and an **`OPEN`** button opening the Dream sheet.
  - Tapping an Achievement landmark shows early completion delta and an **`OPEN`** button viewing the plaque.
  - Plate summary: `TODAY · X OF Y MET · +N bricks`.

---

### 4.6 Folio — Machined Reading Instrument (`instruments/folio/`)

A dedicated instrument for tracking reading with physical craftsmanship:
- **Tracking Modes (`BookTrackMode.PAGES` | `BookTrackMode.PERCENTAGE`):**
  - **Pages Mode:** Tracks by absolute pages (e.g. `187 / 320 pages`).
  - **Percentage Mode:** Tracks by percentage (e.g. `45%`, total automatically set to 100). Ideal for Kindle and e-readers.
  - Editable at any time in `AddBookSheet` and `BookEditSheet` with automatic conversion (e.g. 160 of 320 pages automatically converts to 50%).
  - Shelf cards display clean percentage chips (`58%`).
  - Focus sheets, past session logs, and velocity metrics adapt to `%`.
- **Machined Vernier Caliper (`VernierCaliperView.kt`):**
  - Screwed graphite chassis with 4 corner Phillips screws.
  - Recessed coarse rail with adaptive graduations.
  - Glass carriage with 2.4× magnified tick optics and vermilion hairline.
  - Right-side Vernier column (60-page fine scale, 9dp/page inertial scroll with haptic ticks).
  - LED dot-matrix readout displaying page or `$progress% / 100%`.
  - Jump sheet with direct target entry.
- **Dual-API Search & Procedural Covers:**
  - Asynchronous Open Library + Google Books query with Jaro-Winkler fuzzy matching.
  - Procedural typographic fallback jackets when no internet cover exists (5 color harmonies, textured linen, spine shadow, brass progress bar).
- **Session Logging & Handoff:**
  - Dedicated reading sessions link directly with the Pomodoro timer engine.

---

### 4.7 Pomodoro, Timers & Count-Up Mode (`instruments/pomodoro/`)

Authoritative time-tracking instrument:
- **Layout Variants:** Available in **Module**, **Slab**, and **Pocket** variants with physical toggle buttons.
- **Timer Modes:**
  - **Countdown Mode:** Presets (5, 10, 25, 45, 90 mins) + custom duration input.
  - **Count-Up / Unlimited Mode (`⏱ COUNTDOWN` | `♾ COUNT UP`):** Clock starts from `00:00` and counts upward indefinitely with smooth second pulse.
- **Reading Session Handoff:**
  - When a reading session is initiated from Folio, Pomodoro links to the book.
  - Stopping the timer captures the exact elapsed duration and automatically routes to the session completion dialog (prompting for pages/percentage read).
  - Standalone focus sessions automatically record to the Trace ledger upon completion.

---

### 4.8 Breathing — The Bloom (`instruments/breathing/`)
- 540-dot phyllotaxis spiral orb with center-leading expansion on inhale, edge-leading contraction on exhale.
- 4 protocols: Cyclic Sigh (with micro-pause and corona pulse), Coherent 5.5, Box 4-4-4-4 (morphs to superellipse), 4-7-8.
- Eyes-closed mode (`🌙`) with audio/haptic guidance.

---

### 4.9 Metronome & Rhythm (`instruments/metronome/`)
- Dual modes: **Studio** (digital precision LEDs, tap tempo, time signatures) and **Pendulum** (physical weighted swinging wand with realistic physics and woodblock detent audio).
- Timed practice blocks report to Trace.

---

### 4.10 Stopwatch — Obsidian Dial (`common/StopwatchRenderer.kt`)
- Dual dials: Classic White Dial + Obsidian Night Dial.
- Obsidian dial features 36-wedge sunburst brushing, applied lume markers (`#CFF7E0`), signal orange seconds hand with motion blur trail, and recessed concentric guilloché sub-dials.

---

### 4.11 Companion Home-Screen Widgets (`widgets/`)
- `CalendarMonthWidgetProvider` (4×4) and `CalendarWeekWidgetProvider` (4×2) sharing vector `DrawScope` renderers with the in-app calendar.
- Finishes: Apple Frosted Glass and Archival Paper.
- Midnight rollover alarm and real-time preview config activity.

---

## 5. File & Directory Reference

```text
NdotWidgets/
├── app/
│   ├── build.gradle.kts                          # AGP 9.0, Kotlin 2.4.0, Compose 1.11.4
│   └── src/
│       ├── test/kotlin/rocks/ndot/widgets/
│       │   ├── instruments/habits/ScoreEngineTest.kt   # Pure Kotlin unit tests
│       │   └── instruments/horizon/EnergyModelTest.kt
│       └── main/
│           ├── AndroidManifest.xml               # Permissions: INTERNET, VIBRATE
│           └── kotlin/rocks/ndot/widgets/
│               ├── MainActivity.kt               # Single Compose Activity
│               ├── app/
│               │   ├── AppPrefs.kt               # Centralized SharedPreferences
│               │   ├── Drawer.kt                 # Floating nav pill
│               │   ├── HomeScreen.kt             # Machined Home Hub & Sequence UI
│               │   ├── InstrumentsApp.kt         # Root navigation & app scaffold
│               │   └── SettingsScreen.kt         # Global preferences
│               ├── common/
│               │   └── StopwatchRenderer.kt      # Obsidian & White stopwatch dials
│               ├── instruments/
│               │   ├── almanac/
│               │   │   └── AlmanacScreen.kt      # 12x31 Dot Grid & The Lines
│               │   ├── breathing/
│               │   │   ├── BreathingProtocols.kt # Protocols & scientific citations
│               │   │   └── BreathingScreen.kt    # 540-dot phyllotaxis spiral orb
│               │   ├── calendar/
│               │   │   └── CalendarScreen.kt     # In-app calendar
│               │   ├── folio/
│               │   │   ├── BookStore.kt          # Single source of truth (Pages / %)
│               │   │   ├── BookMetadataFetcher.kt# Dual OpenLibrary/Google Books
│               │   │   ├── CoverImage.kt         # Cache & procedural fallback jackets
│               │   │   ├── VernierCaliperView.kt # Machined caliper canvas
│               │   │   ├── FolioStatsCanvas.kt   # Brass analytics & heatmap
│               │   │   └── FolioScreen.kt        # Shelf, Caliper, Stats container
│               │   ├── habits/
│               │   │   ├── ScoreEngine.kt        # Diminishing credit, streaks, bricks
│               │   │   ├── HabitMasterStore.kt   # AutoQuantityProvider & JSON store
│               │   │   ├── CityModel.kt          # Economy, landmarks, gilding, slots
│               │   │   ├── CityCanvas.kt         # 2:1 isometric renderer + crane
│               │   │   ├── HabitsScreen.kt       # City | Today | Dreams container
│               │   │   ├── HabitDetailSheets.kt  # Log sheet & Habit wizard
│               │   │   ├── DreamsComponents.kt   # Ladders, fuel, milestones
│               │   │   └── GoalAndDreamDetailSheets.kt # Ceremonies & details
│               │   ├── hourglass/                # Dot, Classic, Dusk, Prism styles
│               │   ├── metronome/                # Studio & Pendulum modes
│               │   ├── pomodoro/
│               │   │   └── PomodoroScreen.kt     # Countdown + Count-Up unlimited
│               │   ├── sequence/
│               │   │   ├── SequenceModels.kt     # Sequence & Step definitions
│               │   │   └── SequenceStore.kt      # Active run persistence
│               │   └── trace/
│               │       ├── TraceLedger.kt        # Canonical activity facts ledger
│               │       └── TraceScreen.kt        # Editorial timeline & filters
│               ├── kit/
│               │   ├── Theme.kt                  # Inks, surfaces, accents
│               │   ├── Feedback.kt               # 44.1kHz synthesized audio & haptics
│               │   ├── Components.kt             # NdotSegmented, NdotSheet, GlassPanel
│               │   ├── PixelFont.kt              # 5x7 dot-matrix LED font
│               │   └── Fonts.kt                  # Typography & Bengali safeguards
│               └── widgets/
│                   ├── CalendarWidgetProvider.kt # 4x4 Month & 4x2 Week widgets
│                   └── CalendarWidgetRenderer.kt # CanvasDrawScope vector renderer
```

---

## 6. What Has Been Accomplished So Far (Changelog & Milestone Record)

### 1. Master Build Brief Part 1 & Part 2 Errata (E1 to E12)
- Added `TargetChange(effectiveFromEpochDay, target, minimum, stretch)` with target history.
- Added `quickSteps` and `targetHistory` to `HabitItem`.
- Implemented `Checkpoint` and `CheckpointSeries` for monthly photo tracking.
- Implemented `DayAggregate` model powering Almanac calculations.
- Integrated `AutoSource.FOLIO_BOOKS_COMPLETED` for reading habits.
- Replaced Horizon in navigation with **Almanac**.
- Added `ScoreEngineTest.kt` unit test suite covering credit vectors, streak forgiveness, and brick replay determinism.

### 2. City Integration & Landmarks (§6)
- Replaced brick generation with `ScoreEngine` replay economy.
- Implemented **Gold Day** detection ($\text{overall} \ge 1.25$) and permanent building gilding (brass roof edge and gold crest).
- Added **Dream Towers** in plots 1..8: rung-scaled floors, solid lit floors with glowing windows, wireframe scaffolding for unlit floors, animated swinging crane ($\pm 12^\circ$), and crowning brass flag upon achievement.
- Added **Achievement Landmarks** in plots 36..59 for achieved goals with milestone scaling and early finish gilding.
- Added `GOLD DAYS n` chip in header card.
- Implemented interactive inspector cards with `OPEN` navigation buttons routing to Dream and Achievement details.

### 3. Home Hub & Trace Integration (§7 & Part II)
- Home screen redesigned into the Machined Home Hub with routine hero card, preset selector, and compact Trace snapshot.
- Zero second timer on Home enforced.
- Implemented `AutoQuantityProvider` auto-populating habits from committed Folio sessions, finished books, and Pomodoro focus blocks.
- Added quiet practice line on Home: `PRACTICE · X OF Y MET`.

### 4. Reading Instrument & Caliper Overhaul
- Added dual tracking mode: **Pages** vs. **Percentage (%)** across books, caliper LED, shelf chips, focus sessions, and log sheets.
- Toggling between pages and percentage in edit sheet converts current progress seamlessly.
- Re-engineered Open Library + Google Books dual-API search with fuzzy score matching.
- Procedural archival fallback jackets for books without online covers.

### 5. Pomodoro Unlimited Count-Up Mode
- Added **`⏱ COUNTDOWN`** | **`♾ COUNT UP`** mode selector and `♾ UP` preset chip.
- Timer counts upward continuously from `00:00`.
- Stopping captures elapsed duration and triggers reading log prompt or records focus session to Trace.

### 6. Build & Delivery
- Verified `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL**.
- Verified `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.
- Generated APK copied to `~/Downloads/Ndot-v5.apk` and `ndot-v5-debug.apk`.

---

## 7. Explicit Instructions & Open Upgrade Proposals for Future LLMs

When proposing or implementing next steps for Ndot, adhere strictly to these architectural priorities:

### Proposal Vector 1: Cloud Backup & Encrypted Export (`Track A`)
- **Google Drive App Data Sync:** Implement background sync for `BookStore`, `HabitMasterStore`, `SequenceStore`, and `TraceLedger` using Google Drive's hidden `appDataFolder` via Android `WorkManager`. Private evidence photos remain local unless explicitly exported.
- **Universal JSON / CSV Backup:** Standardize a one-tap encrypted `.ndot` archive import/export in `SettingsScreen.kt` that dumps all stores atomically and restores losslessly.

### Proposal Vector 2: MLKit Offline Camera Barcode Scanner for Folio (`Track B`)
- Add an optional, zero-permission (system picker or camera) barcode scanning sheet in `AddBookSheet` using Google MLKit Barcode Scanning (`play-services-mlkit-barcode-scanning` or in-tree camera frame analyzer) to fetch book metadata by scanning physical ISBN barcodes.

### Proposal Vector 3: Android 14/15 Lockscreen & Glance Widgets (`Track C`)
- Modernize `rocks.ndot.widgets.widgets.*` with Jetpack Glance (`androidx.glance:glance-appwidget`) while preserving the exact Compose `CanvasDrawScope` vector rendering for lockscreen complications and dynamic widgets on Android 14+.

### Proposal Vector 4: Health Connect & Sleep Anchor Integration (`Track D`)
- Replace or augment the manual `ANCHOR WAKE` button with read-only Android Health Connect / Sleep API integration to automatically detect wake time and align circadian/ultradian schedules without invasive background observation.

### Proposal Vector 5: Release Engineering & Binary Optimization (`Track E`)
- Configure ProGuard/R8 optimization rules (`proguard-rules.pro`) and Compose Compiler metrics to bring the release APK size down from ~16.4 MB to under 8 MB.
- Generate Baseline Profiles (`androidx.profileinstaller`) for instantaneous cold starts and smooth 60 FPS scrolling on lower-end devices.

---

> **End of Specification.** This document represents the definitive, verified state of the Ndot repository. All referenced classes and files are live in-tree.
