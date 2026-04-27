# Session Notes

## Date
2026-04-27

## Branch
`main`

## Current Status
- Phase 11 is complete, signed off, merged to `main`, and the remote `phase/11-history-settings` branch has been deleted.
- Completion/status report: `docs/phase_reports/phase_11.md`.
- Pixel 8 visual review is complete; screenshots for history, settings, diagnostics, telemetry, and about are embedded in `docs/phase_reports/phase_11.md`.
- The current debug build has been installed on the connected Pixel 8 for hands-on review.
- Ready to start Phase 12: overlay mode.

## Phase 11 Outcome
- Added Room history persistence in `data-detection` with latest-100 retention.
- History rows store bounded verdict summaries and app-private thumbnails, not original media paths or copied media.
- Scan flow saves history at verdict time before temp media cleanup, so thumbnails can be generated safely.
- History UI now supports grouped list, empty state, item open, item delete, and per-item diagnostic export entry points.
- Historical detail reuses the verdict UI with a history chrome label.
- Added shared Preferences DataStore settings for onboarding, overlay, model update, privacy, telemetry, and reset state.
- Settings UI now includes Overlay, Models, Privacy & Data, Diagnostics, and About panes.
- Added a one-time telemetry opt-in bottom sheet after verdict display; default remains off.
- Added diagnostic export generation and FileProvider share flow with redacted Veritas logs and aggregate state only.

## Key Decisions Logged
- D-059: History stores thumbnails and verdict summaries only.
- D-060: History thumbnails are generated before purge and pruned with Room rows.
- D-061: Phase 11 settings use one Preferences DataStore.
- D-062: Diagnostic export is aggregate and redacted.

## Verification Commands
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :app:compileDebugKotlin`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :data-detection:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :app:assembleDebug`

## Known Follow-Up
- Phase 11 connected Compose test reaches the Pixel 8 but fails before assertions on Android 16 with `NoSuchMethodException: android.hardware.input.InputManager.getInstance` from AndroidX Compose/Espresso idling.
- Phase 10 and Phase 11 connected Compose tests remain blocked on the Android 16 AndroidX Compose/Espresso runtime compatibility issue unless the AndroidX test stack is updated or the tests are run on a compatible Android version.
- Next session should begin with `PHASE_12_SESSION_PROMPT.md` and the Phase 12 section in `docs/03_PHASE_PLAN.md`.
