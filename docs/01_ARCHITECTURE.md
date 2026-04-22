# Veritas — Technical Architecture
**Version:** 1.0 (v1 spec)
**Target:** Android 14+ (API 34+), Android-first, single-developer + AI coding assistant
**Status:** Implementation-ready specification
**Last updated:** April 2026

---

## 0. How to read this document

This spec is written to be executed against by an AI coding assistant with minimal ambiguity. Every section answers the questions: *what are we building, why this way, what are the constraints, and what are the failure modes.* When a decision has multiple valid options, I call out the chosen one and the tradeoff.

Conventions:
- **MUST / SHOULD / MAY** follow RFC 2119.
- **[v1]** = ship in first release. **[v1.1]** = fast follow. **[vNext]** = deferred.
- Anything marked **⚠️ RISK** is a known trap — do not skip the mitigation.

---

## 1. Product thesis

Veritas is an Android accessibility utility that helps users determine whether a piece of media (video, audio clip, or image) they encounter on their phone is AI-generated, authentically captured, or uncertain. It runs on-device, preserves privacy, and integrates into the normal flow of scrolling social apps without requiring the user to leave that app.

**Core promise:** *"Before you believe it, tap Veritas."*

**What Veritas is NOT:**
- Not a content moderation tool. We do not remove, report, or block anything.
- Not a truth oracle. We output calibrated probabilities + explanations, never "TRUE" or "FAKE" as absolutes.
- Not a cloud service. All inference runs on-device by default.
- Not a deepfake detector alone — it also verifies *authentic* content via C2PA/Content Credentials, which is the growing positive-signal side of the problem.

---

## 2. v1 scope (explicit)

| In scope | Out of scope for v1 |
|---|---|
| Share-sheet entry point (primary) | iOS |
| Floating overlay bubble (power-user mode, feature-flagged) | Real-time livestream analysis |
| Video file analysis (MP4, WebM, short clips ≤ 60s) | Long-form video (>60s; deferred to v1.1) |
| Audio file analysis (MP3, AAC, WAV, M4A, ≤ 3 min) | Text-based AI detection |
| Image analysis (JPEG, PNG, WebP, HEIC) | Browser extension |
| C2PA / Content Credentials verification | User accounts, cloud sync |
| On-device inference via LiteRT (Play Services) | Social graph features |
| Explainable output (heatmaps, timestamps, reason codes) | Live reporting to third parties |
| Signed model updates via Play delivery | Real-time camera-capture verification (vNext) |

---

## 3. The three-verdict system (design primitive)

False positives destroy trust faster than missed detections. Veritas never returns a binary fake/real. It returns one of three verdicts with an explicit confidence interval:

1. **LIKELY AUTHENTIC** — C2PA signature verified OR all detector ensembles score below fake threshold with high agreement.
2. **LIKELY SYNTHETIC** — multiple detectors agree above high-confidence threshold, OR known generator fingerprint matched.
3. **UNCERTAIN** — disagreement between detectors, low-quality input, unusual compression, or out-of-distribution content. **This is a first-class verdict, not a failure mode.**

The UI MUST surface "Uncertain" prominently and explain *why* it is uncertain (e.g., "heavy compression prevents reliable analysis"). A detector that returns "Uncertain" 15% of the time on real-world input is healthier than one forced into a binary and wrong 8% of the time.

---

## 4. System architecture (high level)

```
┌─────────────────────────────────────────────────────────────────┐
│                         ENTRY POINTS                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ Share Sheet  │  │  Overlay     │  │  Direct open in app  │   │
│  │ (primary)    │  │  (power-user)│  │  (pick from gallery) │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘   │
└─────────┼─────────────────┼─────────────────────┼───────────────┘
          │                 │                     │
          ▼                 ▼                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      INGESTION LAYER                             │
│  • Media type detection (magic bytes, not extension)             │
│  • Size / duration / resolution guards                           │
│  • Temporary scoped storage (auto-purge 60s after verdict)       │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                   PRE-FLIGHT VERIFICATION                        │
│  • C2PA manifest check (fast path — if verified, skip detectors) │
│  • Known-generator watermark scan (SynthID, etc.)                │
│  • Provenance hash lookup (local cache of known-real hashes)     │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DETECTION PIPELINE                            │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌───────────┐   │
│  │   VIDEO    │  │   AUDIO    │  │   IMAGE    │  │  FUSION   │   │
│  │  ensemble  │  │  ensemble  │  │  ensemble  │  │  & calib. │   │
│  └────────────┘  └────────────┘  └────────────┘  └─────┬─────┘   │
└───────────────────────────────────────────────────────┼─────────┘
                                                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    EXPLANATION LAYER                             │
│  • Spatial heatmaps (Grad-CAM over ViT)                          │
│  • Temporal flag timeline (video / audio)                        │
│  • Human-readable reason codes (e.g., "lip-sync drift @ 0:04")   │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                         OUTPUT / HUD                             │
│  • Verdict card (Authentic / Synthetic / Uncertain + confidence) │
│  • Tap-to-expand forensic view                                   │
│  • "Why?" drawer with reason codes                               │
│  • Dismiss returns user to originating app                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Entry points

### 5.1 Share-sheet intent (primary) [v1]

This is the default, Play-Store-safe flow. Veritas registers as a `Intent.ACTION_SEND` / `ACTION_SEND_MULTIPLE` handler for `video/*`, `audio/*`, and `image/*` MIME types.

User flow:
1. User sees suspect media in TikTok / Instagram / WhatsApp / Telegram.
2. Taps the native share button.
3. Taps "Veritas" in the share sheet.
4. Veritas opens a lightweight verdict activity, runs analysis, shows result.
5. Back gesture returns user to the originating app.

Implementation notes:
- Declare intent filters in `AndroidManifest.xml` for each MIME category.
- Use `ShareTarget` (Android 11+) so Veritas shows in the Direct Share row for frequently-checked content.
- Handle `content://` URIs via `ContentResolver.openInputStream()` — never require `READ_EXTERNAL_STORAGE`.
- Copy to app-private scoped storage before analysis; delete 60s after verdict display or on activity destroy, whichever is first.

⚠️ RISK: Some apps (notably TikTok) strip share intents or only allow sharing a link. Mitigation: the overlay path (5.2) and a "paste link" fallback that resolves a public URL to its CDN file.

### 5.2 Floating overlay bubble (power-user mode, feature-flagged) [v1]

Gated behind a clear onboarding flow that explains the permissions and risks. **Must be opt-in and never the default.**

- Uses `SYSTEM_ALERT_WINDOW` permission.
- Service runs as a `FOREGROUND_SERVICE_MEDIA_PROJECTION` (Android 14 requirement) with a persistent notification — this is non-negotiable for Play compliance.
- On tap: requests `MediaProjection` token (Android re-prompts for this; we cannot cache consent beyond the session on Android 14+).
- Captures a short buffer (default 3 seconds, not 1 — 1s is too short for temporal analysis) via `ImageReader` + `AudioPlaybackCaptureConfiguration`.
- Audio capture via `AudioPlaybackCapture` requires the target app to not have opted out (`allowAudioPlaybackCapture="false"` in their manifest). Gracefully degrade to video-only if audio is blocked.

⚠️ RISK (Play Store): Overlay + screen-capture combos are heavily scrutinized. Mitigations:
- Clear, single-purpose disclosure on first launch.
- No capture without an explicit user tap — zero background capture.
- Data-safety form filled to match reality: captures are processed locally and purged.
- Prepare a privacy policy that explicitly disclaims network egress of captured frames.
- Have the share-sheet path work standalone so the app has value even if overlay is later restricted.

### 5.3 Direct in-app analysis [v1]

Open the Veritas app, pick a file from `ACTION_OPEN_DOCUMENT`, analyze. Useful for files received via download or saved from DMs.

### 5.4 Accessibility Service path [v1.1]

An `AccessibilityService`-based capture mode as a third fallback if `SYSTEM_ALERT_WINDOW` is ever restricted further. Lower fidelity (no pixel capture, only metadata and on-screen text), but survives permission tightening. Implement the interface in v1 as a stub; ship behind flag in v1.1.

---

## 6. Detection pipeline

### 6.1 Pre-flight: provenance before detection

Run provenance checks **before** ML detectors. They are faster, more reliable, and produce definitive positive signals.

**C2PA / Content Credentials [v1]**
- Use the official `c2pa-rs` library via JNI bindings (Rust → Kotlin).
- Parse embedded manifests from JPEG, PNG, MP4 (ISO-BMFF box), WebP.
- Validate signature chain against the C2PA trust list (bundled + updated via Play delivery).
- If valid and signed by a known capture device or editing tool, return `LIKELY AUTHENTIC` with the signing entity name.
- Cache trust-list updates; handle revocation.

**SynthID and known-generator watermarks [v1]**
- Google SynthID (image + audio + video) detector API — bundled model, runs locally.
- If SynthID-text or SynthID-image detected → strong `LIKELY SYNTHETIC` signal with generator attribution.
- Also check for known watermarks from major generators where public detection is available.

**Hash lookup [v1.1]**
- Local bloom filter of known-real media hashes (newsroom outputs, verified sources). Small (<5 MB), updated weekly.
- Local bloom filter of known-synthetic hashes (widely-circulated deepfakes flagged by research orgs).
- Matches are strong signals but not definitive (same-frame re-encodes may hash differently).

### 6.2 Video detection ensemble [v1]

No single model is robust. Ship an ensemble of three small specialists whose outputs are fused.

1. **Spatial artifact detector** — lightweight Vision Transformer (**MobileViT-v2** from Apple CVNets; ~5M params, quantized INT8). Looks at per-frame texture, frequency-domain artifacts (DCT / FFT residuals), facial landmark consistency (ear/jewelry/teeth glitches). This is the same model family as the Phase 7 image detector — reuse it frame-by-frame rather than training a separate spatial branch.
2. **Temporal consistency detector** — **MoViNet-style efficient 3D CNN** (A0 or A1 stream variant) over sampled frames. Detects "skin crawl," unnatural optical flow, frame-to-frame identity drift. MoViNet has official pre-trained TFLite checkpoints, is mobile-designed, and supports streaming inference. **Do not use TimeSformer as the runtime model** — it's a research architecture whose official repo is archived. TimeSformer may be used as an *offline teacher/benchmark* during training, not in the shipped model.
3. **Physiological signal detector** — classical remote PPG (CHROM / POS-style) after MediaPipe Face Landmarker extracts the facial ROI, with a small classifier on top. Real human faces show subtle color pulsing from blood flow (~1Hz). AI-generated faces typically don't, or show implausible patterns. Free signal, hard to fake, small compute cost. There is no turnkey Android rPPG-deepfake SDK — this is glue code around established classical techniques.

**Face detection/landmarking:** MediaPipe Face Detector and Face Landmarker are used to extract facial ROIs for the spatial and rPPG detectors. Do not re-implement face detection.

**Fusion:** calibrated logistic regression or small MLP over the three specialist scores + metadata features (codec, resolution, bitrate anomalies, re-encode count). Calibrated via Platt scaling or isotonic regression so output confidence is meaningful, not raw softmax.

**Frame sampling strategy:** analyze up to 60 sampled frames for videos ≤ 60s. Uniform sampling for short clips, scene-change-based sampling for longer content via simple histogram-difference shot detection.

**Important:** there is no existing packaged "Android video deepfake SDK" that ships this ensemble end-to-end. Phase 9 is substantially glue code around MediaPipe (face), MoViNet (temporal), the reused MobileViT (spatial), and a hand-built rPPG classifier. Budget accordingly.

### 6.3 Audio detection ensemble [v1]

Audio deepfakes (voice cloning, synthetic speech) are arguably the highest-impact threat right now — phone scams, fake political clips, fake audio "evidence." Treat as equal priority to video.

1. **Spectral artifact detector** — CNN over log-mel spectrograms. Detects artifacts from neural vocoders (HiFi-GAN, WaveNet, diffusion-based TTS). Small (~3M params). Runtime: LiteRT; optionally wrapped by MediaPipe `AudioClassifier` / `AudioEmbedder` if model metadata is authored for it.
2. **Prosody / pacing detector** — analyzes rhythm, phoneme duration distributions, pause patterns. Synthetic speech tends toward over-regular prosody.
3. **Codec fingerprint check** — real phone audio carries predictable codec artifacts (AMR, Opus at specific bitrates). Synthetic audio dropped into a "phone recording" context often mismatches.
4. **Speaker consistency** (if clip > 10s) — embedding drift within a single speaker segment.

**Training baselines (offline, NOT shipped):** **AASIST** (official repo) as an anti-spoofing baseline; SSL / wav2vec-based anti-spoofing directions are heavier but worth evaluating. Evaluate against **ASVspoof** benchmark families. These are training/benchmark tools; the shipped mobile model is a compact distilled classifier, not the research model directly.

Fusion: same pattern as video.

### 6.4 Image detection ensemble [v1]

1. **Frequency-domain detector** — FFT / DCT of image; GAN and diffusion outputs have characteristic spectral signatures (e.g., grid patterns from upsampling layers). Cheap, surprisingly robust against current-gen models. Hand-built — this is a classical-signal feature extractor plus a small classifier.
2. **Spatial ViT detector** — **MobileViT-v2** from Apple CVNets, INT8 quantized, exported to LiteRT.
3. **Metadata & compression forensics** — EXIF plausibility (camera model vs. image properties), quantization-table fingerprinting, error-level analysis (ELA). Metadata alone isn't a verdict but contributes to fusion.

**Training baselines (offline, NOT shipped):** **UniversalFakeDetect** is a clean open implementation that serves as a baseline or teacher for knowledge distillation into our mobile model. It is a research PyTorch repo, not an Android runtime dependency. **DeepfakeBench** and **DF40** are the broad evaluation harnesses — use these offline to measure performance across many datasets before each model release. The shipped mobile model is a compact distilled/fine-tuned MobileViT-v2 variant, never the research model directly.

### 6.5 Training vs. runtime — a distinction the agent must internalize

The research ecosystem (UniversalFakeDetect, AASIST, DeepfakeBench, DF40, TimeSformer, pyVHR) is for **offline training, evaluation, and distillation**. These are PyTorch / TensorFlow training repos. They do not run on Android and must never be imported into the Android project.

The runtime ecosystem (LiteRT in Play Services, MediaPipe Tasks, MoViNet TFLite checkpoints, MobileViT-v2 converted to TFLite, custom INT8 classifiers) is what ships in the APK / feature module.

The pipeline between them is: train/distill offline using research tools → convert to TFLite with proper metadata → ship via signed model delivery → run through LiteRT at inference time. The agent must never blur this distinction or try to port a research repo into the mobile app.

### 6.6 Calibration and thresholds

- Thresholds for the 3 verdicts are **not** fixed at `0.5` / `0.8`. They are set per-model via isotonic regression on a held-out validation set targeting: false-positive rate on authentic content ≤ 1%, false-negative rate on known synthetic ≤ 15%. Favor the FP floor — missing a fake is recoverable, falsely accusing real content is not.
- "Uncertain" band is the region where any detector abstains or where the fusion confidence interval straddles the authentic/synthetic threshold.

### 6.7 Adversarial robustness

⚠️ RISK: Once Veritas is public, generators will be tuned to evade it. Mitigations:

- **Don't rely on any single fragile feature.** Ensembles + physiological signals (rPPG) are harder to spoof jointly than one pixel-level detector.
- **Frequency-domain features** are harder to remove cleanly than spatial ones, especially after platform re-encoding.
- **Model rotation:** ship model updates every 4–8 weeks, sometimes with architectural changes so attackers can't over-fit to a fixed target.
- **Canary watermark models** — keep an unreleased detector in rotation that we swap in occasionally, so attackers can't be sure which model they're evading.
- **Server-side confirmation for high-stakes cases** (opt-in, v1.1) — if a user explicitly requests, send hashes (not pixels) for cross-check against a server-side larger model. Off by default to preserve the zero-cloud promise.

---

## 7. Explanation layer (XAI)

A verdict without explanation is as useless as the black-box detectors this project exists to replace. Every verdict MUST produce:

1. **Primary reason** — one sentence, plain language. Example: *"Inconsistent lighting on the face across frames 40–90, typical of video diffusion artifacts."*
2. **Supporting evidence** — up to 3 bullet points with concrete signals.
3. **Visual overlay** — Grad-CAM heatmap over the suspect frame/region, or waveform annotation for audio.
4. **Temporal timeline** (video/audio) — strip showing per-second confidence; user can scrub to flagged moments.
5. **Confidence band, not a point estimate** — display "65–78% likely synthetic" rather than "71.4% fake." Communicates real uncertainty.
6. **Reason codes** (machine-readable, for later use) — `LIP_SYNC_DRIFT`, `RPPG_ABSENT`, `DIFFUSION_SPECTRAL_SIG`, `C2PA_VERIFIED_NIKON`, etc. Stored only in the current session; useful for debugging and for the future "appeal / rescan" feature.

Generate reason text from templates keyed on reason codes, not from an LLM — deterministic, fast, translatable.

---

## 8. Technical stack

### 8.1 Platform

- **Min SDK:** 30 (Android 11) — covers ~92% of active devices in 2026.
- **Target SDK:** 35 (Android 15).
- **Language:** Kotlin 2.x, coroutines + Flow for async.
- **UI:** Jetpack Compose + Material 3 Expressive.
- **Build:** Gradle with version catalogs, KSP over KAPT.

### 8.2 ML runtime

- **Primary:** LiteRT (formerly TensorFlow Lite) distributed via **Google Play Services** (`com.google.android.gms:play-services-tflite-java`). This path is updatable outside app releases and is the Google-recommended replacement for bundled TFLite.
- **Hardware acceleration:** GPU delegate by default (`play-services-tflite-gpu`). On Qualcomm SoCs where it is available and tested to outperform GPU, a vendor delegate (e.g., Qualcomm QNN) may be added in v1.1 as an opt-in per-device. Do NOT use the NNAPI delegate as the primary acceleration path: **NNAPI is deprecated as of Android 15.**
- **Fallback chain:** GPU delegate → XNNPACK (CPU). The app must run correctly on CPU alone.
- **Quantization:** INT8 post-training for all models; keep a FP16 variant on disk for devices where INT8 paths regress accuracy (measured per-chipset in model release).
- **MediaPipe Tasks** (`com.google.mediapipe:tasks-vision`, `tasks-audio`) are used where convenient — specifically for face detection / face landmarking and as a clean wrapper around audio classifiers — but LiteRT direct is always a viable fallback, and we never take a hard dependency on MediaPipe APIs we could implement ourselves.
- **Benchmarking:** every model release ships with a benchmark harness that measures latency + accuracy across a device matrix (Pixel 8, S24, S25, mid-range Snapdragon 7-series, Tensor G3/G4). Regressions block release. Since NPU access paths vary by vendor post-NNAPI, device-specific measurement is non-negotiable — do not assume hardware acceleration works uniformly.

### 8.3 Model management

⚠️ This is the single most important piece of non-obvious infrastructure. Deepfake generators improve every 2–3 months. Static models ship dead.

- **Delivery:** models are NOT in the APK. They ship as separate assets via Play Feature Delivery or a signed CDN download on first run and during scheduled refresh.
- **Signing:** all model blobs signed with an Ed25519 key held by the release process; device verifies signature before loading. Prevents model-swap attacks.
- **Versioning:** each model has `{id, version, arch_hash, quant, target_chip_family}`. Device loads the best-match available variant.
- **Rollout:** staged rollout (1% → 10% → 50% → 100%) with a per-device metrics channel reporting only aggregated verdict distribution + crash telemetry (opt-in; no media, no hashes).
- **Rollback:** previous model kept on-device until N+1 passes a health check.
- **Trust-list updates** (for C2PA) delivered via the same channel, independent of app updates.

### 8.4 Media handling

- **Video decode:** MediaCodec with hardware decoder; pull frames via `ImageReader` in YUV_420_888.
- **Audio decode:** MediaExtractor + MediaCodec; resample to 16 kHz mono for audio models.
- **Color / format:** keep a single normalization path from decoder → tensor; color-space mismatches are a common silent bug source.

### 8.5 Storage and privacy

- App-private directory only (`context.filesDir` / `cacheDir`).
- Scoped storage; no `MANAGE_EXTERNAL_STORAGE`.
- Captured media deleted 60s after verdict display or on activity destroy.
- No analytics SDK that sees media. Telemetry (if user opts in) is verdict-distribution + device-class only, sent via a minimal first-party endpoint, not Firebase Analytics.
- Android Private Compute Core integration for any on-device model that benefits from it (rPPG specifically — PCC's sealed compute environment is a plus for biometric-adjacent signals).

### 8.6 Architecture pattern

- Single-activity app with Compose Navigation.
- Clean architecture: `data/`, `domain/`, `ui/` — keep the detection pipeline in a pure-Kotlin module so it's testable without Android runtime.
- DI: Hilt.
- Background work: WorkManager for model updates only; all user-initiated analysis runs in the foreground on a dedicated dispatcher.

### 8.7 Observability

- Crashlytics alternative that respects privacy (e.g., Sentry self-hosted or a first-party endpoint). No PII, no media.
- Structured logs behind a developer toggle.
- A "diagnostic export" the user can generate manually — includes verdict, reason codes, model versions, device info, **no media** — for support.

---

## 9. Permissions model

| Permission | Why | When requested | Optional? |
|---|---|---|---|
| `INTERNET` | Model updates, C2PA trust list | Install | No |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Overlay capture | First overlay use | Yes (power-user) |
| `SYSTEM_ALERT_WINDOW` | Overlay bubble | First overlay use | Yes (power-user) |
| `POST_NOTIFICATIONS` | Foreground-service notification, model-update complete | First run | Graceful degrade |
| `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` / `READ_MEDIA_IMAGES` | Direct-open-from-gallery mode | When user picks file | Yes |

Never request storage, contacts, location, microphone, or camera. If the user-picker suffices for in-app mode (and it does on Android 13+ via Photo Picker), prefer it over declaring media permissions at all.

---

## 10. UX principles (non-negotiable)

1. **Time-to-verdict ≤ 4 seconds** on a mid-range 2024 Snapdragon device for a 10-second video. Measure this. It governs sampling and model-size choices.
2. **No modal in the user's face.** Verdict appears as a card that can be dismissed with a swipe. The originating app is visible behind it in overlay mode.
3. **"Uncertain" is never scary.** Neutral tone, neutral color. Explain why.
4. **No red/green color-only signaling.** Use icons + text; ~8% of users have some color-vision deficiency.
5. **One-sentence verdict, always.** Details are one tap away.
6. **Offline-first.** If model update fails, the last good model keeps working.
7. **Never auto-share or auto-post.** Verdict is for the user; distribution is never automated.
8. **Accessibility:** full TalkBack support, verdict content is readable as a single utterance, heatmaps have text alternatives.

---

## 11. Testing strategy

- **Unit tests:** fusion math, calibration, manifest parsers, verdict thresholding — 100% on the pure-Kotlin detection module.
- **Instrumented tests:** MediaCodec paths, share-intent handling, overlay permission flow on Android 11–15 emulators.
- **Golden dataset:** maintain a private test set of ~2000 items (video/audio/image, real + synthetic, multiple generators, multiple compression levels). Track per-release: accuracy, FPR, FNR, uncertain-rate, per-device latency. **A model release that regresses FPR on authentic content beyond 1.5% is blocked even if overall accuracy improves.**
- **Adversarial evaluation:** a small suite of known evasion techniques (recompression, frame-rate change, crop, rotation, mild Gaussian blur, low-bitrate re-encode). Accuracy must degrade gracefully, not fall off a cliff.
- **Red-team cadence:** every 6 weeks, attempt to break current models with latest public generators. Results feed the next training cycle.

---

## 12. Threat model (short version)

| Threat | Mitigation |
|---|---|
| Generator tuned to evade the static model | Ensemble + rotation + canary models (§6.7) |
| Attacker swaps a malicious model on device | Ed25519-signed models; verify on load (§8.3) |
| Social engineering: attacker claims Veritas "approved" a fake | Shareable verdicts include a local-only timestamped signature the user can regenerate; we don't publish "verified" badges |
| User over-trusts verdict | "Uncertain" verdict is first-class; confidence bands, not point estimates |
| Privacy attack: adversary tries to exfiltrate captured frames | No network egress of media, ever. Privacy policy + data-safety form align. Code-level enforcement: the network module cannot see the media module's buffers |
| Platform restricts `SYSTEM_ALERT_WINDOW` further | Share-sheet path works standalone; Accessibility-Service fallback (§5.4) ready |
| Generator adds a fake C2PA manifest | Signature chain validation against trust list; unknown signers don't produce `LIKELY AUTHENTIC` |

---

## 13. Things I deliberately did NOT include (and why)

- **A blockchain.** C2PA already solves provenance via PKI. Blockchain adds nothing and costs latency + UX.
- **A user-reputation system.** Out of scope; invites brigading.
- **A "report to platform" button.** We are a detection tool, not a moderation pipeline. Users can use platform-native reporting.
- **Cloud inference by default.** Violates the privacy thesis. Offered as opt-in second opinion in v1.1 only.
- **A confidence score above 95%.** The state of the art doesn't support that honesty-wise on real-world input. Cap displayed confidence at a truthful ceiling.

---

## 14. Open questions for the developer

These are decisions I'm flagging rather than pre-empting:

1. **Monetization model.** Free + donation, one-time purchase, or freemium (cloud second-opinion as paid tier)? Affects Play listing category and data-safety disclosures.
2. **Languages at launch.** English only v1? Reason-code templates are easy to translate but the onboarding copy is load-bearing.
3. **Branding of the "Uncertain" verdict.** "Uncertain" is honest; "Inconclusive" is slightly softer; "Needs more info" is softer still. Pick and A/B if possible.
4. **Model-update network policy.** Wi-Fi only by default (respects data caps) or any network? Recommend Wi-Fi default with user toggle.
5. **Do we expose reason codes in the UI or only in the diagnostic export?** Leaning: plain-language only in UI, codes only in export.

---

## 15. Milestone plan (suggested, solo + AI assistant)

| Phase | Deliverable | Weeks |
|---|---|---|
| 0. Scaffold | Kotlin project, Compose UI shell, share-sheet intent, file picker, CI | 1 |
| 1. Media ingest + decode | Video/audio/image decode paths, normalization, scoped storage, 60s purge | 1 |
| 2. C2PA + SynthID pre-flight | c2pa-rs JNI, SynthID detection, verdict plumbing for authentic fast-path | 2 |
| 3. Image detector | Frequency-domain + MobileViT, calibration, golden-set eval | 2 |
| 4. Audio detector | Spectral + prosody, fusion, calibration | 2 |
| 5. Video detector | Spatial + temporal + rPPG, fusion, calibration | 3 |
| 6. Explanation layer | Heatmaps, timeline, reason codes, verdict card UI | 2 |
| 7. Overlay mode | MediaProjection + foreground service + onboarding + permissions | 2 |
| 8. Model delivery | Signed model pipeline, Play Feature Delivery, rollback | 1 |
| 9. Hardening | Adversarial eval, privacy audit, Play Store pre-review, data-safety form | 2 |
| 10. Closed beta → launch | Staged rollout, telemetry review, fix loop | 2–4 |

Total: roughly 5–6 months of focused work. Realistic for solo + capable AI assistant if scope doesn't creep.

---

## 16. Appendix: things your coding assistant will ask about

- **Where do the initial models come from?** Start with published open-weight detectors (e.g., open deepfake detection checkpoints from academic groups). Fine-tune on the golden set. Do not rely on any single upstream; build the ensemble with heterogeneous sources from day one.
- **C2PA library.** `c2pa-rs` is the canonical Rust implementation. JNI bridge is a small amount of glue; alternative is the Java port if it has stabilized by implementation time — verify at start of phase 2.
- **Why 3 seconds, not 1, in overlay capture?** Temporal models need at least 16 frames @ 24–30 fps to produce reliable signals. 1 second often captures only motion-blur and scene transitions.
- **Why no Jetpack Compose for the overlay?** You can technically use it, but the floating-window lifecycle is awkward with Compose's recomposition model. Use classic `View` subclasses for the overlay surface, Compose for the verdict activity. Pragmatism over purity.
