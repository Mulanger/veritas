# Phase 0 Completion Report

**Phase:** 0 - Scaffold
**Completed:** 2026-04-22 (local scaffold complete; GitHub badge pending workflow verification)
**Estimated effort:** 12 hours
**Actual effort:** ~10 hours

## Deliverables
- [x] Multi-module Android project scaffolded with `app`, `core-common`, `core-design`, `domain-detection`, `data-detection`, and `feature-home`
- [x] Placeholder app screen compiles, installs, and launches
- [x] Hilt, Timber, Compose Material3, Gradle wrapper, static analysis, and test wiring added
- [x] GitHub Actions workflow added for lint, build, test, and APK artifact upload
- [x] README added with local setup, verification commands, and install instructions

## Acceptance criteria
- [x] `./gradlew assembleDebug` succeeds with zero warnings - verified locally with `.\gradlew.bat assembleDebug --warning-mode all`
- [x] `./gradlew test` succeeds - verified locally with `.\gradlew.bat test`
- [x] App installs and launches on Android 11, 14, and 15 devices (or emulators) - verified on headless emulators `veritas_api30`, `veritas_api34`, and `veritas_api35` with `adb install -r` and `am start -W -n com.veritas.app/.MainActivity`
- [ ] CI badge in README shows green - pending first successful GitHub Actions run on `codex/phase/0-scaffold`

## Decisions made
- [D-019]: chose `compileSdk = 36` while keeping `targetSdk = 35` because current stable AndroidX dependencies require SDK 36 at compile time. Logged in `docs/05_GLOSSARY_AND_DECISIONS.md`.

## Deviations from plan
- Used API 30, 34, and 35 ATD emulators for acceptance instead of physical devices. This stays within plan scope because the phase allows device or emulator verification.
- README badge targets the Phase 0 branch instead of `main`. This avoids pretending `main` is the active review branch before the Phase 0 checkpoint merge exists.

## Pitfalls encountered
- Compose PascalCase function names tripped `detekt`'s default `FunctionNaming` rule. Resolved with narrow suppressions on the specific `@Composable` entry points instead of weakening the global rule set.
- The machine-level `JAVA_HOME` pointed at a non-existent Android Studio JRE. Resolved by exporting the Android Studio JBR path explicitly for Gradle and SDK commands.

## Open questions for human
- None.

## Ready for Phase 1?
No - the local scaffold is ready, but Phase 0 is still waiting on a green README badge from GitHub Actions and your checkpoint sign-off.

## Demo instructions
1. Set Java for this shell: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'`
2. Verify local gates: `.\gradlew.bat assembleDebug --warning-mode all`, `.\gradlew.bat test`, `.\gradlew.bat precommitCheck`
3. Install on any connected device or emulator: `.\.android-sdk\platform-tools\adb install -r .\app\build\outputs\apk\debug\app-debug.apk`
4. Launch the app. The placeholder screen should show `Veritas is building`
5. Open the public repo branch on GitHub, confirm the `Android CI` workflow is green, and verify the README badge renders green for `codex/phase/0-scaffold`
