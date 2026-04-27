#!/usr/bin/env python3
"""Convert the Phase 8 audio detector to a signed LiteRT asset.

The shipped Phase 8 model is Hemgg/Deepfake-audio-detection, a wav2vec2-base
binary classifier. The conversion path intentionally uses weight-only INT8:
full-integer conversion either produced unsupported ops or unacceptable parity
for this graph in the Windows conversion environment.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path


REPO_ID = "Hemgg/Deepfake-audio-detection"
MODEL_REVISION = "0d75271368ef2c7efd14831dc503c431f6aab0eb"
MODEL_ASSET_NAME = "deepfake-audio-detector-hemgg-wi8.tflite"
PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEATNyvAq6FDkWUF9zUaVObExc/7QBE7PLa6QNt/AsP/10="
HARD_CAP_BYTES = 120 * 1024 * 1024


@dataclass(frozen=True)
class ConvertedAsset:
    name: str
    path: str
    sha256: str
    size_bytes: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-dir", default="tools/model-conversion/work/audio-base-hemgg-script")
    parser.add_argument("--output-dir", default="data-detection-ml/src/main/assets/models/audio")
    parser.add_argument("--private-key", default="tools/model-conversion/private/ed25519_phase7_private.pem")
    parser.add_argument("--onnx-path", default=None, help="Use an existing Hemgg ONNX export instead of exporting from Hugging Face")
    parser.add_argument("--skip-audit", action="store_true")
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def ensure_dependencies() -> None:
    missing = []
    for module in ("ai_edge_quantizer", "ai_edge_litert", "cryptography", "huggingface_hub"):
        try:
            __import__(module)
        except ImportError:
            missing.append(module)
    if missing:
        raise SystemExit(
            "Missing Python packages: "
            + ", ".join(missing)
            + "\nInstall the Phase 8 conversion environment before running this script."
        )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def export_or_use_onnx(work_dir: Path, explicit_onnx: str | None) -> Path:
    if explicit_onnx:
        onnx_path = Path(explicit_onnx)
        if not onnx_path.exists():
            raise SystemExit(f"ONNX path does not exist: {onnx_path}")
        return onnx_path

    from huggingface_hub import model_info

    info = model_info(REPO_ID, revision=MODEL_REVISION)
    license_name = info.card_data.get("license") if info.card_data else None
    if license_name != "apache-2.0":
        raise SystemExit(f"Unexpected model license: {license_name}")

    onnx_dir = work_dir / "onnx"
    onnx_dir.mkdir(parents=True, exist_ok=True)
    onnx_path = onnx_dir / "model.onnx"
    if onnx_path.exists():
        return onnx_path

    subprocess.run(
        [
            sys.executable,
            "-m",
            "transformers.onnx",
            "--model",
            f"{REPO_ID}@{MODEL_REVISION}",
            "--feature",
            "audio-classification",
            str(onnx_dir),
        ],
        check=True,
    )
    if not onnx_path.exists():
        candidates = sorted(onnx_dir.glob("*.onnx"))
        if not candidates:
            raise SystemExit("transformers.onnx produced no ONNX file")
        return candidates[0]
    return onnx_path


def convert_onnx_to_float_tflite(onnx_path: Path, tflite_dir: Path) -> Path:
    if tflite_dir.exists():
        shutil.rmtree(tflite_dir)
    subprocess.run(
        [
            sys.executable,
            "-m",
            "onnx2tf",
            "-i",
            str(onnx_path),
            "-o",
            str(tflite_dir),
            "-b",
            "1",
            "-ois",
            "input_values:1,80000",
            "-rtpo",
            "erf",
        ],
        check=True,
    )
    float_model = tflite_dir / "model_float32.tflite"
    if not float_model.exists():
        raise SystemExit(f"onnx2tf did not produce {float_model}")
    return float_model


def write_weight_only_recipe(recipe_path: Path) -> None:
    recipe_path.write_text(
        json.dumps(
            [
                {
                    "regex": ".*",
                    "operation": "*",
                    "algorithm_key": "min_max_uniform_quantize",
                    "op_config": {
                        "weight_tensor_config": {
                            "num_bits": 8,
                            "symmetric": True,
                            "granularity": "CHANNELWISE",
                            "dtype": "INT",
                        },
                        "compute_precision": "FLOAT",
                        "explicit_dequantize": True,
                        "skip_checks": False,
                        "min_weight_elements": 0,
                    },
                },
            ],
            indent=2,
        ),
        encoding="utf-8",
    )


def quantize_weight_only_internal(float_model: Path, recipe_path: Path, output_path: Path) -> None:
    from ai_edge_litert.tools import mmap_utils
    from ai_edge_quantizer import model_modifier, quantizer

    mmap_utils.get_file_contents = lambda path: Path(path).read_bytes()
    mmap_utils.set_file_contents = lambda path, data: Path(path).write_bytes(bytes(data))
    mmap_utils.get_mapped_buffer_or_none = lambda *args, **kwargs: None
    mmap_utils.advise_sequential = lambda *args, **kwargs: None
    mmap_utils.advise_dont_need = lambda *args, **kwargs: None

    original_init = model_modifier._PackedBufferData.__init__

    def no_external_init(self, model):
        original_init(self, model)
        self.packed_size = 0

    model_modifier._PackedBufferData.__init__ = no_external_init
    qt = quantizer.Quantizer(str(float_model))
    qt.load_quantization_recipe(str(recipe_path))
    result = qt.quantize()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(bytes(result.quantized_model))


def sign_asset(asset_path: Path, private_key_path: Path) -> str:
    from cryptography.hazmat.primitives import serialization

    private_key = serialization.load_pem_private_key(private_key_path.read_bytes(), password=None)
    public_key_der = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    public_key = base64.b64encode(public_key_der).decode("ascii")
    if public_key != PUBLIC_KEY_BASE64:
        raise SystemExit("Private key does not match the Phase 7/8 public key")
    asset_path.with_suffix(asset_path.suffix + ".sig").write_bytes(private_key.sign(asset_path.read_bytes()))
    return public_key


def run_audit(onnx_path: Path, tflite_path: Path, report_path: Path) -> None:
    subprocess.run(
        [
            sys.executable,
            "tools/model-conversion/audit_audio_tflite.py",
            "--onnx",
            str(onnx_path),
            "--tflite",
            str(tflite_path),
            "--samples",
            "20",
            "--report",
            str(report_path),
        ],
        check=True,
    )


def main() -> None:
    args = parse_args()
    ensure_dependencies()
    work_dir = Path(args.work_dir)
    output_dir = Path(args.output_dir)
    private_key = Path(args.private_key)
    if not private_key.exists():
        raise SystemExit(f"Private key not found: {private_key}")
    output_dir.mkdir(parents=True, exist_ok=True)
    if (output_dir / MODEL_ASSET_NAME).exists() and not args.overwrite:
        raise SystemExit(f"{output_dir / MODEL_ASSET_NAME} exists; pass --overwrite to replace it")

    onnx_path = export_or_use_onnx(work_dir, args.onnx_path)
    float_model = convert_onnx_to_float_tflite(onnx_path, work_dir / "tflite_float32_rtpo_erf")
    recipe_path = work_dir / "ai_edge_weight_only_wi8_afp32_recipe.json"
    write_weight_only_recipe(recipe_path)
    quantized = work_dir / MODEL_ASSET_NAME
    quantize_weight_only_internal(float_model, recipe_path, quantized)
    if quantized.stat().st_size > HARD_CAP_BYTES:
        raise SystemExit(f"Converted model exceeds 120 MB hard cap: {quantized.stat().st_size}")
    if not args.skip_audit:
        run_audit(onnx_path, quantized, work_dir / "audio_tflite_audit.json")

    destination = output_dir / MODEL_ASSET_NAME
    shutil.copy2(quantized, destination)
    public_key = sign_asset(destination, private_key)
    asset = ConvertedAsset(destination.name, str(destination), sha256(destination), destination.stat().st_size)
    print(
        json.dumps(
            {
                "repo_id": REPO_ID,
                "revision": MODEL_REVISION,
                "source_onnx_sha256": sha256(onnx_path),
                "asset": asdict(asset),
                "public_key_base64": public_key,
            },
            indent=2,
        ),
    )


if __name__ == "__main__":
    main()
