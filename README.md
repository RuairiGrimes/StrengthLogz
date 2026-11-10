# StrengthLogz 🏋️‍♂️

An open-source, dual-client strength workout tracker for **Windows Desktop (Electron)** and **Android (Native Kotlin + Jetpack Compose)**, backed by your own **Google Sheet** with full offline local storage fallback.

---

## Features

- **Streamlined Workout Logging**:
  - Auto-fills previous working weights and reps for progressive overload.
  - Instant **Estimated 1RM (Epley)** feedback per set, with automatic invalidation of failed (`0`-rep) sets.
  - Total-load E1RM calculation for bodyweight lifts (`Pull Ups`, `Chin Ups`, `Dips`) using carried-forward body weight logs.
  - Draft auto-persistence across sessions, `Load Program` 1-tap loader, and automatic Personal Record (PR) detection.

- **Custom Multi-Program Split Builder**:
  - Create and store multiple custom training programs from scratch with zero forced templates.
  - Build unlimited custom days per program with exercise steppers (target sets, rep ranges, RPE, rest timer, notes) and 1-tap **Start Workout**.

- **Smart Suggest Workout Engine**:
  - Analyzes 7-day muscle group volume (`Chest`, `Back`, `Shoulders`, `Legs`, `Arms`, `Core`) and detects deficits or plateaued lifts.
  - Generates tailored **Push**, **Pull**, or **Full Body** sessions (`4 Lifts` or `5 Lifts`), with per-slot exercise **Swap**, variation shuffling, progressive overload targets, and 1-tap logger loading.

- **Strength Goals & Periodized Programming**:
  - Track **1RM** or **Sets × Reps** targets across exercises.
  - Generates next-session working set prescriptions, 4-phase periodization roadmaps, weak-point accessory recommendations, and per-exercise / master portfolio reports.

- **Strength Analytics & Interactive Charts**:
  - Track Estimated 1RM, Top Weight, Total Volume, and Max Reps across `3M`, `6M`, `1Y`, and `All` time ranges.
  - Visual color cues for gains (**Green**), regressions (**Red**), and all-time PRs (**Gold**).

- **Exercises Catalog & Body Weight Tracker**:
  - Built-in catalog of 80+ standardized movements with muscle categories and kinetic descriptions.
  - Create custom exercises or rename exercises with cascading updates across logs, goals, and programs.
  - Built-in Body Weight Tracker (and Android Health Connect support on mobile).

- **Google Sheets Cloud Sync + Offline Storage**:
  - Sync directly to your own Google Sheet using a Google Cloud Service Account.
  - Automatically provisions and formats `Workouts`, `Sets`, and `Exercises` sheets.
  - Works 100% offline with local JSON storage (`local-data.json` on Desktop, internal JSON storage on Android), plus one-click migration to Google Sheets when connected.

---

## Getting Started (Desktop)

### 1. Run from Prebuilt Binaries
Download the installer or portable executable from the `builds/` directory or GitHub Releases:
- **Windows Installer**: `StrengthLogz Setup 1.0.0.exe`
- **Windows Portable**: `StrengthLogz 1.0.0.exe`

### 2. Run from Source
```bash
npm install
npm start
```

### 3. Build Windows Executables
```bash
npm run dist
```

---

## Connecting Google Sheets (Optional Cloud Sync)

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and create a project.
2. In **APIs & Services**, enable the **Google Sheets API**.
3. Go to **IAM & Admin > Service Accounts**, create a service account, and under **Keys**, create a **JSON key**. Save it locally.
4. Create a blank Google Sheet in your Google Drive (e.g., named *StrengthLogz*).
5. Share the sheet with your service account email (shown in **Settings**) with **Editor** permissions.
6. In **StrengthLogz > Settings**:
   - Paste the Google Sheet URL or ID.
   - Select or paste your JSON key file.
   - Click **Connect**.

---

## Documentation & Deliverables

- **Developer & Architecture Guide**: [AGENTS.md](file:///c:/MyProjects/StrengthLogz/AGENTS.md)
- **Detailed Changelog & Worklog**: [WORKLOG.md](file:///c:/MyProjects/StrengthLogz/WORKLOG.md)
- **Android APK**: [builds/StrengthLogz-Native.apk](file:///c:/MyProjects/StrengthLogz/builds/StrengthLogz-Native.apk)
