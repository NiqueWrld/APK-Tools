# APK Analyser - Copilot Instructions

## Project Overview

Educational APK analyser that extracts and decompiles Android APKs to Smali code. **Read-only analysis only** - no APK modification, re-signing, or reinstallation.

## Tech Stack & Constraints

- **Language:** Kotlin
- **Target:** Android 8+ (API 26+)
- **Build:** Gradle CLI only (no Android Studio-specific features)
- **UI:** XML layouts or minimal Jetpack Compose
- **Key Library:** `baksmali`/`dexlib2` for DEX→Smali decompilation

## Architecture

```
app/
├── data/           # APK extraction, file I/O
├── domain/         # Smali decompilation engine (dexlib2)
├── ui/
│   ├── applist/    # Installed apps list
│   ├── browser/    # Folder-style Smali file browser
│   └── viewer/     # Monospace code viewer (read-only)
└── util/           # Background threading, progress tracking
```

## Critical Patterns

### Background Processing
All decompilation runs on background threads with:
- Progress reporting to UI
- Cancellation support
- APK size limits to prevent OOM crashes

### Storage
- Output stored in **app-private storage** only (`context.filesDir`)
- Never write to external/shared storage

### APK Access
- Use `PackageManager` APIs to get APK paths
- Copy APK to private storage before extraction
- Extract `classes.dex` (and `classes2.dex`, etc.) from ZIP

## Build & Run (CLI)

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Build release
./gradlew assembleRelease
```

## Key Dependencies

```kotlin
// build.gradle.kts (app)
implementation("org.smali:dexlib2:2.5.2")  // DEX parsing
implementation("org.smali:baksmali:2.5.2") // Smali output
```

## Restrictions (Enforce These)

- ❌ No APK modification
- ❌ No re-signing or reinstalling apps
- ❌ No root access requirements
- ❌ No Java source reconstruction (Smali only)
- ✅ Warn users to analyze only apps they own/have permission for

## UI Components

1. **App List Screen** - Shows installed apps via `PackageManager`
2. **Decompile Button** - Triggers background Smali extraction
3. **File Browser** - Tree view of decompiled Smali files
4. **Code Viewer** - Monospace, read-only Smali display

---

*Last updated: January 5, 2026*
