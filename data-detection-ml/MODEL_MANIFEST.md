# Veritas Runtime Model Manifest

## Phase 7 Image Model

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

## Phase 8 Audio Model

- Source model: `Hemgg/Deepfake-audio-detection`
- Hugging Face revision: `0d75271368ef2c7efd14831dc503c431f6aab0eb`
- License: Apache 2.0
- Architecture: `facebook/wav2vec2-base`, fine-tuned binary audio classification
- Output label order: `logit[0] = AIVoice`, `logit[1] = HumanVoice`; synthetic score uses softmax index `0`
- Runtime asset: TFLite weight-only INT8 quantized model from ai-edge-quantizer (`wi8_afp32`)
- Quantization note: weights are INT8 while input, output, activations, and compute remain float32; full INT8 on this graph was not runtime-valid on Windows conversion attempts
- Compatibility note: the final artifact forces internal TFLite buffers because Play Services LiteRT rejected ai-edge-quantizer's external-buffer serialization with `Input tensor 255 lacks data`
- Input: 16 kHz mono raw PCM waveform, padded/truncated to `80000` float32 samples
- Source ONNX SHA-256: `73f7c3104bf82b0c1f053daf3e5f01d260bdaa3f95f2fc1728d24a23a24d5351`
- TFLite parity softmax MAE: `0.02847679`
- TFLite parity max gate: `< 0.05`
- Final size: `96190928` bytes
- Ed25519 public key (base64 DER): `MCowBQYDK2VwAyEATNyvAq6FDkWUF9zUaVObExc/7QBE7PLa6QNt/AsP/10=`

| Asset | SHA-256 | Size bytes |
| --- | --- | ---: |
| `deepfake-audio-detector-hemgg-wi8.tflite` | `3046375262e631f25eb801c3480306235731f9bd95cedcf450663a31116f0b4c` | 96190928 |

## Phase 9 Video Models

### Temporal Detector

- Source model: TensorFlow Hub `tensorflow/lite-model/movinet/a0/stream/kinetics-600/classification/tflite/int8/1`
- License: Apache 2.0
- Architecture: MoViNet-A0 streaming action-recognition model, used as a temporal drift heuristic rather than as a 600-class action labeler
- Runtime asset: official TF Hub INT8 TFLite stream model
- Input: sampled RGB video frames center-cropped/resized to `172 x 172`, float32 `[0, 1]`, one frame per streaming invocation
- State handling: model state tensors are carried from each invocation output into the next invocation input
- FlexOps check: asset bytes contain no `Flex`, `SELECT`, `Erf`, or `GELU` strings
- Ed25519 public key (base64 DER): `MCowBQYDK2VwAyEATNyvAq6FDkWUF9zUaVObExc/7QBE7PLa6QNt/AsP/10=`

| Asset | SHA-256 | Size bytes |
| --- | --- | ---: |
| `movinet-a0-stream-int8.tflite` | `6125b36e2485eeb5cc1fd6206cf5e5d70235593fbc84610d1dce97fe3aa9c2ac` | 3276048 |

### Face Detector

- Source model: MediaPipe `blaze_face_short_range`, float16 variant
- License: Apache 2.0
- Runtime asset: MediaPipe Face Detector short-range model
- Use: face bounding boxes for optional ROI consistency scoring; no landmarks and no rPPG signal in v1
- Ed25519 public key (base64 DER): `MCowBQYDK2VwAyEATNyvAq6FDkWUF9zUaVObExc/7QBE7PLa6QNt/AsP/10=`

| Asset | SHA-256 | Size bytes |
| --- | --- | ---: |
| `blaze_face_short_range.tflite` | `b4578f35940bf5a1a655214a1cce5cab13eba73c1297cd78e1a04c2380b0152f` | 229746 |
