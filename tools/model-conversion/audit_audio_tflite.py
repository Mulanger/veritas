#!/usr/bin/env python3
"""Audit an audio detector TFLite model against the source ONNX model."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
import tensorflow as tf


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--onnx", default="tools/model-conversion/work/audio-hf/onnx/model.onnx")
    parser.add_argument("--tflite", required=True)
    parser.add_argument("--calibration", default="tools/model-conversion/work/audio_real_calibration_0_1.npy")
    parser.add_argument("--samples", type=int, default=20)
    parser.add_argument("--report", default="tools/model-conversion/work/audio_tflite_audit.json")
    return parser.parse_args()


def softmax(logits: np.ndarray) -> np.ndarray:
    logits = logits.astype(np.float64)
    logits -= np.max(logits, axis=-1, keepdims=True)
    exp = np.exp(logits)
    return exp / np.sum(exp, axis=-1, keepdims=True)


def quantize(values: np.ndarray, tensor_detail: dict) -> np.ndarray:
    dtype = tensor_detail["dtype"]
    if dtype == np.float32:
        return values.astype(np.float32)
    scale, zero_point = tensor_detail["quantization"]
    if not scale:
        raise ValueError(f"missing input quantization parameters for {tensor_detail['name']}")
    q = np.round(values / scale + zero_point)
    info = np.iinfo(dtype)
    return np.clip(q, info.min, info.max).astype(dtype)


def dequantize(values: np.ndarray, tensor_detail: dict) -> np.ndarray:
    dtype = tensor_detail["dtype"]
    if dtype == np.float32:
        return values.astype(np.float32)
    scale, zero_point = tensor_detail["quantization"]
    if not scale:
        raise ValueError(f"missing output quantization parameters for {tensor_detail['name']}")
    return (values.astype(np.float32) - zero_point) * scale


def tensor_dtype_coverage(interpreter: tf.lite.Interpreter) -> dict[str, object]:
    counts: dict[str, int] = {}
    approx_bytes: dict[str, int] = {}
    unquantized_float_tensors: list[str] = []
    for detail in interpreter.get_tensor_details():
        dtype = np.dtype(detail["dtype"]).name
        counts[dtype] = counts.get(dtype, 0) + 1
        shape = detail.get("shape")
        elements = int(np.prod(shape)) if shape is not None and len(shape) else 1
        approx_bytes[dtype] = approx_bytes.get(dtype, 0) + elements * np.dtype(detail["dtype"]).itemsize
        if detail["dtype"] == np.float32 and "quantization" in detail and detail["quantization"] == (0.0, 0):
            unquantized_float_tensors.append(detail["name"])
    return {
        "tensor_counts_by_dtype": counts,
        "approx_tensor_bytes_by_dtype": approx_bytes,
        "unquantized_float_tensor_count": len(unquantized_float_tensors),
        "unquantized_float_tensor_examples": unquantized_float_tensors[:30],
    }


def main() -> None:
    args = parse_args()
    calibration = np.load(args.calibration)
    rows_0_1 = calibration[: args.samples].astype(np.float32)
    rows = (rows_0_1 - 0.5) / 0.5

    onnx_session = ort.InferenceSession(str(args.onnx), providers=["CPUExecutionProvider"])
    onnx_input = onnx_session.get_inputs()[0].name

    interpreter = tf.lite.Interpreter(model_path=str(args.tflite))
    initial_input = interpreter.get_input_details()[0]
    if int(np.prod(initial_input["shape"])) != rows.shape[1]:
        interpreter.resize_tensor_input(initial_input["index"], [1, rows.shape[1]], strict=False)
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]

    softmax_mae_values = []
    fake_probability_mae_values = []
    swapped_softmax_mae_values = []
    swapped_fake_probability_mae_values = []
    for row in rows:
        onnx_logits = onnx_session.run(None, {onnx_input: row.reshape(1, -1).astype(np.float32)})[0]
        onnx_probs = softmax(onnx_logits)

        input_tensor = quantize(row.reshape(input_detail["shape"]).astype(np.float32), input_detail)
        interpreter.set_tensor(input_detail["index"], input_tensor)
        interpreter.invoke()
        tflite_raw = interpreter.get_tensor(output_detail["index"])
        tflite_logits = dequantize(tflite_raw, output_detail)
        tflite_probs = softmax(tflite_logits)
        swapped_tflite_probs = tflite_probs[:, ::-1] if tflite_probs.shape[-1] == 2 else tflite_probs

        softmax_mae_values.append(float(np.mean(np.abs(onnx_probs - tflite_probs))))
        fake_probability_mae_values.append(float(abs(onnx_probs[0, 0] - tflite_probs[0, 0])))
        swapped_softmax_mae_values.append(float(np.mean(np.abs(onnx_probs - swapped_tflite_probs))))
        swapped_fake_probability_mae_values.append(float(abs(onnx_probs[0, 0] - swapped_tflite_probs[0, 0])))

    report = {
        "onnx": str(Path(args.onnx)),
        "tflite": str(Path(args.tflite)),
        "tflite_size_bytes": Path(args.tflite).stat().st_size,
        "input": {
            "name": input_detail["name"],
            "dtype": np.dtype(input_detail["dtype"]).name,
            "shape": input_detail["shape"].tolist(),
            "quantization": input_detail["quantization"],
        },
        "output": {
            "name": output_detail["name"],
            "dtype": np.dtype(output_detail["dtype"]).name,
            "shape": output_detail["shape"].tolist(),
            "quantization": output_detail["quantization"],
        },
        "parity_samples": len(softmax_mae_values),
        "softmax_mae": float(np.mean(softmax_mae_values)),
        "softmax_mae_percent": float(np.mean(softmax_mae_values) * 100.0),
        "fake_probability_mae": float(np.mean(fake_probability_mae_values)),
        "fake_probability_mae_percent": float(np.mean(fake_probability_mae_values) * 100.0),
        "swapped_output_softmax_mae": float(np.mean(swapped_softmax_mae_values)),
        "swapped_output_softmax_mae_percent": float(np.mean(swapped_softmax_mae_values) * 100.0),
        "swapped_output_fake_probability_mae": float(np.mean(swapped_fake_probability_mae_values)),
        "swapped_output_fake_probability_mae_percent": float(np.mean(swapped_fake_probability_mae_values) * 100.0),
        "dtype_coverage": tensor_dtype_coverage(interpreter),
    }
    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
