#!/usr/bin/env python3
"""Build the local Phase 9 golden video set from the Phase 7 image set.

Video files are deliberately ignored by git. The generated MANIFEST.csv is the
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


DEFAULT_SOURCE_DIR = "feature-detect-image/src/androidTest/assets/golden-image"
DEFAULT_OUTPUT_DIR = "feature-detect-video/src/androidTest/assets/golden-video"
VIDEO_VARIANTS = (
    ("mp4", ["-c:v", "libx264", "-pix_fmt", "yuv420p", "-movflags", "+faststart"], "h264_mp4"),
    ("webm", ["-c:v", "libvpx-vp9", "-b:v", "600k"], "vp9_webm"),
    ("mov", ["-c:v", "libx264", "-pix_fmt", "yuv420p"], "h264_mov"),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--target-total", type=int, default=200)
    parser.add_argument("--duration-seconds", type=int, default=3)
    parser.add_argument("--seed", type=int, default=9)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def require_ffmpeg() -> None:
    if not shutil.which("ffmpeg"):
        raise SystemExit("ffmpeg is required to build the video golden set")
    if not shutil.which("ffprobe"):
        raise SystemExit("ffprobe is required to inspect generated video")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_image_manifest(source_dir: Path) -> list[dict[str, str]]:
    manifest = source_dir / "MANIFEST.csv"
    if not manifest.exists():
        raise SystemExit(f"Phase 7 image manifest not found: {manifest}")
    with manifest.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def render_variant(source: Path, destination: Path, duration_seconds: int, ffmpeg_args: list[str]) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and destination.stat().st_size > 0:
        return
    subprocess.run(
        [
            "ffmpeg",
            "-v",
            "error",
            "-y",
            "-loop",
            "1",
            "-t",
            str(duration_seconds),
            "-i",
            str(source),
            "-vf",
            "scale=640:360:force_original_aspect_ratio=increase,crop=640:360,fps=24",
            "-an",
            *ffmpeg_args,
            str(destination),
        ],
        check=True,
    )


def probe(path: Path) -> tuple[int, int, int, int]:
    result = subprocess.run(
        [
            "ffprobe",
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=width,height,nb_frames:format=duration",
            "-of",
            "csv=p=0",
            str(path),
        ],
        check=True,
        text=True,
        capture_output=True,
    )
    parts = [part.strip() for part in result.stdout.replace("\n", ",").split(",") if part.strip()]
    width = int(parts[0])
    height = int(parts[1])
    frame_count = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 0
    duration_ms = int(float(parts[-1]) * 1000)
    return width, height, frame_count, duration_ms


def build_rows(
    source_dir: Path,
    output_dir: Path,
    target_total: int,
    duration_seconds: int,
    seed: int,
) -> list[dict[str, str]]:
    random.seed(seed)
    manifest_rows = read_image_manifest(source_dir)
    by_label = {"real": [], "synthetic": []}
    for row in manifest_rows:
        by_label[row["label"]].append(row)

    rows: list[dict[str, str]] = []
    per_label = target_total // 2
    for label in ("real", "synthetic"):
        candidates = by_label[label][:]
        if not candidates:
            raise SystemExit(f"No {label} Phase 7 images available")
        random.shuffle(candidates)
        for index in range(per_label):
            row = candidates[index % len(candidates)]
            source = source_dir / row["filename"]
            if not source.exists():
                raise SystemExit(f"Source image missing: {source}")
            extension, ffmpeg_args, codec = VIDEO_VARIANTS[index % len(VIDEO_VARIANTS)]
            source_hash = sha256(source)[:12]
            filename = f"{label}_{index:04d}_{codec}_{source_hash}.{extension}"
            relative = f"{label}/{filename}"
            destination = output_dir / relative
            render_variant(source, destination, duration_seconds, ffmpeg_args)
            width, height, frame_count, duration_ms = probe(destination)
            rows.append(
                {
                    "filename": relative,
                    "label": label,
                    "source_dataset": row["source_dataset"],
                    "generator_family": f"image_to_video_static_{row['generator']}",
                    "codec": codec,
                    "duration_ms": str(duration_ms),
                    "width": str(width),
                    "height": str(height),
                    "frame_count": str(frame_count),
                    "source_image_sha256": sha256(source),
                }
            )
    return sorted(rows, key=lambda item: item["filename"])


def write_manifest(rows: list[dict[str, str]], output_dir: Path) -> None:
    manifest_path = output_dir / "MANIFEST.csv"
    fieldnames = [
        "filename",
        "label",
        "source_dataset",
        "generator_family",
        "codec",
        "duration_ms",
        "width",
        "height",
        "frame_count",
        "source_image_sha256",
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
    for family in sorted({row["generator_family"] for row in rows}):
        print(f"{family}: {sum(1 for row in rows if row['generator_family'] == family)}")


def main() -> None:
    args = parse_args()
    require_ffmpeg()
    source_dir = Path(args.source_dir)
    output_dir = Path(args.output_dir)
    if args.overwrite:
        for child in (output_dir / "real", output_dir / "synthetic"):
            if child.exists():
                shutil.rmtree(child)
    rows = build_rows(source_dir, output_dir, args.target_total, args.duration_seconds, args.seed)
    write_manifest(rows, output_dir)
    print_summary(rows)


if __name__ == "__main__":
    main()
