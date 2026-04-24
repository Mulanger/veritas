# Session Notes

## Date
2026-04-24

## Branch
`codex/phase/6-provenance-layer`

## Current status
- Phase 6 provenance layer implementation COMPLETE
- Session commits on `codex/phase/6-provenance-layer`:
  - `data-detection/build.gradle.kts` — fixed c2pa-android version 0.0.9, removed duplicate JNA dep
  - `data-detection/C2PADetector.kt` — real implementation using `org.contentauth.c2pa.C2PA.readFile()` + manifest JSON parsing
  - `domain-detection/C2PAModels.kt` — added `instanceId` field to `C2PAResult.Present`
  - `data-detection/ProvenancePipeline.kt` — unchanged (already used all C2PAResult fields correctly)
  - `data-detection/SynthIDDetector.kt` — stub always returning NotPresent
  - `docs/05_GLOSSARY_AND_DECISIONS.md` — D-035 updated to reflect c2pa-android AAR adoption
  - `native/c2pa-wrapper/` — deleted (Rust JNI work no longer needed)
  - `docs/phase_reports/phase_6.md` — Phase 6 completion report
- APK builds successfully with c2pa-android native libs bundled
- Commit: `b0d84e7`

## Not yet done
- Push branch and open PR
- Human Phase 6 checkpoint review and sign-off
- Wait for explicit human go-ahead before starting Phase 7

## Open questions for next session
- SynthID: confirm if v1.1 should use Gemini API via Play Services or wait for dedicated SDK
- Trust list: placeholder in assets; real trust list download/update deferred to Phase 13
- Lint issues to fix: LongMethod (parseManifest), TooManyFunctions (ProvenancePipeline), unused params in SynthIDDetector

## Recommended starting point
- Push branch, open PR, wait for Phase 6 approval
- Phase 7 (Image detector) begins after human sign-off on Phase 6