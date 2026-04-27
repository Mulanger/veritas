# Phase 9 Report - Video Detector

## Status

Phase 9 implementation is wired and verified on Pixel 8.

## Implemented

- Added `feature-detect-video`.
- Reused the Phase 7 image detector for sampled-frame spatial scoring.
- Added signed MoViNet-A0-Stream INT8 TFLite temporal model.
- Added signed MediaPipe BlazeFace short-range model for optional face ROI consistency.
- Integrated video verdicts into `data-detection` `ProvenancePipeline`.
- Added video sub-scores:
  - `spatial_vit`
  - `temporal_movinet`
  - `face_consistency`
- Added video reason and uncertainty codes, including decode failure handling.
- Added `tools/eval-datasets/build_video_golden_set.py`.
- Built and ran a local 200-video static-derived golden manifest from Phase 7 image assets.
- Replaced the initial retriever-backed frame extraction with sequential `MediaExtractor` + `MediaCodec` decoding, with retriever retained only as a fallback.

## Model Assets

| Asset | Size bytes | SHA-256 |
|---|---:|---|
| `movinet-a0-stream-int8.tflite` | `3276048` | `6125b36e2485eeb5cc1fd6206cf5e5d70235593fbc84610d1dce97fe3aa9c2ac` |
| `blaze_face_short_range.tflite` | `229746` | `b4578f35940bf5a1a655214a1cce5cab13eba73c1297cd78e1a04c2380b0152f` |

Total Phase 9 model payload added: `3505794` bytes, plus two 64-byte Ed25519 signatures.

## Verification

- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-video:testDebugUnitTest`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-video:compileDebugAndroidTestKotlin`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :data-detection:compileDebugKotlin :app:compileDebugKotlin`
- `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; .\gradlew.bat :feature-detect-video:connectedDebugAndroidTest`

Pixel 8 result: pass.

Fallback verification:

| Interpreter | Pixel 8 fallback |
|---|---|
| Phase 7 image detector | `GPU` |
| MoViNet temporal detector | `GPU` |
| MediaPipe BlazeFace detector | `GPU` |

## Golden Set

Builder output:

- Total: `200`
- Real: `100`
- Synthetic: `100`
- H.264 MP4: `68`
- H.264 MOV: `66`
- VP9 WebM: `66`
- Generator buckets:
  - `image_to_video_static_camera_photo`: `100`
  - `image_to_video_static_gan_or_diffusion`: `100`

This harness verifies video decode and end-to-end detector integration but is static-image-derived. It does not replace the real FaceForensics++/DFDC/DF40-style evaluation requested by the plan.

Pixel 8 eval result:

- Overall accuracy: `0.905`
- Pipeline accuracy: `0.750`
- FPR: `0.150`
- FNR: `0.040`
- Uncertain rate: `0.220`
- Spatial-only accuracy: `0.890`
- Full-fusion accuracy: `0.905`
- p50 latency: `2939 ms`
- p95 latency: `3074 ms`
- p50 extraction: `1054 ms`
- p95 extraction: `1158 ms`
- Extraction share: `0.365094` mean for the `0-5s` bucket

Uncertainty gate investigation:

| Uncertain reason | Count |
|---|---:|
| `LOW_CONFIDENCE_RANGE` | `24` |
| `VID_HEAVY_COMPRESSION` | `21` |
| `VID_NO_FACES_DETECTED` | `4` |

The blocker was `VID_HEAVY_COMPRESSION`: before recalibration it fired on `199/200` clips because the original absolute bitrate threshold treated low-motion/static-video compressibility as quality loss. The threshold now flags only extreme bit starvation, which brought the user-facing uncertain rate into the `5%–25%` acceptance range.

Per-generator breakdown:

| Generator bucket | Accuracy | FPR | FNR | Uncertain rate | p95 latency |
|---|---:|---:|---:|---:|---:|
| `image_to_video_static_camera_photo` | `0.850` | `0.150` | `0.000` | `0.260` | `3074 ms` |
| `image_to_video_static_gan_or_diffusion` | `0.960` | `0.000` | `0.040` | `0.200` | `3071 ms` |

## Known Limitations

Eval was conducted against image-to-video static-derived content. Detection quality on true video deepfakes, including face-swap manipulations, full-frame generation models like Sora/Runway/Veo, and lip-sync deepfakes, is unmeasured.

The temporal MoViNet signal contributed only `1.5%` accuracy over spatial-only on this eval, but cannot be evaluated for video deepfake detection without a video-deepfake eval set. This v1 result is best understood as Phase 7 image detection applied to sampled video frames plus integration-ready temporal and face-ROI wiring.

## Decisions

- D-049: rPPG deferred to v1.5.
- D-050: MoViNet A0 stream INT8 v1 selected for temporal drift.
- D-051: 4-frame latency-constrained sampling, first-frame spatial scoring, center-crop MoViNet preprocessing.
- D-052: fixed video fusion weights, false-positive guard band, and static-derived local golden set.
- D-053: initial retriever-backed decode replaced with MediaCodec after Pixel latency evidence.
