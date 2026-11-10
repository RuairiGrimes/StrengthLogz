# StrengthLogz 🏋️‍♂️

A desktop strength workout tracker built with **Node.js + Electron**, backed by **Google Sheets** (with local JSON fallback when offline or unconfigured), designed for tracking weight, reps, sets, notes, exercise history, and strength increase/decrease charts.

---

## Features

- **Streamlined Workout Logging**:
  - Auto-fills previous weights and reps from your last session for easy progressive overload.
  - Quick keyboard workflow: `Enter` to step through and add sets, `Ctrl + S` to save, `Ctrl + 1-4` for navigation.
  - Dropdown search from pre-determined exercises catalogue.
  - Dynamically create new exercises on the fly with custom categories and bodyweight options.
  - Instant estimated 1RM (Epley formula) feedback per set.
  - Automatic Personal Record (PR) celebration alerts.

- **Historical Workouts & Backlog**:
  - Complete backlog of all your 120 historical workout sessions (~1,390 sets) from October 2025 through October 2026 pre-parsed and ready in `data/history.txt`.
  - Repeat past workouts with one click, edit sets, or delete workouts.
  - Filter workouts by exercise name, notes, or date.

- **Strength Increase / Decrease Analytics & Charts**:
  - Interactive charts powered by Chart.js.
  - Color-coded progress segments: **Green** for strength increases, **Red** for decreases, **Gold** for all-time PRs.
  - Multiple metrics: **Estimated 1RM**, **Top Weight**, **Total Volume**, and **Max Reps**.
  - Time range filters (3 Months, 6 Months, 1 Year, All Time).
  - High-level overview cards with sparklines and % deltas for all your core lifts (Bench, OHP, Pull Ups, Dips, Deadlift, Rows, etc.).

- **Google Sheets Integration + Local Offline Storage**:
  - Direct sync to your own Google Sheet using a Google Cloud Service Account.
  - Automatically provisions 3 sheets: `Workouts`, `Sets`, and `Exercises`.
  - Clean relational tabular layout in Google Sheets for manual inspection, pivots, or formulas.
  - Automatic offline JSON storage fallback if Google Sheets is not yet connected.
  - One-click "Copy local data → Sheets" to migrate local logs whenever you connect your sheet.

---

## Getting Started

### 1. Launching the App
In the project directory, run:
```bash
npm start
```
*(Or `npx electron .`)*

### 2. Loading Your Historical Workouts
1. In the app, switch to **Settings** (`Ctrl + 4`).
2. Click **Import historical notes**.
3. All 120 sessions (1,390 sets across 82 exercises) from your notes will be loaded into the database!
4. Navigate to **History** (`Ctrl + 2`) or **Progress** (`Ctrl + 3`) to see your lifts, PRs, and strength charts.

---

## Connecting Google Sheets

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and create a project.
2. In **APIs & Services**, enable the **Google Sheets API**.
3. Go to **IAM & Admin > Service Accounts**, create a service account, and under **Keys**, create a **JSON key**. Download it to your computer.
4. Create a blank Google Sheet in your Google Drive (e.g., named *StrengthLogz*).
5. Share the sheet with your service account email (found in the JSON key or shown in the app settings) with **Editor** permissions.
6. In **StrengthLogz > Settings**:
   - Paste the Google Sheet URL or ID.
   - Click **Browse** and select your downloaded JSON key file.
   - Click **Connect**.
7. The app will automatically create and format the `Workouts`, `Sets`, and `Exercises` tabs!

---

## Deliverables & Documentation

- **Engineering Manual**: [AGENTS.md](file:///c:/MyProjects/StrengthLogz/AGENTS.md)
- **Detailed Changelog & Worklog**: [WORKLOG.md](file:///c:/MyProjects/StrengthLogz/WORKLOG.md)
- **Production Builds**:
  - Windows Installer: [builds/StrengthLogz Setup 1.0.0.exe](file:///c:/MyProjects/StrengthLogz/builds/StrengthLogz%20Setup%201.0.0.exe)
  - Windows Portable: [builds/StrengthLogz 1.0.0.exe](file:///c:/MyProjects/StrengthLogz/builds/StrengthLogz%201.0.0.exe)
  - Android APK: [builds/StrengthLogz-Native.apk](file:///c:/MyProjects/StrengthLogz/builds/StrengthLogz-Native.apk)

