#!/usr/bin/env python3
"""Build the local Phase 8 golden audio set.

Audio files are deliberately ignored by git. The generated MANIFEST.csv is the
reproducibility artifact that may be committed.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import random
import shutil
import subprocess
from pathlib import Path


AUDIO_EXTENSIONS = {".wav", ".mp3", ".aac", ".m4a", ".ogg", ".opus", ".flac"}
DEFAULT_SOURCE_DIR = "tools/model-conversion/work/audio-calibration-sources/raw"
DEFAULT_OUTPUT_DIR = "feature-detect-audio/src/androidTest/assets/golden-audio"
CODEC_VARIANTS = (
    ("wav", ["-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le"], "16000"),
    ("mp3", ["-ac", "1", "-ar", "16000", "-c:a", "libmp3lame", "-b:a", "96k"], "16000"),
    ("m4a", ["-ac", "1", "-ar", "22050", "-c:a", "aac", "-b:a", "96k"], "22050"),
    ("opus", ["-ac", "1", "-ar", "48000", "-c:a", "libopus", "-b:a", "32k"], "48000"),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--target-total", type=int, default=500)
    parser.add_argument("--seed", type=int, default=8)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def require_ffmpeg() -> None:
    if not shutil.which("ffmpeg"):
        raise SystemExit("ffmpeg is required to build the audio golden set")
    if not shutil.which("ffprobe"):
        raise SystemExit("ffprobe is required to inspect generated audio")


def classify(path: Path) -> tuple[str, str, str] | None:
    name = path.name.lower()
    if name.startswith("sapi_tts_"):
        return "synthetic", "local_sapi_tts", "sapi"
    if name.startswith("common_voice_"):
        return "real", "common_voice_en", "human_speaker"
    if name.startswith("minds14_"):
        return "real", "minds14_en_us", "human_speaker"
    return None


def collect_sources(source_dir: Path) -> dict[str, list[tuple[Path, str, str]]]:
    by_label: dict[str, list[tuple[Path, str, str]]] = {"real": [], "synthetic": []}
    for path in sorted(source_dir.glob("*")):
        if path.suffix.lower() not in AUDIO_EXTENSIONS:
            continue
        classified = classify(path)
        if classified is None:
            continue
        label, source_dataset, speaker = classified
        by_label[label].append((path, source_dataset, speaker))
    return by_label


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def probe_duration_ms(path: Path) -> int:
    result = subprocess.run(
        [
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
            str(path),
        ],
        check=True,
        text=True,
        capture_output=True,
    )
    return int(float(result.stdout.strip()) * 1000)


def render_variant(source: Path, destination: Path, ffmpeg_args: list[str]) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and destination.stat().st_size > 0:
        return
    subprocess.run(
        [
            "ffmpeg",
            "-v",
            "error",
            "-y",
            "-i",
            str(source),
            *ffmpeg_args,
            str(destination),
        ],
        check=True,
    )


def build_rows(
    sources: dict[str, list[tuple[Path, str, str]]],
    output_dir: Path,
    target_total: int,
    seed: int,
) -> list[dict[str, str]]:
    random.seed(seed)
    per_label = target_total // 2
    rows: list[dict[str, str]] = []
    for label in ("real", "synthetic"):
        label_sources = sources[label][:]
        if not label_sources:
            raise SystemExit(f"No {label} audio sources found")
        random.shuffle(label_sources)
        for index in range(per_label):
            source, source_dataset, speaker = label_sources[index % len(label_sources)]
            codec, ffmpeg_args, sample_rate = CODEC_VARIANTS[index % len(CODEC_VARIANTS)]
            source_hash = sha256(source)[:12]
            filename = f"{label}_{index:04d}_{source.stem}_{codec}_{source_hash}.{codec}"
            relative = f"{label}/{filename}"
            destination = output_dir / relative
            render_variant(source, destination, ffmpeg_args)
            rows.append(
                {
                    "filename": relative,
                    "label": label,
                    "source_dataset": source_dataset,
                    "tts_system_or_speaker": speaker,
                    "codec": codec,
                    "sample_rate": sample_rate,
                    "duration_ms": str(probe_duration_ms(destination)),
                    "source_sha256": sha256(source),
                }
            )
    return sorted(rows, key=lambda row: row["filename"])


def write_manifest(rows: list[dict[str, str]], output_dir: Path) -> None:
    manifest_path = output_dir / "MANIFEST.csv"
    fieldnames = [
        "filename",
        "label",
        "source_dataset",
        "tts_system_or_speaker",
        "codec",
        "sample_rate",
        "duration_ms",
        "source_sha256",
    ]
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    with manifest_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def print_summary(rows: list[dict[str, str]]) -> None:
    print(f"Total: {len(rows)}")
    for label in ("real", "synthetic"):
        print(f"{label}: {sum(1 for row in rows if row['label'] == label)}")
    for codec in sorted({row["codec"] for row in rows}):
        print(f"{codec}: {sum(1 for row in rows if row['codec'] == codec)}")
    for source in sorted({row["source_dataset"] for row in rows}):
        print(f"{source}: {sum(1 for row in rows if row['source_dataset'] == source)}")


def main() -> None:
    args = parse_args()
    require_ffmpeg()
    source_dir = Path(args.source_dir)
    output_dir = Path(args.output_dir)
    if not source_dir.exists():
        raise SystemExit(f"Audio source directory not found: {source_dir}")
    if output_dir.exists() and args.overwrite:
        for child in (output_dir / "real", output_dir / "synthetic"):
            if child.exists():
                shutil.rmtree(child)
    sources = collect_sources(source_dir)
    rows = build_rows(sources, output_dir, args.target_total, args.seed)
    write_manifest(rows, output_dir)
    print_summary(rows)


if __name__ == "__main__":
    main()
