# Phase 5 Completion Report

**Phase:** 5 - Detection pipeline (stub)
**Completed:** 2026-04-23
**Estimated effort:** 16 hours
**Actual effort:** ~15 hours

## Deliverables
- [x] `domain-detection` interfaces added for `Detector<T : DetectorInput>`, `DetectionPipeline`, `ScanStage`, and `Verdict`
- [x] `FakeDetectionPipeline` implemented in `data-detection` with deterministic filename routing, seeded fallback behavior, realistic stage timing, and fake reasons/confidence ranges
- [x] Scanning screen implemented with progressive stage updates, preview card, and close-to-cancel behavior
- [x] Three verdict variants implemented for `Looks authentic`, `Verified authentic`, `Uncertain`, and synthetic outcomes using the three-verdict UI system
- [x] Forensic view stub implemented with placeholder heatmap, seeded timeline strip, and reason list using real `ReasonCode` values
- [x] Per-reason detail sheet implemented from a reason-code copy dictionary
- [x] Unit and connected test coverage added for filename routing, cancellation, verdict variants, forensic navigation, and reason-sheet dismissal

## Acceptance criteria
- [x] Demo flow share -> scan -> verdict -> forensic -> back to verdict -> done works end to end - verified by `Phase5DetectionFlowTest.syntheticFlowShowsForensicViewAndReasonSheet` plus manual install and launch of the debug app
- [x] All three verdict paths are reachable via filename convention - verified by `FakeDetectionPipelineTest` and `Phase5DetectionFlowTest` coverage for `_authentic`, `_authentic_c2pa`, `_uncertain`, and `_synthetic`
- [x] Cancel during scan returns safely without leaking work - verified by `FakeDetectionPipelineTest.cancelStopsActiveScan` and `Phase5DetectionFlowTest.cancelDuringScanFinishesActivity`
- [x] Visual fidelity for scanning, verdict, forensic, and detail-sheet flows matches the Phase 5 mockup direction - verified during implementation against `docs/02_VISUAL_SPEC.md` sections 2.4 through 2.9 and the corresponding `veritas_mockup.html` screens
- [x] Full local project gates pass - verified by `.\gradlew.bat assembleDebug test precommitCheck --warning-mode all`
- [x] Targeted connected Phase 4 and Phase 5 flows pass on device - verified by `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase4MediaIngestionTest,com.veritas.app.Phase5DetectionFlowTest" --warning-mode all`

## Decisions made
- [D-031]: treated the latest Phase 5 build guidance as the source of truth for the scan-stage contract and media-specific stage lists
- [D-032]: made cancellation pipeline-owned via `DetectionPipeline.cancel()` so fake and later real scans stop cleanly on close
- [D-033]: preserved original filename routing tokens in scoped copies while keeping `ScannedMedia.id` as an opaque UUID

## Deviations from plan
- The fake pipeline exposes a `cancel()` entry point in addition to `scan(...)` because the Phase 5 guidance explicitly required clean user-driven cancellation and later real detectors will need the same control surface.
- The default authentic path renders the softer `Looks authentic` variant, while `_authentic_c2pa` forces `Verified authentic`, so both green states can be exercised before the real provenance layer exists.

## Pitfalls encountered
- No real detection, provenance parsing, watermarking, heatmap generation, or timeline analysis was implemented; all outputs remain deterministic stubs by design.
- Confidence is always represented as a range, not a point estimate, and uncertain ranges always straddle the 50% threshold.
- The scan contract uses `Flow<ScanStage>` plus sealed classes, not a one-shot suspend API or enum-based stage model.
- The authentic verdict styling keeps the softer green default and reserves the stronger verified state for `_authentic_c2pa`.

## Open questions for human
- None.

## Ready for Phase 6?
Yes - Phase 5 acceptance criteria are met, and the fake pipeline contracts are ready for real provenance and detector implementations after human sign-off.

## Demo instructions
1. Set Java for the shell: `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'`
2. Run the local gates: `.\gradlew.bat assembleDebug test precommitCheck --warning-mode all`
3. Boot `veritas_api35` and run the targeted connected suite: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase4MediaIngestionTest,com.veritas.app.Phase5DetectionFlowTest" --warning-mode all`
4. Install the debug app: `.\gradlew.bat :app:installDebug`
5. Launch the home shell: `.\.android-sdk\platform-tools\adb.exe shell am start -n com.veritas.app.debug/com.veritas.app.MainActivity`
6. Share or pick files whose names contain these markers:
   `demo_authentic.mp4` -> `Looks authentic`
   `demo_authentic_c2pa.mp4` -> `Verified authentic`
   `demo_uncertain.mp4` -> `Uncertain`
   `demo_synthetic.mp4` -> synthetic verdict
7. From the synthetic verdict, tap `See details`, confirm the stub heatmap and reason list appear, tap a reason chip to open the detail sheet, dismiss it, go back to the forensic view, then back to the verdict, then finish the flow.
