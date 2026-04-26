#!/usr/bin/env python3
"""Convert Deepfake-audio-detection-V2 ONNX to signed LiteRT assets.

This script is intentionally strict: if conversion produces a model over the
Phase 8 120 MB hard cap, it exits non-zero instead of copying an unusable asset.
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


REPO_ID = "as1605/Deepfake-audio-detection-V2"
ONNX_FILENAME = "onnx/model.onnx"
MODEL_REVISION = "3aeb18add053e945dc69025147afab0d70fa0188"
SAMPLE_RATE = 16_000
SECONDS = 5
SAMPLE_COUNT = SAMPLE_RATE * SECONDS
TARGET_MODEL_BYTES = 100 * 1024 * 1024
HARD_CAP_BYTES = 120 * 1024 * 1024


@dataclass(frozen=True)
class ConvertedAsset:
    name: str
    path: Path
    sha256: str
    size_bytes: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-dir", default="tools/model-conversion/work/audio")
    parser.add_argument("--output-dir", default="data-detection-ml/src/main/assets/models/audio")
    parser.add_argument("--private-key", default="tools/model-conversion/private/ed25519_phase7_private.pem")
    parser.add_argument("--public-key-out", default="data-detection-ml/src/main/assets/models/audio/deepfake-audio-detector-v2.pub")
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def ensure_dependencies() -> None:
    missing = []
    for module in ("huggingface_hub", "onnxruntime", "onnx", "onnx2tf", "cryptography", "numpy"):
        try:
            __import__(module)
        except ImportError:
            missing.append(module)
    if missing:
        raise SystemExit(
            "Missing Python packages: "
            + ", ".join(missing)
            + "\nInstall with: py -m pip install huggingface_hub onnxruntime onnx onnx2tf cryptography numpy"
        )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_onnx(work_dir: Path) -> Path:
    from huggingface_hub import hf_hub_download, model_info

    info = model_info(REPO_ID, revision=MODEL_REVISION)
    license_name = info.card_data.get("license") if info.card_data else None
    if license_name != "apache-2.0":
        raise SystemExit(f"Unexpected model license: {license_name}")
    return Path(
        hf_hub_download(
            repo_id=REPO_ID,
            filename=ONNX_FILENAME,
            revision=MODEL_REVISION,
            local_dir=work_dir / "hf",
        ),
    )


def write_calibration_tensor(output_path: Path) -> None:
    rng = np.random.default_rng(8)
    samples = []
    for index in range(64):
        time = np.arange(SAMPLE_COUNT, dtype=np.float32) / SAMPLE_RATE
        frequency = 180 + index * 13
        wave = 0.25 * np.sin(2 * np.pi * frequency * time)
        wave += 0.08 * np.sin(2 * np.pi * (frequency * 2.3) * time)
        wave += rng.normal(0, 0.015, size=time.shape).astype(np.float32)
        wave = np.clip(wave, -1.0, 1.0)
        samples.append(((wave + 1.0) / 2.0).astype(np.float32))
    np.save(output_path, np.stack(samples))


def convert_with_onnx2tf(onnx_path: Path, conversion_dir: Path, calibration_path: Path) -> list[Path]:
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
            "-b",
            "1",
            "-ois",
            f"input_values:1,{SAMPLE_COUNT}",
            "-oiqt",
            "-cind",
            "input_values",
            str(calibration_path),
            "[0.5]",
            "[0.5]",
            "-iqd",
            "float32",
            "-oqd",
            "float32",
        ],
        check=True,
    )
    return sorted(conversion_dir.glob("*.tflite"))


def sign_assets(assets: list[ConvertedAsset], private_key_path: Path, public_key_out: Path) -> str:
    from cryptography.hazmat.primitives import serialization

    private_key = serialization.load_pem_private_key(private_key_path.read_bytes(), password=None)
    public_key = private_key.public_key()
    public_key_der = public_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    public_key_out.parent.mkdir(parents=True, exist_ok=True)
    public_key_out.write_bytes(public_key_der)
    for asset in assets:
        asset.path.with_suffix(asset.path.suffix + ".sig").write_bytes(private_key.sign(asset.path.read_bytes()))
    return base64.b64encode(public_key_der).decode("ascii")


def main() -> None:
    args = parse_args()
    ensure_dependencies()
    work_dir = Path(args.work_dir)
    output_dir = Path(args.output_dir)
    private_key_path = Path(args.private_key)
    if not private_key_path.exists():
        raise SystemExit(f"Private key not found: {private_key_path}")
    if output_dir.exists() and any(output_dir.iterdir()) and not args.overwrite:
        raise SystemExit(f"{output_dir} is not empty; pass --overwrite to replace generated assets")
    work_dir.mkdir(parents=True, exist_ok=True)
    output_dir.mkdir(parents=True, exist_ok=True)

    onnx_path = download_onnx(work_dir)
    calibration_path = work_dir / "audio_calibration_0_1.npy"
    write_calibration_tensor(calibration_path)
    converted = convert_with_onnx2tf(onnx_path, work_dir / "tflite_onnx2tf_int8", calibration_path)
    candidates = sorted(converted, key=lambda path: path.stat().st_size)
    if not candidates:
        raise SystemExit("onnx2tf produced no .tflite files")
    best = candidates[0]
    if best.stat().st_size > HARD_CAP_BYTES:
        size_report = {path.name: path.stat().st_size for path in candidates}
        raise SystemExit(f"Smallest converted TFLite exceeds 120 MB hard cap: {json.dumps(size_report, indent=2)}")
    if best.stat().st_size > TARGET_MODEL_BYTES:
        print(f"WARNING: model exceeds 100 MB target: {best.stat().st_size}", file=sys.stderr)

    destination = output_dir / "deepfake-audio-detector-v2-int8.tflite"
    shutil.copy2(best, destination)
    asset = ConvertedAsset(destination.name, destination, sha256(destination), destination.stat().st_size)
    public_key_base64 = sign_assets([asset], private_key_path, Path(args.public_key_out))
    print(
        json.dumps(
            {
                "source_onnx_sha256": sha256(onnx_path),
                "source_revision": MODEL_REVISION,
                "asset": asset.__dict__,
                "public_key_base64": public_key_base64,
            },
            default=str,
            indent=2,
        ),
    )


if __name__ == "__main__":
    main()
