# MEZURA & NDOT — Comprehensive Master Architecture, System Specification & State of the Project

> **Target Audience:** LLMs (ChatGPT, Claude, Gemini, DeepSeek), Senior Android Systems Architects, and Core Maintainers.  
> **Document Purpose:** Provide a single, fully exhaustive, self-contained source of truth describing the entire Mezura (Ndot) codebase architecture, physical design system, data stores, implemented instruments, 7-style master plate engine, Grand Library Folio Stats architecture, complete milestone records (v1.0.0 through v1.0.11), strict architectural constraints, and open future upgrade vectors so that any future LLM can immediately propose and implement high-leverage upgrades without prior context or regressions.

---

## 1. Executive Summary & Product Vision

**Mezura** (Application ID: `com.instrument.mezura`, package namespace: `rocks.ndot.widgets`) is an offline-first, native Android suite of tactile, hardware-inspired digital instruments and companion home-screen launcher widgets. It is built with **Kotlin 2.0.21** and **Jetpack Compose 1.7.0** targeting **Android SDK 36 (targetSdk 36, minSdk 26)**.

### Product Philosophy
Mezura rejects flat, generic Material Design and hyperactive digital notifications. Instead, it treats software as handcrafted physical instrumentation:
- **Teenage Engineering & Dieter Rams (Braun):** Machined metal faceplates, knurled knobs, mechanical detents, calibrated hairlines, and analog readouts.
- **Horological & Archival Craftsmanship:** Hand-aged parchment, brushed antique brass, turned walnut tracks, split-flap digit drums, 3D Vernier calipers, and deckle-edged library folios.
- **Nothing OS & Industrial Minimalism:** Dot-matrix LED typography, monochrome graphite chassis, and punchy, selective amber/brass and vermilion accents.
- **Honest Evidence Layer:** Mezura never displays fictitious productivity forecasts, fake bio-energy curves, or duplicate running clocks. It provides tools for deliberate action and records only factual, measured history.

### Core Non-Negotiable Invariants
1. **Zero Material 3 Dependencies:** Strictly **no reliance** on `androidx.compose.material3`. All interactive controls, inputs, sheets, segmented controls, glass panels, and typography are hand-crafted inside `rocks.ndot.widgets.kit.*`.
2. **Deterministic & Local-First:** All state is persisted in clean, lightweight SharedPreferences JSON representations (`BookStore`, `HabitMasterStore`, `AppPrefs`, `TraceLedger`, `SequenceStore`). No cloud account, telemetry, tracking service, analytics, or external network dependence is required for core operation.
3. **Synthesized Real-Time Audio & Haptics (`Feedback.kt`):** Zero static audio asset files (.wav/.mp3) are shipped in the APK. 11 physical switch sounds (plastic keys, aluminum slabs, rubber membranes, detent clicks, chimes) are synthesized in real-time at 44.1 kHz via static `AudioTrack` buffers and paired with hardware vibration waveforms.
4. **Unified Canvas Rendering:** In-app visualisations and launcher home-screen widgets share exact vector drawing routines via Jetpack Compose `DrawScope` and `CanvasDrawScope`.
5. **No Second Timer on Home:** Time belongs authoritative only to active instruments (Pomodoro, Hourglass, Breathing, Folio reading). Home only reflects state, step progress, and estimated durations.
6. **Bengali / Indic Typography Safeguard:** Any text containing Bengali Unicode (`U+0980..U+09FF`) strictly bypasses custom dot-matrix pixel fonts and renders using the bundled `NotoSansBengali.ttf` or system default typography with 1.35 line height and zero letter-spacing to avoid matra clipping or conjunct shaping bugs.
7. **Zero-Diff Preservation on Stable Instruments:** Never regress or perturb the coordinates, pixel alignments, or rendering routines of frozen instruments (`The Counter` Vernier Caliper, `03 · CLIPBOARD`, `04 · CELESTIAL`, `05 · BLUEPRINT`).

---

## 2. Technology Stack & Build Environment

| Component | Specification |
| :--- | :--- |
| **Application ID** | `com.instrument.mezura` |
| **Current Version** | `1.0.11` (VersionCode `12`) |
| **Language & Tooling** | Kotlin 2.0.21, Compose Compiler 2.0.21, Gradle 9.1.0, AGP 9.0.0 |
| **Runtime Environment** | Java 17+ (Android Studio JBR: `C:\Program Files\Android\Android Studio\jbr`) |
| **SDK Targets** | `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` |
| **Core Libraries** | Jetpack Compose Foundation, UI, Runtime; Core-KTX; Activity-Compose; Coil (Covers); Coroutines |
| **Verification & Tests** | Unit tests: `.\gradlew.bat compileDebugKotlin testDebugUnitTest` (100% pass)<br>Assembly: `.\gradlew.bat assembleDebug assembleRelease` |
| **Verified Build Artifacts (v1.0.11)** | **`Mezura-v1.0.11-release.apk`**: 127.84 MB (134,045,504 bytes), SHA-256: `b9ea5cffdf48bcb19b136057682b1d7c98f3f88d92a768c6cb6791fe68919565`<br>**`Mezura-v1.0.11-debug.apk`**: 132.22 MB (138,642,932 bytes), SHA-256: `fdb72abfe5dfec5d322358d603a1f3a2d53200dcd185dbe8776b92a26ac2eaec`<br>**`Mezura.apk`**: 127.84 MB (134,045,504 bytes), SHA-256: `b9ea5cffdf48bcb19b136057682b1d7c98f3f88d92a768c6cb6791fe68919565` |
| **Key In-Tree Packages** | `rocks.ndot.widgets.kit` (Foundations, Theme, Audio, Components)<br>`rocks.ndot.widgets.app` (Navigation, Home, Drawer, Settings)<br>`rocks.ndot.widgets.instruments.*` (All 9 instruments + Folio Master Plates)<br>`rocks.ndot.widgets.widgets` (Launcher App Widgets) |

---

## 3. Design System & Foundation Tokens (`kit/`)

All UI foundations are centralized in `app/src/main/kotlin/rocks/ndot/widgets/kit/`:

### 3.1 Color Palette (`Theme.kt`)
- **Ground / Base Surfaces:** Pitch black (`Ink.Black` / `Ink.GROUND = #0A0A0C`), Surface 1 (`#141417`), Surface 2 (`#1B1C20`), Surface 3 (`#222328`), Hairline (`#2D2E35`).
- **Accent Tokens:**
  - **Brass / Primary:** `Ink.BRASS = #E8B25C` (used for active sequence steps, caliper accents, progress curves).
  - **LED Amber / Gold:** `Ink.AMBER = #FFD08A` (used for dot-matrix readouts, Gold tier tags, gilded accents, night illumination).
  - **Sage / Success:** `Ink.SAGE = #9CC5A1` (used for completed rungs, ahead-of-pace status, finished books).
  - **Signal Vermilion:** `Ink.RED = #E5533D` (seconds hand, caliper hairline, active navigation beacon, warning tags).
  - **Brick Orange:** `#C9694A` (Habit city particles and brick icons).
- **Text Hierarchies:** `Ink.Text` (`#F4F1EA`), `Ink.TEXT_WARM` (`#E6E1D8`), `Ink.TEXT_MUTED_V4` (`#8A8D98`), `Ink.TEXT_FAINT_V4` (`#555862`).

### 3.2 Typography & Systems
- **`PixelFont.kt` & `DotMatrixText.kt`:** 5×7 physical LED matrix font with lit amber/white dots and optional unlit background dots (`#2A2018`) mimicking genuine physical LED displays. Used strictly for short technical labels, page counters, and timers.
- **System Typography (`Fonts.kt`):** Monospace (`Fonts.mono`), Condensed (`Fonts.condensed`), and Sans.
- **Bundled Bengali Font:** `assets/fonts/NotoSansBengali.ttf` packaged directly in assets. Text containing Bengali characters automatically switches to native font rendering with 1.35 line height.

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

### 3.5 Refractive Liquid Glass Dock (`Drawer.kt`)
- **Floating 64dp Pill Navigation Dock:** Pinned at screen bottom with translucent frosted glass backdrop, specular top highlight, and soft ambient drop shadow.
- **Generous 22dp Tab Spacing & Monospace Typography:** Tab labels formatted in uppercase monospace with `letterSpacing = 0.14.sp`, eliminating label cramping and truncation.
- **20dp Horizontal Edge Fade Dissolve Masks:** Uses `CompositingStrategy.Offscreen` + `BlendMode.DstIn` to smoothly fade tabs at viewport edges.
- **Responsive Auto-Centering:** Tapping any tab smoothly scrolls the dock horizontally to center the active instrument in the viewport.
- **Machined Red Beacon Button & Glowing Active Indicator:** Features a machined red beacon core (`#E5533D`) with ambient bloom and crisp red dot indicator for the currently active instrument.
- **Instrument Families:** Root navigation seamlessly groups instruments into logical families:
  * **CORE:** HOME, FOLIO
  * **TIME:** POMODORO, HOURGLASS, BREATHING
  * **PRACTICE:** RHYTHM, HABITS
  * **RECORDS:** ALMANAC, CALENDAR

---

## 4. Architectural Deep-Dive into Instruments & Subsystems

```
┌────────────────────────────────────────────────────────────────────────┐
│                              ROOT APP                                  │
│ ┌───────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────┐ │
│ │   HOME HUB    │ │     FOLIO      │ │    POMODORO    │ │  BREATHING │ │
│ │ (Seq + Trace) │ │(Shelf/Counter/ │ │(Down / CountUp)│ │(Bloom Orb) │ │
│ │               │ │ Grand Stats)   │ │                │ │            │ │
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

The first page of Mezura is the **Home Hub**, fusing sequence-based intentional action with factual evidence.
- **No Second Timer on Home:** The Home screen never displays a running countdown, count-up clock, or circular dial. The authoritative clock lives solely inside the launched instrument.
- **Resting State:**
  - Compact header: Mezura wordmark, current date, settings cog.
  - Hero routine module: Displays active sequence (e.g. `Deep Work`), descriptor (`Breathe · Focus · Recover`), linear step nodes, estimated duration (`3 steps · about 52 min`), and primary action **`START SEQUENCE`**.
  - Routine presets selector: Editable starting templates (Deep Work, Study, Reading, Reset, Custom).
  - Compact Trace snapshot: Horizontal segmented activity strip + summary metrics (`Focus 1h 20m · 4 sessions`), with **`VIEW TRACE`** link.
  - Quiet practice line: `PRACTICE · X OF Y MET` (mono 11sp muted), tapping directly navigates to Habits ▸ TODAY.
- **Active Sequence State:**
  - Module shifts to **`SEQUENCE IN PROGRESS`**: shows routine name, current step index (`STEP 2 OF 4`), instrument name (`Pomodoro · Focus session`), slim progress rail, and primary button **`RETURN TO CURRENT STEP`**.
- **Automatic Instrument Handoff:**
  - One durable run record in `SequenceStore`.
  - When an instrument completes naturally, it emits a completion result. Sequence automatically transitions and launches the next instrument (e.g. Breathing ends $	o$ Pomodoro opens and starts $	o$ Hourglass break begins).
  - Folio reading steps hold at `AWAITING_USER_LOG` until the user enters page/percentage reached or explicitly finishes without log.

---

### 4.2 Trace — Trustworthy Activity Record (`TraceScreen.kt`, `instruments/trace/`)

Trace is the factual ledger of everything the user did in Mezura, replacing speculative productivity forecasts with unassailable data.
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

Almanac answers: *How is my year going, and what have I finished?*
- **Dot Grid (12 Rows × 31 Columns):**
  - Displays all 365/366 days. Columns 1..31 across width, 12 month rows. Months shorter than 31 days leave trailing cells blank.
  - Entrance sweep animation (900ms) sweeps from Jan 1 to today upon year switch.
- **Mathematical Dot Encoding:**
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

#### Today Tab
- Week ribbon with day selector.
- Quantified habit rows with live credit calculation, quick-add chips, rest-day option, and 4-second expiration `UNDO` snackbar.
- Period habits (Week/Month) with pace indicators (`N TO GO · N DAYS LEFT`).

#### Dreams & Goals Tab
- **Goals:** Checklist (course modules), Count total (memorize 100 items), or Metric target (IELTS band 8). Pace module calculates `daysAhead` against planned timeline.
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
- **Gold Days & Gilding:**
  - Unlocking a building on a Gold day ($\text{overall} \ge 1.25$) permanently gilds it with an engraved brass roof edge and central gold crest (`gilded = true` in `buildLog`).
- **Dream Towers:**
  - 8 reserved plots in the Dream District (plots 1..8).
  - Silhouette mapped to dream's `LandmarkStyle`: `TOWER`, `ACADEMY`, `LIBRARY`, `OBSERVATORY`, `ATELIER`, `DOMED_HALL`.
  - Floors: $n$ rungs $\to$ $\text{clamp}(n, 3, 10)$ floors. Achieved rungs render solid with warm glowing windows at night. Unachieved floors render as **scaffolding** (thin wireframe at 40% alpha with diagonal cross-braces).
  - **Animated Crane:** Active, incomplete dreams feature a 3D construction crane with its boom gently swinging $\pm 12^\circ$ over a 6-second cycle.
  - **Crowning Flag:** Achieved dreams lift the crane, crown the tower with a brass flag, and radiate a soft gold ambient halo.
- **Achievement Landmarks:**
  - 24 outer-ring plots (36..59) for achieved goals. Floors scale with milestone count (1..3). Early finishes are gilded.

---

### 4.6 Reading & Grand Library Folio System (`instruments/folio/`)

The Reading Instrument is structured into three dedicated sub-tabs:

#### Sub-Tab 1: The Library Shelf (`FolioScreen.kt`)
- **Reading Back Hierarchy:** System back navigation exclusively owned by Reading when active:
  $$\text{Sheet Dismiss} \to \text{IME Dismiss} \to \text{Search Collapse} \to \text{Sub-Tab Switch (Counter/Stats } \to \text{Shelf)} \to \text{moveTaskToBack(true)}$$
  The app never inadvertently jumps to Home when pressing back inside Reading.
- **Shelf Edge Fade:** Multi-stop offscreen `DstIn` alpha dissolve (`scrollEdgeFade`) at top and bottom scroll boundaries, eliminating artificial specular strokes and frosted lip bands.
- **Fixed 38dp Title Container:** Guarantees strict horizontal alignment for ratings and metadata across varying book title lengths.
- **Dual Tracking Modes:** Supports both **`PAGES`** (e.g. `187 / 320 pages`) and **`PERCENTAGE`** (e.g. `58% / 100%`) across shelf cards, focus sheets, and caliper readouts.

#### Sub-Tab 2: The Counter ("The Caliper") (`VernierCaliperView.kt`)
- Screwed graphite chassis with 4 corner Phillips screws.
- Recessed coarse rail with adaptive graduations.
- Glass carriage with 2.4× magnified tick optics and vermilion hairline.
- Right-side Vernier fine column (60-page fine scale, 9dp/page inertial scroll with haptic ticks).
- LED dot-matrix readout displaying page or `$progress% / 100%`.
- Jump sheet with direct target entry.
- **100% Scope Lock:** Coordinates, physics, and rendering routines are frozen to preserve perfect tactile feel.

#### Sub-Tab 3: The Grand Library Folio Stats (`GrandLibraryFolioStatsScreen.kt`)
A museum-grade tactile folio parchment recording reading velocity, cadence, and multi-genre distribution:
1. **Four-Sided Independent KPI Plaques:**
   - Terminated ruler zone wipe strictly at `0.123f` (strictly above the top cards) to eliminate white-band sampling artifacts.
   - Implemented `restoreIndependentTopKpiCardBorders` cloning authentic card border rows from Row 2 (`0.219f..0.2365f`) onto Row 1 (`0.119f..0.1365f`), establishing all 4 KPI cards (`PAGES READ`, `READING TIME`, `AVERAGE PACE`, `BOOKS COMPLETED`) as 100% independent 4-sided paper islands with complete top borders.
   - Built-in perimeter protection for screws and pocket watch crown (`inScrewOrWatchZone && oB < 168`).
2. **Extended Real Paper Canvas (`PAGE_EXTENSION_FACTOR = 1.06f`):**
   - Kept `0..0.818f` 100% untouched, perfectly preserving the authentic torn deckle edge and drop shadow of the `READING CADENCE` card.
   - Extended total plate height downwards and filled `0.818f..fillEndY` by cloning genuine donor paper rows (`[0.554f..0.561f]`, `[0.382f..0.389f]`, `[0.317f..0.323f]`) using seamless ping-pong mirroring.
   - Grafted the authentic bottom deckle edge (`0.985f..1.000f`) onto the new extended plate base.
3. **Full Multi-Genre Catalog Scrolling & 7 Horological Metal Finishes:**
   - Removed `.take(4)` restriction from the calculator; supports any number of genres ($7, 10, 20+$) without truncation or layout breakage.
   - Implemented 7 distinct horological metal bar finishes:
     * Sovereign Brass (`#C8A652`)
     * Burnished Copper (`#B85C38`)
     * Antique Amber-Gold (`#D4A034`)
     * Archival Bronze (`#7D6242`)
     * Aged Pewter-Brass (`#8C8474`)
     * Rose Vermeil Brass (`#B86B68`)
     * Verdigris Patina Brass (`#5B8A72`)
   - Added two-way navigation: touch drag scrolling with inertia inside the viewport, a micro-machined vertical brass scrollbar slider with bevels, and header pager arrow buttons (`▲` / `▼`).
   - Brass slider knob is clamped safely past the walnut slot's corner radius (`minKnobCenter = slotRect.left + slotCorner + knobW * 0.52f`), preventing any left end-cap overhang at low percentages (e.g. 5% on Bengali).
   - Tapping a genre row displays `${selG.booksCount} BOOKS ($selPct%)` in crimson ink right-aligned in the island header while keeping the row's right readout clean as `"$pctInt%"`.
4. **Holistic Dynamic KPI Auto-Scaling:**
   - Integrated `autoFitKpiTextPaint` typography engine across all 4 KPI cards to dynamically adjust text size and tracking, preventing text overflow regardless of library size.
5. **Procedural 3D Machined Brass Vernier Ruler (`drawArtifactFreeBrassVernierRuler`):**
   - 4 Discrete Ranges: `7D` (x=0.145), `30D` (x=0.270), `YEAR` (x=0.405), `ALL` (x=0.540).
   - Gliding carriage driven by fractional `rangeIndex` with 1:1 pixel drag mapping and spring snap.
   - Anchor math maintains $\ge 6\text{dp}$ clearance right of label edges, eradicating beak collision with `"ALL"`.
6. **Reading Consistency Matrix:**
   - Strictly branded `"READING CADENCE (365-DOT MATRIX)"`.
   - 53 columns $\times$ 7 rows = 371 cells with embossed tactile brass pins indicating daily reading activity.

---

### 4.7 Book Completion & 7-Style Master Plate Architecture

**Core Files:**
- [`BookCompletionModels.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/BookCompletionModels.kt)
- [`MezuraMasterPlateRenderer.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/MezuraMasterPlateRenderer.kt)
- [`BookStatsBitmapExporter.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/BookStatsBitmapExporter.kt)

#### Curated 7-Style Numbering & Asset Mapping
The 7 master share styles use strict 1-based indexing matching visual IDs:
1. `STYLE_01_ARCHIVAL`: Traditional cream parchment folio with embossed brass plaques, split-flap digit counter drums, and cap-height centered typography.
2. `STYLE_02_LED_MATRIX`: Heavy industrial anodized gunmetal steel faceplate with dual Phillips screws, amber LED matrix digits, and recessed star rating trough.
3. `STYLE_03_CLIPBOARD`: Field clipboard with real brass spring clasp, torn craft paper, and stamped utilitarian typewriter lettering.
4. `STYLE_04_CELESTIAL`: Deep indigo midnight sky with astronomical astrolabe charts, golden constellation lines, and stellar rating markers.
5. `STYLE_05_BLUEPRINT`: Technical architectural drafting cyanotype paper, isometric grid lines, drafting paperclip, and technical drafting callouts.
6. `STYLE_06_GLASS`: Apple/VisionOS-style smoked glass card with bokeh gradient, specular rim, and smooth bento erasures for live stat values.
7. `STYLE_07_OBSIDIAN`: Matte dark volcanic obsidian stone with warm espresso gradient, hairline gold inlays, and proportional cover letterbox.

#### Instant Style Switch Cache & Prefetching
- Integrated `MezuraMasterPlateEngine` with `LruCache<String, Bitmap>(14)` memory cache.
- Background asynchronous prefetching pre-loads all 7 master plates upon entering the Completion Sheet, delivering instantaneous, 0ms style switching without placeholder flicker.

---

### 4.8 Pomodoro, Timers & Count-Up Mode (`instruments/pomodoro/`)
- Authoritative time-tracking instrument available in **Module**, **Slab**, and **Pocket** variants.
- **Countdown Mode:** Presets (5, 10, 25, 45, 90 mins) + custom duration input.
- **Count-Up / Unlimited Mode (`⏱ COUNTDOWN` | `♾ COUNT UP`):** Clock starts from `00:00` and counts upward indefinitely with smooth second pulse.
- **Reading Session Handoff:** Stopping the timer captures exact elapsed duration and automatically routes to the session completion dialog (prompting for pages/percentage read).
- Standalone focus sessions automatically record to the Trace ledger upon completion.

---

### 4.9 Breathing — The Bloom (`instruments/breathing/`)
- 540-dot phyllotaxis spiral orb with center-leading expansion on inhale, edge-leading contraction on exhale.
- 4 protocols: Cyclic Sigh (with micro-pause and corona pulse), Coherent 5.5, Box 4-4-4-4 (morphs to superellipse), 4-7-8.
- Eyes-closed mode (`🌙`) with audio/haptic guidance.

---

### 4.10 Metronome & Rhythm (`instruments/metronome/`)
- Dual modes: **Studio** (digital precision LEDs, tap tempo, time signatures) and **Pendulum** (physical weighted swinging wand with realistic physics and woodblock detent audio).
- Timed practice blocks report to Trace.

---

### 4.11 Stopwatch — Obsidian Dial (`common/StopwatchRenderer.kt`)
- Dual dials: Classic White Dial + Obsidian Night Dial.
- Obsidian dial features 36-wedge sunburst brushing, applied lume markers (`#CFF7E0`), signal orange seconds hand with motion blur trail, and recessed concentric guilloché sub-dials.

---

### 4.12 Companion Home-Screen Widgets (`widgets/`)
- `CalendarMonthWidgetProvider` (4×4) and `CalendarWeekWidgetProvider` (4×2) sharing vector `DrawScope` renderers with the in-app calendar.
- Finishes: Apple Frosted Glass and Archival Paper.
- Midnight rollover alarm and real-time preview config activity.

---

## 5. File & Directory Reference

```text
NdotWidgets/
├── app/
│   ├── build.gradle.kts                          # AGP 9.0, Kotlin 2.0.21, Compose 1.7.0
│   └── src/
│       ├── test/kotlin/rocks/ndot/widgets/
│       │   ├── instruments/habits/ScoreEngineTest.kt   # Pure Kotlin unit tests
│       │   └── instruments/horizon/EnergyModelTest.kt
│       └── main/
│           ├── AndroidManifest.xml               # Permissions: INTERNET, VIBRATE
│           ├── assets/
│           │   ├── fonts/
│           │   │   └── NotoSansBengali.ttf       # Bundled Google Fonts Bengali typeface
│           │   └── plates/                       # Master plate artwork for 7 share styles
│           └── kotlin/rocks/ndot/widgets/
│               ├── MainActivity.kt               # Single Compose Activity
│               ├── app/
│               │   ├── AppPrefs.kt               # Centralized SharedPreferences
│               │   ├── Drawer.kt                 # Liquid Glass Pill navigation dock
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
│               │   │   ├── FolioScreen.kt        # Shelf, Caliper, Stats container
│               │   │   ├── GrandLibraryFolioStatsScreen.kt # Master Grand Library Stats
│               │   │   ├── BookCompletionModels.kt         # 7 curated share plate models
│               │   │   ├── MezuraMasterPlateRenderer.kt    # Live vector plate compositor
│               │   │   └── BookStatsBitmapExporter.kt      # High-res export engine
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

## 6. Complete Chronological Milestone & Changelog Record

| Version | Code | Milestone Summary |
| :--- | :--- | :--- |
| `1.0.0` – `1.0.6` | `1`–`7` | Foundation tactile instruments: Machined Vernier Caliper, 5×7 LED PixelFont, 2:1 Isometric City, Habit ScoreEngine, 12×31 Almanac dot grid, Trace activity facts ledger, 540-dot Breathing Bloom, Dual-mode Metronome. |
| `1.0.7` | `8` | Curated 7-style share report card system; removed Field Atlas and Telemetry; fixed Obsidian numbering. |
| `1.0.8` | `9` | **Holistic Polish & macOS Genie Dock Release:**<br>• Seamless master parchment restoration in Grand Library Folio (`y = 0.010..0.124` & `0.814..0.982`).<br>• Procedural 3D brass vernier ruler (7D..ALL) with gliding carriage.<br>• 365-Dot Matrix Cadence bounded to `0.737..0.7886` with strict "CONSISTENCY" branding.<br>• Clean Genre Distribution with carved walnut tracks and sliding brass clasps.<br>• Shelf 38dp fixed title container for horizontal star alignment; hidden progress lines on completed books.<br>• Progressive 92dp liquid glass bottom vignette.<br>• Counter chassis aspect-fitted (`360x425`) with safe bottom clearance. |
| `1.0.9` | `10` | **Master Plate Zero-Bleed & Precision Liquid Glass Dock Release:**<br>• **Procedural Donor-and-Feather Restoration:** Donor strip sampled at $y \approx 0.785f \times h$ with boundary delta luminance adjustments and 10–12px cosine feathering; top wipe band extended to `0.012f..0.134f` removing rivets up to $0.134f$ without touching KPI cards at $0.138f$; bottom band wiped over `0.796f..0.982f`.<br>• **Genre Typography:** Consistent bold sans-serif header matching Velocity Curve; normal sans-serif category titles; bold sans-serif percentages.<br>• **Bundled Bengali Font:** Packaged Google Fonts `NotoSansBengali.ttf` in `assets/fonts/` with automatic Unicode range detection (`\u0980..\u09FF`).<br>• **Card 01 Archival:** Subpixel and linear text flags; grotesque bold numbers; removed artificial blur behind Total Reading Time.<br>• **Card 02 LED Matrix:** Star slot strictly placed at `NormRect(0.516f, 0.290f, 0.938f, 0.355f)` with 5 centered stars lit in warm amber gold (`#E8B04C`).<br>• **Cards 03, 05, 06 Clean Header Erase:** Header text zones wiped with 15px feathering; live rendered typography ("I'VE READ", title, author, dates, rating); Card 05 Blueprint paperclip repositioned to cover top-center (`cx = 0.207f * w, topY = 0.052f * h`).<br>• **Card 07 Obsidian:** Cover zone erased over $x = 0.045..0.450, y = 0.085..0.520$ eliminating dark demo box and halo; clean letterbox aspect-fit with hairline gold border.<br>• **Liquid Glass Dock:** Static rounded liquid glass pill with settled spring-animated thin amber pill bar (`#CCF5B851`, 18dp x 2.5dp); single-line tab labels (`11.sp`) with zero truncation; responsive press scale (`0.94f`) with light haptic tick detent and zero sound click. |
| `1.0.10` | `11` | **Final Completion & Precision Mechanical Fidelity Release:**<br>• **Fix 1 (06 · GLASS Lower Bento Erase):** Restored all 6 `eraseCoonsSmooth` calls across the 5 lower stat tiles and Mezura footer prior to drawing live numbers, eliminating double-number text artifacts while preserving the unified top smoked glass hero platform.<br>• **Fix 2A (Stats Ruler Backing Wipe):** Erased camera hole and baked brass ruler carriage tabs via `eraseVertSmoothFeathered` (`0.052f..0.948f, 0.040f..0.135f`), preventing ruler ends and carriage shadows from bleeding behind the vector caliper.<br>• **Fix 2B (Clean KPI Number Slots):** Replaced horizontal smear with `cloneCardParchmentFeathered` within each card's unprinted parchment, staying strictly clear of brass screws and protractor illustration.<br>• **Fix 2C (Lowered Genre Distribution Island):** Positioned Genre island panel to `0.818f..0.974f` creating a clean 16dp parchment separation below Reading Cadence, with 4 row Y centers at `0.864f, 0.895f, 0.926f, 0.955f`.<br>• **Fix 3 (Bottom Navigation Pill Dock):** Upgraded `DrawerBar` with 22dp tab spacing, letterSpacing = 0.14sp, 20dp left/right edge fade dissolve masks, active glowing red dot indicator (`#E5533D`), and smooth auto-centering on click.<br>• **F1 (Reading Back Hierarchy):** System back navigation exclusively owned by Reading when active: Sheet dismiss $\to$ IME dismiss $\to$ Search collapse $\to$ Sub-tab switch (`Counter`/`Stats` $\to$ `Shelf`) $\to$ Background app (`moveTaskToBack(true)`). Never jumps to Home.<br>• **F2 (Shelf Edge Fade):** Eliminated golden specular strokes and frosted lip bands; implemented multi-stop offscreen DstIn alpha dissolve (`scrollEdgeFade`) at top and bottom scroll boundaries.<br>• **F3 (Dock Edge Clearance):** Removed 8dp horizontal edge fade clipping `"BITS"` and `"YEARLIN"`; tightened `FamilyLabel` horizontal padding (5dp) and typography (10sp SemiBold) to guarantee zero label clipping.<br>• **F4 (Instant Style Switch Cache):** Integrated `MezuraMasterPlateEngine` with `LruCache<String, Bitmap>(14)` memory cache and background asynchronous prefetching; style switches render with 0ms delay without placeholder flicker.<br>• **F5 & F6 (Stats Brass Ruler 1:1 Physics):** Unified highlight oval and brass carriage under single fractional `rangeIndex: Animatable<Float>` (0f..3f). Dynamically computed anchor math maintaining $\ge 6\text{dp}$ clearance right of measured label edges, completely eliminating beak collision with `"ALL"`. Added 1:1 pixel drag mapping with `VelocityTracker` and velocity-preserving spring snap.<br>• **F7 (Healed Parchment KPI Number Zones):** Erased baked numbers in all 4 KPI cards using `restoreKpiNumberZone` feathered donor parchment sampling, eliminating vertical color splits; dynamic text scaling in Card 2 preserving $\ge 10\text{dp}$ clearance left of pocket watch.<br>• **F8 (Genre Distribution Island Panel):** Enclosed `GENRE DISTRIBUTION` inside its own standalone island panel (`top = 0.812f * h`, `bottom = 0.978f * h`) with `#8C7652` border stroke, downward drop shadow, and clean 12dp parchment gap below Cadence card.<br>• **F9, F10, F11 (01 Archival Polish):** Crisp vector rules flanking `"BOOK COMPLETED"` and `"MEZURA · READING RECORD"`; unbroken vector capsule (`#8A6D3B`) with flame icon for `1 DAY LONGEST STREAK`; symmetrical 14px brass split-flap housing bevels; cap-height baseline centering for `PAGES READ` and stat numbers `52` and `89`; title size (+30%) and author size (+25%); tighter Date-to-"MY RATING" gap (-40%); 14dp gauge gap; subtle bottom/left cover drop shadow.<br>• **F12 (02 LED Matrix Single Steel Plate):** Erased title area with `eraseVertSmooth` donor gunmetal steel sampling; lit star glow strictly confined to star trough.<br>• **F12 (06 Glass Seamless Smoked Header):** Single continuous rounded-rectangle glass surface (`x: 0.052..0.948, y: 0.038..0.273`, corner radius 36px) filled with horizontal-diagonal bokeh-to-charcoal gradient and specular rim, eliminating the 36% vertical seam.<br>• **F13 (07 Obsidian Flush Slot):** Erased with `eraseCoonsSmooth` over warm espresso background (`#3E2212` $\to$ `#1E1008`); anchored cover slot flush to top of title block (`left = 0.048f * w, top = 0.083f * h, width = 0.365f * w, height = 0.292f * h`) with proportional width and soft bottom-left shadow, eliminating black top band and side dark strip.<br>• **Locked Scopes:** Strict zero-diff preservation on `03 · CLIPBOARD`, `04 · CELESTIAL`, `05 · BLUEPRINT`, and `The Counter` (`VernierCaliperView.kt`). |
| `1.0.11` | `12` | **Master Final Stats Architecture & Precision Polish Release:**<br>• **Independent 4-Sided KPI Plaques:** Stops ruler erasure strictly at `0.123f` above cards, cloning authentic deckle borders from Row 2 (`0.219f..0.2365f`) onto Row 1 (`0.119f..0.1365f`), establishing all 4 KPI cards as 100% independent 4-sided paper islands with complete top borders.<br>• **Extended Real Paper Canvas (`PAGE_EXTENSION_FACTOR = 1.06f`):** Upper plate `0..0.818f` kept 100% untouched preserving Reading Cadence bottom deckle edge and drop shadow intact; fills `0.818f..fillEndY` by cloning real donor paper rows via ping-pong mirroring, grafting deckle edge onto extended base.<br>• **Full Library Multi-Genre Scrolling & 7 Horological Metal Finishes:** Complete catalog of reading genres without truncation; touch drag gesture scrolling with vertical machined brass scrollbar and header pager buttons (`▲` / `▼`); 7 distinct horological metal bar finishes.<br>• **Dynamic KPI Auto-Scaling:** Holistic `autoFitKpiTextPaint` typography engine scaling large numbers across all 4 KPI cards preventing overflow or overlap.<br>• **Verified Build & Release:** Unit tests passed 100%, assembleDebug/Release successful, GitHub Release `v1.0.11` published as Latest. |

---

## 7. Explicit Instructions & Open Upgrade Proposals for Future LLMs

When proposing or implementing next steps for Mezura, adhere strictly to these architectural priorities:

### Strict Implementation Rules
1. **Never Touch Frozen Code Without Explicit Permission:**
   - `VernierCaliperView.kt` (`The Counter`) is mathematically calibrated and frozen.
   - Cards `03 · CLIPBOARD`, `04 · CELESTIAL`, and `05 · BLUEPRINT` in `MezuraMasterPlateRenderer.kt` are complete.
2. **Always Compile & Test Before Committing:**
   - Execute `.\gradlew.bat compileDebugKotlin testDebugUnitTest` and verify 0 compilation or unit test errors.
3. **Always Package & Hash APKs:**
   - Execute `.\gradlew.bat assembleDebug assembleRelease`.
   - Copy artifacts to `Downloads/` and `Downloads/Mezura/Project Files/`.
   - Update SHA-256 and byte size tables in `README.md`, `MEZURA_MASTER_SPEC_AND_ARCHITECTURE.md`, and this project summary.
4. **Zero Personal Credentials:**
   - Never write personal paths (`C:\Users\LocalUser\...`) or credentials into commit messages or code.

### Open Upgrade Proposals for Future LLMs

#### Proposal Vector 1: Cloud Backup & Encrypted Export (`Track A`)
- **Google Drive App Data Sync:** Implement background sync for `BookStore`, `HabitMasterStore`, `SequenceStore`, and `TraceLedger` using Google Drive's hidden `appDataFolder` via Android `WorkManager`. Private evidence photos remain local unless explicitly exported.
- **Universal JSON / CSV Backup:** Standardize a one-tap encrypted `.mezura` archive import/export in `SettingsScreen.kt` that dumps all stores atomically and restores losslessly.

#### Proposal Vector 2: MLKit Offline Camera Barcode Scanner for Folio (`Track B`)
- Add an optional, zero-permission (system picker or camera) barcode scanning sheet in `AddBookSheet` using Google MLKit Barcode Scanning (`play-services-mlkit-barcode-scanning` or in-tree camera frame analyzer) to fetch book metadata by scanning physical ISBN barcodes.

#### Proposal Vector 3: Android 14/15 Lockscreen & Glance Widgets (`Track C`)
- Modernize `rocks.ndot.widgets.widgets.*` with Jetpack Glance (`androidx.glance:glance-appwidget`) while preserving the exact Compose `CanvasDrawScope` vector rendering for lockscreen complications and dynamic widgets on Android 14+.

#### Proposal Vector 4: Health Connect & Sleep Anchor Integration (`Track D`)
- Replace or augment the manual `ANCHOR WAKE` button with read-only Android Health Connect / Sleep API integration to automatically detect wake time and align circadian/ultradian schedules without invasive background observation.

#### Proposal Vector 5: Release Engineering & Binary Optimization (`Track E`)
- Configure ProGuard/R8 optimization rules (`proguard-rules.pro`) and Compose Compiler metrics to bring release APK size down.
- Generate Baseline Profiles (`androidx.profileinstaller`) for instantaneous cold starts and smooth 60 FPS scrolling on lower-end devices.

---

> **End of Specification.** This document represents the definitive, verified state of the Mezura / Ndot repository. All referenced classes and files are live in-tree.
