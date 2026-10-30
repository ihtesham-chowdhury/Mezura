# MEZURA — Master Technical Architecture & Specification
**Application ID:** `com.instrument.mezura`  
**Current Version:** `1.0.9` (VersionCode `10`)  
**Platform:** Android (Min SDK 26, Target SDK 36, Compiled with Android SDK 36, Jetpack Compose 1.7.0, Kotlin 2.0.21)  
**Last Updated:** October 2026

---

## 1. Executive Summary & Design Ethos

**Mezura** is an offline suite of tactile, photorealistic mechanical instruments designed for measuring time, rhythm, reading progress, habits, and yearlines on Android.

Rather than adhering to generic, flat Material Design paradigms, every instrument in Mezura is engineered as **authentic physical hardware**:
- **Physical Materials:** Cast brass, knurled stainless steel, turned mahogany/walnut, brushed anodized aluminum, satin titanium, amber glass, archival textured paper, smoked optical glass, and obsidian slate.
- **Micro-Mechanical Controls:** Recessed split-flap digit drums, lathe-turned rotary jog wheels with liquid-metal thumb dimples, stepped pushers with tactile knurling, mechanical click levers, and illuminated phosphor LEDs.
- **Analog Skeuomorphism with Apple Spring Dynamics:** Custom hardware Canvas rendering paired with spring physics (`slideInVertically`, `scaleIn`, `spring(dampingRatio = 0.72f, stiffness = 500f)`).
- **Tactile Audio & Haptic Detents:** Hardware clicks, mechanical ratchet buzzes, relays, and vibration detents mapped directly to physical wheel rotations, lever taps, and pusher depressions.
- **Liquid Glass Navigation Dock:** Floating liquid glass navigation bar with specular refractive highlights, spring-animated amber active pill bar indicator, crisp single-line tabs with zero truncation, and instant tactile haptic ticks.
- **100% Local-First & Zero Telemetry:** Zero cloud servers, zero trackers, zero external telemetry. All data is persisted locally with transactional JSON storage, full JSON database backup/restore, and direct Excel (`.xls`) library export.

---

## 2. Navigation Architecture & Liquid Glass Dock Engine

```
                             ┌────────────────────────┐
                             │     InstrumentsApp     │
                             └───────────┬────────────┘
                                         │
               ┌─────────────────────────┼─────────────────────────┐
               ▼                         ▼                         ▼
         ┌───────────┐             ┌───────────┐             ┌───────────┐
         │ HomeScreen│             │ DrawerBar │             │Settings/  │
         │(Seq/Trace)│             │(Glass Dock│             │Backup/    │
         └───────────┘             └─────┬─────┘             │About      │
                                         │                   └───────────┘
               ┌────────────┬────────────┼────────────┬────────────┬────────────┐
               ▼            ▼            ▼            ▼            ▼            ▼
           [ TIME ]     [ RHYTHM ]  [ CALENDAR ]  [ HABITS ]   [ READING ]  [ YEARLINE ]
          • Stopwatch  • Metronome  • Apple Glass • Today/Habits• Shelf      • Full Year
          • Pomodoro   • Breathing  • Archival    • City/Towers• Counter     • Streak Flame
          • Hourglass                 Paper       • Goals/Dreams• Stats/Folio • Lens Filter
```

### 2.1. DrawerBar & Refractive Liquid Glass Dock (`Drawer.kt`)
The bottom navigation bar in Mezura is a physics-driven, floating liquid glass dock:
1. **Refractive Liquid Glass Body:**
   - The pill container renders a sleek rounded rectangle (`RoundedCornerShape(32.dp)`).
   - Filled with a smoked vertical liquid glass gradient (`#E61B1F28` $\rightarrow$ `#F50C0E13`) and bordered by a 1dp specular refractive stroke (`0x33FFFFFF` highlight to `0x0F000000` shadow) with a soft ambient drop shadow.
2. **Settled Spring Amber Indicator:**
   - Active tab features a spring-animated thin amber pill bar (`Color(0xCCF5B851)`, width 18dp, height 2.5dp) settled below the icon, driven by `spring(dampingRatio = 0.9f, stiffness = 450f)`.
   - Eliminates layout shifts and magnification jitter, ensuring rock-solid baseline stability.
3. **Typography & Label Safety:**
   - Single-line tab labels (`maxLines = 1, softWrap = false`) rendered at `11.sp` with compact padding (8dp horizontal, 7dp vertical), completely eliminating text truncation on labels like `"HABITS"`, `"READING"`, `"BREATHING"`, and `"YEARLINE"`.
4. **Haptic & Sound Design:**
   - Subtle spring press scale (`0.94f` with `dampingRatio = 0.8f, stiffness = 900f`).
   - Instant light haptic tick (`Feedback.haptic(context, Feedback.Haptic.TICK)`) on tap with zero sound click.
5. **Left Home Hub Dot:**
   - Red circular beacon (`#FF3B30`) inside a circular bezel for instant 1-tap navigation to the Home Hub, with long-press affordance to set Home as default startup screen.

---

## 3. Reading & Grand Library Folio System

**Core Files:**
- [`FolioScreen.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/FolioScreen.kt)
- [`VernierCaliperView.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/VernierCaliperView.kt)
- [`GrandLibraryFolioStatsScreen.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/GrandLibraryFolioStatsScreen.kt)
- [`MezuraMasterPlateRenderer.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/MezuraMasterPlateRenderer.kt)
- [`BookCompletionModels.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/BookCompletionModels.kt)

```
┌────────────────────────────────────────────────────────────────────────┐
│  FOLIO SUBSYSTEM: THREE DEDICATED SUB-INSTRUMENTS                      │
│                                                                        │
│   [ 1. SHELF ]               [ 2. THE COUNTER ]      [ 3. STATS ]       │
│   • 3-Column Grid            • Hardware Caliper V3   • Grand Library    │
│   • Fixed 38dp Title Box     • 360x425 Aspect Fitted   Folio Parchment  │
│   • Horizontal Star Align    • Split-Flap Display    • Brass Vernier    │
│   • No Progress on Finished  • 120-Tooth Jog Wheel     Ruler (7D..ALL)  │
│   • 92dp Liquid Glass Scrim  • Fast-Scrub Fader      • 365-Dot Matrix   │
│                              • Clear of Bottom Dock  • Walnut Rulers    │
└────────────────────────────────────────────────────────────────────────┘
```

### 3.1. Sub-Tab 1: The Library Shelf (`FolioScreen.kt`)
1. **Horizontal Star Alignment:**
   - `ShelfGridItem` wraps each book's title inside a fixed 2-line container (`Modifier.fillMaxWidth().height(38.dp)` with `maxLines = 2`).
   - Ensures that single-line titles and two-line titles occupy the exact same vertical space, guaranteeing that the 5-star rating row beneath them aligns to the exact same horizontal baseline across the shelf.
2. **Finished Book State:**
   - If a book is completed (`book.status == BookStatus.COMPLETED || book.rating > 0 || book.currentPage >= book.totalPages`), the progress line over the book cover is completely suppressed (`showProgressBar = false`), presenting a pristine cover with unclipped gold stars in the status footer.
3. **Elevated Progressive Liquid Glass Bottom Vignette (`BottomLiquidGlassVignette`):**
   - A $92\text{dp}$ pointer-transparent vertical gradient box (`0.00f to Transparent`, `0.35f to 0x8807080B`, `0.72f to 0xD907080B`, `1.00f to 0xFA060709`) sits behind the floating navigation bar, ensuring that scrolling book covers never peek raw beneath or beside the dock.

### 3.2. Sub-Tab 2: The Counter ("The Caliper") (`VernierCaliperView.kt`)
1. **Hardware Chassis Aspect Ratio & Bounds Fitting:**
   - Logical hardware chassis dimension: `360 x 425` (aspect ratio $360 / 425 = 0.8471$).
   - Fitted with `aspectRatio(360f / 425f, matchHeightConstraintsFirst = true)`.
   - Centering matrix:
     $$s = \min\left(\frac{W_{\text{canvas}}}{360}, \frac{H_{\text{canvas}}}{425}\right), \quad dx = \frac{W_{\text{canvas}} - 360 s}{2}, \quad dy = \frac{H_{\text{canvas}} - 425 s}{2}$$
   - Outer container has `padding(bottom = 108.dp)` in `FolioScreen.kt` and `padding(bottom = 8.dp)` in `VernierCaliperView.kt`, giving $\sim 116\text{dp}$ bottom clearance that guarantees zero overlap with the floating bottom dock bar.
2. **Micro-Mechanical Hardware Features:**
   - **Split-Flap Display:** 3-digit recessed cavity with authentic card leaves and axle pins. Tap anywhere on the bay to trigger direct page jump sheet.
   - **Outside Total Pages:** Laser-etched `/ 300 PAGES` in warm amber `#C98F47`.
   - **Sunray Antique Brass Gauge:** 21 hairline ticks with tapered crimson needle.
   - **120-Tooth Rotary Jog Wheel:** Anisotropic brushed stainless platter, `-44°` bolted brake lug, `+45°` knurled spring-lever for `+1 page` increments, and concave liquid-metal kidney thumb dimple.
   - **Micro Toggles:** Authentic bat-handle switches with laser-etched `HAPTIC` (Electric Cyan `#1AF2FF` LED) and `SOUND` (Emerald Green `#26FF78` LED).
   - **Molten Fast-Scrub Fader:** Vertical fader track with neon-orange incandescent core (`#FFF5B8` core $\rightarrow$ `#FF4D00` bloom).
   - **Pointer Persistence:** Tap and drag gestures keyed on `Unit` so gestures never cancel mid-scroll or upon state commits.

### 3.3. Sub-Tab 3: The Grand Library Folio Stats (`GrandLibraryFolioStatsScreen.kt`)
1. **Procedural Donor-and-Feather Parchment Restoration:**
   - Rather than flat color fills or smudged rectangular erasures, `restoreSeamlessMasterParchmentBand` synthesizes spotless archival parchment bands across:
     - Top Ruler Band: `topFrac = 0.012f, bottomFrac = 0.134f` (eradicates baked camera holes, demo ruler ticks, and baked rivets up to $0.134f$, safely stopping short of the KPI cards at $0.138f$).
     - Bottom Genre Distribution Band: `topFrac = 0.796f, bottomFrac = 0.982f` (eradicates old genre sliders and artifacts).
   - **Donor Strip & Boundary Delta:** Samples an authentic, clean donor strip from above the consistency matrix ($y = 1032..1044$, $\approx 0.783f..0.791f \times h$), capturing pristine paper fiber grain and horizontal vignette without any drop shadows or text bleeds. Computes boundary delta luminance offset $(\Delta R, \Delta G, \Delta B)$ against local border pixels and feathers smoothly over 10–12px cosine gradients, achieving zero-seam texture synthesis.
2. **Procedural 3D Machined Brass Vernier Ruler (`drawArtifactFreeBrassVernierRuler`):**
   - **4 Discrete Ranges:** `7D` (x=0.145), `30D` (x=0.270), `YEAR` (x=0.405), `ALL` (x=0.540).
   - **Ruler Body:** Brushed antique brass body (`#DEC896`, `#BEA26A`, `#9C7E48`), 68 engraved double-sided millimeter graduations, "mm" callouts, and soft drop shadow (`Color.argb(80, 42, 26, 10)`).
   - **Right Slot Track & Sliding Carriage:** Recessed slot track at `0.585f..0.915f * w`. The 3D machined brass carriage glides along `0.655f + progress * 0.135f * w`, featuring a left `<` pointer beak and circular stepped medallion. Zero red lines, zero ghost text, and zero label obstruction.
   - **Smooth Spring Animation:** Selected range indicator animates with `spring(dampingRatio = 0.76f, stiffness = 380f)`.
3. **Reading Consistency Matrix:**
   - Header strictly labeled `"READING CONSISTENCY (365-DOT MATRIX)"` (never "CADENCE").
   - 53 columns $\times$ 7 rows = 371 cells (or 365 days).
   - Bounded strictly within `y = 0.737f..0.7886f` to guarantee zero collision with the genre distribution section below.
4. **Clean Genre Distribution Section (`drawCleanGenreDistribution`):**
   - **Consistent Typography:** Header `"GENRE DISTRIBUTION"` rendered with `Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)` matching `"READING VELOCITY CURVE"` in size and weight. Genre category titles rendered with clean normal sans-serif; right-aligned percentages rendered in bold sans-serif.
   - 4 dynamic genre rows (`y = 0.854f, 0.887f, 0.920f, 0.953f * h`).
   - 3D Carved Walnut Millimeter Ruler Track (`0.405f..0.835f * w`) with dual brass mounting screws, top and bottom engraved millimeter ticks, and 3D brass sliding clasps positioned by animated percentage.
   - Right-aligned percentage readout (`$displayPct%`).

---

## 4. Book Completion & 7-Style Master Plate Architecture

**Core Files:**
- [`BookCompletionModels.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/BookCompletionModels.kt)
- [`MezuraMasterPlateRenderer.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/MezuraMasterPlateRenderer.kt)
- [`BookStatsBitmapExporter.kt`](file:///d:/Claude%20Code/NdotWidgets/app/src/main/kotlin/rocks/ndot/widgets/instruments/folio/BookStatsBitmapExporter.kt)

### 4.1. Curated 7-Style Numbering & Asset Mapping
Following design curation, the active report card designs are numbered strictly **`01` through `07`** with zero skipped numbers (`Field Survey Atlas` and `Orbital Telemetry` were permanently removed):

```kotlin
enum class BookStatsDesignStyle(
    val serialNumber: Int,      // Strictly 1..7 (Displayed as "01".."07" and "1 / 7".."7 / 7")
    val masterPlateId: Int,     // Asset file index (1, 2, 3, 4, 5, 6, 8)
    val pillLabel: String,      // "01 · ARCHIVAL" .. "07 · OBSIDIAN"
    val headerTitle: String     // Full display title
) {
    STYLE_1_ARCHIVAL_SPLIT_FLAP(1, 1, "01 · ARCHIVAL", "1. Archival Brass & Split-Flap"),
    STYLE_2_PERFORATED_LED(2, 2, "02 · LED MATRIX", "2. Gunmetal & Dot-Matrix LED"),
    STYLE_3_PARCHMENT_CLIPBOARD(3, 3, "03 · CLIPBOARD", "3. Warm Parchment & Charcoal Bento"),
    STYLE_4_CELESTIAL_ATLAS(4, 4, "04 · CELESTIAL", "4. Astrolabe & Celestial Atlas"),
    STYLE_5_DRAFTING_BLUEPRINT(5, 5, "05 · BLUEPRINT", "5. Drafting Blueprint Ledger"),
    STYLE_6_FROSTED_GLASS(6, 6, "06 · GLASS", "6. Smoked Frosted Glass & Bokeh"),
    STYLE_7_OBSIDIAN_EDITORIAL(7, 8, "07 · OBSIDIAN", "7. Minimalist Obsidian Editorial")
}
```

*Note on Asset Mapping:* Calling `MezuraMasterPlateRenderer.renderMasterPlate(...)` passes `style.masterPlateId` so that `STYLE_7_OBSIDIAN_EDITORIAL` loads `assets/book_stats/style_8.png`, while UI components show pill `"07 · OBSIDIAN"` and progress `"7 / 7"`.

### 4.2. Per-Card Zero-Bleed Erasure & 5-Star Vector Engine
1. **Guaranteed Crisp 5-Star Vector & Medallion Renderer (`drawCrispFiveStarsLeftToRight`):**
   - Custom mathematical vector calculation with 10 vertices ($R$, $r = 0.42 R$).
   - Outer radius is stroke-inset by $0.55 \times \text{strokeWidth}$ on unlit stars so that lit stars and unlit stroke stars share the exact same outer bounding envelope to the sub-pixel.
   - Optional circular medallion background (e.g. Card 3 Clipboard) and optional radial glow shader (e.g. Card 4 Celestial).
2. **Card-by-Card Surface Rules:**
   - **Card 1 (`01 · ARCHIVAL`):** Text paint rendered with subpixel and linear text flags (`SUBPIXEL_TEXT_FLAG or LINEAR_TEXT_FLAG`). Dynamic font resolution detects Bengali Unicode range (`\u0980..\u09FF`) and dynamically applies bundled `noto_sans_bengali.ttf`. Bold grotesque numeric typefaces. Dedicated removal of background blur box behind "Total Reading Time".
   - **Card 2 (`02 · LED MATRIX`):** LED rating slot strictly placed at `NormRect(0.516f, 0.290f, 0.938f, 0.355f)` with 5 stars centered at $cy = \text{slotRect.centerY()}$ ($D \approx 0.60 \times \text{slotH}$), illuminated in authentic warm amber gold (`#E8B04C` / `Color.rgb(251, 203, 137)`).
   - **Card 3 (`03 · CLIPBOARD`):** Wipes cover zone `[0.068f, 0.056f, 0.342f, 0.336f]` and header text zone `[0.370f, 0.045f, 0.908f, 0.332f]` via row-average sampling with 15px cosine feathering. Draws clean live header ("I'VE READ", book title auto-fit, author, dates, "Gave it" + 5 star medallions). Stamps masked 3D bulldog clip jaws over cover.
   - **Card 4 (`04 · CELESTIAL`):** Master design approved. Fills top medallion interior (`cx = 0.500f * w, cy = 0.162f * h, r = 0.095f * w`) with celestial navy before drawing cover.
   - **Card 5 (`05 · BLUEPRINT`):** Wipes old demo book and paperclip, along with header text zone. Redraws drafting grid lines over wiped area. Repositions 3D copper wire paperclip to top-center of cover (`cx = 0.207f * w, topY = 0.052f * h`). Renders crisp live header text and rating stars.
   - **Card 6 (`06 · GLASS`):** Pre-fills dark smoked glass before drawing rounded cover. Erases baked header text zone cleanly and renders crisp live text and rating stars.
   - **Card 7 (`07 · OBSIDIAN`):** Cover zone erased over $x = 0.045..0.450, y = 0.085..0.520$ with 8px cosine feathering to eliminate dark demo box and shadow halo. User cover fitted cleanly within `RectF(0.054f * w, 0.099f * h, 0.437f * w, 0.505f * h)` with hairline gold border.

---

## 5. Other Instrument Families

### 5.1. Time Family
- **Mechanical Stopwatch (`StopwatchScreen.kt` & `StopwatchRenderer.kt`):** 3-pusher chronograph (Top Reset Crown & Bow, Right Start/Stop Pusher, Left Lap Pusher) with Ivory Archival and Smoked Matte dials.
- **Pomodoro Clock (`PomodoroScreen.kt`):** Module, Slab, and Pocket hardware timers. Supports countdown intervals and open count-up reading mode that logs sessions directly to Folio.
- **Granular Hourglass (`HourglassScreen.kt`):** Sand granular physics across Classic, Dusk, Prism, and Dot themes.

### 5.2. Rhythm Family
- **Metronome (`MetronomeScreen.kt`):** Sample-accurate audio engine with Studio rotary dial and Pendulum mahogany casing with physical inertia simulation.
- **Breathing Instrument (`BreathingScreen.kt`):** Box Breathing and 4-7-8 relaxing breath with dynamic visual phosphor rings and sequence duration overrides.

### 5.3. Habits & The Living City (`HabitsScreen.kt` & `CityCanvas.kt`)
- 20-week visual streak matrix, standardized Light/Standard/Core tiers (48dp height), full edit pencil icons (`✎`), and detail sheets.
- 3D isometric voxel city where completed habits and reading award building bricks.

### 5.4. Yearline ("Almanac") (`AlmanacScreen.kt`)
- Comprehensive 365/366-day consistency heat matrix with streak flame badge and lens filters (`All`, `Habit`, `Dream`).

### 5.5. Home Hub: Sequence Engine & Trace Ledger (`SequenceEngine.kt` & `TraceScreen.kt`)
- Chains guided daily routines and automatically configures and launches target instruments with custom step durations.
- Immutable chronological audit ledger with status bar insets.

---

## 6. Project Directory & Key Files Map

```
app/src/main/
├── assets/
│   └── book_stats/                     # Master art plates (style_1.png .. style_8.png)
├── kotlin/rocks/ndot/widgets/
│   ├── app/
│   │   ├── InstrumentsApp.kt           # Master navigation host & sequence routing
│   │   └── Drawer.kt                   # macOS Genie Funnel & Magnetic Dock navigation bar
│   ├── common/
│   │   ├── GenieMagneticDrawerBar.kt   # Standalone Gaussian magnetic dock composable
│   │   ├── StopwatchRenderer.kt        # 3-pusher chronograph dial rendering
│   │   ├── NdotSheet.kt                # Spring bottom sheets
│   │   └── SoundManager.kt             # Mechanical audio click & haptic engines
│   └── instruments/
│       ├── breathing/
│       │   └── BreathingScreen.kt      # Guided breathing cycles
│       ├── calendar/
│       │   ├── CalendarScreen.kt       # In-app calendar
│       │   └── CalendarWidgetProvider.kt # Glance home screen widgets
│       ├── folio/
│       │   ├── FolioScreen.kt          # Shelf (38dp title container, 92dp vignette), Counter, Stats
│       │   ├── VernierCaliperView.kt   # Photorealistic hardware Counter / Caliper V3 (360x425)
│       │   ├── GrandLibraryFolioStatsScreen.kt # Grand Library Folio: seamless parchment, brass ruler, walnut tracks
│       │   ├── GrandLibrarySoundHapticEngine.kt # Dedicated audio/haptics for Folio
│       │   ├── MezuraMasterPlateRenderer.kt # 7-Style zero-bleed master plate engine
│       │   ├── MezuraBookStatsRenderers.kt  # 3D brass stars & stats overlays
│       │   ├── BookCompletionModals.kt # Book finish modal, 3D stars rating, quote entry
│       │   ├── BookCompletionModels.kt # 7 Active styles (01..07) enum & stats models
│       │   ├── BookStatsBitmapExporter.kt # High-res social share bitmap export
│       │   └── BookMetadataFetcher.kt  # Google Books & Open Library parser
│       ├── habits/
│       │   ├── HabitsScreen.kt         # Habit streaks & 20-week dot matrix
│       │   ├── HabitDetailSheets.kt    # Habit log sheet with Edit & Delete actions
│       │   └── CityCanvas.kt           # The Living City 3D voxel engine
│       ├── sequence/
│       │   ├── SequenceEngine.kt       # Daily routine state machine & tool auto-linking
│       │   └── SequenceEditorSheet.kt  # Routine CRUD editor
│       ├── stopwatch/
│       │   └── StopwatchScreen.kt      # Stopwatch UI with 3 pushers & lap reel
│       ├── timer/
│       │   ├── PomodoroScreen.kt       # Module, Slab, Pocket hardware timers
│       │   └── TimerEngine.kt          # Pomodoro countdown engine
│       ├── hourglass/
│       │   └── HourglassScreen.kt      # Granular sand simulation
│       ├── metronome/
│       │   └── MetronomeScreen.kt      # Studio console & Pendulum metronomes
│       ├── trace/
│       │   └── TraceScreen.kt          # Audit ledger with statusBarsPadding
│       └── yearline/
│           └── AlmanacScreen.kt        # 365-day heat matrix & streak flame
```

---

## 7. Instructions & Guidelines for Future LLMs

When modifying, maintaining, or expanding **Mezura**, adhere strictly to these principles:

1. **Master Parchment Restoration Rule:**
   - In `GrandLibraryFolioStatsScreen.kt`, **never** replace `restoreSeamlessMasterParchmentBand` with flat rectangle fills (`eraseHorizSmooth` or `drawRect`). The parchment texture is procedurally synthesized from authentic asset fibers; flat fills create smudged rectangular visual regressions.
2. **7-Style Numbering & Asset Mapping Rule:**
   - The active design styles are strictly numbered `1..7` (`01` through `07`).
   - `STYLE_7_OBSIDIAN_EDITORIAL` has `serialNumber = 7` and `masterPlateId = 8`. Always pass `style.masterPlateId` when loading art assets, while displaying `style.pillLabel` (`"07 · OBSIDIAN"`) and progress `"7 / 7"`.
   - Never reintroduce removed styles 7 (`Field Survey Atlas`) or 9 (`Orbital Telemetry`) without explicit user instruction.
3. **Hardware Aspect-Fitting & Bottom Clearance Rule:**
   - `The Counter` must maintain an aspect ratio of `360f / 425f` with `matchHeightConstraintsFirst = true`.
   - Always calculate centering transforms via `scale = minOf(size.width / 360f, size.height / 425f)` and ensure at least `86.dp` to `108.dp` clearance above the floating bottom dock.
4. **Library Shelf Star Alignment Rule:**
   - Book titles in `ShelfGridItem` must remain inside a fixed-height container (`height(38.dp)` with `maxLines = 2`) to ensure that 1-line and 2-line titles share identical height and all star rows align to the exact same horizontal baseline.
   - For completed or rated books, suppress progress bars on the book cover.
5. **Progressive Liquid Glass Vignette Rule:**
   - `BottomLiquidGlassVignette` ($92\text{dp}$ height) must be present behind the floating `DrawerBar` on scrolling screens so that book covers and content do not peek raw underneath the navigation dock.
6. **Liquid Glass Dock Rule:**
   - In `Drawer.kt`, maintain the static liquid glass rounded pill (`RoundedCornerShape(32.dp)` with smoked vertical liquid glass fill `#E61B1F28` to `#F50C0E13` and specular refractive stroke).
   - Use settled spring-animated thin amber pill bar (`#CCF5B851`, 18dp x 2.5dp) for active indication.
   - Enforce single-line labels (`maxLines = 1, softWrap = false`, `11.sp`) to prevent clipping. Tab clicks must trigger light haptic tick with zero sound click.
7. **Offline-First & Audio-Haptic Rule:**
   - Never add cloud dependencies, external telemetry, or network-required logins.
   - All physical controls (jog wheel, faders, pushers, levers) must produce corresponding audio and haptic feedback via `SoundManager` and `GrandLibrarySoundHapticEngine`.
8. **Baked Text Overlap Prevention Rule:**
   - **Never draw live rendered text directly over baked plate demo text.**
   - In master plates with baked English placeholder text (e.g. Card 03 Clipboard, Card 05 Blueprint, Card 06 Glass), always execute clean background texture erasure (sampling edge rows or donor textures with 12–15px cosine feathering) before rendering user metadata (title, author, dates, rating).
   - In Card 01 Archival, render text with `SUBPIXEL_TEXT_FLAG or LINEAR_TEXT_FLAG` and use `NotoSansBengali.ttf` for Bengali ranges (`\u0980..\u09FF`). Never paint artificial background blur blocks behind stat values.

---

## 8. Version History & Build Verification (v1.0.9)

| Version | Code | Milestone Summary |
| :--- | :--- | :--- |
| `1.0.0` | `1` | Initial proof-of-concept; basic Stopwatch, Pomodoro, and Calendar widgets. |
| `1.0.1` | `2` | Added Hourglass sand physics, Metronome audio engine, and basic Habit dot matrix. |
| `1.0.2` | `3` | Introduced Folio book tracking, Caliper V1, and Living City isometric engine. |
| `1.0.3` | `4` | Caliper V2 vernier overhaul; added Bengali book search; Excel library export. |
| `1.0.4` | `5` | Transitioned brand to **Mezura** (`com.instrument.mezura`); Caliper V3 photorealistic hardware UI. |
| `1.0.5` | `6` | Reading Stats Master Plate Engine (9 styles); 10-faceted 3D brass stars; 3-pusher stopwatch. |
| `1.0.6` | `7` | Grand Library Folio stats screen introduction; audio-haptic engine overhaul. |
| `1.0.7` | `8` | Curated 7-style share report card system; removed Field Atlas and Telemetry; fixed Obsidian numbering. |
| `1.0.8` | `9` | **Holistic Polish & macOS Genie Dock Release:**<br>• Seamless master parchment restoration in Grand Library Folio (`y = 0.010..0.124` & `0.814..0.982`).<br>• Procedural 3D brass vernier ruler (7D..ALL) with gliding carriage.<br>• 365-Dot Matrix Cadence bounded to `0.737..0.7886` with strict "CONSISTENCY" branding.<br>• Clean Genre Distribution with carved walnut tracks and sliding brass clasps.<br>• Shelf 38dp fixed title container for horizontal star alignment; hidden progress lines on completed books.<br>• Progressive 92dp liquid glass bottom vignette.<br>• Counter chassis aspect-fitted (`360x425`) with safe bottom clearance. |
| `1.0.9` | `10` | **Master Plate Zero-Bleed & Precision Liquid Glass Dock Release:**<br>• **Procedural Donor-and-Feather Restoration:** Donor strip sampled at $y \approx 0.785f \times h$ with boundary delta luminance adjustments and 10–12px cosine feathering; top wipe band extended to `0.012f..0.134f` removing rivets up to $0.134f$ without touching KPI cards at $0.138f$; bottom band wiped over `0.796f..0.982f`.<br>• **Genre Typography:** Consistent bold sans-serif header matching Velocity Curve; normal sans-serif category titles; bold sans-serif percentages.<br>• **Bundled Bengali Font:** Packaged Google Fonts `NotoSansBengali.ttf` in `assets/fonts/` with automatic Unicode range detection (`\u0980..\u09FF`).<br>• **Card 01 Archival:** Subpixel and linear text flags; grotesque bold numbers; removed artificial blur behind Total Reading Time.<br>• **Card 02 LED Matrix:** Star slot strictly placed at `NormRect(0.516f, 0.290f, 0.938f, 0.355f)` with 5 centered stars lit in warm amber gold (`#E8B04C`).<br>• **Cards 03, 05, 06 Clean Header Erase:** Header text zones wiped with 15px feathering; live rendered typography ("I'VE READ", title, author, dates, rating); Card 05 Blueprint paperclip repositioned to cover top-center (`cx = 0.207f * w, topY = 0.052f * h`).<br>• **Card 07 Obsidian:** Cover zone erased over $x = 0.045..0.450, y = 0.085..0.520$ eliminating dark demo box and halo; clean letterbox aspect-fit with hairline gold border.<br>• **Liquid Glass Dock:** Static rounded liquid glass pill with settled spring-animated thin amber pill bar (`#CCF5B851`, 18dp x 2.5dp); single-line tab labels (`11.sp`) with zero truncation; responsive press scale (`0.94f`) with light haptic tick detent and zero sound click. |

### Verified Build Artifacts (v1.0.9)
- **Compilation & Unit Tests:** `.\gradlew.bat compileDebugKotlin testDebugUnitTest` passed with 0 errors.
- **Assembly:** `.\gradlew.bat assembleDebug assembleRelease` completed successfully.
- **Output Directory:** `app/build/outputs/apk/release/` & `app/build/outputs/apk/debug/`

| File | Size | SHA-256 Checksum |
| :--- | :--- | :--- |
| **`Mezura-v1.0.9-release.apk`** | 127.82 MB (134,029,116 bytes) | `B5A30A29764C66B3D32EE2C8EF597269212F735B2A64A4E82FCBEB17EF2CF680` |
| **`Mezura-v1.0.9-debug.apk`** | 132.19 MB (138,610,160 bytes) | `3795D531211D914B782D01ECFA5089E2B2580736D454428E1CE2EE49397ED123` |
| **`Mezura.apk`** | 127.82 MB (134,029,116 bytes) | `B5A30A29764C66B3D32EE2C8EF597269212F735B2A64A4E82FCBEB17EF2CF680` |
