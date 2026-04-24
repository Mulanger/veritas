# Phase 6 Completion Report

**Phase:** 6 — Provenance layer
**Completed:** 2026-04-24
**Estimated effort:** 30 hrs
**Actual effort:** ~6 hrs (c2pa-android swap-in) + ~4 hrs prior session (stub + pipeline)

## Deliverables

- [x] C2PA verification via official `c2pa-android` AAR (JitPack, v0.0.9)
- [x] `C2PADetector` rewritten with `org.contentauth.c2pa.C2PA.readFile()` + manifest JSON parsing
- [x] `C2PAModels.kt` updated with `instanceId` field in `C2PAResult.Present`
- [x] `ProvenancePipeline` wires real C2PA result into `ReasonEvidence.C2PAVerified` with issuerName, deviceName, signedAt
- [x] `SynthIDDetector` stub (deferred to v1.1 per D-034)
- [x] D-035 updated: official c2pa-android AAR, not from-source c2pa-rs compilation
- [x] D-036: ProvenancePipeline bound as primary DetectionPipeline
- [x] `native/c2pa-wrapper/` deleted — Rust JNI work no longer needed
- [x] APK builds with bundled native libs (`libc2pa_c.so`, `libc2pa_jni.so`)

## Acceptance Criteria

- [x] c2pa-android AAR resolves via JitPack — `BUILD SUCCESSFUL`
- [x] `C2PADetector` calls `C2PA.readFile()` and parses manifest JSON for `instance_id`, `claim_generator`, `signature_info`
- [x] No manifest → `C2PAResult.NotPresent`; manifest present → `C2PAResult.Present` with all fields
- [x] Cryptographic validation errors → `C2PAResult.Invalid` with reason
- [x] Provenance pipeline: C2PA VALID short-circuits to VERIFIED_AUTHENTIC; INVALID/REVOKED → LIKELY_SYNTHETIC; NOT_PRESENT → continues to SynthID stub
- [x] Decision log D-035 updated to reflect c2pa-android adoption
- [x] Decision log D-034 (SynthID deferred) unchanged

## Decisions Made

- **D-034** (SynthID deferred to v1.1): Unchanged — no public Android SDK available.
- **D-035** (C2PA via c2pa-android AAR): Supersedes the Kotlin-only header-parsing approach. c2pa-android is Apache 2.0/MIT, JitPack-distributed, eliminates all cross-compilation complexity.
- **D-036** (ProvenancePipeline bound as DetectionPipeline): ProvenancePipeline replaces FakeDetectionPipeline as the primary binding, delegating ML stages to FakeDetectionPipeline for Phase 6.

## Deviations from Plan

- Original plan listed `c2pa-rs` JNI integration as the primary approach. That was blocked by Windows/WSL cross-compilation (ring-dylib build failures). Switched to official `c2pa-android` AAR per human direction.
- SynthID was always stubbed per D-034, which was decided in the prior session.

## Pitfalls Encountered

1. **JNA duplicate classes**: c2pa-android AAR already bundles JNA transitively. Explicit `jna:5.17.0@aar` in `data-detection/build.gradle.kts` caused duplicate class conflicts. Fixed by removing the explicit JNA dependency.
2. **Wrong c2pa-android version**: Initial dependency was `1.0.0` which doesn't exist on JitPack. Latest is `0.0.9`.
3. **`skipName()` doesn't exist on Android JsonReader**: Android's `android.util.JsonReader` doesn't have `skipName()`. Used `nextName()` + `skipValue()` instead.

## Open Questions for Human

- SynthID: Confirm if v1.1 should use Gemini API via Play Services or wait for dedicated SDK.
- Trust list: Current `C2PA-TRUST-LIST.pem` is a placeholder. Real trust list delivery deferred to Phase 13.

## Ready for Phase 7?

Yes — Phase 6 provenance layer is complete. C2PA detection is real (not a stub), pipeline design is validated, APK builds.

## Demo Instructions

1. Install APK on device or emulator
2. Share a known-C2PA-signed image (e.g., Adobe C2PA test content) → expect "Verified authentic" with issuer shown
3. Share a plain (unsigned) image → pipeline continues through stub ML stages
4. Check logcat for `C2PADetector` output showing manifest JSON parsing

## Git Commit SHA

Current HEAD: `8192b07` (Phase 5 completion)
This session's work will be committed on branch `codex/phase/6-provenance-layer`
