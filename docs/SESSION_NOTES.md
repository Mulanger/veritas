# Session Notes

## Date
2026-04-26

## Branch
`main`

## Current Status
- Phase 8 is in progress on branch `phase/8-audio-detector`.
- Phase 8 Step 1 is complete and committed as `6dd2425`.
- Phase 8 is currently blocked in Step 2 by model conversion size / alternate-converter availability.
- Resume point: resolve `as1605/Deepfake-audio-detection-V2` conversion to a signed TFLite artifact <= 120 MB, or get human approval to change the Phase 8 model decision.
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
- Added `feature-detect-audio` and wired it into Gradle/app dependencies.
- Added `data-detection-ml` `AudioPreprocessor` for waveform length normalization and tensor packing.
- Implemented Phase 8 Step 1 decode plumbing:
  - `AudioDecoder` using `MediaExtractor` + sync `MediaCodec`, with raw PCM fast-path for WAV.
  - `PcmConverter` for PCM 8-bit / 16-bit / float to mono 16 kHz `FloatArray`.
  - 10 generated androidTest audio fixtures covering MP3, AAC, M4A, WAV, OGG, OPUS, varied sample rates, and mono/stereo layouts.
- Added `tools/model-conversion/convert_audio_detector.py`, pinned to current HF revision `3aeb18add053e945dc69025147afab0d70fa0188` and `onnx/model.onnx`.
- Logged D-043 for the ONNX path difference and conversion blocker.
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
- `./gradlew :data-detection-ml:testDebugUnitTest :feature-detect-audio:testDebugUnitTest :feature-detect-audio:compileDebugKotlin`
- `./gradlew :feature-detect-audio:compileDebugAndroidTestKotlin`
- `./gradlew :feature-detect-audio:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.feature.detect.audio.decode.AudioDecoderInstrumentedTest"`
- `py -3 tools/model-conversion/convert_audio_detector.py --overwrite` failed as expected because the smallest TFLite output was `189459212` bytes, above the 120 MB Phase 8 hard cap.
- `./gradlew :feature-detect-image:compileDebugKotlin :feature-detect-image:compileDebugAndroidTestKotlin`
- `./gradlew :feature-detect-image:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.feature.detect.image.Phase7GoldenSetEvalTest" --info`
- `./gradlew :app:connectedDebugAndroidTest "-Pandroid.injected.device.serial=45131FDJH0015H" "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase7ImageDetectorInstrumentedTest" --info`
- `./gradlew :data-detection-ml:compileDebugKotlin :app:compileDebugKotlin :app:assembleDebug precommitCheck`

## Open Questions
- Phase 8 needs human direction before proceeding past Step 2:
  - Use a Linux conversion host to retry LiteRT Torch / ai-edge-torch, since this Windows Python 3.12 environment cannot install `litert-converter==0.1.*`.
  - Or approve a smaller Apache 2.0 audio model, because onnx2tf generated artifacts are all above the 120 MB hard cap.

## Phase 8 Follow-Up
- Phase 7 golden-set eval numbers were captured on emulator `CPU_XNNPACK` because the Google Play emulator image lacks the GPU delegate. The Pixel 8 smoke test verified `FallbackLevel.GPU`, but there is not yet a full GPU-baseline golden-set eval. During Phase 8's first instrumented test session, rerun the Phase 7 golden-set eval on the Pixel 8 to capture honest GPU baseline latency numbers.
