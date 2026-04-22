# Veritas — Glossary & Decisions
**Version:** 1.0
**Companion to:** all other docs
**Purpose:** Pin terminology so the agent and humans use words the same way. Track decisions made during build so we don't lose context over a 6-month project.

---

## Part 1 — Glossary

Terms used consistently across the codebase and documentation. When the agent writes comments, log messages, variable names, or user-facing copy, it matches these definitions.

### Detection terminology

**Detector** — a single ML model or classical-signal algorithm that outputs a score. Examples: the spatial ViT is a detector, the rPPG signal extractor is a detector. Detectors live in `data-detection`.

**Ensemble** — a set of detectors whose outputs are combined by a fusion model to produce a single score for one media type. Veritas has three ensembles: video, audio, image.

**Fusion** — the learned combination function over ensemble detector outputs. Usually a small MLP or logistic regression. Produces a raw synthetic-score that is then calibrated.

**Calibration** — mapping a raw fusion score to a well-behaved probability via isotonic regression or Platt scaling. Post-calibration, a score of 0.8 means roughly 80% empirical likelihood of synthetic across held-out data.

**Pipeline** — the full sequence: ingestion → pre-flight (C2PA/SynthID) → ensemble → fusion → calibration → verdict. The pipeline is pure orchestration; it knows *which* detectors to run but not *how* they work.

**Pre-flight** — provenance checks (C2PA, SynthID) that run before ML detection. Fast, deterministic, can short-circuit the pipeline when definitive.

**Reason code** — an enum value representing one contributing signal to a verdict. Example: `LIP_SYNC_DRIFT`. Displayed as evidence chips and full descriptions in forensic view. Never use a reason code as a user-facing string directly — use the templated plain-language description.

**Confidence range** — the `low%–high%` interval on a verdict. Derived from calibration + fusion variance. Never a point estimate.

**Uncertain verdict** — first-class output, not a fallback or error. Triggered by low-quality input, detector disagreement, or fusion variance straddling thresholds.

### UX terminology

**Verdict screen / verdict activity / verdict card** — the screen that shows the outcome of a scan. Three variants by outcome. Screens 2.5, 2.6, 2.7.

**Forensic view** — the deeper view with heatmap, timeline, and reason codes. Screen 2.8.

**Overlay** — the floating bubble system. Distinct from other meanings of "overlay" in Android. When we say "overlay mode," we mean "the floating bubble feature."

**Bubble** — the circular tap target that floats over other apps. One component of overlay mode.

**Toast** — the compact verdict surface that appears over the host app after a bubble scan. Distinct from Android's `Toast` API — we render our own. Visual spec §3.3.

**Share-sheet path** — the primary entry flow: user taps Share → Veritas in any app. Not to be confused with Android Sharesheet the API.

**Direct-open path** — user opens Veritas app, picks file via system picker.

**Host app** — the social or messaging app the user is viewing when they trigger Veritas (TikTok, Instagram, WhatsApp, etc.). Not a Veritas component.

### Architecture terminology

**Scoped storage** — Android's app-private file storage, not accessible to other apps or requiring `READ_EXTERNAL_STORAGE`. All media Veritas handles lives in scoped storage briefly and is auto-purged.

**Auto-purge** — the 60-second post-verdict deletion of ingested media. Enforced via WorkManager. Non-negotiable per privacy commitment.

**Inference hardware** — the chip executing model inference. GPU (preferred, via LiteRT GPU delegate) → CPU via XNNPACK (fallback). NPU access is possible on a per-vendor basis in v1.1 via vendor delegates (Qualcomm QNN etc.), but not assumed.

**LiteRT** — the rebranded name for TensorFlow Lite. Veritas uses LiteRT distributed via Google Play Services (`play-services-tflite-*`), not bundled TFLite. This is the current Google-recommended path after NNAPI deprecation in Android 15. The file extension for models remains `.tflite`.

**NNAPI** — the deprecated (Android 15) Neural Networks API. Veritas does NOT use the NNAPI delegate. If the agent encounters stale instructions suggesting NNAPI, treat as an error.

**Runtime ecosystem** — what ships in the APK: LiteRT via Play Services, MediaPipe Tasks (where convenient), and compact `.tflite` models. This runs on device.

**Training ecosystem** — what is used offline: PyTorch/TF research repos (UniversalFakeDetect, AASIST, DeepfakeBench, DF40, TimeSformer, pyVHR). These are for training, distilling, and benchmarking. **They never ship in the APK.** The pipeline between them is: train offline → convert to TFLite → ship via signed delivery → run via LiteRT.

**Model manifest** — signed JSON served from the Veritas model CDN describing available models. Distinct from C2PA manifest (which lives inside a media file).

**C2PA manifest** — Content Credentials metadata embedded in a media file describing its provenance. Per the C2PA spec.

**Trust list** — the set of signing authorities whose C2PA signatures Veritas recognizes as valid. Bundled + updatable via signed delivery.

**Rollout** — staged percentage-based release of a model update or feature flag. Deterministic per-device via rollout ID.

**Rollout ID** — stable UUID per install, compared to rollout threshold to determine inclusion.

**Pitfall** — a predicted mistake documented in a phase. If the agent sees something matching a pitfall, it stops and handles accordingly.

**Checkpoint** — end-of-phase human review point. Agent produces a completion report and waits.

### Anti-terminology

Words the documentation and user-facing copy must **not** use:

| Do NOT say | Instead say |
|---|---|
| "Fake" | "Synthetic" or "AI-generated" |
| "Real" | "Authentic" (for the verdict) or "not detected as synthetic" |
| "Detected as deepfake" | "Likely synthetic" |
| "Inconclusive" (as a verdict) | "Uncertain" |
| "Score: 87%" (point estimate) | "82–96% likely synthetic" (range) |
| "100% certain" | Nothing — we cap at 96% |
| "This is a fake" | "This looks synthetic" |
| "Confirmed authentic" (without C2PA) | "Looks authentic" |
| "Verified by Veritas" | Never — we are not a certifying authority |

---

## Part 2 — Product decisions (made during planning)

Decisions made during spec authoring that the agent honors during build. Listed chronologically.

### D-001 · Primary entry: share-sheet, not overlay
**Context:** User originally proposed floating overlay as primary flow.
**Decision:** Share-sheet is primary; overlay is opt-in power-user mode.
**Reasoning:** Play Store's increasing restrictions on `SYSTEM_ALERT_WINDOW` + MediaProjection combos; share-sheet works everywhere and is unlikely to be restricted.
**Reversal cost:** low — can promote overlay to primary in v2 if Play Store policies loosen.

### D-002 · v1 scope: video + audio + images
**Context:** Scope question at start of planning.
**Decision:** All three media types in v1.
**Reasoning:** Audio deepfakes are arguably the highest-impact threat (phone scams, voice-cloned clips). Shipping video-only in 2026 is a partial answer to a whole problem.
**Reversal cost:** high — cutting a media type mid-build wastes calibration work.

### D-003 · Three-verdict system with "Uncertain" as first-class
**Context:** Binary fake/real too brittle; false positives destroy trust faster than missed detections.
**Decision:** Verdicts are `VERIFIED_AUTHENTIC`, `LIKELY_AUTHENTIC`, `UNCERTAIN`, `LIKELY_SYNTHETIC`. Uncertain is a success state, not a failure.
**Reasoning:** Documented at length in architecture §3.
**Reversal cost:** high — baked into UI, pipeline, and copy.

### D-004 · Two-tier "authentic" verdict
**Context:** Initial spec had single "Likely authentic" verdict regardless of whether C2PA confirmed.
**Decision:** Split into `VERIFIED_AUTHENTIC` (C2PA-confirmed, strong green, issuer shown) and `LIKELY_AUTHENTIC` (no C2PA, detectors clean, softer green, no attribution).
**Reasoning:** Absence of synthetic signal is not presence of authenticity. Honest language.
**Reversal cost:** low — can collapse into single verdict if user research shows it's confusing.

### D-005 · Paste-link feature deferred to v1.1
**Context:** TikTok and similar platforms strip share intents, offering only link share.
**Decision:** Ship paste-link UI in v1 but stub the resolver; actual URL-to-media resolution is v1.1.
**Reasoning:** Platform-specific link resolution has legal/ToS complexity that is not worth blocking v1 launch on. Can be added without breaking changes.
**Reversal cost:** low.

### D-006 · History search and filters deferred
**Context:** Full history UX.
**Decision:** v1 ships a basic chronological list grouped by date. Search bar and filter chips are v1.1.
**Reasoning:** Low effort but they steal focus; chronological list is sufficient for the usage patterns we expect (users revisit recent verdicts mostly).
**Reversal cost:** low.

### D-007 · Confidence caps: 94% authentic, 96% synthetic
**Context:** Honest confidence ceilings.
**Decision:** Displayed high end of confidence range never exceeds 94% for authentic verdicts, 96% for synthetic.
**Reasoning:** SOTA on real-world input doesn't honestly support higher. Showing 99% when wrong 3% of the time destroys trust when wrong.
**Reversal cost:** medium — if we get significantly more accurate, caps can raise.

### D-008 · Overlay phase is #12, not earlier
**Context:** Build order question.
**Decision:** Overlay implementation comes after all core detection + forensic view + history + settings.
**Reasoning:** Share-sheet path works standalone and is Play-Store-safe. If overlay work blows up (Android API changes, Play Store feedback), we can ship v1 without it. Building overlay early would risk a product that collapses if overlay becomes infeasible.
**Reversal cost:** low — just a schedule question.

### D-009 · rPPG included in video ensemble
**Context:** Choosing detectors for video.
**Decision:** Include remote PPG (detecting blood-flow pulsing in faces) as a third video detector.
**Reasoning:** Cheap compute, hard to spoof, complements ML detectors. Architectural note: it's the signal that's most resistant to generator improvement because it encodes real biology.
**Reversal cost:** low — can remove if it causes more false negatives than expected.

### D-010 · Model delivery: signed, staged, rollback-capable
**Context:** How to ship model updates.
**Decision:** All models delivered via signed manifest + Ed25519 verification. Staged rollout, automatic rollback on health-check failure.
**Reasoning:** Static models ship dead against current-pace generator improvement. Without signed delivery, a compromised CDN could ship a bad model to all users.
**Reversal cost:** high — delivery pipeline is load-bearing security infrastructure.

### D-011 · No cloud inference in v1
**Context:** Option to offer server-side second opinion.
**Decision:** v1 is strictly on-device. Cloud second-opinion (opt-in) is v1.1 candidate.
**Reasoning:** Privacy thesis is the product's strongest differentiation. Adding cloud in v1 forces the privacy story into caveats and confuses Play Store data safety disclosures.
**Reversal cost:** medium.

### D-012 · Dark mode only for v1
**Context:** Light mode also supported?
**Decision:** Dark only for v1. Must not break if user forces light at OS level.
**Reasoning:** Aesthetic is anchored in dark instrumentation. Light mode doubles design surface for no clear user win.
**Reversal cost:** medium.

### D-013 · Telemetry strictly opt-in, post-first-scan prompt
**Context:** When/how to ask about anonymous telemetry.
**Decision:** Never asked during onboarding. Prompted once after first successful scan, deferrable to "never show" or "decide in settings."
**Reasoning:** Onboarding should not include optional asks that aren't required for core function. Asking after first scan means the user has context for what they'd be sharing data about.
**Reversal cost:** low.

### D-014 · Min SDK 30 (Android 11)
**Context:** Device support breadth.
**Decision:** Android 11+.
**Reasoning:** Covers ~92% of active devices in 2026. Android 10 and below introduce significant additional permission-handling complexity for diminishing user base.
**Reversal cost:** medium.

### D-015 · No user accounts, ever (for v1/v1.1)
**Context:** Cloud features, cross-device sync.
**Decision:** No user accounts, no sign-in, no cross-device history.
**Reasoning:** Privacy thesis. Every surface Veritas doesn't have is a surface it can't leak from.
**Reversal cost:** high — introducing accounts later requires honest re-disclosure of data handling.

### D-016 · LiteRT via Play Services, not NNAPI
**Context:** Initial spec called for NNAPI delegate as the primary hardware acceleration path.
**Decision:** Use LiteRT distributed via Google Play Services (`play-services-tflite-java` + `play-services-tflite-gpu`) as the primary ML runtime. GPU delegate is the default accelerator; XNNPACK is the CPU fallback. Vendor delegates (e.g., Qualcomm QNN) are v1.1 opt-in only.
**Alternatives considered:** (a) Bundled TFLite with NNAPI delegate — rejected because NNAPI was deprecated in Android 15 and Google explicitly recommends migration. (b) Bundled TFLite without Play Services — rejected because it loses the benefit of runtime updates outside app releases.
**Reasoning:** NNAPI is deprecated. The Play Services path is Google-recommended, updatable, and covers the vast majority of target devices. Accept the runtime dependency on Play Services in exchange for a supported acceleration path.
**Reversal cost:** medium — swapping runtimes later is possible but touches every detector integration.
**Approved by human:** y, April 2026 (second-opinion review)

### D-017 · MoViNet (not TimeSformer) as temporal video detector
**Context:** Initial spec proposed "TimeSformer-Lite or 3D-CNN" for the temporal branch of the video ensemble.
**Decision:** Use MoViNet (A0 or A1 stream variant) as the shipped temporal model. TimeSformer may be used as an offline teacher for distillation, never in the shipped APK.
**Alternatives considered:** (a) TimeSformer — rejected because the official repo is archived and it was never designed for Android. (b) Custom 3D-CNN from scratch — rejected because MoViNet has pre-trained Kinetics-600 checkpoints and mobile-optimized streaming architecture already, which saves months.
**Reasoning:** MoViNet was designed as a mobile video network with official TFLite checkpoints and streaming support. It's the right default. Teacher models live offline.
**Reversal cost:** medium.
**Approved by human:** y, April 2026 (second-opinion review)

### D-018 · Training ecosystem vs. runtime ecosystem — strict separation
**Context:** Risk that the agent treats research repos (UniversalFakeDetect, AASIST, DeepfakeBench, DF40, TimeSformer, pyVHR) as importable Android dependencies.
**Decision:** Research repos are used offline only for training, distillation, and evaluation. They never ship in the APK. The shipped runtime consists of: LiteRT via Play Services, MediaPipe Tasks (where convenient), and compact TFLite models distilled or fine-tuned from the offline research work.
**Alternatives considered:** Attempting to port research models directly — rejected, they are too large, wrong framework, and not mobile-optimized.
**Reasoning:** Budgeting wrong here is the single most common mistake in mobile-ML projects. Making the separation explicit avoids weeks of wasted effort.
**Reversal cost:** n/a — this is a methodology decision, not an architectural one.
**Approved by human:** y, April 2026 (second-opinion review)

---

## Part 3 — Decision log (to be filled during build)

The agent adds entries here whenever it makes a decision during build that:
1. Resolves an ambiguity in the spec
2. Chooses between multiple viable implementations
3. Deviates from a pitfall recommendation with justification
4. Introduces a dependency or pattern not explicitly listed in the plan
5. Modifies a data contract

**Format:** same as Part 2. The `D-` prefix continues from D-018. Human reviews these at each phase checkpoint.

### Template

```markdown
### D-XXX · [Short title]
**Phase:** N
**Date:** YYYY-MM-DD
**Context:** [what situation prompted this]
**Decision:** [what was chosen]
**Alternatives considered:** [what else was on the table]
**Reasoning:** [why this and not the others]
**Reversal cost:** [low / medium / high — how hard to change later]
**Approved by human:** [y/n, date]
```

### D-019 Â· compileSdk 36 while retaining targetSdk 35
**Phase:** 0
**Date:** 2026-04-22
**Context:** The Phase 0 plan set `targetSdk` to 35. The current stable AndroidX and Compose libraries selected for the scaffold require `compileSdk` 36 even though the app can still target API 35.
**Decision:** Set `compileSdk = 36`, keep `targetSdk = 35`, and keep `minSdk = 30`.
**Alternatives considered:** Pin older AndroidX and Compose versions that still compile against SDK 35; move both `compileSdk` and `targetSdk` to 36 immediately.
**Reasoning:** This keeps the app aligned with the phase plan's `targetSdk` while avoiding unnecessary pinning to stale AndroidX artifacts. `compileSdk` affects compilation compatibility, not shipped runtime behavior on its own.
**Reversal cost:** low â€” dependency and SDK alignment live in the version catalog and module Gradle files, so this can be changed in one pass later if needed.
**Approved by human:** pending checkpoint, 2026-04-22

---

## Part 4 — Open questions

Questions that remain unresolved at start of build. The agent should revisit these at the relevant phase and either resolve (add to decision log) or escalate to human.

### OQ-001 · Monetization model
**Asked by:** Claude (architecture doc §14)
**When to resolve:** before Phase 16 (launch prep)
**Options:**
- Free + donation link
- One-time purchase
- Freemium (cloud second-opinion as paid tier, if/when v1.1 adds it)

**Impact:** Play Store listing category, data-safety disclosures, in-app donation/purchase UX.

### OQ-002 · Languages at launch
**Asked by:** Claude (architecture doc §14)
**When to resolve:** before Phase 15 (polish)
**Default:** English only for v1. Reason-code templates written with translation in mind.

### OQ-003 · Verdict language for "uncertain"
**Asked by:** Claude
**When to resolve:** before Phase 5 (verdict screens)
**Options:** "Uncertain" (honest, direct) / "Inconclusive" (softer) / "Needs more info" (softest, most action-oriented)
**Default:** "Uncertain" unless A/B testing data suggests otherwise.

### OQ-004 · Model-update network policy default
**Asked by:** Claude
**When to resolve:** before Phase 13 (model delivery)
**Default:** Wi-Fi only by default, toggle in settings.

### OQ-005 · Reason codes in UI vs diagnostic export only
**Asked by:** Claude
**When to resolve:** before Phase 10 (forensic view)
**Default:** plain-language explanations in UI, mono code tags visible for identification, machine-readable codes only in diagnostic export.

### OQ-006 · SynthID availability
**Asked by:** Claude
**When to resolve:** Phase 6 (provenance layer)
**Context:** Official Google SynthID detector SDK availability depends on when Phase 6 runs. If not publicly available, defer to v1.1 and note in decision log.

### OQ-007 · iOS timeline
**Asked by:** user implicitly
**When to resolve:** after v1 launch
**Default:** Not in v1 scope. Architecture allows iOS port but UX would differ significantly (no equivalent of share-sheet app detection, no `SYSTEM_ALERT_WINDOW`). Likely a substantially different product on iOS.

### OQ-008 · Play Store risk: overlay + screen capture
**Asked by:** Claude (architecture doc risk register)
**When to resolve:** before Phase 12 (overlay) and Phase 16 (submission)
**Plan:**
1. Ensure share-sheet path ships independently of overlay status.
2. Prepare privacy policy + data safety form to match actual behavior exactly.
3. Expect first Play Store rejection; respond calmly.
4. Have Accessibility-Service fallback plan ready (§5.4 in visual spec) if `SYSTEM_ALERT_WINDOW` is restricted further.

---

## Part 5 — Anti-patterns to avoid

Things that would damage the project if introduced. The agent refuses to implement these even if prompted.

1. **Generating AI-made illustrations for app assets.** Use human-designed or stub placeholders. AI-generated illustrations will clash with the instrumentation aesthetic.

2. **Using the words "certified," "verified by Veritas," or similar** anywhere the user sees them. Veritas is not a certifying authority. Even when C2PA confirms, the user sees "Verified authentic — signed by [issuer]", not "Verified by Veritas."

3. **Shareable verdict badges that look like certifications.** No "Veritas-approved" stamps for users to overlay on media. This creates obvious abuse vectors.

4. **Any feature that lets a third party query "has user X scanned video Y."** Even hashed. Even opt-in. The privacy thesis must hold.

5. **"Report this content" buttons.** Veritas is not a moderation pipeline. Platforms have their own reporting.

6. **Sending media or media-derived features to any server,** regardless of opt-in, for any reason, in v1. Cloud second-opinion is explicitly v1.1 and requires separate re-design.

7. **In-app ads.** Not in v1. Not in v1.1. Would conflict with the trust posture.

8. **Hardcoded color values outside `core-design`.** Every color in the app comes from `VeritasColors`.

9. **Animations that run on the home screen idle.** The aesthetic is instrumentation at rest — decorative animations signal toy, not tool.

10. **Using Compose for the bubble overlay surface.** Use classic View. Compose for the toast and activities only.

---

## Part 6 — Inspiration references (non-binding)

Aesthetic/behavioral references for the agent when making small judgment calls:

- **Instrumentation feel:** Teenage Engineering product UI, high-end camera apps (Lightroom Mobile, Halide), scientific instrument software.
- **Trust posture:** Signal (the messenger) — understated, technical, honest.
- **Forensic explanation:** medical imaging viewers — show the evidence, let the user decide.
- **Not a reference:** anything playful, friendly-mascot-led, or gamified. No confetti. No emoji verdicts.

---

## Part 7 — Version history

### D-020 · Roborazzi over Paparazzi for Phase 1 screenshot tests
**Phase:** 1
**Date:** 2026-04-22
**Context:** Phase 1 required a screenshot test system for Compose primitives and allowed either Paparazzi or Roborazzi.
**Decision:** Use Roborazzi in `core-design` with Robolectric-backed unit tests, and wire `verifyRoborazziDebug` into the root `precommitCheck` task and CI.
**Alternatives considered:** Paparazzi.
**Reasoning:** Roborazzi fits the Kotlin 2.x + current AGP stack cleanly, works with Android resources in local unit tests, and lets the same primitive composables be exercised by both the screenshot suite and the gallery screen without introducing a second rendering model.
**Reversal cost:** medium - the test fixtures and baseline images are isolated to `core-design`, so switching frameworks later is possible but would require re-recording baselines and rewriting the screenshot harness.
**Approved by human:** pending checkpoint, 2026-04-22

### D-021 · Variable font assets for Manrope and JetBrains Mono
**Phase:** 1
**Date:** 2026-04-22
**Context:** The visual spec required multiple weights for both Manrope and JetBrains Mono in the Compose theme.
**Decision:** Store one variable TTF per family in `res/font/` and map the required weight set through Compose `FontFamily` entries.
**Alternatives considered:** Commit one static TTF file per required weight.
**Reasoning:** Variable fonts keep the asset footprint smaller, still expose the exact required weight mappings to Compose, and reduce maintenance overhead versus managing many near-duplicate files.
**Reversal cost:** low - the change is localized to two font resources and the `FontFamily` setup in `core-design`.
**Approved by human:** pending checkpoint, 2026-04-22

---

| Version | Date | Changes |
|---|---|---|
| 1.0 | April 2026 | Initial document set at start of build |
