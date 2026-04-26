# Veritas — Data Contracts
**Version:** 1.0
**Companion to:** `01_ARCHITECTURE.md`, `02_VISUAL_SPEC.md`, `03_PHASE_PLAN.md`
**Purpose:** Pin down every data shape the codebase uses. Agents reinvent data structures across a project three different ways if contracts aren't explicit. This document prevents that.

---

## 0. Conventions

- All types defined in Kotlin, in the `domain-detection` pure-Kotlin module unless noted.
- Use `kotlinx.serialization` for anything that crosses a boundary (disk, network, IPC).
- Use `kotlinx.datetime` for timestamps, not `java.time` — keeps the module pure Kotlin.
- Every sealed hierarchy uses `@Serializable` on root and all cases.
- Nullable only when null is meaningful. Prefer sentinel values or sealed subclasses when null isn't.

---

## 1. Core types

### 1.1 `MediaType`

```kotlin
enum class MediaType { VIDEO, AUDIO, IMAGE }
```

### 1.2 `ScannedMedia`

Input to the detection pipeline. Represents media that has passed ingestion validation and is ready for analysis.

```kotlin
@Serializable
data class ScannedMedia(
  val id: String,                    // UUID, generated at ingestion
  val uri: String,                   // content:// or file:// URI to scoped storage copy
  val mediaType: MediaType,
  val mimeType: String,              // e.g., "video/mp4"
  val sizeBytes: Long,
  val durationMs: Long?,             // null for images
  val widthPx: Int?,                 // null for audio
  val heightPx: Int?,                // null for audio
  val source: MediaSource,
  val ingestedAt: Instant
)

@Serializable
sealed class MediaSource {
  @Serializable data class ShareIntent(val sourcePackage: String?) : MediaSource()
  @Serializable data object FilePicker : MediaSource()
  @Serializable data object Overlay : MediaSource()
  @Serializable data class Link(val url: String) : MediaSource()
}
```

### 1.3 `Verdict`

The primary output. Every successful scan produces exactly one.

```kotlin
@Serializable
data class Verdict(
  val id: String,                       // UUID
  val mediaId: String,                  // matches ScannedMedia.id
  val mediaType: MediaType,
  val outcome: VerdictOutcome,
  val confidence: ConfidenceRange,
  val summary: String,                  // one-sentence user-facing summary
  val reasons: List<Reason>,            // ordered by weight descending
  val modelVersions: Map<String, String>, // e.g., {"video": "2.4.1", "fusion": "1.1.0"}
  val scannedAt: Instant,
  val inferenceHardware: InferenceHardware,
  val elapsedMs: Long
)

@Serializable
enum class VerdictOutcome {
  VERIFIED_AUTHENTIC,   // C2PA confirmed
  LIKELY_AUTHENTIC,     // detectors clean, no C2PA (spec §2.5, softer green)
  UNCERTAIN,            // explicit abstention
  LIKELY_SYNTHETIC      // ensemble agreement
}

@Serializable
data class ConfidenceRange(
  val lowPct: Int,   // 0–100 inclusive
  val highPct: Int   // 0–100 inclusive, >= lowPct
) {
  init {
    require(lowPct in 0..100) { "lowPct out of range" }
    require(highPct in lowPct..100) { "highPct must be >= lowPct" }
  }
}

@Serializable
enum class InferenceHardware { NPU, GPU, CPU_XNNPACK, MIXED }
```

**Display rules:**
- `VERIFIED_AUTHENTIC`: render as "Likely authentic." in strong `--ok`, show device attribution
- `LIKELY_AUTHENTIC`: render as "Looks authentic." in softer `--ok`, no attribution
- `UNCERTAIN`: render as "Uncertain." in `--warn`
- `LIKELY_SYNTHETIC`: render as "Likely synthetic." in `--bad`

**Confidence caps:**
- Never display highPct > 94 for authentic outcomes
- Never display highPct > 96 for synthetic outcomes
- Uncertain ranges always straddle 50% (low < 50, high > 50)

### 1.4 `Reason`

A single contributing signal to a verdict.

```kotlin
@Serializable
data class Reason(
  val code: ReasonCode,
  val weight: Float,              // 0.0–1.0, fraction of verdict score attributable
  val severity: Severity,
  val evidence: ReasonEvidence
)

@Serializable
enum class Severity { POSITIVE, NEUTRAL, MINOR, MAJOR, CRITICAL }

@Serializable
enum class ReasonCode {
  // Provenance — positive
  C2PA_VERIFIED,                  // device/tool in evidence
  // Provenance — negative
  C2PA_INVALID_SIGNATURE,
  C2PA_REVOKED,
  // Watermarks — negative
  SYNTHID_DETECTED,
  // Visual — negative
  DIFFUSION_SPECTRAL_SIG,
  GAN_SPECTRAL_SIG,
  TEMPORAL_INCONSISTENCY,
  LIP_SYNC_DRIFT,
  EAR_GEOMETRY_DRIFT,
  TEETH_ARTIFACTS,
  JEWELRY_FLICKER,
  EYE_REFLECTION_MISMATCH,
  // Physiological — negative/positive
  RPPG_ABSENT,                    // negative signal
  RPPG_IMPLAUSIBLE,               // negative signal
  RPPG_NATURAL,                   // positive signal
  // Audio — legacy/general negative
  AUDIO_SPECTRAL_NEURAL,
  AUDIO_PROSODY_UNNATURAL,
  AUDIO_CODEC_MISMATCH,
  // Audio — Phase 8 shipped detector
  AUD_SYNTHETIC_VOICE_HIGH,
  AUD_CODEC_MISMATCH,
  AUD_TOO_SHORT,
  AUD_LOW_QUALITY,
  AUD_NATURAL_PROSODY,
  // Video — Phase 9 shipped detector
  VID_TEMPORAL_DRIFT_HIGH,
  VID_SPATIAL_SYNTHETIC_FRAMES,
  VID_FACE_INCONSISTENT,
  VID_LIP_SYNC_DRIFT,
  VID_LOW_QUALITY,
  VID_DECODE_FAILED,
  // Forensics
  METADATA_IMPLAUSIBLE,
  ELA_INCONSISTENT,
  CODEC_CONSISTENT,               // positive signal
  // Quality / uncertainty
  COMPRESSION_HEAVY,
  LOW_RESOLUTION,
  DETECTOR_DISAGREEMENT,
  LOW_MEMORY_FALLBACK
}

@Serializable
sealed class ReasonEvidence {
  @Serializable data class C2PAVerified(val issuerName: String, val deviceName: String?, val signedAt: Instant) : ReasonEvidence()
  @Serializable data class Temporal(val timestampsMs: List<Long>) : ReasonEvidence()
  @Serializable data class Region(val regionLabel: String, val bboxNormalized: BBox) : ReasonEvidence()
  @Serializable data class Scalar(val measurement: Float, val unit: String) : ReasonEvidence()
  @Serializable data class Qualitative(val note: String) : ReasonEvidence()
  @Serializable data object None : ReasonEvidence()
}

@Serializable
data class BBox(val x: Float, val y: Float, val w: Float, val h: Float) {
  init {
    require(x in 0f..1f && y in 0f..1f && w in 0f..1f && h in 0f..1f)
  }
}
```

**Reason ordering:** The `reasons` list on `Verdict` is ordered by `weight` descending. Top 3 shown as evidence chips on the verdict card. All shown in forensic view.

**Phase 8 audio reason-code content:**

| Code | Trigger | User-facing meaning |
|---|---|---|
| `AUD_SYNTHETIC_VOICE_HIGH` | `wav2vec2_model > 0.70` | AI voice detection model flagged synthetic patterns |
| `AUD_CODEC_MISMATCH` | `codec < 0.40` | Audio compression does not match its stated source type |
| `AUD_TOO_SHORT` | decoded duration `< 1000 ms` | Audio too short for reliable analysis |
| `AUD_LOW_QUALITY` | decoded sample rate `< 8000 Hz` | Sample rate too low for reliable analysis |
| `AUD_NATURAL_PROSODY` | `wav2vec2_model < 0.30` | No artifacts of synthetic speech detected |

**Phase 8 audio detector sub-scores:** audio `DetectorResult.subScores` uses exactly:

| Key | Meaning |
|---|---|
| `wav2vec2_model` | Hemgg wav2vec2-base softmax probability for `AIVoice` |
| `codec` | codec plausibility score, where `1.0` is plausible and `0.0` is suspicious |

**Phase 9 video reason-code content:**

| Code | Trigger | User-facing meaning |
|---|---|---|
| `VID_SPATIAL_SYNTHETIC_FRAMES` | one or more sampled frames has `spatial_vit > 0.70` | Sampled frames contain still-image synthetic artifacts |
| `VID_TEMPORAL_DRIFT_HIGH` | `temporal_movinet > 0.35` | MoViNet temporal embeddings change unusually between sampled frames |
| `VID_FACE_INCONSISTENT` | `face_consistency > 0.70` on analyzed face crops | Face-region crops show inconsistent synthetic-artifact scores |
| `VID_LOW_QUALITY` | video-specific uncertainty reason is present | Video quality or frame availability reduced detector confidence |
| `VID_DECODE_FAILED` | frame extraction failed before detector inference | Video could not be decoded reliably on this device |
| `CODEC_CONSISTENT` | no strong negative video signal | Spatial, temporal, and face signals did not show strong synthetic patterns |

**Phase 9 video detector sub-scores:** video `DetectorResult.subScores` uses exactly:

| Key | Meaning |
|---|---|
| `spatial_vit` | Mean Phase 7 image-detector synthetic probability across every other sampled frame |
| `temporal_movinet` | Mean cosine drift of MoViNet-A0 streaming logits across sampled frames |
| `face_consistency` | Mean Phase 7 image-detector synthetic probability over MediaPipe face crops; falls back to `spatial_vit` when no face signal is available |

### 1.5 `ScanStage`

Pipeline progress events, emitted as a Flow.

```kotlin
sealed class ScanStage {
  data object Started : ScanStage()
  data class StageActive(val stage: PipelineStage, val startedAt: Instant) : ScanStage()
  data class StageDone(val stage: PipelineStage, val result: StageResult, val elapsedMs: Long) : ScanStage()
  data class VerdictReady(val verdict: Verdict) : ScanStage()
  data class Failed(val error: ScanError) : ScanStage()
  data object Cancelled : ScanStage()
}

enum class PipelineStage {
  C2PA_CHECK,
  WATERMARK_SCAN,
  SPATIAL_DETECTION,
  TEMPORAL_DETECTION,       // video only
  SPECTRAL_ANALYSIS,        // audio only
  METADATA_FORENSICS,       // image only
  FUSION_CALIBRATION
}

sealed class StageResult {
  data class Success(val summary: String) : StageResult()   // e.g., "NONE", "0.3s", "DETECTED"
  data object Skipped : StageResult()
  data class Failed(val reason: String) : StageResult()
}

sealed class ScanError {
  data object DecoderFailed : ScanError()
  data object OutOfMemory : ScanError()
  data object ModelNotLoaded : ScanError()
  data class Unknown(val throwable: Throwable) : ScanError()
}
```

**UI mapping:** `02_VISUAL_SPEC.md` §2.4 shows 5 stage rows. Order them as:
1. `C2PA_CHECK`
2. `WATERMARK_SCAN`
3. `SPATIAL_DETECTION`
4. `TEMPORAL_DETECTION` / `SPECTRAL_ANALYSIS` / `METADATA_FORENSICS` (media-type-dependent)
5. `FUSION_CALIBRATION`

If a stage doesn't apply to the current media type, it's shown as `SKIPPED` immediately.

### 1.6 `HeatmapData`

Produced by video/image detectors; consumed by forensic view.

```kotlin
data class HeatmapData(
  val mediaType: MediaType,
  val frames: List<HeatmapFrame>        // for images, single frame
)

data class HeatmapFrame(
  val timestampMs: Long,
  val widthBins: Int,
  val heightBins: Int,
  val intensities: FloatArray,          // row-major, length = widthBins * heightBins
  val labeledRegions: List<LabeledRegion>
)

data class LabeledRegion(
  val label: String,                    // e.g., "EAR", "MOUTH"
  val bbox: BBox,
  val severity: Severity
)
```

**Storage:** Not persisted. Lives in memory during the verdict session, discarded on activity destroy. This is critical for privacy — we never write heatmaps to disk.

### 1.7 `WaveformData`

Audio analog of `HeatmapData`.

```kotlin
data class WaveformData(
  val durationMs: Long,
  val samplesPerBin: Int,
  val rmsBins: FloatArray,              // RMS per time bin
  val flaggedRegions: List<FlaggedAudioRegion>
)

data class FlaggedAudioRegion(
  val startMs: Long,
  val endMs: Long,
  val severity: Severity,
  val reasonCode: ReasonCode
)
```

### 1.8 `TemporalConfidence`

Per-second confidence for video/audio timeline.

```kotlin
data class TemporalConfidence(
  val bins: List<TemporalBin>
)

data class TemporalBin(
  val startMs: Long,
  val endMs: Long,
  val syntheticProbability: Float       // 0.0–1.0
)
```

Rendered as the colored timeline bar (visual spec §2.8).

---

## 2. Persistence (Room / DataStore)

### 2.1 `HistoryItemEntity`

Stored in Room. Max 100 entries, auto-pruned on insert.

```kotlin
@Entity(tableName = "history_items")
data class HistoryItemEntity(
  @PrimaryKey val id: String,
  val mediaType: MediaType,
  val mediaMimeType: String,
  val durationMs: Long?,
  val sourcePackage: String?,
  val thumbnailPath: String,           // file path in app-private dir
  val verdictOutcome: VerdictOutcome,
  val confidenceLowPct: Int,
  val confidenceHighPct: Int,
  val summary: String,
  val topReasonsJson: String,          // serialized List<Reason>, top 3
  val modelVersionsJson: String,       // serialized Map<String, String>
  val scannedAt: Long                  // epoch millis
)
```

**Migration strategy:** bump Room schema version on any change. Always ship a `Migration` — never `fallbackToDestructiveMigration` in production.

### 2.2 `AppPreferences` (DataStore)

Stored in Preferences DataStore.

```kotlin
object PreferenceKeys {
  val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("completed_onboarding")
  val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
  val BUBBLE_POSITION_X = intPreferencesKey("bubble_x")
  val BUBBLE_POSITION_Y = intPreferencesKey("bubble_y")
  val BUBBLE_HAPTICS = booleanPreferencesKey("bubble_haptics")
  val TOAST_AUTO_DISMISS_SECONDS = intPreferencesKey("toast_auto_dismiss")
  val MODEL_AUTO_UPDATE = booleanPreferencesKey("model_auto_update")
  val MODEL_WIFI_ONLY = booleanPreferencesKey("model_wifi_only")
  val TELEMETRY_OPT_IN = booleanPreferencesKey("telemetry_opt_in")
  val TELEMETRY_PROMPT_SHOWN = booleanPreferencesKey("telemetry_prompt_shown")
  val ROLLOUT_ID = stringPreferencesKey("rollout_id")         // stable UUID for staged rollout
}
```

**Defaults:**
- `OVERLAY_ENABLED`: false
- `BUBBLE_HAPTICS`: true
- `TOAST_AUTO_DISMISS_SECONDS`: 8
- `MODEL_AUTO_UPDATE`: true
- `MODEL_WIFI_ONLY`: true
- `TELEMETRY_OPT_IN`: false (must be explicitly opted in)

### 2.3 `ModelRegistryEntity`

Tracks installed detection models.

```kotlin
@Entity(tableName = "models")
data class ModelRegistryEntity(
  @PrimaryKey val modelId: String,     // e.g., "video_spatial_v2"
  val family: ModelFamily,
  val version: String,                 // semver
  val architectureHash: String,        // identifies architecture
  val quantization: Quantization,
  val filePath: String,
  val sizeBytes: Long,
  val installedAt: Long,
  val isActive: Boolean,
  val healthCheckStatus: HealthCheckStatus
)

enum class ModelFamily { VIDEO_SPATIAL, VIDEO_TEMPORAL, VIDEO_RPPG, AUDIO_SPECTRAL, AUDIO_PROSODY, IMAGE_VIT, IMAGE_FREQUENCY, FUSION_VIDEO, FUSION_AUDIO, FUSION_IMAGE }
enum class Quantization { FP32, FP16, INT8 }
enum class HealthCheckStatus { PENDING, PASSED, FAILED, ROLLED_BACK }
```

Two models per family can exist at once: `isActive = true` (current) and `isActive = false` (previous, kept for rollback). More than 2 per family triggers cleanup.

---

## 3. Model delivery contracts (server ↔ client)

### 3.1 `ModelManifest`

Served over HTTPS, signed with Ed25519.

```kotlin
@Serializable
data class ModelManifest(
  val manifestVersion: Int,            // always 1 for v1
  val publishedAt: Instant,
  val models: List<ModelEntry>,
  val signature: String                // Ed25519 signature of canonical JSON, base64
)

@Serializable
data class ModelEntry(
  val modelId: String,
  val family: ModelFamily,
  val version: String,                 // semver
  val architectureHash: String,
  val quantization: Quantization,
  val supportedAbis: List<String>,     // e.g., ["arm64-v8a"]
  val targetChipFamilies: List<String>?, // e.g., ["snapdragon_8_gen_3"]; null = universal
  val downloadUrl: String,             // HTTPS URL to signed model blob
  val blobSha256: String,
  val blobSizeBytes: Long,
  val blobSignature: String,           // separate Ed25519 signature of blob
  val rolloutPct: Int,                 // 0–100; devices with rollout ID below this fraction install
  val minAppVersion: String,           // semver; reject manifest if app older
  val replaces: List<String>?          // previous modelIds this supersedes
)
```

**Canonical JSON for signing:**
- Sort keys alphabetically at every level
- No whitespace
- UTF-8
- `signature` field is excluded from the signed bytes

Client-side implementation of canonical JSON must match server-side byte-for-byte.

### 3.2 Rollout ID

Stable, per-install random UUID stored in DataStore. Compared to `rolloutPct` as:

```kotlin
fun isInRollout(rolloutId: String, pctThreshold: Int): Boolean {
  if (pctThreshold >= 100) return true
  if (pctThreshold <= 0) return false
  val hash = rolloutId.sha256().toBigInteger(16)
  val position = (hash.mod(BigInteger.valueOf(100))).toInt()
  return position < pctThreshold
}
```

Deterministic per device, uniform distribution across devices.

---

## 4. Telemetry events (opt-in only)

### 4.1 Opt-in gating

Telemetry is collected ONLY if `TELEMETRY_OPT_IN` preference is true. Every telemetry emission checks this gate.

### 4.2 Event schema

```kotlin
@Serializable
sealed class TelemetryEvent {
  abstract val sessionId: String       // rotates per app launch, not persistent
  abstract val appVersion: String
  abstract val androidVersion: Int
  abstract val deviceClass: DeviceClass
  abstract val timestamp: Instant

  @Serializable data class ScanCompleted(
    override val sessionId: String,
    override val appVersion: String,
    override val androidVersion: Int,
    override val deviceClass: DeviceClass,
    override val timestamp: Instant,
    val mediaType: MediaType,
    val outcome: VerdictOutcome,
    val elapsedMs: Long,
    val inferenceHardware: InferenceHardware,
    val modelVersions: Map<String, String>
  ) : TelemetryEvent()

  @Serializable data class Crash(
    override val sessionId: String,
    override val appVersion: String,
    override val androidVersion: Int,
    override val deviceClass: DeviceClass,
    override val timestamp: Instant,
    val exceptionClass: String,
    val stackTraceHash: String         // hash only — no raw stack
  ) : TelemetryEvent()

  @Serializable data class ModelUpdate(
    override val sessionId: String,
    override val appVersion: String,
    override val androidVersion: Int,
    override val deviceClass: DeviceClass,
    override val timestamp: Instant,
    val modelFamily: ModelFamily,
    val fromVersion: String,
    val toVersion: String,
    val success: Boolean,
    val failureReason: String?
  ) : TelemetryEvent()
}

@Serializable
enum class DeviceClass { LOW, MID, HIGH, FLAGSHIP }
```

**Never in telemetry:**
- User identifiers (beyond ephemeral sessionId)
- File names, hashes, or any media-derived data
- Reason codes from specific verdicts (outcome only)
- Locations, IPs (beyond what HTTPS reveals to the endpoint)
- Network info beyond Wi-Fi vs cellular

---

## 5. Inter-module contracts

### 5.1 `DetectionPipeline` interface (domain-detection)

```kotlin
interface DetectionPipeline {
  fun scan(media: ScannedMedia): Flow<ScanStage>
  fun cancel()
}
```

Flow emits stages in order; terminal event is always `VerdictReady`, `Failed`, or `Cancelled`.

### 5.2 `Detector<T>` interface

```kotlin
interface Detector<in TInput, out TResult : DetectorResult> {
  suspend fun detect(input: TInput): TResult
}

sealed class DetectorResult {
  abstract val syntheticScore: Float    // 0.0–1.0
  abstract val confidence: Float         // 0.0–1.0 (not a range, raw confidence)
  abstract val reasons: List<Reason>
  abstract val elapsedMs: Long
}
```

Concrete detectors are internal to `data-detection`; the pipeline orchestrates them but the domain layer doesn't know the specific implementations.

### 5.3 Calibration contract

```kotlin
interface Calibrator {
  fun calibrate(rawScore: Float, detectorId: String): ConfidenceRange
}
```

Backed by bundled isotonic regression parameters (one set per detector), packaged with the model.

---

## 6. File & directory layout on device

```
context.filesDir/
  models/
    active/
      video_spatial_v2.4.1.tflite
      video_spatial_v2.4.1.sig
      ...
    previous/
      video_spatial_v2.3.0.tflite
      video_spatial_v2.3.0.sig
      ...
    manifest.json
    manifest.sig
  c2pa/
    trust_list.json
    trust_list.sig
  history/
    thumbnails/
      {historyItemId}.jpg

context.cacheDir/
  ingested/
    {scannedMediaId}.{ext}      # auto-purged 60s after verdict display
  scan_artifacts/
    {scanId}/                    # heatmap tensors etc., purged on activity destroy

```

---

## 7. Error taxonomy

Every user-facing error maps to a single `AppError` subclass. UI renders from this mapping, not from raw exceptions.

```kotlin
sealed class AppError {
  data object NoNetwork : AppError()                           // → screen 6.1
  data class ModelUpdateFailed(val cause: ModelUpdateFailCause) : AppError()  // → 6.2
  data object ModelUpdateRequired : AppError()                 // → 6.3
  data object FileTooLarge : AppError()                        // → 6.4
  data class DurationTooLong(val actualMs: Long, val limitMs: Long) : AppError()  // → 6.5
  data class UnsupportedFormat(val mimeType: String) : AppError()  // → 6.6
  data object CorruptedFile : AppError()                       // → 6.7
  data class AnalysisCrashed(val technical: String?) : AppError() // → 6.8
  data class C2PAInvalidSignature(val claimedIssuer: String?) : AppError() // → 6.9 (reason, not hard error)
  data class C2PARevoked(val issuer: String) : AppError()      // → 6.10
  data object StorageFull : AppError()                         // → 6.11
  data object NpuUnavailable : AppError()                      // → 6.12 (informational)
  data object DeviceUnsupported : AppError()                   // → 6.13
}

enum class ModelUpdateFailCause { NETWORK, SIGNATURE_INVALID, STORAGE_FULL, UNKNOWN }
```

---

## 8. What's NOT specified here

- UI state classes (per-screen `State` data classes live in each feature module; they consume these contracts but define their own View shapes)
- Repository implementations (per feature, wrap Room/DataStore/detection pipeline into feature-specific facades)
- DI modules (Hilt configuration per module)
- Build-type config (debug vs release vs debug-gallery)

These are implementation details. The data contracts above are the stable public surface between modules.
