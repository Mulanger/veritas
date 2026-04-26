# Veritas — Phase 8 Execution Plan
**Version:** 1.0
**Companion to:** `01_ARCHITECTURE.md`, `02_VISUAL_SPEC.md`, `04_DATA_CONTRACTS.md`, `05_GLOSSARY_AND_DECISIONS.md`
**Purpose:** Execution blueprint for the audio detector. Self-contained — the agent should NOT cross-reference `03_PHASE_PLAN.md` for this phase.
**Status:** The single source of truth for Phase 8. If anything here conflicts with `03_PHASE_PLAN.md` or `04_IMPLEMENTATION_PLAN_7_9.md`, **this document wins.**

---

## 0. Reading order for the agent

Before starting any code, read in this order:

1. **This entire document.** Do not skip sections.
2. `02_VISUAL_SPEC.md` §2.14, §2.15 (audio verdicts) and §7.2 (forensic audio view) — so you know what the downstream UI expects.
3. `04_DATA_CONTRACTS.md` — the `Verdict`, `DetectorResult`, `ConfidenceInterval`, `ReasonCode` definitions. Phase 7 added image-specific reason codes; Phase 8 adds audio ones.
4. `05_GLOSSARY_AND_DECISIONS.md` Part 2, specifically D-016 (LiteRT via Play Services, NNAPI forbidden), D-018 (training vs runtime separation), and any decisions logged during Phase 7.
5. `06_PHASE_7_PLAN.md` — read this. Phase 8 reuses the `data-detection-ml` infrastructure module that Phase 7 built. Understanding what's already there saves you from reinventing it.
6. `01_ARCHITECTURE.md` §6.3 (audio ensemble) and §8 (performance constraints).
7. The Phase 7 completion report (`docs/phase_reports/phase_7.md`) — confirms what shared infrastructure exists. Especially the `LiteRtRuntime`, `DelegateChain`, `ModelAsset`, `ModelRunner`, `Calibrator`, and `HandTunedFusion` classes.

You do NOT need to read `03_PHASE_PLAN.md` Phase 8 or `04_IMPLEMENTATION_PLAN_7_9.md` Phase 8. **This document replaces both.**

---

## 1. Goal

Given an audio file, produce a calibrated `DetectorResult` in under 3 seconds for a 30-second clip on a mid-range 2024 Snapdragon device, distinguishing real human speech from AI-generated speech (ElevenLabs, OpenAI TTS, Bark, Tortoise, neural vocoder outputs from various sources).

Phase 8 is **product-functional, not production-calibrated**. We integrate an existing open-source detector and wrap it in a thin Kotlin pipeline. Real-world accuracy will be imperfect — voice cloning is a moving target and public detectors lag the latest TTS systems. The UNCERTAIN verdict absorbs the gap. Retraining for quality is a separate workstream.

---

## 2. Strategy — "reuse, don't train"

v1 integrates an existing open-source audio deepfake detector. **No model training in this phase.** The model ships as shipped. Quality improvements via fine-tuning are explicitly deferred to the post-product-complete retraining work.

Single primary model, Apache 2.0 license, ONNX export already exists. We do NOT ship multiple ML detectors in v1 — one detector plus codec/duration sanity checks is enough to demonstrate the full app flow.

---

## 3. Chosen model — final decision

**Runtime model (ships in APK):** `as1605/Deepfake-audio-detection-V2`

- **License:** Apache 2.0 ✓ (verified)
- **Architecture:** wav2vec2 base, fine-tuned for binary fake/real classification
- **Base model:** `motheecreator/Deepfake-audio-detection` (also Apache 2.0, this is a fine-tune of that)
- **Input:** 16 kHz mono PCM, raw waveform (wav2vec2 takes raw audio, not spectrograms — important for preprocessing simplicity)
- **Output:** binary classifier → logits → softmax → `["fake", "real"]` probability pair
- **Reported accuracy:** 99.7% on the model's own evaluation set. Real-world accuracy on modern voice clones will be substantially lower — accept this. Modern ElevenLabs and equivalent will probably evade detection more often than not. The UNCERTAIN verdict handles this honestly.
- **Training data:** the audiofolder dataset. Older than current state-of-the-art TTS. Acknowledge via wider UNCERTAIN bands and clear per-source breakdown in eval.
- **Size:** the ONNX export is ~360 MB FP32. After INT8 quantization, target ≤ 100 MB. **Hard cap: 120 MB.** If the converted file exceeds 120 MB, escalate. If we can't get under 120 MB, we may need to switch to a smaller spectrogram-based architecture instead.
- **ONNX source:** `as1605/Deepfake-audio-detection-V2` on Hugging Face has `model/model.onnx` already in the repo. Verify checksum matches before converting.

**What we are NOT shipping in v1:**

- AASIST3 — license is CC-BY-NC-ND, incompatible with our Apache 2.0 distribution
- A second ML detector — one detector plus heuristic forensics is enough for product-functional
- A separate prosody model — this would have been valuable, but Apache 2.0 prosody-only detectors aren't readily available; defer to retraining workstream

**Heuristic secondary signals (Kotlin-only, no ML models):**

- Audio duration sanity — too short clips (< 1 second) trigger UNCERTAIN
- Sample-rate mismatch detection (e.g., file claims 44.1 kHz but contains content that's clearly upsampled)
- Codec fingerprint — neural vocoder outputs often have signature spectral discontinuities at codec boundaries
- Mono/stereo and bit-depth checks for plausibility (real phone audio has predictable patterns)

These are weak priors that nudge the final fused score and contribute to uncertainty gating. They are NOT verdicts on their own.

---

## 4. Module structure

### 4.1 Reuse `data-detection-ml` (already exists from Phase 7)

Phase 7 built the shared ML runtime. **Do not duplicate or re-implement it.** What you reuse:

- `LiteRtRuntime` — singleton interpreter pool
- `DelegateChain` — GPU delegate → XNNPACK CPU fallback
- `ModelAsset` — signed .tflite loading with Ed25519 verification
- `ModelRunner` — generic `run(input, output)` over LiteRT
- `Calibrator` — score → ConfidenceInterval mapping
- `HandTunedFusion` — utility for hand-weighted fusion across sub-scores

What you **add** to `data-detection-ml`:

```
data-detection-ml/
└── preprocessing/
    └── AudioPreprocessor.kt          # NEW — mono 16kHz resample, length normalization
```

This is the only addition to `data-detection-ml` required by this phase. Resist the urge to add audio-specific logic here — keep this module media-type-agnostic.

### 4.2 `feature-detect-audio` (new, audio-specific)

Depends on `data-detection-ml` and `domain-detection`.

```
feature-detect-audio/
├── domain/
│   ├── AudioDetector.kt              # implements Detector<AudioInput>
│   └── AudioReasonCodes.kt           # AUD_SYNTHETIC_VOICE_HIGH, AUD_TOO_SHORT, etc.
├── decode/
│   ├── AudioDecoder.kt               # MediaExtractor + MediaCodec wrapper
│   └── PcmConverter.kt               # decoded PCM → normalized float array for model
├── model/
│   ├── DeepfakeAudioDetectorModel.kt # thin wrapper over ModelRunner
│   └── Wav2Vec2Preprocessor.kt       # raw audio → model tensor
├── forensics/
│   ├── DurationSanity.kt             # length-based UNCERTAIN gating
│   ├── CodecFingerprint.kt           # bitrate/sample-rate plausibility
│   └── AudioForensicSignals.kt       # data class with scalar outputs
├── fusion/
│   └── AudioFusion.kt                # hand-tuned weighted average
└── tests/
    └── AudioDetectorInstrumentedTest.kt
```

---

## 5. Runtime and hardware acceleration

**Same constraints as Phase 7.** Use LiteRT via Google Play Services. GPU delegate primary, XNNPACK CPU fallback. **NNAPI is forbidden per D-016.** No new dependencies needed beyond what Phase 7 added — `data-detection-ml` already has everything.

**One audio-specific note:** wav2vec2 models can be GPU-bottlenecked on smaller delegates. If you observe the GPU delegate being slower than CPU on this specific model on real devices, that's a known issue with transformer architectures on mobile GPU runtimes — fall back to XNNPACK CPU for this model and document as a known per-model decision. Do this measurement during Step 12 (eval) and choose the faster delegate per device class.

**MediaPipe Tasks Audio (`com.google.mediapipe:tasks-audio`)** is available as a runtime wrapper but requires the .tflite model to be authored with proper `AudioProperties` metadata. Wav2Vec2 conversion via `ai-edge-torch` may or may not preserve this metadata cleanly. **Try LiteRT direct first.** If it works, do not add MediaPipe Tasks Audio as a dependency — it's not necessary. If for some reason you want to use MediaPipe Tasks Audio, the metadata authoring is non-trivial; budget extra time and document the decision.

---

## 6. The contract you must satisfy

Your `AudioDetector.analyze()` returns a `DetectorResult` per `04_DATA_CONTRACTS.md`. Same shape as Phase 7 with audio-specific reason codes:

```kotlin
data class DetectorResult(
    val score: Float,                    // 0.0 = authentic, 1.0 = synthetic
    val confidence: ConfidenceInterval,  // e.g. (0.65, 0.85)
    val reasonCodes: List<ReasonCode>,
    val subScores: Map<String, Float>,   // {"wav2vec2_model": 0.78, "codec": 0.4}
    val uncertainReasons: List<UncertainReason>,
    val latencyMs: Int,
    val fallbackUsed: FallbackLevel
)
```

**Constraints (same as Phase 7, listed for completeness):**
- Score clamped to `[0.02, 0.98]`. Never absolute certainty.
- Confidence interval upper bound ≤ 0.95.
- For UNCERTAIN-bound outputs, confidence interval MUST straddle the threshold.
- `subScores` keys for audio: use exactly `"wav2vec2_model"` and `"codec"`. The forensic UI references these.

---

## 7. Step-by-step execution plan

Execute in order. Each step should compile and pass its own tests before moving to the next.

### Step 1 — Audio decode plumbing (4–6 hours)

This is the riskiest step in Phase 8. MediaCodec is fiddly and the source of more Android bugs than any other API. Get this right before touching ML.

Tasks:
1. Implement `AudioDecoder.kt`:
   - Use `MediaExtractor` to identify audio track, codec, sample rate, channel count, bit depth.
   - Use `MediaCodec` to decode to raw PCM.
   - Handle MP3, AAC, M4A, WAV, OGG, OPUS. Verify each in tests.
   - Return a structured result: PCM byte array, sample rate, channel count, duration in ms.
2. Implement `PcmConverter.kt`:
   - Resample to 16 kHz mono (downmix stereo if needed).
   - Convert to normalized `FloatArray` in range `[-1.0, 1.0]`.
   - Pad or truncate to a fixed length matching the model's expected input. Wav2vec2 typically expects 16000 × 5 = 80000 samples for a 5-second clip; verify against the actual model's input shape from its ONNX metadata.
3. Test against ~10 sample audio files of varied formats. Each must decode without error.

**Pitfalls specific to this step:**
- MediaCodec async vs sync mode — use sync mode for simplicity. Async adds threading complexity you don't need.
- Some codecs report incorrect duration in MediaExtractor headers. Don't trust the header — count actual decoded samples.
- AAC files sometimes have priming/trailing silence. Don't attempt to strip it in v1; the model is robust enough to handle small edge artifacts.
- **Use `Dispatchers.Default` for decoding, not `Dispatchers.IO`.** MediaCodec is CPU-bound, not IO-bound.

Acceptance: 10 sample files of varied formats and lengths decode to correct sample count and produce valid PCM.

### Step 2 — Model conversion pipeline (ONNX → TFLite) (4–6 hours)

Off-device. Write `tools/model-conversion/convert_audio_detector.py`:

1. Download `as1605/Deepfake-audio-detection-V2` (the `model/model.onnx` file specifically).
2. Convert ONNX → TFLite using `ai-edge-torch` or `onnx2tf`. wav2vec2 has some operators that don't always convert cleanly — if you hit unsupported ops, document the specific op and try the alternative converter before escalating.
3. INT8 quantize with a representative dataset of 50–100 audio samples (mix of real speech and synthetic). Use real audio from the golden set you'll build in Step 12.
4. Verify TFLite output parity against the original ONNX on 20 test audio clips. Acceptance: MAE < 3% on softmax output. Audio models tolerate slightly higher MAE than image models because temporal averaging is more forgiving — but if MAE > 5%, investigate before proceeding.
5. Save final `.tflite` + checksum + signed `.sig` in `data-detection-ml/src/main/assets/models/audio/`.
6. Update `MODEL_MANIFEST.md` with audio model entry: source ONNX SHA, license (Apache 2.0), quantization mode, commit date, final size.
7. **Hard cap: 120 MB.** If above 120 MB and INT8 quantization can't get smaller, escalate.

Acceptance: `.tflite` file exists, parity verified, MODEL_MANIFEST.md updated.

### Step 3 — Model signing and verification (1–2 hours)

Reuse the Ed25519 signing from Phase 7. Same keypair, same `ModelAsset.load()` verification path. Sign the new audio `.tflite` with the existing private key (your Phase 7 dev note has where this lives).

Acceptance: audio model loads through the same signed-asset pipeline as the image model. Tampered audio model fails verification cleanly.

### Step 4 — Wav2Vec2 preprocessor (2 hours)

The model takes raw waveform input, not mel spectrograms. This makes preprocessing simpler than spectrogram-based architectures. Tasks:

1. Take output of `PcmConverter` (16 kHz mono, FloatArray in [-1.0, 1.0]).
2. Pad/truncate to model's expected sample count (verify from ONNX input shape).
3. Pack into `ByteBuffer` matching the model's input tensor spec (typically `[1, N]` for batch=1, N samples).
4. Test: preprocess a known audio file, compare resulting tensor to Python reference output. MAE should be essentially zero.

### Step 5 — First end-to-end inference (2 hours)

Wire `DeepfakeAudioDetectorModel.kt` through `ModelRunner` → single inference returns logits. Apply softmax, take the "fake" probability as the score. Wrap in a minimal `DetectorResult` with `subScores["wav2vec2_model"] = score`.

Minimal `AudioDetector.analyze()` at this point:
- Decode audio
- Run preprocessor
- Run model inference
- Return `DetectorResult` with single score, no forensics, no fusion

**At this point the audio detector works end-to-end.** Run on ~5 hand-picked clips (mix of real speech and known TTS outputs). Verify direction: real trends low, synthetic trends high.

**This is the minimum viable Phase 8.** If schedule pressure is real, you can pause here and move to Phase 9. Everything below improves quality.

### Step 6 — Duration and format sanity (2 hours)

Implement `DurationSanity.kt`. Emit signals based on input properties:

- `tooShort: Boolean` — true if duration < 1.0 seconds. Wav2Vec2 needs reasonable context.
- `tooLong: Boolean` — true if duration > 60 seconds. We process in chunks beyond this; for v1 just trigger UNCERTAIN.
- `lowSampleRate: Boolean` — true if source sample rate < 8 kHz (telephony quality and below — model accuracy degrades here).
- `monoPlausible: Boolean` — most real human voice recordings are mono or have channels with very high correlation. Stereo with uncorrelated channels suggests post-production / TTS pipeline; weak signal.

These don't independently produce a verdict. They feed UNCERTAIN gating.

### Step 7 — Codec fingerprint (3 hours)

Implement `CodecFingerprint.kt`:

- Read codec (from `MediaExtractor.getTrackFormat`).
- Read bitrate (where available).
- Compare against known codec/bitrate combinations expected for natural sources:
  - Phone calls: AMR-NB, AMR-WB, Opus 8-32 kbps
  - Voice notes: AAC 64-128 kbps, Opus 16-32 kbps
  - Podcasts: MP3 128+ kbps, AAC 96+ kbps
  - Music: MP3 192+ kbps, AAC 128+ kbps, FLAC, ALAC
- Flag mismatches. E.g., 16 kbps Opus content with very high-frequency energy patterns is suspicious — Opus at low bitrate hard-cuts above ~6 kHz, so high-frequency energy in supposedly low-bitrate audio is a signal.

Output a single scalar `codecPlausibilityScore: Float` in `[0, 1]` where lower is more suspicious.

This is heuristic and weak. Weight it low in fusion. Document false-positive sources clearly (e.g., "studio-recorded podcast in high-bitrate Opus is uncommon but legitimate").

### Step 8 — Hand-tuned fusion (2 hours)

```kotlin
object AudioFusion {
    fun fuse(
        wav2vec2Score: Float,            // 0-1, model's "fake" probability
        codecPlausibility: Float,        // 1.0 = plausible, 0.0 = suspicious
    ): Float {
        val codecSignal = 1f - codecPlausibility  // "suspicious codec" direction

        val fused = (
            0.92f * wav2vec2Score +
            0.08f * codecSignal
        ).coerceIn(0.02f, 0.98f)

        return fused
    }
}
```

Wav2Vec2 model dominates. Codec is a tiebreaker. Document weights in decision log. A learned fusion replaces this in retraining.

### Step 9 — Calibration to confidence interval (2 hours)

Same heuristic approach as Phase 7's calibrator. Reuse the `Calibrator` from `data-detection-ml`:

```kotlin
fun scoreToInterval(fused: Float): ConfidenceInterval {
    val width = when {
        fused < 0.25f -> 0.20f
        fused < 0.40f -> 0.30f
        fused < 0.60f -> 0.32f       // wider uncertain band for audio
        fused < 0.75f -> 0.30f
        else -> 0.20f
    }
    val lo = (fused - width / 2).coerceIn(0.02f, 0.98f)
    val hi = (fused + width / 2).coerceIn(0.02f, 0.95f)
    return ConfidenceInterval(lo, hi)
}
```

Audio gets wider uncertain bands than image because public detectors generalize worse to modern TTS. Real isotonic regression replaces this in retraining.

### Step 10 — Uncertainty gating (2 hours)

Emit `UncertainReason` entries:

```kotlin
if (durationMs < 1000) add(TOO_SHORT)
if (sampleRate < 8000) add(LOW_SAMPLE_RATE)
if (durationMs > 60_000) add(TOO_LONG_PROCESSED_TRUNCATED)
if (fused > 0.35f && fused < 0.65f) add(LOW_CONFIDENCE_RANGE)
if (fallbackUsed == FallbackLevel.CPU_XNNPACK) add(CPU_FALLBACK)
```

The fusion engine uses these to decide UNCERTAIN bucketing. Surface honest signals; don't pre-decide.

### Step 11 — Reason codes (2 hours)

Add to `AudioReasonCodes.kt` and author content in `04_DATA_CONTRACTS.md`:

- `AUD_SYNTHETIC_VOICE_HIGH` if `wav2vec2Score > 0.70` — "AI voice detection model flagged synthetic patterns"
- `AUD_CODEC_MISMATCH` if `codecPlausibility < 0.4` — "Audio compression doesn't match its stated source type"
- `AUD_TOO_SHORT` if duration < 1s — "Audio too short for reliable analysis"
- `AUD_LOW_QUALITY` if `sampleRate < 8000` — "Sample rate too low for reliable analysis"
- `AUD_NATURAL_PROSODY` if `wav2vec2Score < 0.30` — "No artifacts of synthetic speech detected"

### Step 12 — Golden-set eval harness (5–7 hours)

Same pattern as Phase 7. Agent-built, agent-executed.

#### 12.1 — Dataset acquisition (agent-scripted)

Approved sources, all permissive:

**Real human speech:**
- LibriSpeech `dev-clean` subset — public, CC-BY 4.0. Pull ~150 utterances via `datasets` library or direct download.
- Common Voice English subset — CC-0. Pull ~50 utterances for diversity (LibriSpeech is audiobook-heavy; Common Voice has more accent variety).

**Synthetic speech:**
- ASVspoof 2019 LA evaluation set — research-licensed but redistribution-permitted for academic/research, **and importantly: only used in our test pipeline, not bundled in production APK** (same pattern as image fixtures).
- WaveFake or similar public TTS-output dataset — verify license per source.
- Generate ~50 fresh samples from current TTS systems if possible: ElevenLabs free tier, Coqui TTS open models, Bark (Apache 2.0). This catches cases the older datasets miss.

Target distribution: ~250 real, ~250 synthetic, ~500 total. Stratify by:
- Source TTS system (older GAN, modern neural, ElevenLabs-class)
- Codec (raw WAV, MP3, AAC, Opus)
- Sample rate (16 kHz, 22.05 kHz, 44.1 kHz, 48 kHz)
- Duration buckets (1-5s, 5-15s, 15-60s)

Agent writes `tools/eval-datasets/build_audio_golden_set.py`:
1. Pull from each source.
2. Stratify and sample.
3. Optionally re-encode some samples to common codecs (simulating real-world distribution).
4. Copy into `feature-detect-audio/src/androidTest/assets/golden-audio/real/` and `.../synthetic/`.
5. Write `MANIFEST.csv` with `filename, label, source_dataset, tts_system_or_speaker, codec, sample_rate, duration_ms`.
6. Print summary table.

Commit MANIFEST.csv. Audio files are gitignored — pulled on demand.

#### 12.2 — Eval harness (agent-built, on-device)

`AudioEvalHarness` is an instrumented test in `feature-detect-audio/src/androidTest/`. Iterates ~500-clip set, runs each through full `AudioDetector`, aggregates:

- Overall accuracy
- FPR on real speech
- FNR on synthetic
- Uncertain rate
- **Per-TTS-system accuracy** (key diagnostic — tells you which generators leak through)
- **Per-codec accuracy** (real audio is almost always recompressed; codec breakdown shows compression robustness)
- p50 and p95 latency

Writes results to `phase_8_eval_results.json` and prints a summary table.

#### 12.3 — Acceptance for product-functional mode

- Overall accuracy > 60% (relaxed below Phase 7's 65% — audio detection of modern TTS is genuinely harder than image detection of modern diffusion)
- FPR (real speech flagged synthetic) ≤ 18% (relaxed from image's 15% — same reason)
- p95 latency ≤ 3.5 seconds for clips up to 30 seconds

If overall accuracy is below 60%, escalate. Likely a conversion bug, preprocessing mismatch, or wiring issue. **Per-TTS-system breakdown is the most important output of this phase** — it directly informs the audio retraining workstream's priorities.

Do not tune fusion weights based on these numbers. Hand-tuned weights ship as-is. Tuning happens during retraining.

### Step 13 — Latency check (0–4 hours)

Profile on reference device. If p95 > 3.5s for 30s clips:
1. Verify chosen delegate (GPU vs CPU) is actually being used.
2. Check for unnecessary FloatArray copies in preprocessing.
3. Confirm INT8 quantization is applied to the full graph.
4. **Special case for wav2vec2:** the convolutional feature extractor often dominates inference time. If the bottleneck is there and the GPU delegate is slow on it, try CPU XNNPACK — sometimes faster.
5. Profile before optimizing.

Skip if already under budget.

---

## 8. Pipeline integration

The audio input path end-to-end:

```
Share intent (or file picker, or paste link)
  → IngestionLayer detects audio MIME type, decodes via MediaCodec (Phase 4 + Step 1)
  → PreFlight: C2PA check for audio (Phase 6 — works for MP4/M4A/WAV with C2PA manifests)
  → PreFlight: SynthID stub (Phase 6 — NoOp per D-034)
  → AudioDetector.analyze(audioInput, budget)
      → AudioDecoder → PcmConverter → 16kHz mono FloatArray
      → Wav2Vec2Preprocessor → DeepfakeAudioDetectorModel → wav2vec2Score
      → CodecFingerprint → codecPlausibility
      → DurationSanity → uncertain reasons
      → AudioFusion.fuse(...) → fused score
      → Calibrator → ConfidenceInterval
      → Build DetectorResult
  → FusionEngine combines with C2PA priors
  → Calibrator → Verdict bucket
  → UI renders (Phase 5 verdict screens, audio variant)
```

You implement the `AudioDetector` box. Decoding (Step 1) is in this phase but routes through the same ingestion layer Phase 4 built.

---

## 9. Testing requirements

**Unit tests (in `feature-detect-audio/src/test/`):**
- `AudioFusion.fuse()` returns expected values for hand-constructed inputs
- `AudioPreprocessor` produces correct length and amplitude range
- `Calibrator.scoreToInterval()` invariants (already covered by Phase 7 tests but re-verify)
- Uncertainty gating emits correct `UncertainReason` for edge cases
- `PcmConverter` resampling is mathematically correct (sinc interpolation or linear, document choice)

**Instrumented tests (in `feature-detect-audio/src/androidTest/`):**
- Decode samples in MP3, AAC, M4A, WAV, OGG, OPUS — each produces valid PCM
- Load a known real speech clip → score < 0.5
- Load a known TTS clip → score > 0.5
- Load a 0.5-second clip → `TOO_SHORT` in uncertain reasons
- Load a 90-second clip → handled gracefully (truncate or chunk; v1 truncate is OK)
- GPU delegate fail → clean fallback, `FallbackLevel.CPU_XNNPACK` set
- `AudioEvalHarness` runs against the 500-clip golden set, passes §7 Step 12 acceptance

**Integration test:**
- Share intent with a synthetic voice clip → full pipeline → synthetic verdict renders correctly

---

## 10. Known pitfalls (mandatory read)

**Do not use NNAPI.** Same rule as Phase 7. GPU delegate via Play Services or CPU XNNPACK only.

**Do not add a second ML model in v1.** A prosody-only model would help but isn't available with a clean Apache 2.0 license. Defer.

**MediaCodec quirks will eat your time.** Specifically:
- Channel-count assumptions break on weirdly-encoded files (mono encoded as 2-channel L=R)
- Some MP4 files have multiple audio tracks; pick the first audio track explicitly
- Sample rate from MediaExtractor is sometimes 0 for malformed headers; fall back to MediaCodec output format
- Don't assume MediaCodec gives you complete frames; you may need to buffer until a complete frame arrives

**Don't trust the model on heavily compressed audio.** Public deepfake audio detectors degrade fast on phone-quality recordings. Wider UNCERTAIN bands are the answer; do not silently raise thresholds to "look more accurate."

**Don't generate synthetic samples for the eval set with the model you're testing.** Sounds obvious but agents have done it before. Use real datasets for synthetic samples; if you're generating fresh ones, use TTS systems that are independent of the detector's training data.

**Don't iterate fusion weights endlessly.** One pass after eval. Move on. Retraining replaces this.

**Don't bundle the eval audio in `main/assets/`.** Same lesson as Phase 7's APK bloat — fixtures go in `androidTest/assets/`, never in `main/`.

**Wav2Vec2 conversion may produce unsupported ops.** If `ai-edge-torch` fails on a specific operator, try `onnx2tf` before assuming the model can't be converted. Document any custom conversion steps in the model manifest.

**Audio preprocessing must match training preprocessing exactly.** wav2vec2 expects specific normalization (zero-mean, unit-variance for the original training pipeline). If parity check shows MAE > 3%, this is the most likely cause — verify normalization matches the HuggingFace `Wav2Vec2FeatureExtractor` defaults.

**Don't ship if model load fails silently.** Same Ed25519 verification rule as Phase 7. Tampered audio model must fail loudly.

---

## 11. Deliverables

By end of this phase:

- [ ] `feature-detect-audio` module created, wired into app
- [ ] `data-detection-ml/preprocessing/AudioPreprocessor.kt` added (only addition to shared module)
- [ ] `deepfake-audio-detector-v2.tflite` (≤ 120 MB INT8) in assets, signature-verified on load
- [ ] `MODEL_MANIFEST.md` updated with audio model entry
- [ ] `AudioDetector` produces calibrated `DetectorResult` for any supported audio format
- [ ] `AudioDecoder` decodes at minimum: MP3, AAC, M4A, WAV, OGG, OPUS
- [ ] Codec fingerprint + duration sanity as secondary signals
- [ ] Uncertainty gating emits correct reasons for edge inputs
- [ ] `tools/eval-datasets/build_audio_golden_set.py` — dataset builder, MANIFEST.csv committed
- [ ] `AudioEvalHarness` runs on-device against ~500-clip golden set, produces per-TTS-system metrics
- [ ] §7 Step 12 acceptance passed: overall accuracy > 60%, FPR ≤ 18%, p95 latency ≤ 3.5s
- [ ] Decision log entries recorded for: model choice, fusion weights, dataset sources, any deviations
- [ ] All unit + instrumented tests pass
- [ ] End-to-end demo: share synthetic voice clip → synthetic verdict, share real voice clip → authentic-leaning verdict

---

## 12. When to stop and ask

Escalate when:

- ONNX → TFLite conversion fails for wav2vec2 and both `ai-edge-torch` and `onnx2tf` produce errors
- Parity check shows > 5% MAE between PyTorch and TFLite
- Final TFLite size exceeds 120 MB hard cap
- Kaggle / Common Voice / LibriSpeech downloads fail and no clean alternative works
- Golden-set overall accuracy is below 60% after one investigation round
- p95 latency exceeds 5 seconds even after profiling
- MediaCodec consistently fails on common formats (MP3, AAC) — this is a build environment issue, not a code issue
- Asked to skip or weaken signature verification
- Any Apache 2.0 license concern arises around dataset provenance
- Decoding takes > 1.5 seconds for a 30-second clip on a real device — that's a sign of CPU contention or wrong dispatcher

Do not silently drop any §11 deliverable.

---

## 13. Phase completion report

At phase end, produce `docs/phase_reports/phase_8.md` with:

- Golden-set eval results: overall accuracy, FPR, FNR, uncertain rate, **per-TTS-system breakdown**, **per-codec breakdown**, p50/p95 latency
- Reference to `phase_8_eval_results.json` for raw numbers
- Final model size (INT8 and FP16 if produced)
- Device compatibility matrix (which delegate worked on which device class)
- Decision log entries added during this phase
- Known limitations: which TTS systems perform worst, which codecs cause most issues
- Demo script: exactly which audio files to use, how to share, what user should see
- **Wav2vec2-specific notes:** any conversion quirks, op-not-supported workarounds, or chipset-specific issues encountered

---

## 14. What happens next

Phase 9 (video detector) reuses both Phase 7 (image model applied frame-by-frame as the spatial branch) and Phase 8 (audio detector applied to the audio track for video AV-sync analysis if used). The shared `data-detection-ml` runtime continues to grow but should remain media-type-agnostic; audio-specific and video-specific code lives in their respective `feature-detect-*` modules.

By end of Phase 8, two of three detectors are working end-to-end. The user can share an image, a video file, or an audio file and get a real verdict. Video-specific temporal analysis is the last piece.
