# Veritas

![Android CI](https://github.com/Mulanger/veritas/actions/workflows/android.yml/badge.svg?branch=codex%2Fphase%2F0-scaffold&event=push)

Veritas is an Android app for on-device forensic media verification. Phase 0 establishes the project scaffold only: a multi-module Kotlin project that compiles, installs, launches, and shows a placeholder screen.

## CI

Workflow definition: [`.github/workflows/android.yml`](./.github/workflows/android.yml)

The badge above tracks the current Phase 0 branch, `codex/phase/0-scaffold`. After Phase 0 is signed off and merged, switch the badge to the long-term default branch.

## Requirements

- JDK 17 or newer
- Android SDK packages:
  - `platform-tools`
  - `platforms;android-36`
  - `build-tools;36.0.0`

## Local setup

1. Point the project at your Android SDK.
2. Generate the Gradle wrapper if it is missing.
3. Build and test the project.

If you want to use the local SDK path used in this workspace, create `local.properties` with:

```properties
sdk.dir=C:/Users/Mulen/Desktop/veritas/.android-sdk
```

## Commands

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
.\gradlew.bat assembleDebug --warning-mode all
.\gradlew.bat test
.\gradlew.bat precommitCheck
```

## Install on device

```powershell
.\gradlew.bat assembleDebug
.\.android-sdk\platform-tools\adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Then launch **Veritas** on the device or emulator. The Phase 0 placeholder screen should display `Veritas is building`.
