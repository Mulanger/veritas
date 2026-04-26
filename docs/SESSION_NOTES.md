# Session Notes

## Date
2026-04-26

## Branch
`main`

## Current Status
- Phase 7 is complete and merged.
- Ready for Phase 8 per `docs/07_PHASE_8_PLAN.md`.
- Phase 7 image detector integration is implemented and verified on top of verified Phase 6.
- `data-detection-ml` and `feature-detect-image` are wired into the app.
- The signed Deep-Fake-Detector-v2 TFLite asset loads and runs on `veritas_play_avd`.
- The 500-image golden-set eval completed on-device and passed detector acceptance after uncertainty recalibration:
  - accuracy `0.896`
  - FPR `0.064`
  - FNR `0.144`
  - uncertain rate `0.190`
  - p95 latency `1075 ms`
- Physical-device GPU delegate verification passed on Google Pixel 8, Android API 36, with `FallbackLevel.GPU`.

## Fixes Applied
- Added `data-detection-ml` for Play-services LiteRT runtime initialization, model verification, delegate selection, runners, preprocessing, and calibration.
- Added `feature-detect-image` with Deep-Fake-Detector-v2 model wrapper, EXIF/ELA/JPEG forensic signals, and hand-tuned fusion.
- Extended `DetectorResult` with confidence interval, subscores, uncertainty reasons, and fallback level.
- Integrated image detection into `ProvenancePipeline` after C2PA/SynthID checks.
- Converted the ONNX model to a pseudo-lowered dynamic-range TFLite artifact under the Phase 7 hard cap.
- Signed the model with Ed25519 and committed only the public key/signature/model asset.
- Patched runtime fallback so missing GPU delegate support falls back to CPU-only initialization.
- Switched on-device Ed25519 verification to Bouncy Castle because Android API 34 lacks `Ed25519` `KeyFactory`.
- Added the golden-set eval harness and generated a 500-image local golden set using env-only Kaggle credentials.
- Fixed full-resolution image decode causing emulator low-memory kills during eval.
- Fixed the model output label order so `logit[0] - logit[1]` maps to synthetic probability.
- Recalibrated uncertainty gating: CPU fallback is diagnostic-only, and severe JPEG compression now uses `0.088 bytes/pixel`, producing `HEAVY_COMPRESSION=20/500` and `uncertainRate=0.190`.
- Fixed the Play-services LiteRT GPU path by replacing the instantiated `GpuDelegate` with `GpuDelegateFactory` via `InterpreterApi.Options.addDelegateFactory(...)`; the Pixel 8 smoke test now reports `fallback=GPU`.
- Recorded APK size as a Phase 13 delivery issue: `app-debug.apk` is `183.25 MB`, and Play Asset Delivery must bring the installed APK below `100 MB`.

## Verification Commands
- `./gradlew :feature-detect-image:compileDebugKotlin :feature-detect-image:compileDebugAndroidTestKotlin`
- `./gradlew :feature-detect-image:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.feature.detect.image.Phase7GoldenSetEvalTest" --info`
- `./gradlew :app:connectedDebugAndroidTest "-Pandroid.injected.device.serial=45131FDJH0015H" "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase7ImageDetectorInstrumentedTest" --info`
- `./gradlew :data-detection-ml:compileDebugKotlin :app:compileDebugKotlin :app:assembleDebug precommitCheck`

## Open Questions
- None for Phase 7 sign-off.

## Phase 8 Follow-Up
- Phase 7 golden-set eval numbers were captured on emulator `CPU_XNNPACK` because the Google Play emulator image lacks the GPU delegate. The Pixel 8 smoke test verified `FallbackLevel.GPU`, but there is not yet a full GPU-baseline golden-set eval. During Phase 8's first instrumented test session, rerun the Phase 7 golden-set eval on the Pixel 8 to capture honest GPU baseline latency numbers.
