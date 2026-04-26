# Session Notes

## Date
2026-04-26

## Branch
`phase/8-audio-detector`

## Current Status
- Phase 8 is complete.
- Phase 8 completion report: `docs/phase_reports/phase_8.md`.
- Raw Phase 8 eval results: `docs/phase_reports/phase_8_eval_results.json`.
- Do not move to Phase 9 until the human checkpoint accepts Phase 8.

## Phase 8 Outcome
- Added standalone audio detection through `feature-detect-audio`.
- Integrated audio verdicts into `data-detection` `ProvenancePipeline`.
- Shipped signed audio TFLite asset:
  - `data-detection-ml/src/main/assets/models/audio/deepfake-audio-detector-hemgg-wi8.tflite`
  - size `96190928` bytes
  - SHA-256 `3046375262e631f25eb801c3480306235731f9bd95cedcf450663a31116f0b4c`
  - Ed25519 signature alongside the model
- Final model: `Hemgg/Deepfake-audio-detection`, wav2vec2-base, Apache 2.0.
- Quantization: ai-edge-quantizer weight-only INT8 with internal TFLite buffers.
- Runtime path: Play Services LiteRT CPU XNNPACK for this model.

## Eval Results
- 500-clip on-device eval passed:
  - accuracy `0.880`
  - FPR `0.088`
  - FNR `0.056`
  - uncertain rate `0.048`
  - p50 latency `2241 ms`
  - p95 latency `2877 ms`
- Weakest bucket: Opus, accuracy `0.758`, uncertain rate `0.0968`.

## Key Decisions Logged
- D-045: Phase 8 audio model ships Hemgg wav2vec2-base weight-only INT8.
- D-046: Force internal TFLite buffers for ai-edge audio quantization.
- D-047: Phase 8 wav2vec2 audio runs CPU XNNPACK only.
- D-048: Phase 8 hand-tuned audio fusion and golden-set sources.

## Verification Commands
- `py -3 -m py_compile tools/model-conversion/convert_audio_detector.py tools/eval-datasets/build_audio_golden_set.py`
- `py -3 tools/eval-datasets/build_audio_golden_set.py`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-audio:compileDebugAndroidTestKotlin`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-audio:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.feature.detect.audio.AudioDetectorInstrumentedTest"`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-audio:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.feature.detect.audio.AudioEvalHarness"`

## Known Follow-Up
- Phase 13 must move bundled models out of the base APK using signed model delivery / Play Asset Delivery per D-041.
- Audio retraining/eval workstream should add ASVspoof, WaveFake, current commercial TTS, and more Opus-heavy samples.
