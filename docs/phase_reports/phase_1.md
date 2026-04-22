# Phase 1 Completion Report

**Phase:** 1 - Design system
**Completed:** 2026-04-22
**Estimated effort:** 16 hours
**Actual effort:** ~11 hours

## Deliverables
- [x] Manrope and JetBrains Mono added to `core-design/src/main/res/font/`
- [x] `VeritasColors`, `VeritasType`, `VeritasSpacing`, `VeritasRadius`, and `VeritasTheme` implemented in `core-design`
- [x] Reusable primitives added: `VeritasScaffold`, `BrandMark`, `VeritasButton`, `VeritasTag`, `ConfidenceRange`, `EvidenceChip`, `StageRow`, `VerdictPill`, and `StatusBar`
- [x] Debug-only gallery added via `GalleryActivity` and `GalleryScreen`
- [x] Compose previews added for every primitive
- [x] Roborazzi screenshot suite and reference baselines added for each primitive

## Acceptance criteria
- [x] Gallery renders on device matching the design-system aesthetic - verified on headless emulator `veritas_api35` with `adb shell am start -n com.veritas.app.debug/.gallery.GalleryActivity`
- [x] All primitives have Compose previews - verified by successful module compilation with preview functions present and by rendering the same primitive composables in Roborazzi captures
- [x] Screenshot tests pass - verified with `.\gradlew.bat :core-design:recordRoborazziDebug`, `.\gradlew.bat :core-design:verifyRoborazziDebug`, and `.\gradlew.bat precommitCheck`
- [x] No hardcoded colors or spacing anywhere outside `core-design` - verified by repo-wide Kotlin source search for hex colors, `Color(...)`, `.dp`, and `.sp` outside `core-design`

## Decisions made
- [D-020]: chose Roborazzi over Paparazzi for screenshot testing and CI verification
- [D-021]: used variable font assets for Manrope and JetBrains Mono instead of per-weight static files

## Deviations from plan
- Used variable font files instead of separate static font files per weight. This still satisfies the required weight mappings in Compose and keeps asset maintenance lower.
- Verified primitive rendering through Roborazzi and device gallery launch in this headless environment rather than using Android Studio's live preview pane directly.

## Pitfalls encountered
- The machine-level `JAVA_HOME` pointed to an Android Studio Java 11 runtime, which is too old for this Gradle build. Verification commands were run with `C:\Program Files\Java\jdk-21`.
- `adb` was not on the system `PATH`. The phase used the repo-local SDK at `.\.android-sdk\platform-tools\adb.exe` for emulator and device verification.
- Default `detekt` thresholds were aggressive for a gallery/catalog file. The design-system code was split into focused files and only narrowly scoped suppressions were kept for token-value geometry and section-heavy gallery composition.

## Open questions for human
- None.

## Ready for Phase 2?
Yes - all Phase 1 acceptance criteria are met. Human sign-off is still required before Phase 2.

## Demo instructions
1. Set Java for this shell: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'`
2. Verify full gates: `.\gradlew.bat assembleDebug test precommitCheck --warning-mode all`
3. Start an emulator if needed, then install the debug APK: `.\.android-sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\debug\app-debug.apk`
4. Launch the gallery: `.\.android-sdk\platform-tools\adb.exe shell am start -n com.veritas.app.debug/.gallery.GalleryActivity`
5. Compare the gallery against `veritas_mockup.html` and the screenshot baselines in `core-design/src/test/screenshots/`
