# Veritas — Phase 7 Execution Plan
**Version:** 2.0 (supersedes Phase 7 in `03_PHASE_PLAN.md` and `04_IMPLEMENTATION_PLAN_7_9.md`)
**Companion to:** `01_ARCHITECTURE.md`, `02_VISUAL_SPEC.md`, `04_DATA_CONTRACTS.md`, `05_GLOSSARY_AND_DECISIONS.md`
**Purpose:** Execution blueprint for the image detector. Self-contained — the agent should NOT cross-reference `03_PHASE_PLAN.md` for this phase.
**Status:** The single source of truth for Phase 7. If anything here conflicts with `03_PHASE_PLAN.md` or `04_IMPLEMENTATION_PLAN_7_9.md`, **this document wins.**

---

## 0. Reading order for the agent

Before starting any code, read in this order:

1. **This entire document.** Do not skip sections.
2. `02_VISUAL_SPEC.md` §2.16, §2.17 (image verdicts) and §7.3 (forensic image view) — so you know what the downstream UI expects.
3. `04_DATA_CONTRACTS.md` — the `Verdict`, `DetectorResult`, `ConfidenceInterval`, `ReasonCode` definitions. These are the contracts your output must satisfy.
4. `05_GLOSSARY_AND_DECISIONS.md` Part 2, specifically D-016 (LiteRT via Play Services, NNAPI forbidden) and D-018 (training vs runtime separation). These constrain your implementation.
5. `01_ARCHITECTURE.md` §6 (detection pipeline) and §8 (performance constraints).

You do NOT need to read `03_PHASE_PLAN.md` Phase 7 or `04_IMPLEMENTATION_PLAN_7_9.md` Phase 7. **This document replaces both.**

---

## 1. Goal

Given an image, produce a calibrated `DetectorResult` in under 2 seconds on a mid-range 2024 Snapdragon device, distinguishing real photos from AI-generated images (Midjourney, Stable Diffusion, DALL-E, Flux, GAN families, and similar).

Phase 7 is **product-functional, not production-calibrated**. We are building toward a working end-to-end app, not shipping to the Play Store. Accept real-world accuracy limitations — the UNCERTAIN verdict handles the cases the model can't decide confidently. Post-product-complete, we will retrain for quality; that's a separate workstream.

## 2. Strategy — "reuse, don't train"

v1 integrates an existing open-source detector and wraps it in a thin, well-structured Kotlin pipeline. **No model training in this phase.** The model ships as shipped. Quality improvements via fine-tuning, distillation, or ensembling are explicitly deferred to the post-product retraining work.

**One primary model, Apache 2.0 licensed, already has an ONNX export.** We do not ship multiple detectors in v1 — one detector plus EXIF/ELA forensic heuristics is enough to demonstrate the full app flow. Ensemble complexity is deferred.

## 3. Chosen model — final decision

**Runtime model (ships in APK):** `prithivMLmods/Deep-Fake-Detector-v2-Model`
- **License:** Apache 2.0 ✓ (verified, clean, no author contact needed)
- **Architecture:** Vision Transformer (ViT), base model `google/vit-base-patch16-224-in21k`
- **Input:** 224×224 RGB, standard ImageNet preprocessing
- **Output:** binary classifier → logits → sigmoid → "Realism" / "Deepfake" probability
- **Reported accuracy:** ~92% on author's test set (56K images, balanced). Real-world in-the-wild accuracy will be lower; accept this.
- **Training data note:** author states datasets were combined from public Hugging Face / Kaggle sources, 4–5 years old. Performance on modern generators (Flux, recent Midjourney, GPT-4o image) is likely weaker than on older GAN outputs. Acknowledge via generous UNCERTAIN banding.
- **Size:** ONNX INT8 version is ~87 MB. After conversion to TFLite and re-quantization, target ≤ 80 MB. If the converted file exceeds 100 MB, escalate — that's the hard cap.
- **ONNX source:** `onnx-community/Deep-Fake-Detector-v2-Model-ONNX` on Hugging Face (automatic conversion). Verify this against the original model's PyTorch outputs before converting further to TFLite.

**What we are NOT shipping in v1:** a second ML detector (FreqNet was proposed but the original repo has no LICENSE file — same legal concern that killed NPR; drop from v1). EXIF/ELA forensics fill the "second signal" slot instead, weighted lightly.

**Secondary signals (Kotlin-only, no ML models):**
- EXIF metadata analyzer (AndroidX `ExifInterface`)
- Error Level Analysis (ELA) — JPEG recompression difference, pure bitmap ops
- Neither of these is a verdict on its own; they are weak priors that nudge the final fused score and contribute to uncertainty gating.

## 4. Module structure

Create **two new Gradle modules** in this phase:

### 4.1 `data-detection-ml` (new, shared)

Android module, depends on `domain-detection`. This is the shared ML runtime infrastructure that Phases 8 and 9 will also use. Creating it here avoids duplicating LiteRT plumbing across three detectors.

```
data-detection-ml/
├── runtime/
│   ├── LiteRtRuntime.kt              # singleton, manages interpreter pool, lazy load
│   ├── DelegateChain.kt              # GPU delegate → XNNPACK CPU fallback
│   ├── ModelAsset.kt                 # loads .tflite from assets, verifies signature
│   ├── TensorBuffers.kt              # reusable ByteBuffer pools
│   └── ModelRegistry.kt              # model id → asset path + version mapping
├── preprocessing/
│   ├── ImagePreprocessor.kt          # resize, normalize, NCHW conversion
│   └── (Phase 8/9 will add AudioPreprocessor, FrameSampler here later)
├── inference/
│   ├── ModelRunner.kt                # generic run(input: Tensor) → Tensor
│   └── RunnerFactory.kt              # builds configured runners
├── fusion/
│   ├── HandTunedFusion.kt            # v1: fixed weights per phase
│   └── Calibrator.kt                 # maps raw score → calibrated confidence interval
└── assets/
    └── models/
        └── image/
            └── deepfake-detector-v2.tflite  # ~80 MB after quantization
```

### 4.2 `feature-detect-image` (new, image-specific)

Depends on `data-detection-ml` and `domain-detection`.

```
feature-detect-image/
├── domain/
│   ├── ImageDetector.kt              # implements Detector<Bitmap>
│   └── ImageReasonCodes.kt           # IMG_DEEPFAKE_MODEL_HIGH, IMG_EXIF_MISSING, etc.
├── model/
│   ├── DeepfakeDetectorV2Model.kt    # thin wrapper over ModelRunner
│   └── ViTPreprocessor.kt            # 224×224 resize + ImageNet normalization
├── forensics/
│   ├── ExifAnalyzer.kt               # AndroidX ExifInterface wrapper
│   ├── ElaAnalyzer.kt                # JPEG recompression difference
│   └── ForensicSignals.kt            # data class with scalar outputs
├── fusion/
│   └── ImageFusion.kt                # hand-tuned weighted average
└── tests/
    └── ImageDetectorInstrumentedTest.kt
```

## 5. Runtime and hardware acceleration

**Use LiteRT via Google Play Services.** The delegate chain is:

```
GPU delegate (via play-services-tflite-gpu)
  → XNNPACK (CPU fallback)
```

**Do NOT use the NNAPI delegate.** NNAPI is deprecated as of Android 15 and per D-016 it is forbidden in this project. If the agent encounters any older documentation (including `04_IMPLEMENTATION_PLAN_7_9.md`) suggesting an NNAPI-first delegate chain, that guidance is superseded. Use only GPU + CPU.

**Dependencies to add (in `data-detection-ml/build.gradle.kts`):**

```kotlin
dependencies {
    implementation("com.google.android.gms:play-services-tflite-java:16.x.x")
    implementation("com.google.android.gms:play-services-tflite-gpu:16.x.x")
    implementation("com.google.android.gms:play-services-tflite-support:16.x.x")
}
```

Use the latest stable Play Services versions at build time. Do not pin to old versions unless a known incompatibility forces it.

**Probe at app start:** check GPU delegate availability, cache the result per device in DataStore. If GPU delegate fails to initialize on a specific device, automatically fall back to CPU-only and emit a `FallbackLevel.CPU_XNNPACK` signal in `DetectorResult` so the UI can render the "running on CPU" note per visual spec §10.9.

## 6. The contract you must satisfy

Your `ImageDetector.analyze()` returns a `DetectorResult` that the existing fusion/verdict pipeline (from Phase 5) will consume. This shape is fixed by `04_DATA_CONTRACTS.md`. In abbreviated form:

```kotlin
data class DetectorResult(
    val score: Float,                    // 0.0 = authentic, 1.0 = synthetic
    val confidence: ConfidenceInterval,  // e.g. (0.72, 0.91)
    val reasonCodes: List<ReasonCode>,
    val subScores: Map<String, Float>,   // e.g. {"vit_model": 0.83, "exif": 0.2}
    val uncertainReasons: List<UncertainReason>,
    val latencyMs: Int,
    val fallbackUsed: FallbackLevel
)
```

- **Score cap:** never emit a score of exactly 0.0 or 1.0. Clamp to `[0.02, 0.98]`. See architecture §13 — absolute certainty is dishonest.
- **Confidence interval ceiling:** the upper bound must not exceed 0.95. Never claim more than 95% confidence.
- **For UNCERTAIN verdicts:** confidence interval MUST straddle the threshold boundary (e.g. `0.35–0.62`). See `04_DATA_CONTRACTS.md` range generation rules.
- **`subScores` keys for this detector:** use exactly `"vit_model"` and `"exif_ela"`. These are referenced by the forensic UI.

## 7. Step-by-step execution plan

Execute in order. Each step should compile and pass its own tests before moving to the next.

### Step 1 — Runtime plumbing (3–5 hours)

Goal: prove that LiteRT via Play Services works end-to-end with a trivial model, before investing in the real conversion.

Tasks:
1. Create `data-detection-ml` Gradle module, wire into settings + dependencies.
2. Add Play Services LiteRT dependencies (see §5).
3. Implement `LiteRtRuntime.kt` as a singleton that lazily initializes the Play Services TFLite runtime.
4. Implement `DelegateChain.kt` that probes GPU delegate at first use, falls back to XNNPACK if unavailable, and caches the result in DataStore keyed by device model.
5. Implement `ModelAsset.kt` to load a `.tflite` file from `assets/` into a `MappedByteBuffer`.
6. Implement `ModelRunner.kt` — generic `suspend fun run(input: ByteBuffer, output: ByteBuffer)`.
7. Write an instrumented test that loads a **known-good 2 MB MobileNet v2 checkpoint** (pre-existing public model, included in androidTest assets) and runs one classification inference on a device. Verify the output matches the expected label.

Acceptance: the MobileNet test passes on a real device. Do not proceed to Step 2 until this works.

### Step 2 — Model conversion pipeline (ONNX → TFLite) (4–6 hours)

This runs OFF-DEVICE. Write a one-shot Python script in `tools/model-conversion/` that downloads the Apache 2.0 ONNX model, converts it to TFLite, quantizes, and verifies output parity.

```python
# tools/model-conversion/convert_deepfake_detector_v2.py
# Pseudocode outline — agent to implement fully
# 1. Download onnx-community/Deep-Fake-Detector-v2-Model-ONNX (or prithivMLmods fp32 safetensors)
# 2. Convert ONNX -> TFLite via ai-edge-torch or onnx2tf
# 3. INT8 quantize with representative dataset (200 diverse photos, mix of real/synthetic)
# 4. Verify output MAE < 2% vs ONNX reference on 20 test images
# 5. Save final .tflite + checksum + signed .sig file
```

Requirements:
- Verify TFLite output parity against the original ONNX on 20 diverse images (pass/fail: MAE < 2% on sigmoid output).
- Target final size ≤ 80 MB INT8. If above 100 MB, escalate — hard cap.
- Produce a FP16 fallback variant as well for devices where INT8 regresses accuracy. Ship both; runtime selects.
- Commit the final `.tflite` files to `data-detection-ml/src/main/assets/models/image/`.
- Record model checksum, quantization mode, source ONNX SHA, license (Apache 2.0), and commit date in a new file `data-detection-ml/MODEL_MANIFEST.md`.

Acceptance: `.tflite` file exists in assets, MODEL_MANIFEST.md records full provenance, Python script is runnable end-to-end.

### Step 3 — Model signing and verification (2–3 hours)

Even though models are bundled in v1, implement Ed25519 signature verification at load time. This forces us to get the signed-model-loading path right before Phase 13 activates OTA updates.

Tasks:
1. Generate an Ed25519 keypair (one-time). Public key embedded in `ModelAsset.kt`. Private key kept out of the repo (in a local dev note for now; proper key management comes in Phase 13).
2. Sign the `.tflite` file, commit the `.sig` alongside.
3. In `ModelAsset.load()`, verify signature before mapping the model into memory. Throw on verification failure.
4. Unit test the happy path (valid signature loads) and the unhappy path (tampered bytes fail).

Acceptance: model loading fails cleanly if someone edits the `.tflite` without re-signing.

### Step 4 — ViT preprocessor (2 hours)

Standard ImageNet ViT preprocessing:
1. Resize input bitmap to 224×224.
2. Normalize pixel values using ImageNet mean `[0.485, 0.456, 0.406]` and std `[0.229, 0.224, 0.225]` per channel.
3. Convert HWC → CHW layout as the model expects NCHW.
4. Pack into a `ByteBuffer` matching the model's input tensor spec.

Test: preprocess a known image, compare the resulting tensor to the Python reference output (export one via the model conversion script for reference). MAE should be essentially zero — this is deterministic preprocessing.

### Step 5 — First end-to-end inference (2 hours)

Wire `DeepfakeDetectorV2Model.kt` through `ModelRunner` → single inference returns logits. Apply sigmoid to get a score in `[0.0, 1.0]`. Wrap in a minimal `DetectorResult` with `subScores["vit_model"] = score`.

Minimal `ImageDetector.analyze()` at this point:
- Run ViT preprocessor
- Run model inference
- Return `DetectorResult` with single score, no forensics, no fusion, no calibration, hand-picked confidence interval

**At this point the image detector works end-to-end.** Run it on 10 hand-picked images (5 real photos, 5 known synthetic from Midjourney / SD / etc.) and verify direction: real trends low, synthetic trends high. This is the product's first real signal — demo it.

**This is the minimum viable Phase 7.** If schedule pressure is real, you can pause here and move to Phase 8. Everything below improves quality.

### Step 6 — EXIF analyzer (3–4 hours)

Use AndroidX `ExifInterface`. Extract these fields and produce scalar forensic signals:

- `hasCameraMake: Boolean`
- `hasCameraModel: Boolean`
- `hasCaptureDateTime: Boolean`
- `hasGps: Boolean` (NOT used as a verdict signal; captures often stripped for privacy)
- `hasExposureMetadata: Boolean` (aperture, ISO, shutter speed)
- `quantTableIsStandard: Boolean` — parse JPEG quantization tables, check against a small allowlist of known-good tables (DSLR cameras, phone cameras, Photoshop). Unknown table ≠ synthetic, just unusual.
- `exifCompletenessScore: Float` in `[0, 1]` — fraction of expected fields present.

**Critical:** missing EXIF is NOT a synthetic signal. Social media strips EXIF from everything. These are weak priors that barely move the fused score. Weight them low.

### Step 7 — ELA analyzer (2 hours)

Only meaningful for JPEG. For PNG/WebP/HEIC, return `null` and emit `ELA_SKIPPED_NON_JPEG`.

Steps:
1. Take input JPEG, re-encode at quality 90.
2. Pixel-diff against original, convert to grayscale.
3. Compute the ratio of high-energy pixels (diff > some threshold).
4. Return a single scalar `elaAnomalyScore: Float` in `[0, 1]`.

Weight this even lower than EXIF — it has very high false-positive rates on legitimately edited images.

### Step 8 — Hand-tuned fusion (2 hours)

```kotlin
object ImageFusion {
    fun fuse(
        vitScore: Float,
        exifCompleteness: Float,     // 1.0 = all fields present
        elaAnomalyScore: Float?       // null for non-JPEG
    ): Float {
        val exifSignal = 1f - exifCompleteness  // "missing EXIF" direction
        val ela = elaAnomalyScore ?: 0.5f        // neutral when absent

        val fused = (
            0.85f * vitScore +
            0.10f * exifSignal +
            0.05f * ela
        ).coerceIn(0.02f, 0.98f)

        return fused
    }
}
```

The ViT model dominates by design. EXIF and ELA are tiebreakers. These weights are hand-tuned starting points — document them in the decision log (new entry, next ID after Phase 6's). A learned fusion model replaces this post-product-complete.

### Step 9 — Calibration to confidence interval (3 hours)

Raw fused score → calibrated confidence interval. Use a bundled isotonic regression lookup table for now (fitted later during the retraining workstream, from the §7 Step 12 golden-set labels plus additional data). For Phase 7 v1, use a simple heuristic that satisfies the contract:

```kotlin
fun scoreToInterval(fused: Float): ConfidenceInterval {
    val width = when {
        fused < 0.25f -> 0.18f      // confident real
        fused < 0.40f -> 0.28f      // leaning real but uncertain
        fused < 0.60f -> 0.30f      // true uncertain zone — wider interval
        fused < 0.75f -> 0.28f
        else -> 0.18f                // confident synthetic
    }
    val lo = (fused - width / 2).coerceIn(0.02f, 0.98f)
    val hi = (fused + width / 2).coerceIn(0.02f, 0.95f)  // 95% ceiling
    return ConfidenceInterval(lo, hi)
}
```

This is placeholder calibration. A fitted isotonic regression drops in later — the interface doesn't change.

### Step 10 — Uncertainty gating (2 hours)

Emit `UncertainReason` entries under these conditions:

```kotlin
if (image.width < 256 || image.height < 256) add(TOO_SMALL)
if (isJpegAndVeryCompressed(image)) add(HEAVY_COMPRESSION)  // JPEG quality < 50
if (fused > 0.35f && fused < 0.65f) add(LOW_CONFIDENCE_RANGE)
if (fallbackUsed == FallbackLevel.CPU_XNNPACK) add(CPU_FALLBACK)
```

The fusion engine (Phase 5) uses these to decide whether the final verdict should be UNCERTAIN. Our job here is to surface signals honestly, not to make the decision.

### Step 11 — Reason codes (2 hours)

Map signals to human-readable codes. Include these entries in `ImageReasonCodes.kt` and author their content in `04_DATA_CONTRACTS.md` reason-code catalog:

- `IMG_DEEPFAKE_MODEL_HIGH` if `vitScore > 0.70` — "AI detection model flagged generative patterns"
- `IMG_EXIF_MISSING` if `exifCompletenessScore < 0.3` — "No camera metadata present (not conclusive)"
- `IMG_EXIF_SUSPICIOUS` if `quantTableIsStandard == false` — "JPEG compression signature unusual"
- `IMG_ELA_ANOMALY` if `elaAnomalyScore > 0.6` — "Possible local editing detected"
- `IMG_LOW_QUALITY` (uncertain gating) — "Image too compressed or small for reliable analysis"

### Step 12 — Golden-set eval harness (6–8 hours)

Build `ImageEvalHarness` as an instrumented test that runs on-device against a real labeled dataset. **The agent builds the entire pipeline — dataset download, filtering, organization, and eval execution. The human's only involvement is generating a Kaggle API token once.**

#### 12.1 — Dataset acquisition (agent-scripted)

Create `tools/eval-datasets/` with scripts that pull public datasets. Approved sources (all Apache 2.0 / CC / research-open):

**Primary dataset — Kaggle:**
- `manjilkarki/deepfake-and-real-images` — pre-split, well-organized. Provides the base of ~400 mixed real/fake images across older GAN families and diffusion.
- Download via Kaggle API: `pip install kaggle` + `kaggle datasets download -d manjilkarki/deepfake-and-real-images`.
- Human prerequisite (one-time, ~5 min): human creates free Kaggle account, generates API token at kaggle.com/settings/account, places `kaggle.json` in `~/.kaggle/`. Agent validates token presence at script start and fails with a clear message if missing.

**Secondary dataset — Hugging Face (modern generators):**
- `ComplexDataLab/OpenFake` — subset. Target ~100 synthetic from Flux, DALL-E 3, Midjourney 6/7, SD 3.5. Stream via `datasets` library; no full download needed.
- `OpenRL/DeepFakeFace` — subset. Target ~50 diffusion-generated faces for face-specific coverage.

**Real photos complement — Hugging Face:**
- FFHQ or similar open face dataset — 100 real face photos if the Kaggle dataset's real count is low. Accessible via `datasets`.

Agent writes `tools/eval-datasets/build_golden_set.py` that:
1. Verifies Kaggle API token presence; fails loudly if missing.
2. Downloads and extracts all sources.
3. Samples a balanced set: target 500 images total, ~250 real and ~250 synthetic.
4. Stratifies synthetic by generator family so each (Midjourney, Flux, DALL-E, SD, GAN) contributes roughly equal counts — gives per-generator metrics.
5. Resizes and normalizes filenames.
6. Copies into `feature-detect-image/src/androidTest/assets/golden-image/real/` and `.../synthetic/` with naming convention `{source}_{generator}_{hash}.jpg`.
7. Writes a `MANIFEST.csv` with columns `filename, label, source_dataset, generator, original_path`.
8. Prints a summary table: total counts, per-generator breakdown, size on disk.

Script is re-runnable and idempotent. Commits `MANIFEST.csv` to the repo; the actual image files should be in `.gitignore` (Kaggle terms usually forbid redistribution — the MANIFEST lets the eval reproduce, the images are pulled on demand).

#### 12.2 — Eval harness (agent-built, on-device)

`ImageEvalHarness` is an instrumented test in `feature-detect-image/src/androidTest/`. It:
1. Iterates the 500-image set.
2. Runs each image through the full `ImageDetector` pipeline.
3. Records: raw score, calibrated confidence interval, verdict bucket, reason codes, latency.
4. Aggregates into metrics:
   - Overall accuracy
   - False Positive Rate (FPR) on real images
   - False Negative Rate (FNR) on synthetic
   - Uncertain rate
   - **Per-generator accuracy** (key output — tells you which generators the model handles well vs poorly)
   - p50 and p95 latency
5. Writes results to `phase_7_eval_results.json` in test-output dir.
6. Pretty-prints a summary table.

#### 12.3 — Acceptance for product-functional mode

- Overall accuracy > 65% (relaxed from the original 70% — the chosen model is 4–5 years old, modern generators will hurt it)
- FPR (real photos flagged as synthetic) ≤ 15%
- p95 latency ≤ 2.5 seconds
- Per-generator breakdown present in output (no threshold — this is diagnostic, informs the retraining workstream)

If overall accuracy is below 65%, escalate. This is either a conversion bug, a preprocessing bug, or a sign that the model genuinely can't handle the test distribution — all three require human input.

Do not tune the fusion weights based on these numbers. The hand-tuned weights from Step 8 ship as-is. Weight tuning happens during the retraining workstream when there's a real training pipeline.

**What the per-generator results are actually for:** this is the single most valuable output of Phase 7. A table like "Midjourney v7: 45%, Flux: 58%, Stable Diffusion 1.5: 81%, DALL-E 3: 52%" tells you exactly which generator families to prioritize when you come back to retrain. Without this data, the retraining workstream starts blind.

### Step 13 — Latency check (0–4 hours)

Profile on a reference mid-range 2024 Snapdragon device. If p95 latency from Step 12 exceeds 2.5s:
1. Verify GPU delegate is actually being used (not silently falling back to CPU).
2. Check for unnecessary bitmap copies in preprocessing.
3. Confirm INT8 quantization is applied to the full graph, not just input/output layers.
4. Profile before optimizing — never rewrite speculatively.

Skip this step entirely if latency is already under budget.

**No fusion-weight tuning step.** Hand-tuned weights from Step 8 are the shipped weights for v1. Weight tuning happens during the retraining workstream when there's a real training pipeline.

## 8. Pipeline integration

The image input path end-to-end:

```
Share intent (or file picker, or paste link)
  → IngestionLayer decodes to Bitmap (Phase 4, already built)
  → PreFlight: C2PA check (Phase 6). If verified authentic, skip detectors entirely.
  → PreFlight: SynthID probe. Per D-034, returns NoOp in v1 — no change to downstream flow.
  → ImageDetector.analyze(bitmap, budget)
      → ViTPreprocessor → DeepfakeDetectorV2Model → vitScore
      → ExifAnalyzer → forensicSignals
      → ElaAnalyzer (if JPEG) → elaScore
      → ImageFusion.fuse(...) → fused score
      → Calibrator → ConfidenceInterval
      → Build DetectorResult with reason codes, uncertainty, etc.
  → FusionEngine combines DetectorResult with C2PA/SynthID priors (Phase 5, already built)
  → Calibrator → Verdict bucket (Phase 5)
  → UI renders (Phase 5 verdict screens)
```

You do not modify anything upstream or downstream. You implement the `ImageDetector` box. Everything else already exists.

## 9. Testing requirements

Minimum tests before this phase is considered complete:

**Unit tests (in `feature-detect-image/src/test/`):**
- `ImageFusion.fuse()` returns expected values for hand-constructed inputs
- `Calibrator.scoreToInterval()` never returns `hi > 0.95` or `lo < 0.02`
- Uncertainty gating emits correct `UncertainReason` values for edge cases
- ViT preprocessor output matches Python reference within MAE < 0.001

**Instrumented tests (in `feature-detect-image/src/androidTest/`):**
- Load a known real photo → detector returns score < 0.5
- Load a known synthetic image → detector returns score > 0.5
- Load a 128×128 image → `TOO_SMALL` in uncertain reasons
- Load a heavily compressed JPEG → `HEAVY_COMPRESSION` in uncertain reasons
- GPU delegate load failure → clean fallback to CPU, `FallbackLevel.CPU_XNNPACK` in result
- `ImageEvalHarness` runs against the 500-image golden set and passes §7 Step 12 acceptance

**Integration test:**
- Share intent with a synthetic image → full pipeline → synthetic verdict renders correctly on device

## 10. Known pitfalls (mandatory read)

**Do not use NNAPI.** Deprecated per Android 15. Use LiteRT via Play Services, GPU delegate, CPU fallback only. If you find yourself typing `NnApiDelegate`, stop — you are violating D-016.

**Do not add a second ML model in v1.** The earlier implementation plan proposed FreqNet as a complement. FreqNet has the same license issue we avoided by not using NPR. Skip it. One clean-license model plus EXIF/ELA forensics is enough.

**Do not generate random confidence intervals.** They must come from calibrated mapping of raw scores. A score of 0.85 should consistently map to roughly `[0.72, 0.91]`, not randomly. If you're tempted to use `Random.nextFloat()`, stop.

**Do not emit scores of exactly 0.0 or 1.0.** Architecture §13 forbids it. Clamp to `[0.02, 0.98]`.

**Do not block the Main thread.** ML inference runs on a dedicated coroutine dispatcher (2-thread pool). Never call `analyze()` from `Dispatchers.Main`. Never from `Dispatchers.IO` either — IO is for network and file I/O, inference is CPU/GPU-bound.

**Do not allocate per-inference.** Pre-allocate input/output ByteBuffers at model load time, reuse across calls. A heap allocation per scan will GC-pause the animation.

**Do not ship if model load fails silently.** The Ed25519 signature verification must throw on tampered files and propagate a clear error to the UI. "Model didn't load and we ran without it" is worse than a visible error.

**Do not silently lower thresholds when eval numbers look bad.** If FPR on real photos is 25%, that's a model or conversion problem, not a reason to raise the synthetic threshold. Investigate first (likely ONNX parity or quantization issue), then either ship with wider UNCERTAIN bands or escalate.

**Do not iterate endlessly on fusion weights.** One pass of tuning in Step 13 is the budget. Learned fusion replaces this in the post-product-complete workstream.

**Do not treat the ONNX conversion casually.** ONNX → TFLite INT8 can silently introduce 3–5% accuracy drops. The parity check in Step 2 is load-bearing. If parity fails, fix the conversion — do not ship a worse model and call it fine.

## 11. Deliverables

By end of this phase:

- [ ] `data-detection-ml` module created, LiteRT runtime working, signed model loading
- [ ] `feature-detect-image` module created, wired into app
- [ ] `deepfake-detector-v2.tflite` (~80 MB INT8) in assets, signature-verified on load
- [ ] `MODEL_MANIFEST.md` recording model provenance, license (Apache 2.0), checksum
- [ ] `ImageDetector` produces calibrated `DetectorResult` for any supported image format
- [ ] EXIF + ELA forensics as secondary signals
- [ ] Uncertainty gating emits correct reasons for low-quality inputs
- [ ] `tools/eval-datasets/build_golden_set.py` — dataset builder script, commits MANIFEST.csv
- [ ] `ImageEvalHarness` runs on-device against 500-image golden set, produces per-generator metrics
- [ ] §7 Step 12 acceptance passed: overall accuracy > 65%, FPR ≤ 15%, p95 latency ≤ 2.5s
- [ ] Decision log entries recorded for: model choice, fusion weights, dataset sources, any implementation deviations
- [ ] All unit + instrumented tests pass
- [ ] End-to-end demo: share synthetic image → synthetic verdict, share real photo → authentic-leaning verdict

## 12. When to stop and ask

Escalate to the human when:

- ONNX → TFLite conversion fails and no documented workaround works
- Parity check (Step 2) shows > 5% MAE between PyTorch and TFLite outputs
- Final TFLite size exceeds 100 MB hard cap
- Kaggle API token is missing and human hasn't been able to provide one (block before Step 12)
- Dataset downloads fail (network, quota, dataset removed) and no clean alternative works
- Golden-set overall accuracy is below 65% after investigation, or FPR on real photos exceeds 20%
- A required dependency conflicts with an existing one in the project
- Latency is > 5 seconds even after profiling and optimization
- Signature verification is being asked to be skipped or weakened
- Any Apache 2.0 license concern arises around the downstream dataset provenance

Do not silently drop any of the Deliverables in §11.

## 13. Phase completion report

At phase end, produce `docs/phase_reports/phase_7.md` with:

- Golden-set eval results: overall accuracy, FPR, FNR, uncertain rate, **per-generator breakdown** (the key output), p50/p95 latency
- Reference to `phase_7_eval_results.json` for raw numbers
- Final model size (INT8 and FP16 variants)
- Device compatibility matrix: GPU delegate works on / falls back to CPU on (list specific devices tested)
- Decision log entries added during this phase
- Known limitations: which generator families perform worst (from per-generator breakdown). This directly informs the retraining workstream's priority list.
- Demo script: exactly which test images to use, what share command, what the human should see

## 14. What happens next

Phase 8 (audio detector) reuses the `data-detection-ml` runtime. The `LiteRtRuntime`, `DelegateChain`, `ModelRunner`, `Calibrator`, and `HandTunedFusion` are all shared infrastructure — Phase 8 just adds audio-specific preprocessing, a new model asset, and audio-specific reason codes.

Phase 9 (video detector) reuses this Phase 7 image model directly, applied frame-by-frame. Same `.tflite`, same interpreter instance, shared via `LiteRtRuntime`. Plus MediaPipe face detection (new) and frame-extraction plumbing (new).

**This phase is foundational. Get the runtime right, and Phases 8 and 9 are mostly assembly.**
