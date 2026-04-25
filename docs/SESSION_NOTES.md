# Session Notes

## Date
2026-04-25

## Branch
`codex/phase/6-provenance-layer`

## Current Status
- Phase 6 has been audited, patched, and verified on `veritas_test_avd`.
- `Phase6C2PAVerificationTest` passes 10/10, including trust-policy and pipeline verdict tests.
- `Phase5DetectionFlowTest` passes 5/5 after provenance integration.
- Ready for Phase 6 human checkpoint / PR review; do not start Phase 7 until sign-off.

## Fixes Applied
- `C2PADetector.kt`
  - Uses `Reader.fromStream(mimeType, ByteArrayStream(file.readBytes()))`.
  - Parses detailed JSON fields under `claim`, `signature`, `assertion_store`, and `validation_results`.
  - Treats integrity failures such as `assertion.hashedURI.mismatch` as `C2PAResult.Invalid`.
  - Treats parser/no-manifest failures on unsigned or dummy media as `NotPresent`.
  - Enforces `C2PATrustPolicy` so untrusted issuers and disallowed expired credentials return `Invalid`.
- `C2PATrustPolicy.kt`
  - Loads bundled trusted issuers from `app/src/main/assets/c2pa_trusted_issuers.txt`.
  - Provides fixture and strict policies for instrumented verification.
- `Phase6C2PAVerificationTest.kt`
  - Copies fixtures from androidTest assets through `InstrumentationRegistry.getInstrumentation().context`.
  - Verifies trusted fixture extraction, tamper invalidation, strict untrusted rejection, strict expired-credential rejection, and provenance-pipeline verdict mapping.
- `ProvenancePipeline.kt`
  - Delegates `NotPresent` media to the Phase 5 fake detector path.
  - Cancels the fake delegate when provenance pipeline cancellation is requested.
- Documentation
  - Updated D-035 and `docs/phase_reports/phase_6.md` to reflect actual verified behavior.
  - Added D-037 for runtime C2PA issuer allowlist enforcement.

## Verification Commands
- `./gradlew :data-detection:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
- `./gradlew :app:connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase6C2PAVerificationTest" --info`
- `./gradlew :app:connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.veritas.app.Phase5DetectionFlowTest" --info`
- `./gradlew precommitCheck`

## Open Questions
- SynthID remains deferred to v1.1 pending an official public Android detector SDK.
- Signed C2PA trust-list delivery remains deferred to Phase 13; runtime enforcement now exists in Phase 6.
