# Session Notes

## Date
2026-04-26

## Branch
`main`

## Current Status
- Phase 8 is in progress on branch `phase/8-audio-detector`.
- Phase 8 Step 1 is complete and committed as `6dd2425`.
- Phase 8 is currently blocked in Step 2 by audio TFLite conversion parity/runtime validity.
- Resume point: choose a Phase 8 audio model/conversion path that produces a runtime-valid TFLite artifact <= 120 MB with MAE <= 5% vs ONNX, then continue to Step 3 signing.
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
- Added `tools/model-conversion/build_audio_calibration_set.py` and `tools/model-conversion/audit_audio_tflite.py` for repeatable Phase 8 calibration/provenance/parity checks.
- Built a 220-row real calibration tensor at `tools/model-conversion/work/audio_real_calibration_0_1.npy` with 10 fixtures, 120 real speech clips, and 90 Windows SAPI TTS synthetic clips.
- Retried `as1605/Deepfake-audio-detection-V2` full integer quantization with real calibration and int8 input/output; output stayed oversized (`365701120` bytes) and was not invokable due unresolved `ONNX_CONV`.
- Evaluated Apache 2.0 wav2vec2-base fallback `Hemgg/Deepfake-audio-detection`; dynamic-range `-rtpo erf` TFLite reached `96194440` bytes but failed parity (`17.23%` MAE even after correcting observed output-order reversal), so it was not accepted for signing.
- Logged D-044 for the calibrated quantization retry and fallback-model result.
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
- `py -3 tools/model-conversion/build_audio_calibration_set.py --target-count 220 --common-voice-count 30 --real-count 120 --synthetic-count 90`
- `py -3 -m onnx2tf -i tools/model-conversion/work/audio-hf/onnx/model.onnx -o tools/model-conversion/work/audio-tflite-real-int8 -b 1 -ois input_values:1,80000 -oiqt -cind input_values tools/model-conversion/work/audio_real_calibration_0_1.npy "[0.5]" "[0.5]" -iqd int8 -oqd int8`
- `optimum-cli export onnx -m Hemgg/Deepfake-audio-detection --task audio-classification --opset 14 --audio_sequence_length 80000 --batch_size 1 --no-dynamic-axes --monolith tools/model-conversion/work/audio-base-hemgg-onnx`
- `py -3 -m onnx2tf -i tools/model-conversion/work/audio-base-hemgg-onnx/model.onnx -o tools/model-conversion/work/audio-base-hemgg-tflite-tf-int8-rtpo-erf -tb tf_converter -b 1 -ois input_values:1,80000 -rtpo erf -oiqt -cind input_values tools/model-conversion/work/audio_real_calibration_0_1.npy "[0.5]" "[0.5]" -iqd int8 -oqd int8`
- `py -3 tools/model-conversion/audit_audio_tflite.py --onnx tools/model-conversion/work/audio-base-hemgg-onnx/model.onnx --tflite tools/model-conversion/work/audio-base-hemgg-tflite-tf-int8-rtpo-erf/model_dynamic_range_quant.tflite --samples 20 --report tools/model-conversion/work/audio_base_hemgg_dynamic_rtpo_erf_audit.json`
- `./gradlew :feature-detect-image:compileDebugKotlin :feature-detect-image:compileDebugAndroidTestKotlin`
- `./gradlew :feature-detect-image:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.feature.detect.image.Phase7GoldenSetEvalTest" --info`
- `./gradlew :app:connectedDebugAndroidTest "-Pandroid.injected.device.serial=45131FDJH0015H" "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase7ImageDetectorInstrumentedTest" --info`
- `./gradlew :data-detection-ml:compileDebugKotlin :app:compileDebugKotlin :app:assembleDebug precommitCheck`

## Open Questions
- Phase 8 needs human direction before proceeding past Step 2:
  - Hemgg wav2vec2-base dynamic-range TFLite is small enough but parity is too high (`17.23%` MAE after output-order correction).
  - Full INT8 wav2vec2 conversion on Windows either stays oversized, requires unsupported/custom ops, or fails runtime invocation.
  - Next practical path is likely a smaller non-wav2vec2 Apache 2.0 audio detector or a known-good conversion recipe that preserves GELU/Erf parity without Select TF Ops.

## Phase 8 Follow-Up
- Phase 7 golden-set eval numbers were captured on emulator `CPU_XNNPACK` because the Google Play emulator image lacks the GPU delegate. The Pixel 8 smoke test verified `FallbackLevel.GPU`, but there is not yet a full GPU-baseline golden-set eval. During Phase 8's first instrumented test session, rerun the Phase 7 golden-set eval on the Pixel 8 to capture honest GPU baseline latency numbers.
