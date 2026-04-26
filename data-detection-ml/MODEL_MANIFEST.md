# Phase 7 Image Model Manifest

- Source model: `prithivMLmods/Deep-Fake-Detector-v2-Model`
- ONNX conversion source: `onnx-community/Deep-Fake-Detector-v2-Model-ONNX` / `model.onnx`
- License: Apache 2.0
- Runtime asset: TensorFlow Lite dynamic-range quantized model with pseudo-lowered `Erf`/`GeLU` operators
- FP16 fallback: omitted because the generated FP16 artifact is `171768976` bytes and exceeds the 100 MB Phase 7 hard cap
- Output label order: `logit[0] - logit[1]` maps to synthetic probability for this ONNX export
- Source ONNX SHA-256: `f8bee0974ae734cc0b2dbc088f0fe8ac1714397b4578d337d585c7c6699eb15b`
- TFLite parity MAE: `0.002471`
- TFLite parity max absolute error: `0.005911`
- Ed25519 public key (base64 DER): `MCowBQYDK2VwAyEATNyvAq6FDkWUF9zUaVObExc/7QBE7PLa6QNt/AsP/10=`

| Asset | SHA-256 | Size bytes |
| --- | --- | ---: |
| `deepfake-detector-v2-int8.tflite` | `1c2cb319ef5e01e5e6c0688b99817fcddf7719f8e8b69a18bba316972dbf2f1e` | 87776504 |
