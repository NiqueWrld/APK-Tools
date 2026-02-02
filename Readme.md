# APK Tools

An educational Android app that extracts and decompiles APK files into Smali code for analysis.

## Features

- 📱 **List Installed Apps** - Browse all user-installed apps on your device
- 🔍 **Search Apps** - Filter apps by name or package
- ⚙️ **Decompile to Smali** - Extract and decompile DEX files using baksmali/dexlib2
- 📁 **File Browser** - Navigate decompiled Smali files in a folder structure
- 🔎 **Search Files** - Search recursively through decompiled files
- 📄 **Code Viewer** - Read-only monospace viewer for Smali code
- 🖼️ **Media Viewer** - Preview images, videos, and audio from APKs
- 📦 **Export ZIP** - Export decompiled files as ZIP to Downloads/APKTools
- 📲 **Extract APK** - Extract original APK to Downloads/APKTools

## Requirements

- Android 8.0+ (API 26+)
- ~50MB free storage for app + decompiled output
- USB debugging enabled for CLI installation

## Build & Install (CLI)

```bash
# Clone or download the project
cd "APK-Tools"

# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Or build release
./gradlew assembleRelease
```

The debug APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```
app/
├── src/main/
│   ├── java/com/niquewrld/apktools/
│   │   ├── data/
│   │   │   ├── model/       # AppInfo, FileItem, DecompileResult
│   │   │   └── repository/  # AppRepository, FileRepository
│   │   ├── domain/          # SmaliDecompiler engine
│   │   └── ui/
│   │       ├── applist/     # Main screen with app grid
│   │       ├── detail/      # App detail page
│   │       ├── browser/     # Smali file browser
│   │       ├── viewer/      # Code viewer
│   │       └── media/       # Media viewer
│   └── res/
│       └── layout/          # XML layouts
├── build.gradle.kts
└── proguard-rules.pro
```

## How It Works

1. **App Discovery** - Uses `PackageManager` APIs to get installed app info and APK paths
2. **DEX Extraction** - Opens APK as ZIP, extracts all `classes*.dex` files
3. **Decompilation** - Uses `dexlib2` to parse DEX and `baksmali` to generate Smali
4. **Storage** - All output stored in app-private storage (`filesDir`)
5. **Exports** - APKs and ZIPs exported to `Downloads/APKTools`

## Limitations

- ❌ **No APK modification** - Read-only analysis only
- ❌ **No re-signing/reinstalling** - Cannot modify or redistribute apps
- ❌ **No root required** - Works on unrooted devices
- ❌ **No Java reconstruction** - Outputs Smali bytecode, not Java source
- ⚠️ **100MB limit** - Large APKs are blocked to prevent OOM crashes

## Legal Notice

⚠️ **Educational Use Only**

This app is designed for:
- Learning how Android apps work internally
- Analyzing your own apps during development
- Security research on apps you have permission to inspect

**Only analyze apps you own or have explicit permission to inspect.**

## Tech Stack

- **Kotlin** - Primary language
- **dexlib2** (2.5.2) - DEX file parsing
- **baksmali** (2.5.2) - Smali code generation
- **AndroidX** - UI components
- **Material 3** - Modern UI design
- **Coroutines** - Background processing with cancellation support

## License

MIT License - See LICENSE file for details.
