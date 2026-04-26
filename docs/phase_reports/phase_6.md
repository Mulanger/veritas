# Phase 6 Completion Report

**Phase:** 6 - Provenance layer  
**Completed:** 2026-04-25  
**Estimated effort:** 30 hrs  
**Actual effort:** Prior implementation plus verification/fix pass

## Deliverables

- [x] C2PA verification via `com.github.contentauth:c2pa-android:0.0.9`
- [x] `C2PADetector` parses real C2PA manifests using `Reader.fromStream(mimeType, ByteArrayStream(file.readBytes()))`
- [x] Detailed JSON parsing supports the observed c2pa-android manifest shape: `claim`, `signature`, `assertion_store`, and `validation_results`
- [x] Integrity failures such as `assertion.hashedURI.mismatch` return `C2PAResult.Invalid`
- [x] Bundled C2PA issuer allowlist gates credential trust through `C2PATrustPolicy`
- [x] Untrusted issuers and disallowed expired credentials return `C2PAResult.Invalid`
- [x] Test-fixture asset copying uses the androidTest asset context
- [x] `ProvenancePipeline` is the primary `DetectionPipeline` binding
- [x] `C2PAResult.NotPresent` falls through to SynthID stub, then Phase 5 fake detection
- [x] `C2PAResult.Present` short-circuits to `VERIFIED_AUTHENTIC`
- [x] `C2PAResult.Invalid` and `Revoked` produce negative provenance reason codes
- [x] `SynthIDDetector` is stubbed and deferred to v1.1 per D-034

## Acceptance Criteria

- [x] Signed Adobe C2PA test image returns a present C2PA result and can drive verified-authentic provenance behavior
- [x] Manually tampered signed image returns `C2PAResult.Invalid` with `assertion.hashedURI.mismatch`
- [x] Unsigned/unparseable media returns `C2PAResult.NotPresent` and continues to the fake detector path
- [x] Strict C2PA trust policy rejects an otherwise signed fixture when the issuer is not trusted
- [x] Strict C2PA trust policy rejects an expired Nikon credential when expiry is not allowed
- [x] Decision log records SynthID deferral and c2pa-android AAR adoption
- [x] Phase 5 scan-flow behavior still passes after provenance integration

## Decisions Made

- **D-034** (SynthID deferred to v1.1): unchanged; no public Android SDK is available.
- **D-035** (C2PA via c2pa-android AAR): retained, but implementation now uses `Reader.fromStream` with `ByteArrayStream` to avoid native file/mmap behavior.
- **D-036** (ProvenancePipeline binding): retained; fixed so the pipeline actually delegates `NOT_PRESENT` media to the Phase 5 fake detector path.
- **D-037** (C2PA issuer allowlist gates credential trust): added to make credential trust and expiry failures runtime-enforced in Phase 6.

## Deviations From Plan

- The plan called for from-source `c2pa-rs` JNI work. The implementation uses the official c2pa-android AAR, which already wraps the Rust library.
- SynthID remains deferred because no public Android detector SDK is available.
- Trust-list updates still belong with signed trust-list/update work in Phase 13. Phase 6 now ships the runtime enforcement seam and bundled initial issuer allowlist.

## Pitfalls Encountered

- `copyFixtureToCache()` used app assets instead of androidTest assets, so signed fixtures were packaged but invisible to the test.
- The detector used `FileStream`, contradicting the intended byte-array stream path and risking native file access issues.
- The previous JSON parser ignored the detailed C2PA shape where fields live under `claim`, `signature`, `assertion_store`, and `validation_results`.
- `ProvenancePipeline` claimed fake-detector delegation but actually returned likely-authentic for all `NOT_PRESENT` media.
- Dummy Phase 5 MP4 files were initially treated as invalid provenance instead of no C2PA manifest.

## Known Limitations

- Revoked certificate behavior is modeled but not fixture-tested.
- Files over 150 MB are skipped for in-memory C2PA parsing and return `NotPresent`.

## Verification

- `./gradlew :data-detection:compileDebugKotlin :app:compileDebugAndroidTestKotlin` - passed
- `./gradlew :app:connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase6C2PAVerificationTest" --info` - passed, 10/10
- `./gradlew :app:connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase5DetectionFlowTest" --info` - passed, 5/5
- `./gradlew precommitCheck` - passed

## Ready for Phase 7?

Yes. Phase 6 C2PA verification, trust-policy behavior, provenance-pipeline verdict mapping, and Phase 5 regression coverage are green on `veritas_test_avd`.
