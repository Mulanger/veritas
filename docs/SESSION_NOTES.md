# Session Notes

## Date
2026-04-26

## Branch
`phase/9-video-detector`

## Current Status
- Phase 9 video detector code is implemented and Pixel 8 verified.
- Completion report: `docs/phase_reports/phase_9.md`.
- Raw Phase 9 eval results: `docs/phase_reports/phase_9_eval_results.json`.

## Phase 9 Outcome So Far
- Added `feature-detect-video`.
- Reused the Phase 7 image detector for spatial frame scoring.
- Added signed MoViNet-A0-Stream INT8 temporal model:
  - size `3276048` bytes
  - SHA-256 `6125b36e2485eeb5cc1fd6206cf5e5d70235593fbc84610d1dce97fe3aa9c2ac`
- Added signed MediaPipe BlazeFace short-range model:
  - size `229746` bytes
  - SHA-256 `b4578f35940bf5a1a655214a1cce5cab13eba73c1297cd78e1a04c2380b0152f`
- Integrated video verdicts into `data-detection` `ProvenancePipeline`.
- Added video golden-set builder and 200-video static-derived manifest/eval result.
- Added unit and instrumented smoke coverage for sampling, fusion, drift, decode fixtures, MoViNet invocation, face no-face handling, and full detector contract.
- Pixel 8 eval passed: accuracy `0.905`, pipeline accuracy `0.750`, FPR `0.150`, FNR `0.040`, uncertain rate `0.220`, p95 `3074 ms`, all video interpreters `GPU`.
- Important limitation for Phase 10+: v1 video detection is not validated true temporal deepfake detection. The eval set is static-image-derived, so current quality evidence is mostly Phase 7 image detection applied to sampled frames. True face-swap/full-frame/lip-sync video eval belongs to the v1.5 retraining/eval workstream.

## Key Decisions Logged
- D-049: rPPG deferred to v1.5.
- D-050: MoViNet A0 stream INT8 v1 selected for temporal drift.
- D-051: latency-constrained frame sampling and MoViNet preprocessing policy.
- D-052: fixed video fusion, false-positive guard band, and static-derived local golden set.
- D-053: initial retriever-backed decode replaced with MediaCodec after Pixel latency evidence.
- D-054: evidence required before substituting easier implementations for load-bearing phase requirements.
- D-055: v1.5 priority is a proper video deepfake eval set and retraining/eval loop.

## Verification Commands
- `py -3 tools/eval-datasets/build_video_golden_set.py --target-total 200 --overwrite`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-video:testDebugUnitTest`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-video:compileDebugAndroidTestKotlin`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :data-detection:compileDebugKotlin :app:compileDebugKotlin`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-video:connectedDebugAndroidTest`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase7ImageDetectorInstrumentedTest'`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-video:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.feature.detect.video.Phase9GoldenSetEvalTest'`

## Known Follow-Up
- Add true video-family datasets (FaceForensics++/DFDC/DF40-style face-swap, hand-collected Sora/Runway/Veo full-frame generation, and lip-sync clips) to the v1.5 retraining/eval workstream before making production-grade video deepfake detection claims.
- Phase 13 must move bundled models out of the base APK using signed model delivery / Play Asset Delivery per D-041.
