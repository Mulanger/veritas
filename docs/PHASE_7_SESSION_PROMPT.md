# Veritas — Phase 7 Session Prompt

> Copy everything below the `---` line into your new agent. All reference docs should be in the agent's accessible directory (typically `docs/` in the repo), including the new `06_PHASE_7_PLAN.md`.

---

I'm continuing work on **Veritas**, an Android app that verifies whether videos, audio clips, and images are AI-generated. We work through this in sequenced phases. You have full project context in the `docs/` folder. Phases 0–6 are complete.

## Current state

We are working on: **PHASE 7 — Image detector (real ML integration)**

Session goal: **complete Phase 7 per `docs/06_PHASE_7_PLAN.md`. If time runs short, the minimum acceptable stopping point is §7 Step 5 — first end-to-end inference on a Bitmap with a single score emitted. Steps 6+ can be split across sessions.**

## IMPORTANT — Documentation authority for this phase

Phase 7 has been rewritten as a standalone execution plan. **Use `docs/06_PHASE_7_PLAN.md` as the single source of truth for this phase.**

**Do NOT** look at `docs/03_PHASE_PLAN.md` Phase 7 or `docs/04_IMPLEMENTATION_PLAN_7_9.md` Phase 7 sections. Both are superseded. The new plan corrects several issues in those documents (license of chosen model, deprecated NNAPI references, scope creep into ensemble complexity) and is self-contained.

If you encounter guidance in any other document that contradicts `06_PHASE_7_PLAN.md` for this phase, `06_PHASE_7_PLAN.md` wins. Note the contradiction in the phase completion report so the older documents can be cleaned up afterward.

## Before writing any code, read these documents in the order specified in `06_PHASE_7_PLAN.md` §0

1. **`docs/06_PHASE_7_PLAN.md`** — read completely, including all 14 sections. This is your primary execution document for this session.
2. **`docs/SESSION_NOTES.md`** — see what previous sessions left in place. Phases 0–6 should all be complete with passing tests.
3. **`docs/phase_reports/phase_6.md`** — confirms prerequisites. Specifically: C2PA provenance layer should be working, SynthID is stubbed per D-034, and the detection pipeline from Phase 5 has the `FusionEngine` ready to accept real `DetectorResult` values.
4. **`docs/05_GLOSSARY_AND_DECISIONS.md`** — scan Part 3 (decision log) for any decisions logged since Phase 6 started. Pay special attention to **D-016** (LiteRT via Play Services, NNAPI forbidden), **D-018** (training vs runtime separation), and **D-034** (SynthID stubbed).
5. **`docs/04_DATA_CONTRACTS.md`** — the `Verdict`, `DetectorResult`, `ConfidenceInterval`, `ReasonCode` definitions. These are the contracts your output must satisfy.
6. **`docs/02_VISUAL_SPEC.md`** §2.16, §2.17, §7.3 — image verdict screens and forensic image view. Note the **Mockup** column — these screens are in `veritas_mockup_3_verdicts_forensic.html` (file 3).
7. **`docs/01_ARCHITECTURE.md`** §6 (detection pipeline) and §8 (performance). You're not redesigning architecture; you're implementing against it.

After reading, give me a **6-line status summary**:
- Confirm Phase 7's scope as you understand it (one sentence).
- Which model is being integrated and its license (one sentence).
- What Phase 6 left in place (from SESSION_NOTES / phase_6.md).
- Your planned execution order for `06_PHASE_7_PLAN.md` §7 Steps 1–14, with any reordering justified.
- Any blockers, ambiguities, or decisions you think need my input before coding.
- Confirmation you've read all the pitfalls in `06_PHASE_7_PLAN.md` §10 and understand them.

**Wait for my go-ahead before writing code.**

## Phase 7 specific guidance

Everything below is a summary. The authoritative details are in `06_PHASE_7_PLAN.md`.

### The single most important constraint for this phase
**This phase is product-functional, not production-calibrated.** We are integrating an existing open-source model, not training or fine-tuning. The goal is a working image detector embedded in a working app. Real-world accuracy will be imperfect — the UNCERTAIN verdict absorbs edge cases. Retraining for quality is a separate workstream that happens AFTER the full product is working. Do not propose training runs, dataset curation, or fine-tuning in this phase.

### Model choice is already decided
- Use `prithivMLmods/Deep-Fake-Detector-v2-Model` (Apache 2.0).
- ONNX version available at `onnx-community/Deep-Fake-Detector-v2-Model-ONNX`.
- Target final size ≤ 80 MB INT8 TFLite, hard cap 100 MB.
- Do NOT propose alternative models. Do NOT add a second ML detector (FreqNet was evaluated and dropped — license issue, same as NPR).

### Runtime stack is already decided
- LiteRT via **Google Play Services** (`play-services-tflite-java` + `play-services-tflite-gpu`).
- Delegate chain: **GPU delegate → XNNPACK CPU**.
- **NNAPI is forbidden** per D-016. If you see any document mentioning NNAPI-first delegate chains, that's a superseded recommendation and must not be followed.

### Two new modules to create
- `data-detection-ml` — shared ML runtime infrastructure. Phases 8 and 9 will reuse this.
- `feature-detect-image` — image-specific detector logic.

Details of module structure in `06_PHASE_7_PLAN.md` §4.

### Contract your detector must satisfy
Output is a `DetectorResult` per `04_DATA_CONTRACTS.md`. Key constraints:
- Score clamped to `[0.02, 0.98]` — never emit absolute certainty.
- Confidence interval ceiling at 0.95.
- For UNCERTAIN-bound outputs, confidence interval MUST straddle the verdict threshold.
- Use exactly the `subScores` keys `"vit_model"` and `"exif_ela"` — these are referenced by the forensic UI.

### Step sequencing matters
`06_PHASE_7_PLAN.md` §7 has 14 numbered steps. Execute in order. Each step should compile and pass its own tests before moving to the next. Step 1 (runtime plumbing) is foundational — do not skip ahead. Steps 6–11 (forensics, fusion, calibration, uncertainty, reason codes) can be approached as one coherent block once the core inference works (Step 5).

### Golden-set eval scope
§7 Step 12 defines a **500-image golden-set eval** that the agent builds autonomously. The agent writes a Python script (`tools/eval-datasets/build_golden_set.py`) that pulls from public datasets (Kaggle: `manjilkarki/deepfake-and-real-images`; Hugging Face: `ComplexDataLab/OpenFake`, `OpenRL/DeepFakeFace`) and organizes them into `feature-detect-image/src/androidTest/assets/golden-image/`. Acceptance thresholds for product-functional mode are relaxed from a production system:
- Overall accuracy > 65%
- FPR ≤ 15%
- p95 latency ≤ 2.5s
- Per-generator breakdown present (no threshold — it's diagnostic)

**Human prerequisite (one-time, 5 min):** create a free Kaggle account, generate API token at kaggle.com/settings/account, place `kaggle.json` in `~/.kaggle/`. Agent's dataset script validates token presence and fails loudly if missing.

If accuracy is 65–85%, ship as-is. Do not chase higher numbers — that's retraining territory. The per-generator breakdown is the key output; it tells you which models to prioritize in the retraining workstream.

No fusion-weight tuning step in this phase. Hand-tuned weights from Step 8 ship as-is. Weight tuning happens during retraining.

### Model signature verification is non-optional
Even though the model ships bundled in v1, Ed25519 signature verification at load time is required (§7 Step 3). This forces correct implementation of the signed-model path before Phase 13 activates OTA updates. Do not defer this.

## Operating rules (unchanged every session)

1. Work tasks in the order listed in `06_PHASE_7_PLAN.md` §7 unless a dependency forces otherwise. If you reorder, justify it.
2. Reference docs in this priority for this phase: **`06_PHASE_7_PLAN.md`** > data contracts > architecture > visual spec > glossary.
3. Record non-trivial decisions in `docs/05_GLOSSARY_AND_DECISIONS.md` Part 3 using the template. Continue decision IDs from the last logged entry. Likely decisions for this phase: how the ModelAsset signature verification key is stored, fusion weight adjustments after eval, any deviations from the ViT preprocessing spec.
4. Do not skip pitfalls listed in `06_PHASE_7_PLAN.md` §10. Read them before starting code.
5. Do not expand scope. Do not add a second ML model. Do not start training or fine-tuning anything in this phase — the golden-set eval is the bar, retraining is a separate workstream.
6. Anti-patterns in `05_GLOSSARY_AND_DECISIONS.md` Part 5 are hard rules.
7. Honest communication > looking productive. If blocked on model conversion, device testing, or eval quality, say so. Don't silently drop requirements.
8. Commit frequently. Use branch `phase/7-image-detector`. Conventional commit prefixes.
9. **Evidence before workaround.** Before claiming a library, platform, or environment limitation is blocking you, you must produce: (a) the actual error or output observed, verbatim; (b) the relevant section of documentation or source code that supports your claim; (c) what you tried that didn't work, with results. Workaround proposals without all three are rejected. This rule exists because in earlier phases agents proposed plausible-sounding limitations that turned out to be wrong on investigation. Don't repeat that pattern.
10. **Run your own verification.** When you finish a step, you run the tests yourself and report results — don't ask the human to run them and report back. The human will verify your verification, but the actual `gradlew test` / `gradlew connectedAndroidTest` execution is your job. If you cannot run a test in your environment, that's a blocker to escalate, not a task to pass off.

## Testing requirements

See `06_PHASE_7_PLAN.md` §9 for the full list. Minimum tests before phase is considered complete include unit tests, on-device instrumented tests, and the 500-image golden-set eval from §7 Step 12.

## At end of session

When Phase 7 acceptance criteria are met (see `06_PHASE_7_PLAN.md` §11 Deliverables):
1. Produce the phase completion report per `06_PHASE_7_PLAN.md` §13 — save as `docs/phase_reports/phase_7.md`.
2. Include the smoke-test results table: 10 files, verdicts, confidence intervals, reason codes, directional-correctness ratio.
3. Record every new decision in `05_GLOSSARY_AND_DECISIONS.md`.
4. Update `MODEL_MANIFEST.md` with final model provenance.
5. Merge `phase/7-image-detector` into `main` via PR. Do NOT squash — preserve per-step commit history.
6. Update `docs/SESSION_NOTES.md` with Phase 7 end state and what Phase 8 needs.
7. **Wait for my explicit go-ahead before starting Phase 8.**

If Phase 7 is NOT complete at session end:
1. Update `docs/SESSION_NOTES.md` with exact resume point.
2. Leave the branch open.
3. Do not merge.

## When to stop and ask me

See `06_PHASE_7_PLAN.md` §12. Escalate when:
- ONNX → TFLite conversion fails, or parity check shows > 5% MAE
- Final TFLite size exceeds 100 MB hard cap
- Smoke test shows 0/10 directional-correctness after one investigation round, or all verdicts identical regardless of input
- Dependency conflicts with an existing project dependency
- Latency > 5 seconds even after profiling
- Any license concern arises around training data provenance
- Asked to skip or weaken signature verification

Never silently drop any of the §11 Deliverables.

## First action

Give me your 6-line status summary. Wait for my go-ahead.
