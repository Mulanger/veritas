# Phase 2 Completion Report

**Phase:** 2 - Navigation + home
**Completed:** 2026-04-22
**Estimated effort:** 12 hours
**Actual effort:** ~9 hours

## Deliverables
- [x] Typed Compose Navigation added with `kotlinx.serialization` route objects in the app shell
- [x] Top-level destinations implemented for Home, History, and Settings
- [x] Custom bottom nav matching the mockup's mono labels and accent/inactive states
- [x] Home screen implemented with hero, CTAs, recent-history section, empty state, and mock recent items via in-memory `MutableStateFlow`
- [x] History empty-state stub and Settings title-only stub added in dedicated feature modules
- [x] Compose UI tests added for bottom-nav navigation, picker launch injection, recent-state rendering, and configuration-change survival

## Acceptance criteria
- [x] Bottom nav works on device - verified on emulator `veritas_api35` with `.\gradlew.bat :app:connectedDebugAndroidTest` and by launching `com.veritas.app.debug/com.veritas.app.MainActivity`
- [x] Home screen matches the Phase 2 home mockup direction - verified by launching the debug app on `veritas_api35` after `.\gradlew.bat :app:installDebug`
- [x] Empty and populated recent states both render correctly - verified by `Phase2NavigationTest.homeSupportsEmptyAndPopulatedRecentStates` and the debug-only READY long-press toggle

## Decisions made
- [D-022]: kept the bottom-nav label as `ABOUT` while routing it to the Phase 2 Settings stub to reconcile the plan with the visual spec
- [D-023]: used a debug-only READY long-press menu plus BuildConfig initial state to expose empty/populated recent-history rendering without changing the production layout

## Deviations from plan
- Used a single `OpenDocument` launcher in `MainActivity` as the Phase 2 picker stub. The media-type-specific ingestion split remains deferred to Phase 4, which is where actual copy, validation, and storage handling belong.
- Verified configuration-change survival with an activity recreation test in a debug-only Compose host activity rather than only by manual rotation.

## Pitfalls encountered
- The phase warned against string routes. All top-level navigation uses typed serializable route objects; no string route constants were introduced.
- The phase warned that bottom-nav state must survive configuration change. A connected test recreates the activity on `veritas_api35` and verifies the selected destination is preserved.
- The phase warned against `savedStateHandle` state-sharing. Home recent-state is isolated inside a feature-local `ViewModel` backed by in-memory `MutableStateFlow`.

## Open questions for human
- None.

## Ready for Phase 3?
Yes - all Phase 2 acceptance criteria are met. Human sign-off is still required before Phase 3.

## Demo instructions
1. Set Java for this shell: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'`
2. Run the full verification gates: `.\gradlew.bat assembleDebug test precommitCheck --warning-mode all`
3. Run the connected UI suite on the emulator: `.\gradlew.bat :app:connectedDebugAndroidTest`
4. Install and launch the app: `.\gradlew.bat :app:installDebug` then `.\.android-sdk\platform-tools\adb.exe shell am start -n com.veritas.app.debug/com.veritas.app.MainActivity`
5. On the home screen, long-press `READY` in the debug build to flip between empty and mock recent-history states
