# AGENTS.md — Developer & AI Assistant Guide

This document provides system architecture, design patterns, directory maps, operational workflows, and engineering rules for AI agents and developers working on **StrengthLogz**.

---

## 1. Project Overview & Architecture

**StrengthLogz** is a dual-client workout tracking ecosystem with unified data structures backed by **Google Sheets** (with seamless offline fallback to local JSON/Room storage).

```
                        ┌────────────────────────────────┐
                        │   Google Sheets Cloud Backend  │
                        │ (Workouts / Sets / Exercises)  │
                        └───────────────┬────────────────┘
                                        │
                ┌───────────────────────┴───────────────────────┐
                ▼                                               ▼
┌───────────────────────────────┐               ┌───────────────────────────────┐
│       Windows Desktop         │               │        Android Mobile         │
│      (Electron + Node)        │               │   (Native Kotlin + Compose)   │
│                               │               │                               │
│ - IPC Main <-> Renderer       │               │ - Jetpack Compose Navigation  │
│ - HTML5 + Chart.js Canvas     │               │ - Canvas-drawn Sparklines     │
│ - Local JSON File Cache       │               │ - In-Memory / SharedPreferences│
│ - electron-builder Packaging  │               │ - Android Gradle / Proguard   │
└───────────────────────────────┘               └───────────────────────────────┘
```

---

## 2. Directory Structure

```
c:\MyProjects\StrengthLogz\
├── AGENTS.md                 # System documentation & agent operating manual (this file)
├── WORKLOG.md                # Comprehensive changelog and chronological development log
├── README.md                 # User-facing installation and setup guide
├── main.js                   # Electron main process (IPC handlers, window lifecycle)
├── preload.js                # Context bridge exposing window.api to renderer
├── package.json              # Desktop dependencies and electron-builder configurations
├── assets/                   # App icons (.ico, .png, high-res bitmaps)
├── builds/                   # Production distribution deliverables
│   ├── StrengthLogz Setup 1.0.0.exe   # Windows NSIS installer
│   ├── StrengthLogz 1.0.0.exe         # Windows standalone portable executable
│   └── StrengthLogz-Native.apk        # Compiled Android APK package
├── data/
│   └── exercises.json        # Standardized default exercise catalog
├── src/
│   ├── data-service.js       # Core business logic, cache state, Google Sheets coordinator
│   └── stores/
│       ├── local-store.js    # Local JSON storage fallback (userData/local-data.json)
│       └── sheets-store.js   # Google Sheets API client using service account auth
├── renderer/
│   ├── index.html            # Single page app container with modular view panels
│   ├── styles.css            # Dark mode theme design system (#0F1115 obsidian, #FF7A45 orange)
│   └── app.js                # Desktop frontend logic, charts, volume calculations, suggest engine
└── mobile-android/           # 100% Native Kotlin Jetpack Compose Android Client
    ├── build.gradle.kts      # Project-level Gradle build script
    ├── settings.gradle.kts   # Project repositories and module settings
    └── app/
        ├── build.gradle.kts  # Dependencies (Compose BOM, Material3, Coroutines, OkHttp, GSON)
        └── src/main/
            ├── AndroidManifest.xml  # Permissions, launcher intent, portrait lock, custom icons
            ├── res/                 # Adaptive launcher icons across mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi
            └── java/com/strengthlogz/mobile/
                ├── MainActivity.kt               # Root Activity, Tab navigation, global state flow
                ├── model/Models.kt               # Relational data models (Workout, WorkoutSet, Exercise, ExerciseGoal)
                ├── util/
                │   ├── DateUtils.kt              # Strict DD-MON-YYYY <-> ISO parsing & formatting
                │   ├── BodyWeightUtils.kt        # Carry-forward bodyweight & total-load E1RM helpers
                │   └── GoalEngine.kt             # Exercise goal calculations, periodized programming & reports
                ├── data/
                │   ├── GoogleSheetsClient.kt     # Lightweight HTTP direct Sheets v4 sync engine
                │   └── WorkoutRepository.kt      # Local cache, sync dispatch, sample data loader
                └── ui/
                    ├── theme/Color.kt            # Obsidian (#0F1115), Orange (#FF7A45), PR Gold (#FFD166)
                    ├── LogWorkoutScreen.kt       # Workout logging, set add/delete, live Epley E1RM & Goal Progress
                    ├── ProgramScreen.kt          # Weekly workout split builder (1-7 days), day editor & 1-tap logger loader
                    ├── HistoryScreen.kt          # Chronological session list, set inspection, delete/repeat
                    ├── ProgressScreen.kt         # Search bar, 4-card dashboard, scrubbing graph, Goals entry
                    ├── GoalsScreen.kt            # Dedicated Goals page, programming roadmap, Exercise & Master Reports
                    ├── SuggestWorkoutScreen.kt   # Push/Pull/FB workout engine with 7-day deficit ranking
                    └── SettingsScreen.kt         # User-configurable Google Sheets credentials, setup guide & sync
```

---

## 3. Core Business Logic & Algorithms

### 3.1 Estimated 1RM (E1RM) Formula
Both clients strictly use the **Epley Formula** for all estimated 1-rep maximums:
$$\text{E1RM} = \text{weight} \times \left(1 + \frac{\text{reps}}{30}\right)$$

#### Bodyweight Exercises (Total-Load E1RM)
For exercises flagged `bodyweight` in the catalog (Pull Ups, Chin Ups, Dips, ...), the logged weight is the **added** load (`BW` = 0). E1RM is computed on **total load = body weight on the set's date + added weight** (`BodyWeightUtils.totalLoad`), and relative strength = E1RM / body weight. Body weight comes from `AppData.bodyWeights` (manual entries, Health Connect), using carry-forward by date.

#### Invalidation of Failed/Zero-Rep Sets
*Sets with `reps == 0` or missing reps (e.g. `115kg x 0`) represent failed attempts.*
- Failed sets **MUST NEVER** be passed into E1RM calculations (`reps > 0` validation required).
- Failed sets **MUST NEVER** count as working sets, top weight, or PR records.

### 3.2 Date Format Standard
All dates presented across the UI and logged into records must conform to:
$$\text{DD-MON-YYYY}\quad\text{(e.g., } 09\text{-OCT-}2026\text{)}$$
Sorting internally is done using sortable ISO format (`YYYY-MM-DD`). The utility functions `DateUtils.toSortableIso()` and `DateUtils.formatDisplay()` (or `shortDate()` in JS) manage bidirectional transformations.

### 3.3 "Suggest a Workout" Engine
Workouts are suggested based on weekly training volume deficits and PR opportunities across 3 splits:
1. **Push**:
   - Primary Compound Press (Chest)
   - Push Leg: Quad Overload (Leg Press, Bulgarian Split Squat, Hack Squat, Squat, Front Squat)
   - Vertical / Shoulder Press (Overhead Press, Dumbbell Press)
   - Secondary Chest / Push Accessory (Dips, Incline Machine, Flies)
   - Push Leg: Quad & Knee Extension Finisher (Bulgarian Split Squat, Leg Extension, Pistol Squat)
   - Medial Delts & Triceps Finisher (Lateral Raises, Tricep Extensions, Pushdowns)
2. **Pull**:
   - Primary Vertical Pull (Pull Ups, Chin Ups, Lat Pulldown)
   - Heavy Horizontal Row (Pendlay Row, DB Row, Barbell Row)
   - Pull Leg: Hip Hinge & Posterior Chain (Good Morning, Hyperextension, RDL, Deadlift)
   - Posterior Chain & Hamstring Finisher (Hyperextension, Good Morning, Leg Curl)
   - Rear Delts, Upper Back & Stretch (Face Pulls, Rear Delt Raises, Pullovers, High Pulls)
   - Biceps & Grip Finisher (Fat Gripz Hammer Curls, Barbell Curls, Preacher Curls)
3. **Full Body (FB)**:
   - Primary Lower Compound (Squat, Leg Press, Good Morning, Deadlift)
   - Primary Upper Push (Bench, OHP, Dips)
   - Primary Upper Pull (Pull Ups, Rows)
   - Secondary Lower Quad/Hamstring Assistance
   - Shoulders & Upper Back
   - Arms & Core Overload

#### Candidate Scoring & Progressive Overload
Candidates in each slot are scored via:
$$\text{Score} = \text{Sessions} \times 0.8 + \text{PlateauBonus} + \text{RecencyBonus} + \text{VolDeficitBonus} + \text{BenefitBonus}$$
- **Discovery**: Exercises with zero logged sessions receive an initial discovery score (8.0 + volume bonus + benefit bonus) so they regularly surface upon shuffle or swap.
- **Overload Prescription**:
  - Weight jump of $+2.5\text{kg}$ (or $+1.25\text{kg}$ for arms/raises) when previous top set $\ge 8\text{ reps}$ for strength slots or $\ge 12\text{ reps}$.
  - Rep progression ($+1\text{ rep}$) when working weight is maintained.
- **Benefit Annotation**: Exercises contain descriptive kinetic benefits (e.g., *"Unilateral quad/glute strength, eliminates leg imbalances, and spares lower spine"* for Bulgarian Split Squats).

---

## 4. Operational Instructions for Agents

### 4.1 Modifying Android Code
1. Kotlin source files are located in `mobile-android/app/src/main/java/com/strengthlogz/mobile/`.
2. Do not use plain Android shell execution directly without setting Android dev paths. Always execute Gradle commands via:
   ```cmd
   cmd.exe /c "set ANDROID_HOME=C:\AndroidDev\android-sdk&& set JAVA_HOME=C:\AndroidDev\jdk-17&& set PATH=%JAVA_HOME%\bin;%PATH%&& C:\AndroidDev\gradle-8.7\bin\gradle.bat <task> --no-daemon"
   ```
3. Run `testDebugUnitTest` prior to building.
4. When building APK, run `assembleDebug` and copy the output:
   ```cmd
   cmd.exe /c "copy /Y mobile-android\app\build\outputs\apk\debug\app-debug.apk builds\StrengthLogz-Native.apk"
   ```

### 4.2 Modifying Desktop Code
1. Main process code is in `main.js`, preload bridge in `preload.js`, and views in `renderer/`.
2. Do not invoke `npm` via PowerShell (`npm.ps1` is blocked by Windows execution policy). Always run via:
   ```cmd
   cmd.exe /c "npm.cmd <command>"
   ```
3. When updating packages or electron-builder configs, make sure `package.json` includes `"data/exercises.json"` and `"assets/**/*"` in `build.files` so bundled `.asar` executables do not fail on missing JSON assets.
4. Build native Windows distribution executables using:
   ```cmd
   cmd.exe /c "npm.cmd run dist"
   ```
   and copy generated `.exe` binaries into `builds/`.

### 4.3 Git & Revision Control
If Git repository initialization is requested:
- Keep `node_modules/`, `dist/`, `.gradle/`, and `mobile-android/app/build/` in `.gitignore`.
- Keep standalone deliverables in `builds/` checked in if pre-compiled binaries are desired for direct download.

