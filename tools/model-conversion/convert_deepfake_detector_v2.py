#!/usr/bin/env python3
"""Convert Deep-Fake-Detector-v2 ONNX to signed LiteRT assets.

The script intentionally keeps credentials and private signing keys outside
tracked paths. It fails loudly when parity images or a signing key are missing.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image


REPO_ID = "onnx-community/Deep-Fake-Detector-v2-Model-ONNX"
ONNX_FILENAME = "onnx/model.onnx"
INPUT_SIZE = 224
IMAGE_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
IMAGE_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)
TARGET_MODEL_BYTES = 80 * 1024 * 1024
HARD_CAP_BYTES = 100 * 1024 * 1024
MAX_PARITY_MAE = 0.02


@dataclass(frozen=True)
class ConvertedAsset:
    name: str
    path: Path
    sha256: str
    size_bytes: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-dir", default="tools/model-conversion/work")
    parser.add_argument("--output-dir", default="data-detection-ml/src/main/assets/models/image")
    parser.add_argument("--parity-images", required=True, help="Directory with at least 20 real/synthetic images")
    parser.add_argument("--representative-images", required=True, help="Directory with about 200 calibration images")
    parser.add_argument("--private-key", required=True, help="Untracked Ed25519 private key PEM")
    parser.add_argument("--onnx-file", default=ONNX_FILENAME, help="ONNX file path inside the Hugging Face repo")
    parser.add_argument("--public-key-out", default="data-detection-ml/src/main/assets/models/image/deepfake-detector-v2.pub")
    parser.add_argument("--manifest-out", default="data-detection-ml/MODEL_MANIFEST.md")
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def ensure_dependencies() -> None:
    missing = []
    for module in ("huggingface_hub", "onnxruntime", "tensorflow", "cryptography", "onnx2tf"):
        try:
            __import__(module)
        except ImportError:
            missing.append(module)
    if missing:
        raise SystemExit(
            "Missing Python packages: "
            + ", ".join(missing)
            + "\nInstall with: py -m pip install huggingface_hub onnxruntime tensorflow cryptography onnx2tf pillow numpy"
        )


def image_paths(directory: Path, minimum: int) -> list[Path]:
    paths = sorted(
        path
        for path in directory.rglob("*")
        if path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}
    )
    if len(paths) < minimum:
        raise SystemExit(f"{directory} contains {len(paths)} images; need at least {minimum}")
    return paths


def preprocess_nhwc(path: Path) -> np.ndarray:
    image = Image.open(path).convert("RGB").resize((INPUT_SIZE, INPUT_SIZE), Image.BICUBIC)
    array = np.asarray(image, dtype=np.float32) / 255.0
    array = (array - IMAGE_MEAN) / IMAGE_STD
    return array[np.newaxis, ...].astype(np.float32)


def preprocess_nchw(path: Path) -> np.ndarray:
    return np.transpose(preprocess_nhwc(path)[0], (2, 0, 1))[np.newaxis, ...].astype(np.float32)


def sigmoid(value: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-value))


def fake_probability(output: np.ndarray) -> float:
    flattened = np.asarray(output).reshape(-1).astype(np.float32)
    if flattened.size >= 2:
        return float(sigmoid(flattened[0] - flattened[1]))
    return float(sigmoid(flattened[0]))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_onnx(work_dir: Path, filename: str) -> Path:
    from huggingface_hub import hf_hub_download

    return Path(hf_hub_download(repo_id=REPO_ID, filename=filename, local_dir=work_dir / "hf"))


def write_calibration_tensor(paths: list[Path], output_path: Path) -> None:
    tensors = [preprocess_nhwc(path)[0] for path in paths]
    np.save(output_path, np.stack(tensors).astype(np.float32))


def convert_onnx_to_tflite(
    onnx_path: Path,
    conversion_dir: Path,
    calibration_path: Path,
) -> tuple[Path, Path]:
    if conversion_dir.exists():
        shutil.rmtree(conversion_dir)
    subprocess.run(
        [
            sys.executable,
            "-m",
            "onnx2tf",
            "-i",
            str(onnx_path),
            "-o",
            str(conversion_dir),
            "-tb",
            "tf_converter",
            "-b",
            "1",
            "-ois",
            "pixel_values:1,3,224,224",
            "-odrqt",
            "-rtpo",
            "erf",
            "gelu",
        ],
        check=True,
    )
    fp16_path = conversion_dir / f"{onnx_path.stem}_float16.tflite"
    dynamic_path = conversion_dir / f"{onnx_path.stem}_dynamic_range_quant.tflite"
    if not fp16_path.exists() or not dynamic_path.exists():
        raise SystemExit(f"onnx2tf did not produce expected FP16/dynamic outputs in {conversion_dir}")
    return fp16_path, dynamic_path


def run_onnx(onnx_path: Path, image_batch: np.ndarray) -> float:
    import onnxruntime as ort

    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    output = session.run(None, {input_name: image_batch})[0]
    return fake_probability(output)


def run_tflite(tflite_path: Path, image_batch: np.ndarray) -> float:
    import tensorflow as tf

    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    value = image_batch
    if input_detail["dtype"] != np.float32:
        scale, zero_point = input_detail["quantization"]
        value = (image_batch / scale + zero_point).round().astype(input_detail["dtype"])
    interpreter.set_tensor(input_detail["index"], value)
    interpreter.invoke()
    output = interpreter.get_tensor(output_detail["index"])
    return fake_probability(output)


def verify_parity(onnx_path: Path, tflite_path: Path, paths: list[Path]) -> float:
    errors = []
    for path in paths[:20]:
        errors.append(abs(run_onnx(onnx_path, preprocess_nchw(path)) - run_tflite(tflite_path, preprocess_nhwc(path))))
    mae = float(np.mean(errors))
    if mae > MAX_PARITY_MAE:
        raise SystemExit(f"Parity MAE {mae:.4f} exceeds limit {MAX_PARITY_MAE:.4f}")
    return mae


def sign_assets(assets: list[ConvertedAsset], private_key_path: Path, public_key_out: Path) -> str:
    from cryptography.hazmat.primitives import serialization

    private_key = serialization.load_pem_private_key(private_key_path.read_bytes(), password=None)
    public_key = private_key.public_key()
    public_key_der = public_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    public_key_out.write_bytes(public_key_der)
    for asset in assets:
        asset.path.with_suffix(asset.path.suffix + ".sig").write_bytes(private_key.sign(asset.path.read_bytes()))
    return base64.b64encode(public_key_der).decode("ascii")


def write_manifest(
    manifest_path: Path,
    source_onnx: Path,
    assets: list[ConvertedAsset],
    parity_mae: float,
    public_key_base64: str,
) -> None:
    lines = [
        "# Phase 7 Image Model Manifest",
        "",
        "- Source model: `prithivMLmods/Deep-Fake-Detector-v2-Model`",
        f"- ONNX conversion source: `{REPO_ID}` / `{source_onnx.name}`",
        "- License: Apache 2.0",
        "- Runtime asset: TensorFlow Lite dynamic-range quantized model with pseudo-lowered `Erf`/`GeLU` operators",
        "- FP16 fallback: omitted because generated artifact exceeds the 100 MB Phase 7 hard cap",
        f"- Source ONNX SHA-256: `{sha256(source_onnx)}`",
        f"- TFLite parity MAE: `{parity_mae:.6f}`",
        f"- Ed25519 public key (base64 DER): `{public_key_base64}`",
        "",
        "| Asset | SHA-256 | Size bytes |",
        "| --- | --- | ---: |",
    ]
    for asset in assets:
        lines.append(f"| `{asset.name}` | `{asset.sha256}` | {asset.size_bytes} |")
    manifest_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    args = parse_args()
    ensure_dependencies()
    work_dir = Path(args.work_dir)
    output_dir = Path(args.output_dir)
    parity_paths = image_paths(Path(args.parity_images), minimum=20)
    representative_paths = image_paths(Path(args.representative_images), minimum=20)
    private_key_path = Path(args.private_key)
    if not private_key_path.exists():
        raise SystemExit(f"Private key not found: {private_key_path}. Keep it untracked.")
    if output_dir.exists() and any(output_dir.iterdir()) and not args.overwrite:
        raise SystemExit(f"{output_dir} is not empty; pass --overwrite to replace generated assets")
    output_dir.mkdir(parents=True, exist_ok=True)
    work_dir.mkdir(parents=True, exist_ok=True)

    onnx_path = download_onnx(work_dir, args.onnx_file)
    calibration_path = work_dir / "pixel_values_calibration.npy"
    write_calibration_tensor(representative_paths[:200], calibration_path)
    converted_fp16_path, converted_int8_path = convert_onnx_to_tflite(
        onnx_path = onnx_path,
        conversion_dir = work_dir / "tflite_conversion",
        calibration_path = calibration_path,
    )

    int8_path = output_dir / "deepfake-detector-v2-int8.tflite"
    shutil.copy2(converted_int8_path, int8_path)
    if converted_fp16_path.stat().st_size <= HARD_CAP_BYTES:
        shutil.copy2(converted_fp16_path, output_dir / "deepfake-detector-v2-fp16.tflite")
    else:
        print(f"WARNING: FP16 model omitted; exceeds hard cap: {converted_fp16_path.stat().st_size} bytes", file=sys.stderr)

    if int8_path.stat().st_size > HARD_CAP_BYTES:
        raise SystemExit(f"Dynamic-range model exceeds hard cap: {int8_path.stat().st_size} bytes")
    if int8_path.stat().st_size > TARGET_MODEL_BYTES:
        print(f"WARNING: dynamic-range model exceeds target size: {int8_path.stat().st_size} bytes", file=sys.stderr)

    parity_mae = verify_parity(onnx_path, int8_path, parity_paths)
    assets = [
        ConvertedAsset(int8_path.name, int8_path, sha256(int8_path), int8_path.stat().st_size),
    ]
    public_key_base64 = sign_assets(assets, private_key_path, Path(args.public_key_out))
    write_manifest(Path(args.manifest_out), onnx_path, assets, parity_mae, public_key_base64)
    print(json.dumps({"parity_mae": parity_mae, "assets": [asset.__dict__ for asset in assets]}, default=str, indent=2))


if __name__ == "__main__":
    main()
