# WORKLOG.md — StrengthLogz Changelog & Development Record

This worklog back-dates and details all active features, milestones, refactors, and bug fixes in **StrengthLogz** across both Desktop and Native Android implementations.

---

## [Phase 1: Project Genesis & Desktop Core] — 15-OCT-2026
*Commit Scope: Initial desktop foundation, Google Sheets synchronization, and parser.*

### Features & Infrastructure
- **Electron Desktop Application**:
  - Initialized Node.js + Electron architecture with secure IPC `contextBridge` (`preload.js` -> `main.js`).
  - Implemented single-window desktop UX with auto-hiding menu bars, responsive dark UI (`#0F1115` obsidian, `#FF7A45` electric orange), and shortcut accelerators (`Ctrl+S` save, `Ctrl+1-5` tab navigation).
- **Google Sheets Cloud Backend**:
  - Implemented `src/stores/sheets-store.js` using Google Cloud Service Account OAuth credentials (`googleapis`).
  - Automated three-tab relational schema: `Workouts` (ID, Date, Title, Notes), `Sets` (Workout ID, Exercise, Weight, Reps, RPE, Notes, Set Index), and `Exercises` (Name, Category, Bodyweight flag).
  - Built offline local JSON fallback in `src/stores/local-store.js` (`userData/local-data.json`).
  - Created one-click cloud migration tool to append or replace local workout history in Google Sheets without data loss.
- **Backlog Parser**:
  - Created `src/history-parser.js` and `scripts/parse-history.js` to ingest raw historical workout logs (`data/history.txt`).
  - Parsed 120 historical workout sessions (~1,390 sets across 82 unique exercises) ranging from October 2025 through October 2026.
  - Standardized exercises catalog in `data/exercises.json`.
- **Desktop Analytics & Visualizer**:
  - Integrated Chart.js canvas graphing with interactive metric toggles (Estimated 1RM, Top Weight, Total Volume, Max Reps).
  - Built 3-month, 6-month, 1-year, and all-time range filters.

---

## [Phase 2: Native Android App Implementation] — 16-OCT-2026 (Morning & Early Afternoon)
*Commit Scope: 100% Native Kotlin Jetpack Compose client architecture.*

### Motivation & Decision
- Retired wrapped web views in favor of an Android native portrait application built from scratch in `mobile-android/`.
- Configured Gradle 8.7 with Android SDK 34, Kotlin 1.9.23, Jetpack Compose BOM 2024.04.01, and Material3.

### Implementation Details
- **Architecture**:
  - Established `com.strengthlogz.mobile` with unified data models (`Workout`, `WorkoutSet`, `Exercise`) matching the desktop schema.
  - Implemented `WorkoutRepository` maintaining memory-cached session indices and local persistence.
  - Implemented `GoogleSheetsClient` using lightweight OkHttp v4 REST calls for authenticating and exchanging sheet data on mobile devices.
- **Mobile Screen Suite**:
  1. **Log Workout Screen (`LogWorkoutScreen.kt`)**:
     - Fast mobile-optimized numeric keyboard inputs.
     - Live Epley E1RM calculation as weights and reps are entered.
     - Quick "Previous Session" auto-fill pills to facilitate progressive overload.
  2. **History Screen (`HistoryScreen.kt`)**:
     - Searchable session cards with expandable set tables.
     - One-click "Repeat Workout" action copying previous sets into active logger.
  3. **Settings Screen (`SettingsScreen.kt`)**:
     - Cloud sync manager allowing paste of Google Sheet ID and service account JSON key.
     - Instant sync verification status badge.

---

## [Phase 3: Strength Progress Overhaul & Bug Fixes] — 16-OCT-2026 (Late Afternoon)
*Commit Scope: Analytical UI revamp, zero-rep bug fix, and date standardization.*

### Features & Fixes
- **Strength Progress Redesign (`ProgressScreen.kt`)**:
  - Replaced basic charts with an interactive scrubbing canvas chart featuring visual tooltips and date indicators.
  - Added exercise search bar with auto-filtered quick-picker chips.
  - Created a 4-card metric dashboard:
    1. **All-Time PR**: Best estimated 1RM with date and set details.
    2. **Current E1RM**: Most recent session value with $\pm \Delta$ versus PR.
    3. **Top Weight Logged**: Absolute heaviest weight moved.
    4. **Total Sessions & Sets**: Lifetime frequency and volume tally.
  - Added metric toggle between **Estimated 1RM (E1RM)** and **Working Set Weights & Reps**.
  - Expanded session history list to scroll fluidly with the entire page instead of being constrained to an inner box.
  - Built full-screen Session Inspector modal.
- **Zero-Rep Failed Set Bug Fix**:
  - **Issue**: Attempting a weight and achieving 0 reps (e.g. `115kg x 0` or `190kg x 0`) previously computed an invalid E1RM of $126.5\text{kg}$ ($115 \times (1 + 0/30)$).
  - **Resolution**: Strict validation rule implemented in both Android (`WorkoutRepository.kt`, `ProgressScreen.kt`) and Desktop (`renderer/app.js`): sets with `reps <= 0` or missing reps are explicitly excluded from E1RM calculation, working set stats, top weight, and PR determinations.
- **Date Format Standardization**:
  - Created `DateUtils.kt` (Android) and updated `renderer/app.js` (Desktop) to enforce `DD-MON-YYYY` format (e.g., `09-OCT-2026`) across all screens, cards, modals, and export tables.

---

## [Phase 4: Smart Workout Suggestion Engine] — 16-OCT-2026 (Evening)
*Commit Scope: "Suggest a Workout" screen and volume deficit algorithms.*

### Features & Infrastructure
- **7-Day Muscle Volume Analysis**:
  - Computes rolling 7-day and 14-day total sets logged for Chest, Back, Shoulders, Legs, Arms, and Core.
  - Automatically identifies deficit muscle groups relative to weekly targets (Chest: 10, Back: 12, Shoulders: 9, Legs: 10, Arms: 9, Core: 4) and recommends the optimal split for the day.
- **Splits Supported**:
  - **Push**: Chest, Quads, Shoulders, Triceps.
  - **Pull**: Vertical Pulls, Heavy Rows, Hip Hinges / Posterior Chain, Rear Delts, Biceps.
  - **Full Body (FB)**: Compound Push/Hinge, Upper Push, Upper Pull, Secondary Lower, Delts, Arms/Core.
- **Progressive Overload Prescriptions**:
  - Evaluates previous workout weight/reps and assigns a target:
    - $+2.5\text{kg}$ weight jump when previous performance exceeded 8 reps on compounds.
    - $+1.25\text{kg}$ for smaller isolation movements.
    - $+1\text{ rep}$ volume overload when holding weight constant.
- **One-Click Logger Loading**:
  - Seamlessly ports suggested exercise sets directly into the active logging screen for the day's training.

---

## [Phase 5: Branding, Push/Pull Leg Refactor, & Windows Native Packaging] — 16-OCT-2026 (Night)
*Commit Scope: Custom app icon, Windows .exe packaging, and exercise benefit enhancements.*

### Features & Fixes
- **Custom Android App Icon Logo**:
  - Generated high-resolution custom icon featuring an obsidian background (`#0F1115`) with a glowing electric orange (`#FF7A45`) and gold (`#FFD166`) hexagonal barbell emblem and bold "StrengthLogz" typography.
  - Resized and compiled into standard launcher densities: `mipmap-mdpi` (48px), `mipmap-hdpi` (72px), `mipmap-xhdpi` (96px), `mipmap-xxhdpi` (144px), `mipmap-xxxhdpi` (192px), and round icons.
  - Updated [AndroidManifest.xml](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/AndroidManifest.xml) and built [builds/StrengthLogz-Native.apk](file:///c:/MyProjects/StrengthLogz/builds/StrengthLogz-Native.apk).
- **Windows Native `.exe` Packaging**:
  - Integrated `electron-builder` into `package.json`.
  - Resolved bundled asset resolution bug by including `data/**/*` in the `.asar` build archive so `data/exercises.json` is properly packaged.
  - Generated both **NSIS Setup Installer** (`builds/StrengthLogz Setup 1.0.0.exe`) and **Standalone Portable Executable** (`builds/StrengthLogz 1.0.0.exe`).
- **Push/Pull Leg Refactor & Exercise Discovery**:
  - Refactored suggestion slots so Push splits include dedicated push legs (Leg Press, Bulgarian Split Squats, Hack Squats, Leg Extension) and Pull splits include pull legs/hinges (Good Mornings, Hyperextensions, Romanian Deadlifts, Leg Curls).
  - Integrated training benefit explanations across both mobile and desktop (e.g. Bulgarian Split Squats for unilateral balance and spinal relief; Good Mornings for posterior chain lumbar resilience).
  - Added new exercise discovery weighting so unlogged movements regularly surface upon shuffle or slot swap.

---

## [Phase 6: Set Notation Standardization, Workout Detail Page & Mobile Logger Overhaul] — 19-OCT-2026
*Commit Scope: Unified set display notation, dedicated session inspection view (Desktop & Mobile), and animated mobile workout logger overhaul with explicit Save Exercise workflow.*

### Features & Infrastructure
- **Standardized Set Notation (`sets x reps @ weight`)**:
  - Implemented unified grouping and formatting logic across Windows Desktop (`renderer/app.js`) and Native Android (`util/SetUtils.kt`).
  - Formats identical consecutive sets as `sets x reps @ weight` (e.g., `3 x 10 @ 10kg` or `3 x 10 @ BW`).
  - Delimits varying sets with clean commas: `1 x 10 @ 10kg, 2 x 9 @ 10kg`.
  - Automatically adopted across History cards, active logger hints, progress tooltips/tables, and workout suggestions.
- **Dedicated Workout Detail Page (Desktop & Mobile)**:
  - **Desktop (`#view-workout-detail`)**:
    - Clicking any workout card in History navigates to a dedicated full-view session inspection screen with `← Back to History` navigation.
    - Displays overall workout stats (Total Volume with +/- delta vs previous workout, Total Working Sets, Total Reps, and 🏆 PR count).
    - Per-exercise cards show full set-by-set breakdown tables alongside a 4-metric comparison grid against the prior session for that exact movement (Volume delta, Top Weight delta, Est. 1RM delta, and Total Reps delta).
    - One-click "View Progress Chart →" buttons jump directly to Chart.js analytics for each movement.
  - **Native Android (`WorkoutDetailScreen.kt`)**:
    - Tapping any workout card in `HistoryScreen` opens `WorkoutDetailScreen` with Material3 TopAppBar and back navigation.
    - Features overall session volume hero card and per-exercise comparison cards with PR badges, set breakdown tables, and direct links to `ProgressScreen`.
- **Mobile Log Workout Screen Overhaul (`LogWorkoutScreen.kt`)**:
  - Upgraded to a two-state card model (Active Editing vs. Confirmed/Saved) with spring physics animations (`animateContentSize`).
  - Active editing card features a glowing electric orange border (`#FF7A45`), live Epley E1RM preview, and quick increment chips (`+2.5kg`, `+5kg`, `+1 rep`).
  - Added high-confidence **"Save Exercise"** button that smoothly locks in the sets and displays a green `✓ Saved` checkmark badge.
  - Confirmed exercises display a clean summary with volume badge and an "Edit" button to re-open for modifications anytime.
  - Rebuilt the exercise picker with category filter chips (`All`, `Chest`, `Back`, `Legs`, `Shoulders`, `Arms`, `Core`, `Custom`), instant search, and 1-tap addition.
- **Verification & Builds**:
  - Executed Android unit tests (`testDebugUnitTest` - 23 tasks passed).
  - Compiled and verified Android debug APK: `builds/StrengthLogz-Native.apk`.

---

## [Phase 8: Exercises Section & Google Sheets Backend Backup] — 20-OCT-2026
*Commit Scope: Dedicated Exercises management screen, multi-line form/setting descriptions, 4-column Google Sheets schema synchronization, full exercise renaming with historical set cascade.*

### Features & Architecture
- **Dedicated Exercises Screen (`ExercisesScreen.kt`)**:
  - Created a top-level mobile destination with real-time exercise search, category filter chips (`All`, `Chest`, `Back`, `Legs`, `Shoulders`, `Arms`, `Core`, `Custom`), and an exercise count badge.
  - Interactive exercise cards displaying lifetime tracking stats (total sets logged, total volume tonnage), category tag, and bodyweight indicators.
  - Multi-line expandable **Description & Form Cues** drawer for each movement (e.g., machine pin settings, bench angle, grip cues, execution tempo).
  - "+ New Exercise" dialog with input validation, duplicate detection, category selector, bodyweight toggle, and multi-line notes.
  - Full Edit dialog allowing lifters to rename exercises and update category, bodyweight status, and descriptions.
- **Relational Integrity & Cascade Renaming**:
  - `WorkoutRepository.updateExercise(oldName, updated: Exercise)` preserves data continuity by updating catalog entries and cascading renames across every historical `WorkoutSet` in `appData.sets`.
  - Remote batch cascade via `GoogleSheetsClient.renameExerciseInSets(rawSpreadsheetId, oldName, newName)`: searches `Sets!D:D` and updates every matching set cell in Google Sheets.
- **Google Sheets Backend Backup & 4-Column Schema Parity**:
  - Extended Google Sheets `Exercises` tab schema to: `Name | Category | Bodyweight | Description`.
  - `GoogleSheetsClient.kt`:
    - `loadAll()` updated to read `Exercises!A2:D`, parsing description with backward-compatible fallback (`r.getOrElse(3) { "" }`).
    - `addExercises()` appends the description field.
    - `overwriteExercises()` clears and updates the entire catalog while ensuring A1:D1 header integrity.
    - `backupExercisesToGoogleSheets()` orchestrates local catalog updates, remote Sheets catalog overwrite, and remote set renames.
    - Bidirectional sync merges local and cloud exercise descriptions without overwriting user notes.
  - Desktop `src/stores/sheets-store.js`:
    - Updated `TABS.Exercises` to `Exercises!A2:D`.
    - Updated `loadAll()` and `addExercises()` to maintain schema parity across desktop and mobile clients.
- **Navigation & Integration**:
  - Added 6th primary tab `Exercises` with `Icons.Default.FitnessCenter` to the bottom navigation bar in `MainActivity.kt`.
  - Added a `Manage →` quick-action link in the `LogWorkoutScreen` exercise picker dialog title, allowing lifters to jump directly to exercise management mid-logging.
- **Verification & Builds**:
  - Android unit tests passed (`testDebugUnitTest` - 23 tasks passed).
  - Compiled and verified native Android APK deliverable: `builds/StrengthLogz-Native.apk` (15.7 MB).

---

## [Bugfix: Null-Safety on Legacy Exercise Deserialization & Exercises Screen Crash] — 20-OCT-2026
*Commit Scope: Resolved crash on opening Exercises screen caused by Gson deserializing legacy JSON records missing the `description` field into Kotlin null references.*

### Root Cause Analysis
- In `Models.kt`, `description` was defined as `val description: String = ""`. When Gson deserializes JSON from existing device storage (`strengthlogz_data.json`) or pre-Phase-8 catalog assets (`initial_data.json`), Gson uses `UnsafeAllocator` and bypasses Kotlin's constructor defaults, setting the missing `description` field directly to Java `null`.
- In `ExercisesScreen.kt`, the initial search filtering (`ex.description.contains(...)`) and card rendering (`exercise.description.isNotBlank()`) attempted to call String extension functions on the null reference, resulting in `java.lang.NullPointerException` inside Kotlin's `Intrinsics.checkNotNullParameter`.

### Key Fixes
- **Model Null-Safety (`Models.kt`)**: Updated `Exercise` to accept `val description: String? = ""` and exposed `val safeDescription: String get() = description ?: ""` to guarantee non-null string access everywhere.
- **Data Normalization (`WorkoutRepository.kt`)**: Enhanced `normalizeDates()` to safely sanitize all deserialized exercises, mapping null or blank descriptions to `""` and missing categories to `"Other"` upon app launch and Sheets sync.
- **Screen Resiliency (`ExercisesScreen.kt`)**:
  - Used `ex.safeDescription` in filter matching and UI rendering.
  - Safeguarded `itemsIndexed` keys with `${ex.name}_$index` to prevent crashes in the event of duplicate exercise names.
- **Seed Asset Synchronization**: Updated `initial_data.json` and `data/exercises.json` so all 82 catalog movements explicitly carry `"description": ""`.
- **Automated Regression Testing (`GoogleSheetsSyncTest.kt`)**: Added `verifyLegacyExerciseJsonWithoutDescriptionDoesNotCrash` unit test verifying that legacy JSON without descriptions deserializes safely without NPEs.
- **Verification & Build**: All unit tests passed (`testDebugUnitTest` - 23 tasks passed); compiled fresh APK to `builds/StrengthLogz-Native.apk` (15.7 MB).

---

## [Phase 9: Workout Draft Local Cache, History Weekday Presentation & 4-5 Exercise Suggest Limits] — 23-OCT-2026
*Commit Scope: Active workout draft auto-persistence across screen switches and app reloads, day-of-week presentation in workout history, and streamlined 4-5 exercise suggestions.*

### Features & Architecture
- **In-App Active Workout Draft Local Cache**:
  - Created relational draft models (`ActiveWorkoutDraft`, `DraftExerciseLog`, `DraftSetInput`) in [`Models.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/model/Models.kt).
  - Implemented thread-safe `SharedPreferences` draft storage in [`WorkoutRepository.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/data/WorkoutRepository.kt) (`getActiveDraft()`, `saveActiveDraft()`, `clearActiveDraft()`, `hasActiveDraft()`).
  - Integrated reactive `snapshotFlow` in [`LogWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/LogWorkoutScreen.kt) to continuously auto-persist session state on every set entry, weight/rep modification, exercise addition/deletion, or completion checkmark (`✓`).
  - Lifters can safely navigate to the Exercises screen (or History/Progress/Settings) mid-workout and return to find their entire session restored intact.
  - Added an in-logger "Active Session Restored" banner and a "Discard Active Workout" action with confirmation dialog.
  - Draft is cleared automatically upon tapping "Finish Workout" or confirming discard.
- **Day-of-Week History Presentation**:
  - Implemented `DateUtils.getDayOfWeek(raw)` and `DateUtils.formatDisplayWithDay(raw)` in [`DateUtils.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/util/DateUtils.kt).
  - Updated [`HistoryScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/HistoryScreen.kt) cards to format session dates with the day of the week (e.g. `Monday • 20-OCT-2026`).
  - Updated [`WorkoutDetailScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/WorkoutDetailScreen.kt) top bar and session header to display the day of the week.
- **4–5 Exercise Suggestion Limits & Length Selector**:
  - Streamlined `PUSH_SLOTS`, `PULL_SLOTS`, and `FULL_BODY_SLOTS` from 6 down to 5 high-yield movements in [`SuggestWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SuggestWorkoutScreen.kt) and desktop [`renderer/app.js`](file:///c:/MyProjects/StrengthLogz/renderer/app.js).
  - Added interactive `[ 4 Exercises | 5 Exercises ]` segmented selector chips, allowing lifters to choose between a rapid 4-exercise routine or a 5-exercise session.
- **Verification & Builds**:
  - Added unit test suite [`WorkoutUpgradeTest.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/test/java/com/strengthlogz/mobile/WorkoutUpgradeTest.kt) covering `DateUtils` day-of-week transformations and `ActiveWorkoutDraft` JSON round-trip serialization.
  - Executed Gradle unit tests (`testDebugUnitTest` - 23 tasks executed, 100% passed in 1m 6s).
  - Compiled and verified production debug Android binary: [`builds/StrengthLogz-Native.apk`](file:///c:/MyProjects/StrengthLogz/builds/StrengthLogz-Native.apk) (15.7 MB).

---

## [Phase 10: Complete Visual & UI Overhaul of the Android Application] — 23-OCT-2026 (Evening)
*Commit Scope: Full visual styling overhaul, slick animations, high-readability interactive charts, responsive gym ergonomics, and plate calculator visualization across the entire Android app.*

### Visual Design System & Component Architecture
- **Obsidian Dark Theme & Token Refinement**:
  - Engineered a cohesive design system in [`Color.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/theme/Color.kt) featuring deep obsidian surfaces (`BgDark` `#0C0E12`, `SurfaceDark` `#13161D`, `SurfaceCard` `#181C26`, `SurfaceCardElevated` `#1F2432`, `BorderDark` `#282E3D`), brand neon orange accents (`AccentOrange` `#FF7A45`, `AccentPeach` `#FF9A6B`), PR trophy gold (`GoldPR` `#FFD166`), emerald green (`IncreaseGreen` `#34D399`), and rose red (`DecreaseRed` `#F87171`).
  - Added visual gradient tokens: `OrangeGradient`, `GoldGradient`, `PrCardGradient`, `HeroHeaderGradient`, and `CardSurfaceGradient`.
- **Reusable Core Components Suite**:
  - Created [`SharedComponents.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/theme/SharedComponents.kt):
    - `StrengthCard`: Elevated card container with subtle borders, gentle gradient backgrounds, and spring tactile press states.
    - `CategoryPill`: High-contrast categorical badges for muscle groups (Chest, Back, Legs, Shoulders, Arms, Core) with custom background/text pairings.
    - `PrTrophyBadge`: Golden trophy PR indicator with custom glowing pill.
    - `SetCompletionCheckButton`: Smooth bouncy spring completion toggle (`DampingRatioMediumBouncy`) with checkmark icon transition.
    - `ModernSearchBar`: Outlined search field with focused glowing orange border, magnifying glass, and instant clear button.
    - `ModernScreenHeader`: Screen title typography, uppercase tracking subtitles, and optional action badges.

### Screen-by-Screen Overhaul & Ergonomics
- **Main Shell & Navigation ([`MainActivity.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/MainActivity.kt))**:
  - Integrated `AnimatedContent` cross-fade and scale transitions between tabs and workout detail views.
  - Replaced the clunky default bottom bar with an ultra-responsive 62dp custom bottom navigation bar with active micro-capsule indicators, animated icon bounce, and 1-line labels to guarantee zero text truncation on any phone width.
  - Added an active draft badge dot on the Log tab when a workout is in progress while browsing other screens.
- **Log Workout Experience ([`LogWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/LogWorkoutScreen.kt))**:
  - Symmetrical 38dp numerical inputs with centered bold text, keyboard types, and focused border states.
  - Live Epley E1RM preview calculated on every keystroke, with an animated Gold PR banner (`🏆 NEW ALL-TIME PR!`) when beating all-time peak records.
  - Invalidation of failed attempts (`reps <= 0`): Instant red badge (`✕ FAILED ATTEMPT (0 reps) — not counted in PRs or volume`) and zero E1RM.
  - Quick-copy "PREV" ghost pill that auto-fills past set data in 1 tap.
  - Tactile increment chips (`+1.25k`, `+2.5k`, `+5k`, `+1r`) directly under each exercise.
  - Quick split preset pills (`Push`, `Pull`, `Legs`, `Full Body`, `Upper`, `Lower`) for 1-tap title fill.
  - Floating sticky action bar showing live total tonnage and completed sets counter (`14 sets • 8,450 kg`), "+ Add Exercise", and "Finish Workout".
- **Interactive Progress Charts & Metrics ([`ProgressScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/ProgressScreen.kt))**:
  - 2x2 Hero Metric Dashboard: All-Time E1RM PR (gold trophy card), Heaviest Set PR (flame card), Latest Session with delta (+/- kg vs previous), and All-Time Change since day 1 (+kg and % gain).
  - Modern Canvas-drawn progress graph:
    - Glowing multi-segment progression line (green for increases, red for dips, orange for maintenance).
    - Vertical gradient area fill under the curve.
    - Horizontal dashed grid lines with accurate kilogram labels and peak PR dashed baseline.
    - Responsive magnetic touch scrubbing across data points with vertical crosshair guideline.
    - Inspected Point Callout HUD displaying exact date, workout title, top set, E1RM source, volume, PR badge, and delta vs previous session.
  - Full-screen chart inspector dialog with range filters and point-by-point navigation.
- **Workout History & Detail ([`HistoryScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/HistoryScreen.kt) & [`WorkoutDetailScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/WorkoutDetailScreen.kt))**:
  - History cards with day-of-week badges, session tonnage pills, working sets count, and exercise chips.
  - Workout Detail: 4-stat overview grid (Volume + delta vs previous workout, Sets/Reps, Movements count, PRs Achieved), exercise breakdown tables, and 1-tap deep links to Progress graph.
  - Animated spinning sync indicator.
- **Progressive Overload Workout Suggester ([`SuggestWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SuggestWorkoutScreen.kt))**:
  - Split cards (Push / Pull / Full Body) with volume deficit status indicators and "Recommended Today" badge.
  - 7-day muscle group volume progress bars (Chest, Back, Legs, Shoulders, Arms, Core) with color-coded deficit status.
  - Exercise prescription cards with reason tags, overload prescriptions (+2.5kg / +1 rep), kinetic benefits, and "Swap" buttons.
  - Large sticky "Start Session" button that loads the prescribed routine directly into the logger.
- **Visual Barbell & Dumbbell Plate Calculator ([`PlateCalculatorDialog.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/PlateCalculatorDialog.kt))**:
  - Olympic color-coded plates: 20kg Blue, 15kg Yellow, 10kg Green, 5kg White, 2.5kg Red, 1.25kg Silver.
  - Dynamic visual sleeve rendering the actual plate stack loaded on the collar with proportional plate heights!
  - Steppers and quick increment chips, plate count per side, and "Apply to Set" button.
- **Exercises Catalog & Cloud Settings ([`ExercisesScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/ExercisesScreen.kt) & [`SettingsScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SettingsScreen.kt))**:
  - Exercises: Category filter pills, search bar, exercise cards with PR highlights, total sessions, and edit/create modal with instant Google Sheets cloud backup.
  - Settings: Cloud sync status badge, spreadsheet ID input, animated sync button, and 3-card local database summary.

### Verification & Deliverables
- Executed full unit test suite: `testDebugUnitTest` (23/23 tasks executed, 100% passed in 44s).
- Compiled and verified production debug Android binary: `assembleDebug` (34/34 tasks executed, 100% passed in 43s).
- Deliverable copied and verified: [`builds/StrengthLogz-Native.apk`](file:///c:/MyProjects/StrengthLogz/builds/StrengthLogz-Native.apk) (15.7 MB).



  - Compiled and verified Windows desktop installers: `builds/StrengthLogz Setup 1.0.0.exe` and `builds/StrengthLogz 1.0.0.exe`.

---

## [Phase 7: Log Workout Screen Redesign, Symmetrical Inputs, Plate Calculator & Live PR Radar] — 20-OCT-2026
*Commit Scope: Complete redesign of mobile Log Workout UX, symmetrical centered inputs, start onboarding guidance, in-logger Barbell and Adjustable Dumbbell plate calculator, and mid-workout PR celebrations.*

### Features & Infrastructure
- **Complete Redesign of Log Workout Screen (`LogWorkoutScreen.kt`)**:
  - **Start Guidance & Header**:
    - Replaced raw text typing for dates with an interactive Date Pill (`Today • DD-MON-YYYY`) and modal date switcher.
    - Added 1-tap **Quick Split Presets** (`Push`, `Pull`, `Legs`, `Full Body`, `Upper`, `Lower`) directly under the title to auto-fill the session name.
    - Designed a welcoming 3-step Start Guidance hero card when no exercises are logged, featuring dual action buttons (`+ Add Exercise` and `⚡ Load AI Routine`) and horizontal quick-add chips for the lifter's top 6 most frequent exercises.
  - **Symmetrical Inputs & Centered Typography**:
    - Standardized Weight and Reps inputs to **exact identical proportions** (`Modifier.weight(1f)`), eliminating uneven sizing.
    - Horizontally centered numeric text (`TextAlign.Center`) inside compact 40dp containers (`BgDark` background, subtle focus borders).
    - Added dedicated `PREV` ghost column showing last workout's performance for each set (e.g. `80×8`), with 1-tap auto-fill.
  - **Ergonomic Buttons & Set Completion**:
    - Removed confusing "Save Exercise" accordion locking in favor of persistent, live editable cards.
    - Added satisfying circular completion toggle (`✓`) turning vibrant green (`#22C55E`) upon set completion.
    - Symmetrical tactile increment chips (`+1.25kg`, `+2.5kg`, `+5kg`, `+1 rep`) with comfortable touch targets.
    - High-utility `+ Add Set` pill button duplicating previous set values for rapid tracking.
    - Upgraded sticky bottom bar with live workout stats: `Finish Workout (X sets • Y kg)`.
- **Novel Feature A: Barbell & Adjustable Dumbbell Plate Calculator (`PlateCalculatorDialog.kt`)**:
  - Built an interactive modal launched directly from any exercise card via a barbell icon.
  - **Olympic Barbell Mode**: Supports 20kg standard bar (and 15kg short bar) with 20, 15, 10, 5, 2.5, and 1.25kg plates.
  - **Adjustable Dumbbell Mode**: Specifically tailored for 5kg dumbbell bars with 2.5kg and 1.25kg plates per side.
  - Visual colored plate stack breakdown, per-side math, nearest achievable weight alerts, and 1-tap "Apply to Set" button.
- **Novel Feature C: Mid-Workout Live PR Radar & Celebration**:
  - Connected `getAllSetsForExercise` to evaluate historical PRs live during set input.
  - Displays glowing Gold celebration badges (`🏆 NEW ALL-TIME PR! +X kg Est. 1RM` or `🏆 NEW TOP WEIGHT!`) mid-workout as weight and reps are entered.
- **UI Polish & Layout Adjustments**:
  - Prevented "Ready" / "In Progress" status badge from wrapping or splitting across rows by assigning `weight(1f)` with right padding to the title column and enforcing `maxLines = 1, softWrap = false`.
  - Upgraded "+ Add Exercise" and "⚡ Load Suggested AI Routine" buttons to full-width stacked layouts with generous touch targets (48dp & 46dp) and explicit content paddings, completely eliminating clipped text.
  - Expanded Workout Title input to full card width (`Modifier.fillMaxWidth().height(46.dp)`), allowing ample room for long workout names and placeholder ghost text without truncation.
  - Resolved bottom bar text clipping on the "Add Exercise" button by increasing button height to 50dp, setting compact `contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)`, enforcing `maxLines = 1, softWrap = false`, and balancing both bottom buttons to equal `weight(1f)`.
  - Refined the "Finish Workout" button with a two-tier centered layout displaying `✓ Finish Workout` on top and live set count/tonnage below.
- **Pre-requisite Compilation Fixes**:
  - Resolved unresolved references to `SetUtils.formatWeightValue` in `ProgressScreen.kt` and `SuggestWorkoutScreen.kt`.
  - Corrected `baseWeight` declaration order in `SuggestWorkoutScreen.kt`.
- **Verification & Builds**:
  - Executed Android unit tests (`testDebugUnitTest` - 23 tasks passed).
  - Compiled and verified Android debug APK: `builds/StrengthLogz-Native.apk`.

---

## [Phase 8: Exercises Section & Google Sheets Backend Backup] — 20-OCT-2026
*Commit Scope: Dedicated Exercises management screen, multi-line form/setting descriptions, 4-column Google Sheets schema synchronization, full exercise renaming with historical set cascade.*

### Features & Architecture
- **Dedicated Exercises Screen (`ExercisesScreen.kt`)**:
  - Created a top-level mobile destination with real-time exercise search, category filter chips (`All`, `Chest`, `Back`, `Legs`, `Shoulders`, `Arms`, `Core`, `Custom`), and an exercise count badge.
  - Interactive exercise cards displaying lifetime tracking stats (total sets logged, total volume tonnage), category tag, and bodyweight indicators.
  - Multi-line expandable **Description & Form Cues** drawer for each movement (e.g., machine pin settings, bench angle, grip cues, execution tempo).
  - "+ New Exercise" dialog with input validation, duplicate detection, category selector, bodyweight toggle, and multi-line notes.
  - Full Edit dialog allowing lifters to rename exercises and update category, bodyweight status, and descriptions.
- **Relational Integrity & Cascade Renaming**:
  - `WorkoutRepository.updateExercise(oldName, updated: Exercise)` preserves data continuity by updating catalog entries and cascading renames across every historical `WorkoutSet` in `appData.sets`.
  - Remote batch cascade via `GoogleSheetsClient.renameExerciseInSets(rawSpreadsheetId, oldName, newName)`: searches `Sets!D:D` and updates every matching set cell in Google Sheets.
- **Google Sheets Backend Backup & 4-Column Schema Parity**:
  - Extended Google Sheets `Exercises` tab schema to: `Name | Category | Bodyweight | Description`.
  - `GoogleSheetsClient.kt`:
    - `loadAll()` updated to read `Exercises!A2:D`, parsing description with backward-compatible fallback (`r.getOrElse(3) { "" }`).
    - `addExercises()` appends the description field.
    - `overwriteExercises()` clears and updates the entire catalog while ensuring A1:D1 header integrity.
    - `backupExercisesToGoogleSheets()` orchestrates local catalog updates, remote Sheets catalog overwrite, and remote set renames.
    - Bidirectional sync merges local and cloud exercise descriptions without overwriting user notes.
  - Desktop `src/stores/sheets-store.js`:
    - Updated `TABS.Exercises` to `Exercises!A2:D`.
    - Updated `loadAll()` and `addExercises()` to maintain schema parity across desktop and mobile clients.
- **Navigation & Integration**:
  - Added 6th primary tab `Exercises` with `Icons.Default.FitnessCenter` to the bottom navigation bar in `MainActivity.kt`.
  - Added a `Manage →` quick-action link in the `LogWorkoutScreen` exercise picker dialog title, allowing lifters to jump directly to exercise management mid-logging.
- **Verification & Builds**:
  - Android unit tests passed (`testDebugUnitTest` - 23 tasks passed).
  - Compiled and verified native Android APK deliverable: `builds/StrengthLogz-Native.apk` (15.7 MB).

---

## [Bugfix: Null-Safety on Legacy Exercise Deserialization & Exercises Screen Crash] — 20-OCT-2026
*Commit Scope: Resolved crash on opening Exercises screen caused by Gson deserializing legacy JSON records missing the `description` field into Kotlin null references.*

### Root Cause Analysis
- In `Models.kt`, `description` was defined as `val description: String = ""`. When Gson deserializes JSON from existing device storage (`strengthlogz_data.json`) or pre-Phase-8 catalog assets (`initial_data.json`), Gson uses `UnsafeAllocator` and bypasses Kotlin's constructor defaults, setting the missing `description` field directly to Java `null`.
- In `ExercisesScreen.kt`, the initial search filtering (`ex.description.contains(...)`) and card rendering (`exercise.description.isNotBlank()`) attempted to call String extension functions on the null reference, resulting in `java.lang.NullPointerException` inside Kotlin's `Intrinsics.checkNotNullParameter`.

### Key Fixes
- **Model Null-Safety (`Models.kt`)**: Updated `Exercise` to accept `val description: String? = ""` and exposed `val safeDescription: String get() = description ?: ""` to guarantee non-null string access everywhere.
- **Data Normalization (`WorkoutRepository.kt`)**: Enhanced `normalizeDates()` to safely sanitize all deserialized exercises, mapping null or blank descriptions to `""` and missing categories to `"Other"` upon app launch and Sheets sync.
- **Screen Resiliency (`ExercisesScreen.kt`)**:
  - Used `ex.safeDescription` in filter matching and UI rendering.
  - Safeguarded `itemsIndexed` keys with `${ex.name}_$index` to prevent crashes in the event of duplicate exercise names.
- **Seed Asset Synchronization**: Updated `initial_data.json` and `data/exercises.json` so all 82 catalog movements explicitly carry `"description": ""`.
- **Automated Regression Testing (`GoogleSheetsSyncTest.kt`)**: Added `verifyLegacyExerciseJsonWithoutDescriptionDoesNotCrash` unit test verifying that legacy JSON without descriptions deserializes safely without NPEs.
- **Verification & Build**: All unit tests passed (`testDebugUnitTest` - 23 tasks passed); compiled fresh APK to `builds/StrengthLogz-Native.apk` (15.7 MB).

---

## [Phase 9: Workout Draft Local Cache, History Weekday Presentation & 4-5 Exercise Suggest Limits] — 23-OCT-2026
*Commit Scope: Active workout draft auto-persistence across screen switches and app reloads, day-of-week presentation in workout history, and streamlined 4-5 exercise suggestions.*

### Features & Architecture
- **In-App Active Workout Draft Local Cache**:
  - Created relational draft models (`ActiveWorkoutDraft`, `DraftExerciseLog`, `DraftSetInput`) in [`Models.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/model/Models.kt).
  - Implemented thread-safe `SharedPreferences` draft storage in [`WorkoutRepository.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/data/WorkoutRepository.kt) (`getActiveDraft()`, `saveActiveDraft()`, `clearActiveDraft()`, `hasActiveDraft()`).
  - Integrated reactive `snapshotFlow` in [`LogWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/LogWorkoutScreen.kt) to continuously auto-persist session state on every set entry, weight/rep modification, exercise addition/deletion, or completion checkmark (`✓`).
  - Lifters can safely navigate to the Exercises screen (or History/Progress/Settings) mid-workout and return to find their entire session restored intact.
  - Added an in-logger "Active Session Restored" banner and a "Discard Active Workout" action with confirmation dialog.
  - Draft is cleared automatically upon tapping "Finish Workout" or confirming discard.
- **Day-of-Week History Presentation**:
  - Implemented `DateUtils.getDayOfWeek(raw)` and `DateUtils.formatDisplayWithDay(raw)` in [`DateUtils.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/util/DateUtils.kt).
  - Updated [`HistoryScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/HistoryScreen.kt) cards to format session dates with the day of the week (e.g. `Monday • 20-OCT-2026`).
  - Updated [`WorkoutDetailScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/WorkoutDetailScreen.kt) top bar and session header to display the day of the week.
- **4–5 Exercise Suggestion Limits & Length Selector**:
  - Streamlined `PUSH_SLOTS`, `PULL_SLOTS`, and `FULL_BODY_SLOTS` from 6 down to 5 high-yield movements in [`SuggestWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SuggestWorkoutScreen.kt) and desktop [`renderer/app.js`](file:///c:/MyProjects/StrengthLogz/renderer/app.js).
  - Added interactive `[ 4 Exercises | 5 Exercises ]` segmented selector chips, allowing lifters to choose between a rapid 4-exercise routine or a 5-exercise session.
- **Verification & Builds**:
  - Added unit test suite [`WorkoutUpgradeTest.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/test/java/com/strengthlogz/mobile/WorkoutUpgradeTest.kt) covering `DateUtils` day-of-week transformations and `ActiveWorkoutDraft` JSON round-trip serialization.
  - Executed Gradle unit tests (`testDebugUnitTest` - 23 tasks executed, 100% passed in 1m 6s).
  - Compiled and verified production debug Android binary: [`builds/StrengthLogz-Native.apk`](file:///c:/MyProjects/StrengthLogz/builds/StrengthLogz-Native.apk) (15.7 MB).

---

## [Phase 10: Complete Visual & UI Overhaul of the Android Application] — 23-OCT-2026 (Evening)
*Commit Scope: Full visual styling overhaul, slick animations, high-readability interactive charts, responsive gym ergonomics, and plate calculator visualization across the entire Android app.*

### Visual Design System & Component Architecture
- **Obsidian Dark Theme & Token Refinement**:
  - Engineered a cohesive design system in [`Color.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/theme/Color.kt) featuring deep obsidian surfaces (`BgDark` `#0C0E12`, `SurfaceDark` `#13161D`, `SurfaceCard` `#181C26`, `SurfaceCardElevated` `#1F2432`, `BorderDark` `#282E3D`), brand neon orange accents (`AccentOrange` `#FF7A45`, `AccentPeach` `#FF9A6B`), PR trophy gold (`GoldPR` `#FFD166`), emerald green (`IncreaseGreen` `#34D399`), and rose red (`DecreaseRed` `#F87171`).
  - Added visual gradient tokens: `OrangeGradient`, `GoldGradient`, `PrCardGradient`, `HeroHeaderGradient`, and `CardSurfaceGradient`.
- **Reusable Core Components Suite**:
  - Created [`SharedComponents.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/theme/SharedComponents.kt):
    - `StrengthCard`: Elevated card container with subtle borders, gentle gradient backgrounds, and spring tactile press states.
    - `CategoryPill`: High-contrast categorical badges for muscle groups (Chest, Back, Legs, Shoulders, Arms, Core) with custom background/text pairings.
    - `PrTrophyBadge`: Golden trophy PR indicator with custom glowing pill.
    - `SetCompletionCheckButton`: Smooth bouncy spring completion toggle (`DampingRatioMediumBouncy`) with checkmark icon transition.
    - `ModernSearchBar`: Outlined search field with focused glowing orange border, magnifying glass, and instant clear button.
    - `ModernScreenHeader`: Screen title typography, uppercase tracking subtitles, and optional action badges.

### Screen-by-Screen Overhaul & Ergonomics
- **Main Shell & Navigation ([`MainActivity.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/MainActivity.kt))**:
  - Integrated `AnimatedContent` cross-fade and scale transitions between tabs and workout detail views.
  - Replaced the clunky default bottom bar with an ultra-responsive 62dp custom bottom navigation bar with active micro-capsule indicators, animated icon bounce, and 1-line labels to guarantee zero text truncation on any phone width.
  - Added an active draft badge dot on the Log tab when a workout is in progress while browsing other screens.
- **Log Workout Experience ([`LogWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/LogWorkoutScreen.kt))**:
  - Symmetrical 38dp numerical inputs with centered bold text, keyboard types, and focused border states.
  - Live Epley E1RM preview calculated on every keystroke, with an animated Gold PR banner (`🏆 NEW ALL-TIME PR!`) when beating all-time peak records.
  - Invalidation of failed attempts (`reps <= 0`): Instant red badge (`✕ FAILED ATTEMPT (0 reps) — not counted in PRs or volume`) and zero E1RM.
  - Quick-copy "PREV" ghost pill that auto-fills past set data in 1 tap.
  - Tactile increment chips (`+1.25k`, `+2.5k`, `+5k`, `+1r`) directly under each exercise.
  - Quick split preset pills (`Push`, `Pull`, `Legs`, `Full Body`, `Upper`, `Lower`) for 1-tap title fill.
  - Floating sticky action bar showing live total tonnage and completed sets counter (`14 sets • 8,450 kg`), "+ Add Exercise", and "Finish Workout".
- **Interactive Progress Charts & Metrics ([`ProgressScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/ProgressScreen.kt))**:
  - 2x2 Hero Metric Dashboard: All-Time E1RM PR (gold trophy card), Heaviest Set PR (flame card), Latest Session with delta (+/- kg vs previous), and All-Time Change since day 1 (+kg and % gain).
  - Modern Canvas-drawn progress graph:
    - Glowing multi-segment progression line (green for increases, red for dips, orange for maintenance).
    - Vertical gradient area fill under the curve.
    - Horizontal dashed grid lines with accurate kilogram labels and peak PR dashed baseline.
    - Responsive magnetic touch scrubbing across data points with vertical crosshair guideline.
    - Inspected Point Callout HUD displaying exact date, workout title, top set, E1RM source, volume, PR badge, and delta vs previous session.
  - Full-screen chart inspector dialog with range filters and point-by-point navigation.
- **Workout History & Detail ([`HistoryScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/HistoryScreen.kt) & [`WorkoutDetailScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/WorkoutDetailScreen.kt))**:
  - History cards with day-of-week badges, session tonnage pills, working sets count, and exercise chips.
  - Workout Detail: 4-stat overview grid (Volume + delta vs previous workout, Sets/Reps, Movements count, PRs Achieved), exercise breakdown tables, and 1-tap deep links to Progress graph.
  - Animated spinning sync indicator.
- **Progressive Overload Workout Suggester ([`SuggestWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SuggestWorkoutScreen.kt))**:
  - Split cards (Push / Pull / Full Body) with volume deficit status indicators and "Recommended Today" badge.
  - 7-day muscle group volume progress bars (Chest, Back, Legs, Shoulders, Arms, Core) with color-coded deficit status.
  - Exercise prescription cards with reason tags, overload prescriptions (+2.5kg / +1 rep), kinetic benefits, and "Swap" buttons.
  - Large sticky "Start Session" button that loads the prescribed routine directly into the logger.
- **Visual Barbell & Dumbbell Plate Calculator ([`PlateCalculatorDialog.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/PlateCalculatorDialog.kt))**:
  - Olympic color-coded plates: 20kg Blue, 15kg Yellow, 10kg Green, 5kg White, 2.5kg Red, 1.25kg Silver.
  - Dynamic visual sleeve rendering the actual plate stack loaded on the collar with proportional plate heights!
  - Steppers and quick increment chips, plate count per side, and "Apply to Set" button.
- **Exercises Catalog & Cloud Settings ([`ExercisesScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/ExercisesScreen.kt) & [`SettingsScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SettingsScreen.kt))**:
  - Exercises: Category filter pills, search bar, exercise cards with PR highlights, total sessions, and edit/create modal with instant Google Sheets cloud backup.
  - Settings: Cloud sync status badge, spreadsheet ID input, animated sync button, and 3-card local database summary.

### Verification & Deliverables
- Executed full unit test suite: `testDebugUnitTest` (23/23 tasks executed, 100% passed in 44s).
- Compiled and verified production debug Android binary: `assembleDebug` (34/34 tasks executed, 100% passed in 43s).
- Deliverable copied and verified: [`builds/StrengthLogz-Native.apk`](file:///c:/MyProjects/StrengthLogz/builds/StrengthLogz-Native.apk) (15.7 MB).





---

## [Phase 9: Body Weight, Relative Strength, Wheel Picker & Full-Screen Ready State] — 03-NOV-2026
*Commit Scope: Android client only.*

### Features & Fixes
- **Increment chips apply to every open set**: `+1.25k / +2.5k / +5k / +1r` now update all not-yet-completed sets of the exercise (previously only the last set). Helpers: `SetUtils.addToWeight` / `addToReps`.
- **Body weight tracking** (`BodyWeightEntry`, `BodyWeightUtils`, `bodyWeights` in local data):
  - 125 backdated readings (07-OCT-2025 → 04-MAY-2026, supplied in lb, converted to kg) seeded once from `assets/bodyweight_seed_lbs.csv`.
  - Lookup is carry-forward by date (latest earlier reading; earliest reading before the first entry).
  - **Health Connect** integration (`HealthConnectWeightSource`): read-only `READ_WEIGHT` (+ history permission), imports readings from 01-MAY-2026 onward; auto-import on launch once permission is granted; manual entry (kg/lb) from Settings → Body Weight. Manual entries are never overwritten by automatic sources.
- **Total-load E1RM & relative strength**: for catalog-flagged bodyweight exercises the logged weight is the *added* load; E1RM = Epley(body weight on that date + added load). Pure-BW sets now get an E1RM. Progress shows `Relative strength: N× BW`; the logger, workout detail and suggest engine use the same rule. Failed (0-rep) sets remain excluded.
- **Home screen**: when no exercises are logged, header, session card and "Ready to Lift" card fill the whole screen (the Ready card takes the remaining height; recent exercises are a 2-column grid).
- **Weight/Reps entry**: tapping a value opens a wheel-picker dialog (weight in 1.25 kg steps, reps 0-60, OK / Cancel). A small keyboard button switches to typed entry; the weight of a brand-new exercise (no history) opens in keyboard mode by default.

### Notes
- Body-weight data is stored locally (not yet synced to a Google Sheets tab); sync preserves it.

---

## [Phase 10: Application-Wide UI/UX Decluttering & Streamlining] — 03-NOV-2026
*Commit Scope: Android client (`mobile-android/`) — progressive disclosure, multi-step navigation, and visual hierarchy overhaul with 100% feature retention.*

### Architectural & UX Changes
1. **Navigation Streamlining ([`MainActivity.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/MainActivity.kt))**:
   - Reduced the bottom navigation bar from 6 cramped tabs to **4 Primary Bottom Tabs** (`Log`, `Suggest`, `History`, `Progress`) with larger touch targets, `11.sp` labels, and generous spacing.
   - Added a sleek top utility bar giving 1-tap access to **Catalog** (`ExercisesScreen`) and **Settings & Body Weight** (`SettingsScreen`), with hardware `BackHandler` support to return to the active primary tab.
2. **Two-Step Progress Architecture ([`ProgressScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/ProgressScreen.kt))**:
   - **Step 1 (`ProgressDirectoryView`)**: Clean searchable and category-filterable exercise directory displaying each movement's category, logged session count, and a prominent Best E1RM (or Relative Strength `× BW` for bodyweight exercises) badge.
   - **Step 2 (`ExerciseProgressDetailView`)**: Dedicated drill-down view with a clean back bar and **3 focused sub-tabs**:
     - **Overview**: Compact 3-stat KPI strip (`Best E1RM` / `Relative Strength`, `Heaviest Set`, `Total Volume`), metric switcher (`Est. 1RM`, `Max Weight`, `Session Vol`), interactive scrubbing chart (`240.dp` height with full-screen expand button), and a compact single-line inspected session banner.
     - **History (`N`)**: Chronological list of every session for that lift, including date, session volume, best E1RM, and set-by-set pills.
     - **PRs (`M`)**: Dedicated trophy board filtering strictly to Personal Record sessions.
3. **Workout Logger Decluttering ([`LogWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/LogWorkoutScreen.kt))**:
   - **Collapsible Session Header**: Once exercises are added, the session metadata card collapses into a single summary bar (`Session Title • DD-MON-YYYY • Edit`) with session notes tucked behind a `+ Add note` toggle until needed.
   - **Unified Exercise Overflow Menu**: Replaced competing header buttons on each exercise card with a single `⋯` (`MoreVert`) menu containing `Plate Calculator`, `Copy Last Session`, and `Remove Exercise`.
   - **Cleaner Set Rows**: Replaced repetitive per-row E1RM text with a single card-level `"Best Est. 1RM"` header summary while keeping inline PR and Failed (`0 reps`) callout pills.
4. **Suggest Workout Progressive Disclosure ([`SuggestWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SuggestWorkoutScreen.kt))**:
   - Moved the 6-card 7-Day Muscle Volume grid out of the main scroll flow into an on-demand `"7-Day Volume"` modal dialog triggered from the header.
   - Merged the Split selector (`Push` / `Pull` / `Full Body`) and Session Length toggle (`4 Lifts` / `5 Lifts`) into a single compact control bar.
   - Made prescribed exercise cards **collapsible on tap**: collapsed cards show slot number, exercise name, reason tag, and target prescription (`3 × 8-10 @ 80kg`); tapping expands the full kinetic benefit description, previous top set, and `"Swap Movement"` button.
   - Pinned the `"Start [Split] Session"` CTA button into `Scaffold.bottomBar`.
5. **History, Workout Detail, Catalog & Settings Refinements**:
   - [`HistoryScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/HistoryScreen.kt): Streamlined session cards with bold title, date/day-of-week, right-aligned volume & set count, and up to 4 clean exercise preview lines; removed destructive trash icons from outer list cards (deletion lives safely inside `WorkoutDetailScreen` with confirmation).
   - [`WorkoutDetailScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/WorkoutDetailScreen.kt): Replaced the 2×2 stat grid with a 3-column summary strip (`VOLUME`, `SETS & REPS`, `RECORDS`) and made per-exercise breakdowns expandable on tap.
   - [`ExercisesScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/ExercisesScreen.kt) & [`SettingsScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SettingsScreen.kt): Replaced multi-line description boxes in the catalog list with clean 2-line tappable cards that open the edit/inspect modal on tap, and added an Exercise Catalog quick-navigation card inside Settings.
   - [`SharedComponents.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/theme/SharedComponents.kt): Softened card border contrast (`BorderDark.copy(alpha = 0.65f)`) and standardized `16.dp` internal card padding.

---

## [Phase 11: Application-Wide Performance & Frame-Rate Optimization] — 04-NOV-2026
*Commit Scope: Android client (`mobile-android/`) — $O(1)$ repository indexing, cached date/bodyweight lookup engine, debounced background persistence, and Compose stability.*

### Root Causes Addressed & Optimizations Implemented
1. **$O(1)$ Memoized Date & Binary-Search Body Weight Engine ([`DateUtils.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/util/DateUtils.kt) & [`BodyWeightUtils.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/util/BodyWeightUtils.kt))**:
   - Eliminated repeated $O(N \log N)$ sorting and linear Regex scanning of 125+ body-weight records inside `BodyWeightUtils.weightOn(date)` by pre-sorting entries on `publish()` and using $O(\log N)$ binary search backed by a `ConcurrentHashMap` ISO lookup cache.
   - Added fast character-inspection paths and `ConcurrentHashMap` caches in `DateUtils` for `toDdMonYyyy`, `toSortableIso`, `getDayOfWeek`, `formatDisplayWithDay`, and `toEpochMs`, eliminating thousands of Regex evaluations and `SimpleDateFormat` allocations during scroll and chart rendering.
2. **In-Memory Relational Indexing & Background Disk I/O ([`WorkoutRepository.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/data/WorkoutRepository.kt))**:
   - Built `RepositoryIndexes` maintaining pre-sorted `workoutsSorted`, `setsByWorkoutId`, `setsByExerciseLower`, `previousSetsByExerciseLower`, `distinctExercisesByFrequency`, and `recentExercises`.
   - Replaced $O(N)$ linear scans over 1,390+ sets with $O(1)$ map lookups across all screens.
   - Cached `ActiveWorkoutDraft` in memory (`cachedDraft`) and offloaded JSON serialization + disk/SharedPreferences writes (`saveLocalDataAsync`, `saveActiveDraft`) to a dedicated background thread executor so main-thread UI frames are never blocked by I/O.
3. **Screen-Level Recomposition & Scroll-Frame Optimizations**:
   - [`ProgressScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/ProgressScreen.kt): Pre-computed `ExerciseDirectorySummary` metrics once per dataset version at the directory level instead of running `buildSessionPoints()` inside `LazyColumn` items during scroll; made `SessionProgressPoint` `@Immutable`.
   - [`HistoryScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/HistoryScreen.kt) & [`SettingsScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SettingsScreen.kt): Stopped unconditional 60 FPS `rememberInfiniteTransition` spin animations when `isSyncing == false`; pre-computed and memoized `HistoryCardModel` and `filteredModels`.
   - [`LogWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/LogWorkoutScreen.kt) & [`WheelPickerDialog.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/WheelPickerDialog.kt): Debounced `snapshotFlow` draft auto-persistence (`debounce(300L)`), memoized `recentLifts` in the Ready state, hoisted per-card bodyweight lookup out of set loops, memoized exercise picker filtering, and added stable item keys to `WheelPicker`.
   - [`SuggestWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SuggestWorkoutScreen.kt): Pre-grouped sets by exercise and cached `latestGlobalIso` before ranking slot candidates.
   - [`Models.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/model/Models.kt) & [`build.gradle.kts`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/build.gradle.kts): Annotated domain/UI models with `@Immutable` / `@Stable` and enabled Compose Compiler Strong Skipping mode (`experimentalStrongSkipping=true`).

---

## [Phase 12: Exercise Goals, Periodized Programming Engine, Live Session Coach & Reports] — 06-NOV-2026
*Commit Scope: Dedicated Goals page (Mobile & Desktop), `GoalEngine` programming & periodization analytics, live `'Goal Progress'` coaching in the workout logger, and single-exercise + master all-goals reports.*

### Features & Architecture
1. **Dedicated Goals Page (`GoalsScreen.kt` & `#view-goals`)**:
   - Accessible directly via the **`Goals`** button on the `Progress` screen (`ProgressDirectoryView` & `ExerciseProgressDetailView` on Android; `#progress-open-goals` on Desktop).
   - Users can select any exercise from the catalog and configure either a **1RM Goal** (`"E1RM"`) or a **Sets × Reps Goal** (`"SETS_REPS"`, e.g. `3 sets × 8 reps @ 100kg`).
   - Includes 1-tap smart target presets (`+2.5kg`, `+5%`, `+10%`, `1.25× BW`, `1.5× BW`, `3×8`, `5×5`) and 1-tap **`Load in Logger`** to pre-populate the workout logger with prescribed goal sets.
2. **In-App Calculations & Programming Recommendations ([`GoalEngine.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/util/GoalEngine.kt))**:
   - Evaluates the user's current strength (best & recent Epley E1RM, top working sets), body weight (`BodyWeightUtils.weightOn`, relative strength $\times \text{BW}$, and tier classification from `Starter` to `Elite`), and historical sessions (weekly frequency, avg working sets, E1RM velocity per session, plateau detection, and dominant rep zone).
   - Generates tailored periodization block recommendations (**Hypertrophy & Work Capacity**, **Linear Strength Accumulation**, **Strength-Power Intensification**, **Peaking & Realization**, **Plateau-Breaker Wave Block**, or **Specificity Rep-Builder**), next-session working sets & warmup ramp-up sets, a 4-phase progression roadmap, a `1RM–12RM` rep-equivalence table, and weak-point accessory recommendations with live transfer status.
3. **Workout Logger Integration (`'Goal Progress'` Button & Post-Session Updates)**:
   - When logging an exercise with an active goal in [`LogWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/LogWorkoutScreen.kt) (and Desktop `#view-log`), a prominent **`Goal Progress`** button appears on the exercise card along with a live in-card target bar.
   - Clicking **`Goal Progress`** opens `GoalProgressSessionDialog`, displaying today's prescribed sets/reps/weights, live progress against today's session target as sets are entered, dynamic coaching feedback for the next set, warmup protocol, and 1-tap **`Auto-Fill Sets`**.
   - Upon saving a workout (`WorkoutRepository.saveWorkout`), any active goal matching a logged exercise is automatically updated with a `GoalSessionReport` snapshot and immediately surfaces the updated Exercise Goal Report.
4. **Exercise & Master All-Goals Reports**:
   - **Per-Exercise Goal Report (`ExerciseGoalReportDialog`)**: Executive summary, strength & bodyweight metrics, E1RM trajectory sparkline, full programming & 4-phase roadmap, rep-equivalence table, weak-point accessories, and chronological logged goal session history.
   - **Master All-Goals Report (`AllGoalsReportDialog`)**: Portfolio-wide KPIs, status breakdown (`Achieved`, `Ahead`, `On Track`, `Plateau`, `Building`), weekly programming blueprint across all goal lifts, and per-goal status cards.

---

## [Phase 13: Multi-User Options Menu, Built-In Exercise Catalog, Weekly Program Builder & GitHub Actions CI/CD] — 10-NOV-2026
*Commit Scope: Removal of all hardcoded personal spreadsheet/service-account credentials, user-configurable Google Sheets options menu with built-in setup guide, upgrade-safe built-in exercise catalog (`exercises.json`), new `ProgramScreen` (1–7 day split builder with 1-tap Logger loading), and GitHub Actions Android APK build pipeline.*

### Features & Architecture
1. **Zero Hardcoded Credentials & Multi-User Options Menu ([`GoogleSheetsClient.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/data/GoogleSheetsClient.kt), [`WorkoutRepository.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/data/WorkoutRepository.kt), [`SettingsScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/SettingsScreen.kt))**:
   - Removed the hardcoded `DEFAULT_SPREADSHEET_ID` and unbundled private `service_account.json`, `initial_data.json`, and `bodyweight_seed_lbs.csv` from APK assets.
   - Upgraded `SettingsScreen.kt` into a complete **Options & Settings** hub where any user can:
     - Enter their own Google Spreadsheet URL or ID.
     - Import their own Google Cloud Service Account `.json` key file via the Android system file picker (`ActivityResultContracts.OpenDocument`) or paste it from the clipboard.
     - View and 1-tap copy the extracted `client_email` (`GoogleSheetsClient.extractServiceAccountEmail`) to share their Google Sheet with Editor access.
     - Follow a built-in expandable **4-Step Google Sheets Setup Guide** directly inside the Options menu, or run 100% offline with local storage.
2. **Built-In Exercise Catalog & Seamless App Upgrade Safety ([`WorkoutRepository.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/data/WorkoutRepository.kt))**:
   - Bundled the 82-movement standardized catalog in `mobile-android/app/src/main/assets/exercises.json`.
   - New installs automatically populate all 82 exercises so new lifters never start with an empty catalog.
   - Existing installs preserve `filesDir/strengthlogz_data.json` (workouts, sets, body weights, goals, custom exercises) untouched across APK updates, while `ensureDefaultExercisesPresent()` non-destructively merges any missing built-in exercises.
3. **Weekly Program Builder & 1-Tap Logger Loader ([`ProgramScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/ProgramScreen.kt) & [`Models.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/model/Models.kt))**:
   - Added relational program models (`WorkoutProgram`, `ProgramDay`, `ProgramExerciseItem`) persisted in `AppData.program`.
   - Created a dedicated **`Program`** tab in the bottom navigation bar ([`MainActivity.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/MainActivity.kt)) and a quick-action **`Load Program`** button in [`LogWorkoutScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/LogWorkoutScreen.kt).
   - Lifters can select **1 to 7 training days per week**, customize day names and muscle focus, add/remove/reorder exercises from the catalog, and adjust target sets × reps with compact steppers.
   - Includes intelligent split templates for 1–7 days/week, automatic `NEXT` day rotation tracking (`lastLoadedDayIndex`), and contextual coaching messages on session volume and recovery.
   - Tapping **`Load Day N to Logger`** pre-populates `LogWorkoutScreen` with all exercises for that day, automatically carrying forward the lifter's previous working weights alongside their programmed sets and reps.
4. **GitHub Actions CI/CD Pipeline ([`.github/workflows/build-apk.yml`](file:///c:/MyProjects/StrengthLogz/.github/workflows/build-apk.yml))**:
   - Configured automated Ubuntu CI workflow with JDK 17 (Temurin) and Gradle 8.7 that runs `testDebugUnitTest`, compiles `assembleDebug`, and publishes `StrengthLogz-Native-APK` as a downloadable build artifact on every push and pull request.

---

## [Phase 14: Navigation Spacing, Goals Decluttering & Multi-Program Custom Builder] — 10-NOV-2026 (Evening)
*Commit Scope: Android client (`mobile-android/`) — Bottom navigation pill clearance & vertical centering, decluttered Goals screen with focused goal cards, and full custom multi-program system with zero forced defaults.*

### Features & Refactors
1. **Navigation Bar Gap & Vertical Centering ([`MainActivity.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/MainActivity.kt))**:
   - Resolved the top edge collision where the highlighted oval selection capsule pushed directly against the top border of the bottom navigation bar.
   - Expanded bar height from `64.dp` to `68.dp` and added comfortable vertical padding (`padding(horizontal = 6.dp, vertical = 4.dp)`).
   - Constrained the selection capsule to a fixed, centered `28.dp × 48.dp` container with `RoundedCornerShape(14.dp)`, applying icon scaling purely to the vector glyph rather than scaling the outer container bounds upward.
   - Guaranteed an ~11–12dp top clearance margin from the top border for all selected and unselected navigation items.
2. **Goals Screen Decluttering ([`GoalsScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/GoalsScreen.kt))**:
   - Stripped away the bulky 3-card executive KPI strip (`PORTFOLIO AVG`, `ACHIEVED`, `BODY WEIGHT`) and long subtitle banners that previously pushed actual goals below the fold.
   - Removed the automatic expansion of the first goal card on load, keeping all goal cards neatly collapsed by default.
   - Redesigned `GoalProgrammingCard` into a clean, modern card:
     - **Card Header**: Bold exercise name + category pill on the left, target headline (`120 kg 1RM` or `3 × 8 @ 100 kg`) on the right.
     - **Progress Row**: Clean status badge (`On Track`, `Achieved 🏆`, `Paused`) + current best and percentage (`Best: 105 kg • 88%`).
     - **Visual Bar**: Thin 5dp rounded progress bar with smooth gold/orange gradients.
     - **Quick Action Row**: Single-line next session target (`Next: 3 × 5 @ 102.5 kg`) + 1-tap **`Train`** button (loads session into Logger) + subtle expand chevron.
     - **Expanded Content**: Kept strictly actionable—next session prescribed working sets, periodization milestones, and clean action buttons (`Report`, `Edit`, `Pause/Resume`, `Delete`), eliminating long walls of redundant text.
3. **Multi-Program Custom Builder ([`ProgramScreen.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/ui/ProgramScreen.kt), [`WorkoutRepository.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/data/WorkoutRepository.kt), [`Models.kt`](file:///c:/MyProjects/StrengthLogz/mobile-android/app/src/main/java/com/strengthlogz/mobile/model/Models.kt))**:
   - Removed all hardcoded split templates, forced days per week (1–7), preset exercise lists, and template reset buttons.
   - Added support for storing and switching between **multiple custom programs** (`AppData.programs`, `activeProgramId`).
   - Built a **Program Switcher & Manager** modal allowing lifters to create new programs, switch between active programs, rename programs, duplicate programs, and delete programs.
   - Empty state cleanly guides lifters to create their first program from scratch.
   - Lifters can add any number of custom workout days (`+ Add Day`), customize day names (e.g. `Day 1: Push`, `Upper Body A`, `Quad Focus`) and muscle focus, and add exercises directly from the catalog or create custom exercises on the fly.
   - Exercise management per day includes target sets and reps steppers, last session performance indicators, reordering (Move Up / Down), and deletion.
   - 1-tap **`Start Workout`** / **`Load in Logger`** loads the selected day's exercises and target sets into `LogWorkoutScreen` with previous weights automatically carried forward.

---

## [Phase 15: Full Desktop Parity Rebuild & Windows Distribution Packaging] — 10-NOV-2026 (Night)
*Commit Scope: Electron desktop app rebuild (`main.js`, `preload.js`, `src/stores/local-store.js`, `src/data-service.js`, `renderer/`) to achieve 100% feature parity with the Native Android client, and fresh compilation of Windows installers and portable executables.*

### Features & Architecture
1. **Multi-Program Custom Split Builder (`#view-program`)**:
   - Built a dedicated Program tab and full split builder view mirroring Android's `ProgramScreen.kt`.
   - Complete multi-program management (`LocalStore.saveProgram`, `createProgram`, `deleteProgram`, `duplicateProgram`, `setActiveProgramId`): lifters can create programs completely from scratch (no forced templates or defaults), switch active programs, and manage days.
   - Dynamic day editor with custom day naming, focus annotations, day reordering (Up/Down), and deletion.
   - Exercise builder per day with search autocomplete from catalog, target sets stepper, reps min/max steppers, RPE target, rest timer target, notes, and previous performance weight hints.
   - 1-tap **`Start Workout`** / **`Load in Logger`** seamlessly pre-fills `#view-log` with the day's exercises and automatically carries forward previous working weights.
2. **Decluttered Modern Goals Module (`#view-goals`)**:
   - Replaced old goals view with a modern, decluttered design matching Android's compact card layout.
   - Quick filter tabs: `All`, `Active`, and `Achieved`.
   - Compact goal cards with exercise name, category badge, target headline (`120 kg 1RM` or `3 × 8 @ 100 kg`), current best E1RM, status badge (`On Track`, `Achieved 🏆`, `Plateau Alert`, `Building Base`), thin progress bar, and gap closed percentage.
   - Next Session prescription headline (`Next: 3 × 5 @ 102.5 kg`) and 1-tap **`Train`** action button that directly loads the prescribed session into `#view-log`.
   - Collapsible deep-dive section with 4-phase periodization roadmap, weak-point accessories, and frequency/volume breakdown.
   - Comprehensive **Create & Edit Goal** modal with smart start weight/reps auto-filled from personal records.
   - Detailed **Exercise Goal Report** and **Master All-Goals Report** dialogs with status distributions, weekly programming blueprints, and history logs.
3. **Body Weight Tracker & Total-Load E1RM Engine (`#view-settings`)**:
   - Created a dedicated Body Weight logging card in Settings with date picker, weight input (kg), and instant logging.
   - Chronological bodyweight log table with individual entry deletion.
   - Integrated `BodyWeightUtils` with date carry-forward lookups across all historical and future sessions.
   - Total-load E1RM computation for bodyweight movements (`Pull Ups`, `Chin Ups`, `Dips`): $\text{totalLoad} = \text{bodyWeightOnDate} + \text{addedLoad}$, correctly updating all analytics, personal records, and goal evaluations.
4. **Dedicated Exercises Catalog Manager (`#view-exercises`)**:
   - Full 82+ movement catalog browser with muscle category filter pills and live search.
   - Category tags, kinetic descriptions, and bodyweight exercise badges.
   - Custom exercise creator with name, category, bodyweight checkbox, and kinetic description.
   - Exercise rename / edit modal featuring cascading updates across historical workout sets, active exercise goals, and weekly workout programs.
5. **Enhanced Workout Logger (`#view-log`)**:
   - Added **`Load Program`** quick-action button in header opening the Day picker modal to load any programmed day in 1 tap.
   - Dynamic **`Goal Progress`** indicator and modal when logging an active goal exercise.
   - Live total-load E1RM display for bodyweight lifts as weights and reps are entered.
   - Automatic draft persistence across sessions (`saveDraft` / `clearDraft`).
   - Post-workout goal evaluation popup presenting achieved milestones and updated progress upon saving.
6. **Unified Navigation & Windows Native Distribution Packaging**:
   - 8 sidebar navigation tabs (`Log`, `Program`, `Suggest`, `History`, `Progress`, `Goals`, `Exercises`, `Settings`) with keyboard shortcuts `Ctrl 1` through `Ctrl 8` and `Ctrl S` for saving.
   - Obsidian dark UI theme (`#0F1115` obsidian, `#FF7A45` orange, `#FFD166` gold).
   - Packaged production distribution via `electron-builder` into:
     - `builds/StrengthLogz Setup 1.0.0.exe` (Windows NSIS installer, 82.5MB)
     - `builds/StrengthLogz 1.0.0.exe` (Windows portable standalone, 82.3MB)
