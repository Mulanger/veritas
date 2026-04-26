# Phase 3 Completion Report

**Phase:** 3 - Onboarding
**Completed:** 2026-04-23
**Estimated effort:** 20 hours
**Actual effort:** ~13 hours

## Deliverables
- [x] `feature-onboarding` module added with the full onboarding flow and state management
- [x] Separate `LaunchActivity` and `OnboardingActivity` implemented, with splash handoff and `hasCompletedOnboarding` persisted in DataStore
- [x] All Phase 3 onboarding screens implemented with placeholder asset boxes and spec copy
- [x] Overlay and notifications permission flows implemented, including denied and return-from-settings behavior
- [x] Connected UI test coverage added for happy path, declined overlay path, return-from-settings path, and relaunch-after-completion behavior
- [x] Permission handling verified on Android 11, 13, 14, and 15 emulators

## Acceptance criteria
- [x] Fresh install -> onboarding -> home - verified by `Phase3OnboardingTest.happyPathCompletesOnboardingAndShowsHome` on `veritas_api30`, `veritas_api33`, `veritas_api34`, and `veritas_api35`
- [x] Fresh install -> skip overlay -> home - verified by `Phase3OnboardingTest.overlayDeniedCanContinueWithoutBubbleAndShowHome` on `veritas_api30`, `veritas_api33`, `veritas_api34`, and `veritas_api35`
- [x] Onboarding does not show on subsequent launches - verified by `Phase3OnboardingTest.completedOnboardingSkipsFlowOnNextLaunch`, plus `LaunchActivity` routing off the persisted `hasCompletedOnboarding` flag
- [x] All screens match the visual spec copy exactly - verified by implementation audit against `docs/02_VISUAL_SPEC.md` section 2 while building `feature-onboarding`

## Decisions made
- [D-024]: followed the written onboarding spec over the older onboarding HTML where they conflicted, because the phase requires exact current copy
- [D-025]: used a debug-only harness activity for connected permission-flow tests so overlay and notification branches stay deterministic across API levels
- [D-026]: added an optional internal `testTag` on `VeritasButton` so Compose instrumentation targets the clickable semantics node reliably

## Deviations from plan
- Used a debug-only `Phase3TestActivity` and `Phase3TestHarness` for instrumentation instead of driving real Settings screens and system permission UIs directly. Production onboarding still uses the real platform launchers.

## Pitfalls encountered
- `SYSTEM_ALERT_WINDOW` cannot be requested as a runtime permission. The flow opens `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` and handles the result on return.
- `POST_NOTIFICATIONS` is Android 13+. Below API 33, the notifications screen is skipped entirely by the onboarding state machine.
- The phase warned not to start services during onboarding. No foreground service or overlay runtime was started; onboarding stops at education and permission capture only.
- Copy had to match the spec exactly. The written visual spec was treated as the source of truth instead of the older onboarding HTML when wording differed.

## Open questions for human
- None.

## Ready for Phase 4?
Yes - all Phase 3 acceptance criteria are met. Human sign-off is still required before Phase 4.

## Demo instructions
1. Set Java for this shell: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'`
2. Run the local gates: `.\gradlew.bat assembleDebug test precommitCheck --warning-mode all`
3. Run the connected onboarding suite on a booted emulator: `.\gradlew.bat :app:connectedDebugAndroidTest --warning-mode all`
4. For a fresh-install walkthrough, clear app data: `.\.android-sdk\platform-tools\adb.exe shell pm clear com.veritas.app.debug`
5. Launch the app: `.\.android-sdk\platform-tools\adb.exe shell am start -n com.veritas.app.debug/com.veritas.app.LaunchActivity`
6. Walk through the onboarding flow once, then relaunch to confirm the app routes directly to home
