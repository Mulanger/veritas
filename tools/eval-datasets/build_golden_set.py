#!/usr/bin/env python3
"""Build the local Phase 7 golden image set.

Images are deliberately ignored by git. The generated MANIFEST.csv is the
reproducibility artifact that may be committed.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import os
import random
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


KAGGLE_DATASET = "manjilkarki/deepfake-and-real-images"
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-dir", default="tools/eval-datasets/work")
    parser.add_argument("--output-dir", default="feature-detect-image/src/androidTest/assets/golden-image")
    parser.add_argument("--target-total", type=int, default=500)
    parser.add_argument("--seed", type=int, default=7)
    return parser.parse_args()


def require_kaggle_credentials() -> None:
    if os.environ.get("KAGGLE_USERNAME") and os.environ.get("KAGGLE_KEY"):
        return
    kaggle_json = Path.home() / ".kaggle" / "kaggle.json"
    if kaggle_json.exists():
        return
    raise SystemExit("Kaggle credentials missing. Set KAGGLE_USERNAME and KAGGLE_KEY or create ~/.kaggle/kaggle.json.")


def download_kaggle(work_dir: Path) -> Path:
    require_kaggle_credentials()
    zip_path = work_dir / "deepfake-and-real-images.zip"
    if not zip_path.exists():
        subprocess.run(
            kaggle_command()
            + [
                "datasets",
                "download",
                "-d",
                KAGGLE_DATASET,
                "-p",
                str(work_dir),
            ],
            check=True,
        )
    extract_dir = work_dir / "kaggle"
    if not extract_dir.exists():
        extract_dir.mkdir(parents=True)
        with zipfile.ZipFile(zip_path) as archive:
            archive.extractall(extract_dir)
    return extract_dir


def kaggle_command() -> list[str]:
    executable = shutil.which("kaggle")
    if executable:
        return [executable]
    scripts_dir = Path(sys.executable).parent / "Scripts"
    for candidate in (scripts_dir / "kaggle.exe", scripts_dir / "kaggle"):
        if candidate.exists():
            return [str(candidate)]
    raise SystemExit("Kaggle CLI is not installed. Install with: py -m pip install kaggle")


def classify(path: Path) -> tuple[str, str] | None:
    lowered = " ".join(part.lower() for part in path.parts)
    if "fake" in lowered or "synthetic" in lowered:
        return "synthetic", "gan_or_diffusion"
    if "real" in lowered:
        return "real", "camera_photo"
    return None


def collect_images(root: Path) -> list[tuple[Path, str, str, str]]:
    rows = []
    for path in root.rglob("*"):
        if path.suffix.lower() not in IMAGE_EXTENSIONS:
            continue
        classified = classify(path)
        if classified is None:
            continue
        label, generator = classified
        rows.append((path, label, "kaggle_deepfake_and_real_images", generator))
    return rows


def stable_name(source: str, generator: str, original: Path) -> str:
    digest = hashlib.sha256(str(original).encode("utf-8")).hexdigest()[:12]
    return f"{source}_{generator}_{digest}.jpg"


def copy_sampled(rows: list[tuple[Path, str, str, str]], output_dir: Path, target_total: int, seed: int) -> None:
    random.seed(seed)
    by_label = {"real": [], "synthetic": []}
    for row in rows:
        by_label[row[1]].append(row)
    per_label = target_total // 2
    manifest_rows = []
    for label, items in by_label.items():
        if len(items) < per_label:
            raise SystemExit(f"Only {len(items)} {label} images available; need {per_label}")
        random.shuffle(items)
        label_dir = output_dir / label
        label_dir.mkdir(parents=True, exist_ok=True)
        for original, _, source, generator in items[:per_label]:
            filename = stable_name(source, generator, original)
            destination = label_dir / filename
            shutil.copy2(original, destination)
            manifest_rows.append(
                {
                    "filename": f"{label}/{filename}",
                    "label": label,
                    "source_dataset": source,
                    "generator": generator,
                    "original_path": str(original),
                }
            )
    manifest_path = output_dir / "MANIFEST.csv"
    with manifest_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["filename", "label", "source_dataset", "generator", "original_path"])
        writer.writeheader()
        writer.writerows(sorted(manifest_rows, key=lambda row: row["filename"]))
    print_summary(manifest_rows)


def print_summary(rows: list[dict[str, str]]) -> None:
    print(f"Total: {len(rows)}")
    for label in ("real", "synthetic"):
        count = sum(1 for row in rows if row["label"] == label)
        print(f"{label}: {count}")
    generators = sorted({row["generator"] for row in rows})
    for generator in generators:
        count = sum(1 for row in rows if row["generator"] == generator)
        print(f"{generator}: {count}")


def main() -> None:
    args = parse_args()
    work_dir = Path(args.work_dir)
    output_dir = Path(args.output_dir)
    work_dir.mkdir(parents=True, exist_ok=True)
    dataset_dir = download_kaggle(work_dir)
    rows = collect_images(dataset_dir)
    copy_sampled(rows, output_dir, args.target_total, args.seed)


if __name__ == "__main__":
    main()
