# Veritas — Phase Plan
**Version:** 1.0
**Companion to:** `01_ARCHITECTURE.md`, `02_VISUAL_SPEC.md`
**Purpose:** Sequenced, checkpointed build plan. The agent works through these phases linearly. Each phase compiles, runs, and can be demoed before the next starts.

---

## 0. How to use this document (agent instructions)

This is the primary document you execute against. Read it before starting work and re-read the current phase at the start of every session.

**Operating rules:**

1. **Work phases in order.** Do not start phase N+1 until phase N's acceptance criteria are all met and the human has checkpointed.
2. **Within a phase, work tasks in listed order** unless a dependency makes it impossible.
3. **After every task** run the phase's test command. If tests fail, fix before moving on.
4. **At the end of each phase**, produce a phase completion report (template in §Appendix A) and wait for human sign-off before starting the next phase.
5. **When in doubt, reference the other docs in this order**: `02_VISUAL_SPEC.md` for UI questions, `01_ARCHITECTURE.md` for design rationale, `04_DATA_CONTRACTS.md` for data shapes, `05_GLOSSARY_AND_DECISIONS.md` for terminology and past decisions.
6. **Record every new decision** you make during build in `05_GLOSSARY_AND_DECISIONS.md` under the "Decision log" section. Examples: "chose library X over Y because Z," "resolved ambiguity in spec by doing A."
7. **Do not skip pitfalls.** Every phase has a "Pitfalls" section listing mistakes I predict you'll make. Read them before starting the phase.
8. **Do not expand scope.** If a phase says "stub the detection pipeline," do not implement a real detector — that's a later phase. Scope creep is the single biggest risk to a project of this size.

**Per-phase structure:**
- **Goal** — one sentence
- **Prerequisites** — what must already be true
- **Tasks** — concrete work items in order
- **Deliverables** — files, features, tests produced
- **Acceptance criteria** — how the human verifies completion
- **Pitfalls** — predicted mistakes and how to avoid them
- **Effort** — rough estimate in focused-working-hours
- **Human checkpoint** — what to demo to the human

---

## 1. Phase overview

| # | Phase | Goal | Effort (hrs) |
|---|---|---|---|
| 0 | Scaffold | Project compiles, runs on device, CI green | 12 |
| 1 | Design system | Theme, tokens, primitives match visual spec | 16 |
| 2 | Navigation + home | App shell, navigation graph, home screen | 12 |
| 3 | Onboarding | Full 10-screen onboarding flow | 20 |
| 4 | Media ingestion | Share-sheet intent, file picker, scoped storage, purge | 20 |
| 5 | Detection pipeline (stub) | End-to-end scanning UX with fake verdicts | 16 |
| 6 | Provenance layer | C2PA + SynthID real implementations | 30 |
| 7 | Image detector (real) | Frequency-domain + ViT, golden-set eval | 40 |
| 8 | Audio detector (real) | Spectral + prosody, fusion, calibration | 40 |
| 9 | Video detector (real) | Spatial + temporal + rPPG, fusion | 60 |
| 10 | Forensic view | Heatmap rendering, timeline, reason sheets | 24 |
| 11 | History & settings | History list, settings tree, diagnostic export | 24 |
| 12 | Overlay mode | Bubble, MediaProjection, toast, foreground service | 40 |
| 13 | Model delivery | Signed updates, rollback, Play Feature Delivery | 24 |
| 14 | Errors & edge states | All 13 error screens, long-scan handling | 16 |
| 15 | Accessibility & polish | TalkBack, reduce motion, final QA pass | 20 |
| 16 | Hardening & launch prep | Adversarial eval, Play Store submission | 30 |

**Total estimated effort:** ~450 hours of focused work, or roughly 5–7 months at a realistic solo-plus-agent pace (assuming ~20 effective hours/week).

**Checkpoint cadence:** human review at the end of every phase. Non-negotiable.

---

## Phase 0 — Scaffold

**Goal:** Empty but correctly-configured Android project compiles, installs on a real device, runs, shows a placeholder screen, has CI.

**Prerequisites:** None. This is the start.

**Tasks (in order):**

1. Create Android Studio project:
   - Name: `Veritas`
   - Package: `com.veritas.app` (change if you own a different domain)
   - Language: Kotlin
   - Min SDK: 30 (Android 11)
   - Target SDK: 35
   - Template: Empty Compose Activity

2. Configure Gradle:
   - Use version catalogs (`libs.versions.toml`)
   - Pin Kotlin 2.x, AGP latest stable, Compose BOM latest stable
   - Enable Compose compiler plugin (required for Kotlin 2.x)
   - Enable KSP (not KAPT)

3. Add core dependencies:
   - Compose BOM + `androidx.compose.material3`
   - Compose Navigation
   - Hilt (DI)
   - Coroutines + Flow
   - DataStore (preferences)
   - Coil (images)
   - Room (persistence)
   - Timber (logging)

4. Create module structure:
   ```
   app/              (UI shell, application class)
   core-design/      (theme, tokens, primitives)
   core-common/      (pure-Kotlin utilities)
   feature-home/     (home screen)
   domain-detection/ (pure-Kotlin; detection pipeline interfaces + stubs)
   data-detection/   (Android-specific implementations)
   ```
   Note: Feature modules will be added in later phases. For now, just create the core modules.

5. Set up Hilt in application class + `@AndroidEntryPoint` on MainActivity.

6. Set up Timber in application class.

7. Create GitHub Actions (or equivalent) CI:
   - Run on every push
   - Assemble debug build
   - Run unit tests
   - Upload APK artifact
   - Fail build on compiler warnings (`allWarningsAsErrors` in Kotlin options)

8. Add pre-commit checks:
   - ktlint
   - detekt with default rules
   - Run both in CI

9. Create README with build instructions.

10. Verify: install debug APK on a real Android device, confirm launch.

**Deliverables:**
- Project on local filesystem, committed to git
- CI pipeline passing
- README
- Placeholder home screen saying "Veritas is building" in default styling

**Acceptance criteria:**
- `./gradlew assembleDebug` succeeds with zero warnings
- `./gradlew test` succeeds
- App installs and launches on Android 11, 14, and 15 devices (or emulators)
- CI badge in README shows green

**Pitfalls:**
- Do not install any libraries not on the list. Especially not: any analytics SDK, any network library beyond what's strictly needed later, any UI library beyond Compose Material3.
- Kotlin 2.x requires the Compose compiler plugin. If builds fail with weird Compose errors, this is almost always why.
- Hilt + KSP: use the KSP-compatible Hilt plugin. Do not fall back to KAPT even if docs suggest it.
- Do not use Material2. Material3 only.

**Human checkpoint:** demo the running app on device. Show CI green. Show module structure. Review README.

---

## Phase 1 — Design system

**Goal:** Every token, color, typography style, and primitive component from `02_VISUAL_SPEC.md` §10 is implemented as reusable Compose code. A "gallery" screen displays them all.

**Prerequisites:** Phase 0 complete.

**Tasks:**

1. Add font dependencies:
   - Download Manrope (weights 300, 400, 500, 600, 700, 800)
   - Download JetBrains Mono (weights 400, 500, 700, 800)
   - Add to `res/font/` and register with Compose `FontFamily`

2. In `core-design`, implement:
   - `VeritasColors` object (from visual spec §10)
   - `VeritasType` with named `TextStyle`s:
     - `displayXl`, `displayLg`, `displayMd` (Manrope 300, large)
     - `headingLg`, `headingMd`, `headingSm` (Manrope 600/700)
     - `bodyLg`, `bodyMd`, `bodySm` (Manrope 400)
     - `monoSm`, `monoXs` (JetBrains Mono, letter-spacing 0.15em)
   - `VeritasSpacing` object
   - `VeritasRadius` object
   - `VeritasTheme` composable that wires Material3 theme + provides these via CompositionLocal

3. Implement primitives:
   - `VeritasScaffold` — standard screen shell with brand header + close
   - `BrandMark` — the logo square with inner dot
   - `VeritasButton` variants: `Primary`, `Secondary`, `Ghost`
   - `VeritasTag` — mono tag with letter spacing
   - `ConfidenceRange` — the range bar from the verdict screens
   - `EvidenceChip` — variant for `plus` / `mixed` / `minus`
   - `StageRow` — scan step row (dot + label + meta)
   - `VerdictPill` — small colored pill for history list
   - `StatusBar` — fake status bar for screenshots only (not production)

4. Create `GalleryActivity` (debug-only, behind BuildConfig flag):
   - Scrollable screen showing every token and primitive in the design system
   - Tap any primitive to see its variants
   - Route: open via `adb shell am start -n com.veritas.app.debug/.gallery.GalleryActivity`

5. Write Compose preview functions for every primitive.

6. Screenshot tests:
   - Use Paparazzi or Roborazzi
   - One reference screenshot per primitive
   - Include in CI

**Deliverables:**
- `core-design` module with all tokens and primitives
- Gallery activity (debug only)
- Screenshot test suite

**Acceptance criteria:**
- Gallery renders on device matching visual spec aesthetic
- All primitives have Compose previews that render without errors
- Screenshot tests pass
- No hardcoded colors or spacing anywhere outside `core-design`

**Pitfalls:**
- Do not use `MaterialTheme.colorScheme.primary` anywhere. Always reference `VeritasColors` directly. Material3 token mapping is unnecessary for this app — the design system is deliberately opinionated.
- Letter-spacing in Compose is `letterSpacing: TextUnit`, expressed as `em`. The visual spec uses `0.15em` — pass as `0.15.em` in Compose.
- Fonts registered in `res/font/` have specific naming requirements (lowercase, underscores). Respect them.
- The `BrandMark` looks simple but is load-bearing for brand recognition — match mockup exactly (border, inner dot, glow). Cross-check against `veritas_mockup.html`.

**Human checkpoint:** demo gallery on device side-by-side with `veritas_mockup.html`. Verify visual fidelity.

---

## Phase 2 — Navigation + home

**Goal:** App shell with bottom nav, navigation graph, home screen (2.1) rendering with mock recent items.

**Prerequisites:** Phase 1 complete.

**Tasks:**

1. Set up Compose Navigation with typed routes (use `kotlinx.serialization` route types, not string routes).

2. Define top-level destinations:
   - `Home` (2.1)
   - `History` (4.1)
   - `Settings` (5.1)

3. Implement bottom nav bar matching mockup:
   - Active state uses `--accent`
   - Inactive uses `--ink-mute`
   - Mono labels

4. Implement `HomeScreen`:
   - Hero section with gradient
   - `READY` tag, large heading, two CTAs
   - Recent section stub: list reads from an in-memory `MutableStateFlow<List<HistoryItem>>` (empty for now; mock data for development)
   - Empty state when list is empty

5. Stub screens for History (empty state 4.2) and Settings (title only, TODO).

6. Wire close/back behavior:
   - System back navigates within nav graph
   - Close button on verdict-style screens exits activity

7. Write UI tests for navigation (Compose testing library):
   - Tap each bottom nav item, confirm destination
   - Tap "Pick a file" on home, confirm picker launches (test via fake launcher)

**Deliverables:**
- `feature-home`, `feature-history` (stub), `feature-settings` (stub) modules
- Navigation graph
- Home screen rendering
- UI tests

**Acceptance criteria:**
- Bottom nav works on device
- Home screen matches mockup row 3, third phone ("APP HOME") visually
- Empty and populated states both render correctly (toggle via dev menu or BuildConfig)

**Pitfalls:**
- Do not use string-based routes. They rot. Use typed routes from the start.
- Bottom nav items should survive configuration change (rotation). Test explicitly.
- Do not try to share state via `savedStateHandle` between screens; use a scoped ViewModel per feature.

**Human checkpoint:** demo navigation and home screen.

---

## Phase 3 — Onboarding

**Goal:** Full 10-screen onboarding flow from splash to ready, with real permission flows (overlay, notifications) but without starting any services yet.

**Prerequisites:** Phase 2 complete.

**Tasks:**

1. Implement `OnboardingActivity` separate from `MainActivity`:
   - Launched when `hasCompletedOnboarding` DataStore flag is false
   - On completion, sets flag and launches `MainActivity`

2. Implement each screen per `02_VISUAL_SPEC.md` §2.

3. Implement permission flows:
   - Screen 1.7: overlay permission via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` intent
   - Screen 1.9: `POST_NOTIFICATIONS` runtime permission (API 33+)
   - Handle return-from-settings flows

4. Implement system-splash → Compose-splash handoff.

5. Persist `hasCompletedOnboarding` in DataStore.

6. Implement "I've used Veritas before" skip on welcome (1.2).

**Stub assets:** use placeholder boxes labeled with asset names per visual spec §12. Do not generate illustrations.

7. Write UI tests:
   - Complete flow end-to-end
   - Skip flow (overlay denied → arrives at home)
   - Return-from-settings flow

**Deliverables:**
- `feature-onboarding` module
- Onboarding fully functional on device
- Permission handling tested on Android 11, 13, 14, 15

**Acceptance criteria:**
- Fresh install → onboarding → home (happy path)
- Fresh install → skip overlay → home (declined path)
- Onboarding doesn't show on subsequent launches
- All screens match visual spec copy exactly (no lorem ipsum, no paraphrasing)

**Pitfalls:**
- `SYSTEM_ALERT_WINDOW` cannot be requested via runtime dialog — only via settings intent. Do not try to use `requestPermissions`.
- `POST_NOTIFICATIONS` is Android 13+. Below 13, skip screen 1.9 entirely.
- Do not start foreground service during onboarding. Overlay toggle flip in settings (phase 11) is the earliest a service should start.
- Copy must match the spec exactly. If copy feels awkward, record in decision log and request human review — do not silently edit.

**Human checkpoint:** walk through full onboarding on fresh install. Verify copy.

---

## Phase 4 — Media ingestion

**Goal:** Share-sheet intent, file picker, and paste-link entry all accept media, copy to scoped storage, and hand off to a stub scanning screen.

**Prerequisites:** Phase 3 complete.

**Tasks:**

1. Declare intent filters in manifest for `video/*`, `audio/*`, `image/*`, `text/plain`.

2. Implement `ShareTargetActivity`:
   - Handles `ACTION_SEND` + `ACTION_SEND_MULTIPLE`
   - Resolves `content://` URIs
   - Validates MIME type, size, duration
   - Copies to app-private cache dir
   - Launches stub scanning screen (will be replaced in phase 5)

3. Implement file picker on home screen:
   - Use `PickVisualMedia` for image/video
   - Fall back to `ACTION_OPEN_DOCUMENT` for audio
   - Same copy-to-scoped-storage path

4. Implement paste-link screen (2.3) **UI only** — actual link resolution is deferred to v1.1 per visual spec §2.3. Stub with error message for any URL.

5. Implement validation + error screens (6.4, 6.5, 6.6, 6.7, 6.11):
   - File > 200 MB → 6.4
   - Video > 60s or audio > 180s → 6.5 (detect via `MediaMetadataRetriever`)
   - Unsupported MIME → 6.6
   - Corrupt (decoder error) → 6.7
   - Storage full (IOException on copy) → 6.11

6. Implement auto-purge:
   - Scoped timer via `WorkManager` one-time work
   - 60 seconds after verdict display OR on scanning/verdict activity destroy
   - Tests verify file deletion

7. Implement `ScannedMedia` data class (see `04_DATA_CONTRACTS.md`).

8. Write tests:
   - Share intent with video → file in scoped storage → stub scan starts
   - File picker → same flow
   - Oversized file → 6.4
   - Wrong MIME → 6.6
   - Auto-purge after 60s

**Deliverables:**
- Share-sheet integration (appears in system share sheet for media)
- File picker working
- Scoped storage correctly used
- Auto-purge verified

**Acceptance criteria:**
- Install Veritas, open TikTok (or any app), share a video → Veritas appears in share sheet → tapping routes to stub scan screen with the file accessible
- File picker flow also reaches scan screen
- All file-size / duration / format limits enforced with correct error screens
- File is deleted from cache dir 60s after verdict shown (verify via shell)

**Pitfalls:**
- `FileProvider` is not needed for incoming shares — `ContentResolver.openInputStream()` on the incoming URI is sufficient. Do not declare a FileProvider until you need one for outgoing shares (diagnostic export).
- Don't request `READ_MEDIA_VIDEO` etc. permissions unless you need the direct gallery access path. For share intents, no permission is required.
- `MediaMetadataRetriever` can throw on edge-case files. Wrap defensively and route to 6.7 on failure.
- Auto-purge must survive activity destroy — use WorkManager, not a coroutine tied to activity lifecycle.

**Human checkpoint:** share videos from 3+ apps, confirm Veritas appears and receives them correctly.

---

## Phase 5 — Detection pipeline (stub)

**Goal:** End-to-end UX flow from share → scanning animation → one of three verdicts → forensic view. Detection is entirely faked; verdict is deterministic based on filename or random.

**Prerequisites:** Phase 4 complete.

**Tasks:**

1. In `domain-detection`, define interfaces:
   - `Detector<T : DetectorInput>` with `suspend fun detect(input: T): DetectorResult`
   - `DetectionPipeline` with `fun scan(media: ScannedMedia): Flow<ScanStage>`
   - `ScanStage` sealed class emitting pipeline events (started, stage_active, stage_done, verdict_ready)
   - `Verdict` data class (match `04_DATA_CONTRACTS.md`)

2. Implement `FakeDetectionPipeline` in `data-detection`:
   - Emits stages with realistic timing (300–800ms each)
   - Generates verdict based on filename: `_authentic` → authentic, `_uncertain` → uncertain, `_synthetic` → synthetic, otherwise random
   - Produces realistic-looking reason codes and confidence ranges

3. Implement `ScanningScreen` matching visual spec §2.4:
   - Subscribes to pipeline Flow
   - Shows 5 stage rows with correct state transitions
   - Scan preview placeholder
   - Close button cancels scan

4. Implement `VerdictScreen` with three variants (2.5, 2.6, 2.7):
   - Route based on `Verdict.outcome`
   - Pull evidence chips from verdict's top 3 reasons

5. Implement `ForensicScreen` stub (2.8):
   - Placeholder heatmap (dark box with "Heatmap" label)
   - Timeline bar with random colored segments
   - Reason list with real reason codes
   - Real behavior deferred to phase 10

6. Implement per-reason detail sheet (2.9) with template text from a reason-code dictionary (will be real copy in phase 10).

7. Tests:
   - Scan with `_authentic` filename → 2.5 verdict
   - Scan with `_synthetic` filename → 2.7 verdict
   - Cancel during scan returns to previous screen
   - Tap `See details` → forensic view

**Deliverables:**
- End-to-end flow works with fake detection
- All three verdict screens functional
- Forensic view stub

**Acceptance criteria:**
- Demo flow: share → scan → verdict → forensic → back to verdict → done
- All three verdict paths reachable via filename convention
- Visual fidelity matches mockup verdict screens

**Pitfalls:**
- Do not implement any real detection in this phase. Tempting but wrong — the scaffolding is what we need to validate first.
- The pipeline interface you design here will be used by real detectors later. Be thoughtful. Emit a `Flow`, not `suspend` returning a single value — we need progressive updates.
- Confidence ranges must straddle the calibrated thresholds correctly. See `04_DATA_CONTRACTS.md` for range generation rules.
- Use `--ok` green softer for "Looks authentic" (no C2PA) vs "Verified authentic" (C2PA present) — per spec §2.5 decision flag.

**Human checkpoint:** demo all three verdict outcomes end-to-end. Compare to mockup.

---

## Phase 6 — Provenance layer (real)

**Goal:** Real C2PA manifest verification + real SynthID detection running on-device. Feeds into pipeline as pre-flight checks before any ML detectors run.

**Prerequisites:** Phase 5 complete.

**Tasks:**

1. Integrate `c2pa-rs`:
   - Add Rust toolchain to build
   - Cross-compile `c2pa-rs` for Android ABIs (arm64-v8a, x86_64 at minimum)
   - Write JNI bindings or use the C API via JNA
   - Parse manifests from JPEG, PNG, MP4, WebP

2. Implement `C2PADetector`:
   - Extract manifest if present
   - Validate signature chain against bundled trust list
   - Return `C2PAResult` with outcome: NOT_PRESENT / VALID / INVALID / REVOKED

3. Bundle initial C2PA trust list as app asset.

4. Integrate SynthID SDK (use the official Google SynthID detector API if publicly available by implementation time; otherwise skip to v1.1 and defer this task).
   - Image, audio, video watermark detection
   - Return `SynthIDResult` with detected-generator attribution

5. Wire into pipeline:
   - Pre-flight runs C2PA first
   - If C2PA VALID → short-circuit to "Verified authentic" verdict
   - If C2PA INVALID → strong negative signal, continue to ML detectors
   - Otherwise, continue to SynthID check
   - If SynthID detected → strong synthetic signal, still run ML for confirmation
   - Otherwise, continue to ML detectors (still fake in this phase)

6. Implement reason codes for provenance findings:
   - `C2PA_VERIFIED_*` with issuing device
   - `C2PA_INVALID_SIGNATURE`
   - `C2PA_REVOKED`
   - `SYNTHID_DETECTED_*`

7. Test with real signed media:
   - Adobe's C2PA test content
   - Canon/Sony sample photos with credentials
   - SynthID-watermarked Gemini/Imagen images if available
   - Expected: authentic verdict on valid, invalid-signature reason on tampered

**Deliverables:**
- C2PA verification working
- SynthID detection (or deferred with decision log entry)
- Pre-flight pipeline wired

**Acceptance criteria:**
- Signed Adobe C2PA test image → "Verified authentic" verdict
- Manually tampered signed image → negative reason code for invalid signature
- Unsigned image → continues to (fake) ML detection
- Decision log updated with SynthID status

**Pitfalls:**
- `c2pa-rs` binary sizes: the compiled library adds ~4 MB per ABI. Don't ship armeabi-v7a — 64-bit only. Confirm app APK stays under reasonable size.
- Trust list updates must go through the signed-asset delivery channel (phase 13), not bundled as a raw file post-v1.
- `c2pa-rs` has complex error types. Surface them as `C2PAResult` subclasses cleanly — don't leak Rust-specific details into the domain layer.
- MP4 C2PA manifests are stored in specific ISO-BMFF boxes. Test with real sample files, not just JPEG.

**Human checkpoint:** demo C2PA verification with sample signed files. Discuss SynthID status.

---

## Phase 7 — Image detector (real)

**Goal:** Real ML-based image detection. Model ships with app initially (real delivery pipeline comes in phase 13). Calibrated to the three-verdict system.

**Prerequisites:** Phase 6 complete.

**Critical distinction for this phase:** research repos (UniversalFakeDetect, DeepfakeBench, DF40) are used **offline** for training, distillation, and benchmarking. They are PyTorch repos; they do NOT ship in the APK. The shipped model is a compact MobileViT-v2 variant exported to TFLite. See architecture doc §6.5 for full explanation.

**Tasks:**

1. Source initial models:
   - **Runtime (ships in APK):** MobileViT-v2 from Apple's `ml-cvnets` repo, fine-tuned on deepfake/real image pairs, INT8 quantized, exported to TFLite with metadata.
   - **Frequency-domain detector:** hand-built — small CNN over FFT/DCT features plus EXIF/quantization/ELA features. Exported to TFLite.
   - **Ensemble fusion model:** small MLP over all scores + metadata features. TFLite.
   - **Offline baselines/teachers (NOT shipped):** UniversalFakeDetect as baseline or distillation teacher. DeepfakeBench / DF40 for evaluation across many generators.

2. Integrate LiteRT via Google Play Services:
   - Add dependencies: `com.google.android.gms:play-services-tflite-java`, `play-services-tflite-gpu`, `play-services-tflite-support`.
   - Configure GPU delegate as default accelerator; XNNPACK (CPU) as fallback.
   - Do NOT use the NNAPI delegate — it is deprecated as of Android 15.
   - Test on multiple device classes; device-specific acceleration results vary post-NNAPI and must be measured, not assumed.

3. Implement `ImageDetector`:
   - Preprocessing: resize, normalize, frequency transform.
   - Inference: run all three models (spatial ViT, frequency CNN, metadata classifier).
   - Post-processing: calibrate scores via bundled isotonic regression parameters.
   - Output: `DetectorResult` with score, confidence interval, reason codes.

4. Implement metadata / EXIF forensics module:
   - EXIF plausibility (camera model vs image properties).
   - JPEG quantization table fingerprinting.
   - Basic ELA (Error Level Analysis).

5. Define and populate golden dataset:
   - ~500 image pairs (real + synthetic) across generators (Midjourney, Stable Diffusion, DALL-E, Flux, ideogram, GAN outputs).
   - Stored in test resources (or separate repo to avoid APK bloat).
   - Include edge cases: heavy compression, screenshots of images, thumbnails.

6. Implement eval harness:
   - Run all golden images through pipeline.
   - Produce metrics: FPR, FNR, uncertain-rate, per-class accuracy.
   - Fail CI if FPR > 1.5% on authentic content.
   - Also run the offline DeepfakeBench / DF40 eval pipeline against the distilled mobile model to detect regressions.

7. Wire into pipeline:
   - Replace fake image detection with real.
   - Ensure fusion combines provenance + ML scores correctly.

**Deliverables:**
- Working image detector (shipped TFLite models + runtime code).
- Golden-set eval plus offline DeepfakeBench/DF40 eval.
- Calibration pipeline.
- Documentation of which training repos produced the final model, recorded in `05_GLOSSARY_AND_DECISIONS.md`.

**Acceptance criteria:**
- 500+ image golden-set eval: FPR ≤ 1.5% on real, FNR ≤ 20% on synthetic, uncertain-rate between 5–20%.
- End-to-end: share a known real photo → authentic verdict; share a known AI image → synthetic verdict; share a heavily compressed unknown → often uncertain.
- Inference completes ≤ 2s on mid-range 2024 device.
- All reason codes render with correct descriptions.

**Pitfalls:**
- Do NOT try to import UniversalFakeDetect (or any PyTorch research repo) into the Android project. That repo trains models; we ship the distilled result.
- Do NOT use the NNAPI delegate. It is deprecated. Use GPU delegate and CPU fallback via LiteRT in Play Services.
- Published deepfake detection models often generalize poorly to real-world in-the-wild images. Expect accuracy in the 70–85% range, not 95%+. Calibrate thresholds accordingly.
- INT8 quantization can silently degrade accuracy on specific chipsets. Benchmark per-chipset (Pixel, Samsung, midrange). Keep FP16 fallback.
- Don't just use one generator's output for synthetic class — diversity matters.
- EXIF forensics is not a verdict signal on its own. It's one feature in the fusion. Absent EXIF ≠ synthetic.

**Human checkpoint:** review eval metrics. Discuss if quality is acceptable to proceed.

---

## Phase 8 — Audio detector (real)

**Goal:** Real audio deepfake detection. Same bar as image detector.

**Prerequisites:** Phase 7 complete.

**Critical distinction for this phase:** AASIST, SSL/wav2vec anti-spoofing, and ASVspoof are offline training/benchmark tools. The shipped model is a compact distilled audio classifier. See architecture doc §6.5.

**Tasks:**

1. Source / build models:
   - **Runtime (ships in APK):** compact spectral CNN over log-mel spectrograms, exported to TFLite. Prosody and codec classifiers as small separate TFLite models. Fusion MLP.
   - **Offline baselines/teachers (NOT shipped):** **AASIST** (official repo) as anti-spoofing baseline. SSL/wav2vec anti-spoofing directions if they distill cleanly to a mobile-sized model.
   - **Offline evaluation:** **ASVspoof** benchmark families.
   - Speaker-consistency (for clips >10s) — small embedding model.

2. Implement audio preprocessing:
   - Decode via MediaExtractor + MediaCodec.
   - Resample to 16 kHz mono.
   - Generate mel spectrogram, MFCCs, pitch contour.

3. Implement `AudioDetector`:
   - Run via LiteRT (Play Services). MediaPipe `AudioClassifier` / `AudioEmbedder` MAY be used as a convenience wrapper **only if** the TFLite model is authored with proper metadata and the wrapper is a net simplification. Otherwise call LiteRT directly.
   - Calibrate + fuse.
   - Emit reason codes: `AUDIO_SPECTRAL_NEURAL`, `AUDIO_PROSODY_UNNATURAL`, `AUDIO_CODEC_MISMATCH`.

4. Extend forensic view for audio:
   - Replace heatmap with waveform visualization.
   - Red-highlighted regions showing synthetic-confidence peaks.
   - Timeline still applies.

5. Golden dataset for audio:
   - ~500 audio clips (real speech + synthetic TTS/voice-cloned).
   - Include phone call quality, podcast quality, music-background edge cases.
   - Languages: at minimum English; ideally a few others for robustness.

6. Eval:
   - Same metrics as image detector.
   - FPR ≤ 1.5% on real audio.
   - Also run the ASVspoof-family eval against the distilled mobile model.

7. Wire into pipeline.

**Deliverables:**
- Working audio detector (shipped TFLite models + runtime code).
- Audio variant of forensic view.
- Audio golden-set eval plus ASVspoof eval.

**Acceptance criteria:**
- 500+ audio golden-set eval passes thresholds.
- Real podcast clip → authentic; ElevenLabs/similar voice clone → synthetic.
- Inference ≤ 3s for 30s clip on mid-range device.

**Pitfalls:**
- Do NOT import AASIST or any research repo into the Android project. They are for offline training only.
- Real-world audio is almost always recompressed (AAC, Opus). Train and eval on recompressed audio, not just raw.
- Speaker-consistency model is sensitive to short clips. Only run it on clips >10s, skip otherwise.
- Audio timestamps in reason codes need to be sample-accurate — off-by-one errors break timeline scrubbing.
- MediaPipe `AudioClassifier` has version-coupling to specific TFLite versions. If the wrapper fights you, drop it and use LiteRT direct.

**Human checkpoint:** review eval metrics.

---

## Phase 9 — Video detector (real)

**Goal:** Real video deepfake detection via the three-specialist ensemble (spatial + temporal + rPPG). The highest-compute, highest-stakes phase.

**Prerequisites:** Phase 8 complete.

**Tasks:**

**Critical framing for this phase:** there is no turnkey "Android video deepfake detector SDK." This phase is substantially glue code around established components. See architecture doc §6.2.

1. Source / build models:
   - **Spatial detector (runtime):** reuse the MobileViT-v2 from Phase 7, applied frame-by-frame to sampled frames. Do not train a separate spatial branch unless a golden-set regression justifies it.
   - **Temporal detector (runtime):** **MoViNet-A0 or A1 stream variant**, fine-tuned on real-vs-synthetic video pairs, exported to TFLite. MoViNet has official pre-trained checkpoints for Kinetics-600 action classification — use those as the backbone and add a small binary classifier head. Streaming-friendly architecture.
   - **Do NOT use TimeSformer as the runtime model.** The official repo is archived and it was never designed for Android. TimeSformer MAY be used as an *offline teacher* for distillation, nothing more.
   - **rPPG signal extractor:** classical CHROM or POS algorithm after face ROI extraction via MediaPipe Face Landmarker. Small binary classifier on top of the pulse signal. Frameworks like `pyVHR` may help prototype offline; do NOT depend on pyVHR at runtime.

2. Use MediaPipe for face handling:
   - `MediaPipe Face Detector` or `Face Landmarker` for face ROI extraction at the frame level.
   - Do not re-implement face detection.
   - Face ROIs feed both the spatial detector (cropped frames) and rPPG extractor.

3. Implement frame sampling strategy:
   - Up to 60 frames for videos ≤ 60s.
   - Uniform sampling for short clips.
   - Shot-change detection (histogram differencing) for longer clips.

4. Implement `VideoDetector`:
   - Run via LiteRT in Play Services.
   - Parallel inference where possible (spatial + temporal can run in parallel).
   - Per-second confidence tracking for timeline.
   - Fusion with metadata (codec, bitrate anomalies).

5. Implement heatmap generation:
   - Grad-CAM (or attention rollout) over spatial ViT per sampled frame.
   - Output as per-frame tensor.
   - Pass to forensic view for rendering (real render in phase 10).

6. Golden dataset for video:
   - ~500 video clips (real + synthetic, multiple generators: Sora-class, Runway, Pika, HeyGen, etc.).
   - Include common platform recompressions (TikTok, Instagram, YouTube compression profiles).
   - Short clips (5–15s) and longer (30–60s).

7. Eval:
   - FPR ≤ 1.5% on real.
   - FNR ≤ 20% on synthetic.
   - Per-generator breakdown (where do we fail most?).

8. Adversarial eval suite:
   - Recompress golden set at low bitrate — accuracy should degrade gracefully.
   - Frame-rate change, crop, rotation, mild Gaussian blur.
   - Track baseline vs adversarial accuracy delta.

9. Wire into pipeline.

**Deliverables:**
- Working video detector (reused MobileViT + MoViNet-derived temporal model + classical rPPG, all via LiteRT).
- MediaPipe-based face handling.
- Heatmap tensors flowing to forensic view.
- Adversarial eval baseline.

**Acceptance criteria:**
- Golden-set eval passes.
- Inference ≤ 4s for 10s video on mid-range device (the headline UX target).
- Adversarial eval shows graceful degradation, not cliff.

**Pitfalls:**
- Do NOT try to port TimeSformer to Android, even a "lite" variant. Use MoViNet.
- Do NOT depend on `pyVHR` or any Python-only rPPG framework at runtime — use it for prototyping, then reimplement CHROM/POS in Kotlin or C++.
- Temporal models are memory-hungry. OOM on 4GB devices is a real risk. Add memory checks and fallback to spatial-only on constrained devices (reason code: `LOW_MEMORY_FALLBACK`).
- Grad-CAM on transformer models is non-trivial. Use attention rollout as a simpler alternative if classic Grad-CAM is problematic.
- Parallel inference uses all the GPU. Test that this doesn't make the device hot or drain battery disproportionately during repeated use.
- If a face is not detected, rPPG is simply skipped (reason code `RPPG_SKIPPED_NO_FACE`) — not an error, not a verdict degradation by itself.

**Human checkpoint:** comprehensive review of eval metrics. This is the core product. If quality is below bar, this phase iterates.

---

## Phase 10 — Forensic view

**Goal:** Real heatmap rendering, real temporal timeline, real reason-code detail sheets.

**Prerequisites:** Phase 9 complete.

**Tasks:**

1. Heatmap renderer:
   - Take per-frame Grad-CAM tensor
   - Render via `Canvas` with `BlendMode.Screen` for blob overlays
   - Color map: low → transparent, mid → amber, high → red
   - Label flagged regions with callouts (face landmarks → "EAR", "MOUTH", "EYES")

2. Timeline component:
   - Take per-second confidence array from detector
   - Render 8–16 segments colored by severity
   - Interactive: tap segment → scrub heatmap to that timestamp's flagged frame
   - Tick labels

3. Waveform component for audio:
   - Generate from audio samples
   - Highlight flagged regions in red

4. Image region overlay:
   - Static image + heatmap overlay (no timeline)
   - Pinch-zoom support

5. Full reason-code dictionary:
   - Template per code with placeholders
   - Fill at render time with scan-specific values
   - Store in resource files for translation

6. Per-reason detail sheet (2.9):
   - Modal bottom sheet, expandable
   - Full template text
   - Tappable timestamps that scrub forensic view

7. Fullscreen heatmap view:
   - Pinch-zoom
   - Close returns to forensic view

**Deliverables:**
- Real forensic view
- Reason code templates

**Acceptance criteria:**
- Heatmap renders correctly for video + image variants
- Timeline is interactive
- Reason sheet opens from tap, closes on gesture
- Scrubbing works

**Pitfalls:**
- Compose `Canvas` performance on large bitmaps: render heatmaps at reduced resolution, upscale on display. Don't render per-frame at full resolution.
- Timeline scrubbing should debounce — rapid taps shouldn't re-render heatmap 30 times.
- Reason templates must be translatable — all strings in resource files, no hardcoded.

**Human checkpoint:** demo forensic view with real detections.

---

## Phase 11 — History & settings

**Goal:** Complete history list, settings tree, and diagnostic export.

**Prerequisites:** Phase 10 complete.

**Tasks:**

1. Room database:
   - `HistoryItem` entity (per data contracts doc)
   - Store thumbnail (generated at scan time), verdict, reasons, model version
   - Max 100 items, auto-prune on insert

2. History list (4.1):
   - Group by date
   - Tap → history detail (4.3)
   - Long-press → context menu

3. History empty state (4.2).

4. History detail (4.3) reuses verdict screen with historical chrome.

5. Settings home (5.1) with all sections.

6. Overlay settings (5.2) — the toggle doesn't actually start a service yet (phase 12). Settings persist via DataStore.

7. Model management (5.3) — shows current versions (hardcoded for now; real delivery in phase 13). "Check for updates" is stub.

8. Privacy & data (5.4) — including "Clear history" and "Clear all data" actions.

9. Diagnostic export (5.5):
   - Generate text file with device info, model versions, settings, last 50 log entries
   - No media, no history contents by default
   - Open via share sheet

10. Telemetry opt-in modal (5.7) — shows after first successful scan. Sets DataStore flag. Endpoint stubbed; real telemetry deferred.

11. About (5.6) with licenses screen.

**Deliverables:**
- Full settings tree
- History persists
- Diagnostic export works

**Acceptance criteria:**
- Scan a video → history item appears
- Tap history item → detail view with original verdict
- Clear history → empty state
- Diagnostic export generates valid text file, opens share sheet
- All settings persist across relaunch

**Pitfalls:**
- Thumbnail generation happens at scan time (from the media file, before auto-purge). Never regenerate from history (the file is gone).
- Room + Compose: collect as Flow, not LiveData. Use `collectAsStateWithLifecycle`.
- Diagnostic export must include enough info to be useful without leaking PII. Audit the output manually before committing format.

**Human checkpoint:** complete demo of settings tree + history list.

---

## Phase 12 — Overlay mode

**Goal:** Floating bubble + MediaProjection consent + verdict toast + foreground service. The hardest UX engineering in the app.

**Prerequisites:** Phase 11 complete.

**Tasks:**

1. Foreground service:
   - `OverlayService` with `FOREGROUND_SERVICE_MEDIA_PROJECTION` type
   - Persistent notification (7.1 idle, 7.2 active scan)
   - Start/stop via settings toggle

2. Bubble overlay window:
   - `WindowManager` + `TYPE_APPLICATION_OVERLAY`
   - Classic `View` (not Compose) per architecture §16
   - Draw bubble matching mockup (XML drawable or custom `onDraw`)
   - Long-press → drag → snap-to-edge
   - Dismiss zone at bottom during drag

3. Tap handler:
   - Request MediaProjection consent each time (Android 14+ requirement)
   - On consent: capture short buffer (3s video or image frame)
   - Run through detection pipeline
   - Show verdict toast (3.3)

4. Toast overlay:
   - Separate `WindowManager` surface
   - Use Compose for toast content (separate `ComposeView`)
   - Auto-dismiss after 8s
   - Tap opens full verdict activity

5. Dismiss flow:
   - Drag to trash → hide bubble
   - Show "bubble hidden" notification (3.5)
   - "Bring back" restores; "Turn off" stops service

6. Handle edge cases:
   - App in background when tap fires → correct foreground service behavior
   - Permissions revoked externally → stop service, update settings state
   - Device rotation while bubble visible → reposition smoothly

7. Extensive testing:
   - Test on Android 11, 12, 13, 14, 15 (permission model changed across these)
   - Test with apps that block audio capture (`allowAudioPlaybackCapture="false"`)
   - Test drag-snap-edge on different screen sizes including foldables

**Deliverables:**
- Working overlay mode
- All overlay-related screens from visual spec §3

**Acceptance criteria:**
- Enable overlay in settings → bubble appears
- Tap bubble over a video in any app → capture consent → scan → verdict toast
- Drag bubble to any edge, it snaps smoothly
- Drag to trash → bubble hides with notification
- Works on Android 11 through 15

**Pitfalls:**
- Do not try to cache MediaProjection consent across sessions on Android 14+. It will break. Request fresh every time.
- `AudioPlaybackCaptureConfiguration` requires API 29+ and can be denied by the source app. Always gracefully degrade to video-only.
- Bubble position persistence must handle rotation and foldable device states. Test on a foldable.
- Bubble overlay cannot be Compose — Compose's recomposition model conflicts with WindowManager lifecycles. Classic View only for the bubble. Compose OK for the toast.

**Human checkpoint:** full overlay demo across 3+ host apps on 2+ Android versions.

---

## Phase 13 — Model delivery

**Goal:** Real signed model update pipeline. Models ship separately from APK.

**Prerequisites:** Phase 12 complete.

**Tasks:**

1. Server-side: set up CDN + signed-manifest endpoint (static host is sufficient for v1; just needs HTTPS + reliable delivery). Generate Ed25519 keypair; hold private key in release infrastructure.

2. Model manifest schema (per `04_DATA_CONTRACTS.md`):
   - Model family, version, architecture hash, quantization, target chip families
   - Signature

3. Client-side model registry:
   - Fetch manifest
   - Verify signature
   - Download delta if version is newer
   - Verify per-model signature before installation
   - Keep previous version as rollback

4. WorkManager job:
   - Scheduled every 24h by default
   - Wi-Fi only by default (respect setting)
   - Retries with exponential backoff

5. Rollout support:
   - Manifest supports staged rollout percentage
   - Client generates stable rollout ID, compares to manifest threshold
   - Only installs if rollout ID is within threshold

6. Rollback:
   - If new model fails health check (e.g., crashes during first 3 scans), automatically roll back
   - User-facing notification if this happens

7. Health checks:
   - Every model-update-complete fires a self-test scan against a bundled reference file
   - If verdict differs by more than X from expected, roll back

8. Remove bundled models from APK (they're now delivered; first-run downloads them).

9. Handle first-run-offline case (6.1): app functional but can't scan until models downloaded.

**Deliverables:**
- Working signed model delivery
- Rollback mechanism
- Health checks

**Acceptance criteria:**
- Release a test model update → device receives it within 24h (or on manual check)
- Signature tampering → update rejected
- Forced rollout percentage → only devices within percentage receive update
- Simulate bad model → auto-rollback triggered

**Pitfalls:**
- Ed25519 keys must be kept out of the repo. Use a secret management system from day one.
- First-run-no-network: users must still be able to see a useful UI, not a broken app. The "models not ready" state needs a dedicated surface (6.1 covers this for updates; add similar for first-run).
- APK size without models should be ≤ 30 MB ideally. Models add ~30 MB total post-download.

**Human checkpoint:** full update cycle demo.

---

## Phase 14 — Errors & edge states

**Goal:** All 13 error screens from visual spec §7 polished and consistent.

**Prerequisites:** Phase 13 complete.

**Tasks:**

1. Audit every error path: 6.1 through 6.13.

2. Ensure all have:
   - Correct copy from visual spec
   - Correct icon and color
   - Correct action(s)
   - Accessibility (TalkBack reads error clearly)

3. Long-scan warning (scanning > 15s) UI implemented.

4. Add dev menu (BuildConfig debug-only) to trigger each error state manually for QA.

5. Crash recovery: if scanning activity dies mid-scan, clean up state, show recovery screen on next launch.

**Deliverables:**
- All error states polished
- Dev menu for testing them

**Acceptance criteria:**
- QA dry-run: trigger every error via dev menu, confirm each matches spec
- TalkBack reads each error correctly

**Pitfalls:**
- Error copy must match visual spec exactly. Do not improvise.
- "No network" (6.1) is NOT the same as "failed to fetch link" (2.3). Different causes, different copy.

**Human checkpoint:** QA walkthrough of all error states.

---

## Phase 15 — Accessibility & polish

**Goal:** Full accessibility, motion preferences respected, final visual polish.

**Prerequisites:** Phase 14 complete.

**Tasks:**

1. TalkBack pass:
   - All screens readable end-to-end
   - Verdicts announced as single utterance ("Likely synthetic, 82 to 96 percent confidence, with three reasons")
   - Heatmaps have text alternatives
   - All interactive elements have `contentDescription`

2. Reduce-motion respect:
   - Animator scale = 0 disables all animations
   - Scanning scan-line replaced with static progress indicator when motion disabled

3. Font-scale support: layouts don't break at 2.0x font scale.

4. Color contrast audit:
   - WCAG AA minimum on all text
   - Fix any violations

5. RTL support (even if English-only at launch, prepare for it): no hardcoded left/right paddings, use `start`/`end`.

6. Dark-mode only is fine for v1 per architecture — but ensure system setting forcing light mode doesn't produce an unreadable screen.

7. Polish pass:
   - Animation timing feels right on device (not just on emulator)
   - Micro-interactions (button press, list scroll) feel snappy
   - Haptics on key actions (verdict arrival, bubble drag)

8. Performance audit:
   - Startup time cold start < 1s on mid-range device
   - Scan time meets §10 target (4s for 10s video)
   - No jank in any Compose screen (run Compose profiler)

**Deliverables:**
- Accessibility-clean app
- Polish sweep complete

**Acceptance criteria:**
- TalkBack end-to-end walkthrough succeeds
- 2.0x font scale doesn't break layouts
- WCAG AA contrast on all text
- No jank > 16ms on any screen during scroll

**Human checkpoint:** accessibility walkthrough. Polish sign-off.

---

## Phase 16 — Hardening & launch prep

**Goal:** Adversarial eval baseline, Play Store submission ready, beta rollout started.

**Prerequisites:** Phase 15 complete.

**Tasks:**

1. Adversarial eval:
   - Run full golden sets
   - Run adversarial suite (recompressions, transforms)
   - Document results in a report
   - Identify weaknesses → log in decision doc

2. Privacy audit:
   - Trace every network call in the app
   - Confirm no media-derived data leaves the device except model-signed manifest fetches
   - Write privacy policy matching actual behavior
   - Fill Data Safety form for Play Store matching actual behavior

3. Play Store listing:
   - Screenshots (reuse mockup aesthetics)
   - Description (short + full)
   - Category: Tools or Productivity
   - Target audience and content rating
   - Data safety form complete

4. Pre-review checklist:
   - Permissions: each has clear in-app justification before request
   - Overlay permission: discloser screen (1.6) is unavoidable and clear
   - Foreground service: notification visible, purpose matches
   - No prohibited permissions
   - `targetSdk` = 35

5. Closed beta:
   - 20–50 users via Play Console
   - 2-week beta
   - Collect feedback, fix issues

6. Staged rollout:
   - 1% → 10% → 50% → 100%
   - Pause any stage if crash rate > 0.5% or ANR > 0.1%

7. Prepare v1.1 backlog:
   - Features deferred during v1 (paste-link resolution, history search, bubble position presets, etc.)
   - Adversarial weaknesses to address

**Deliverables:**
- Adversarial eval report
- Privacy audit report
- Play Store listing submitted
- Beta running

**Acceptance criteria:**
- Play Store review approves listing
- Beta gathers feedback, critical issues triaged
- v1.0 launches to staged rollout

**Pitfalls:**
- Play Store reviews of overlay + screen-capture apps are strict. Expect rejection first time; address the feedback calmly.
- Privacy policy must match Data Safety form must match actual behavior. Inconsistency is a rejection risk.
- Do not launch without the model-rollback mechanism tested. One bad model can tank the app.

**Human checkpoint:** launch decision.

---

## Appendix A — Phase completion report template

At the end of each phase, the agent produces a report in this format:

```markdown
# Phase N Completion Report

**Phase:** [N — Name]
**Completed:** [date]
**Estimated effort:** [from plan]
**Actual effort:** [hours]

## Deliverables
- [x] Item from plan
- [x] Another item
- [ ] Item NOT completed — reason:

## Acceptance criteria
- [x] Criterion 1 — verified via [method]
- [x] Criterion 2 — verified via [method]

## Decisions made
- [Decision X]: chose [A] over [B] because [reason]. Logged in 05_GLOSSARY_AND_DECISIONS.md.

## Deviations from plan
- [what was different and why]

## Pitfalls encountered
- [pitfall]: [how resolved]

## Open questions for human
- [question]

## Ready for Phase N+1?
[Yes / No — blockers if no]

## Demo instructions
[How to verify this phase on device]
```

Human sign-off required before agent starts N+1.

---

## Appendix B — When to stop and ask

Agent: escalate to human and wait for guidance in any of these situations:

1. A task requires a library or service not listed in the plan
2. Acceptance criteria cannot be met despite sincere effort
3. A pitfall was encountered that isn't in the list
4. A decision would meaningfully change the visual spec or architecture
5. Detection quality fails to meet thresholds in phases 7/8/9
6. Any security, privacy, or Play Store compliance concern arises
7. Estimated effort for current phase exceeds 2x the estimate

Never silently drop a requirement or paper over a problem. Honest communication > looking productive.
