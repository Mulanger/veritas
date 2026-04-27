#!/usr/bin/env python3
"""Build a representative wav2vec2 calibration tensor for Phase 8.

The output is pre-normalized to [0, 1] because onnx2tf expects calibration
arrays before applying the `-cind` mean/std normalization.
"""

from __future__ import annotations

import argparse
import csv
import io
import math
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np


SAMPLE_RATE = 16_000
SECONDS = 5
SAMPLE_COUNT = SAMPLE_RATE * SECONDS
FFMPEG = Path(r"C:\ffmpeg\ffmpeg-2022-12-29-git-d39b34123d-full_build\bin\ffmpeg.exe")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="tools/model-conversion/work/audio_real_calibration_0_1.npy")
    parser.add_argument("--manifest", default="tools/model-conversion/work/audio_real_calibration_manifest.csv")
    parser.add_argument("--work-dir", default="tools/model-conversion/work/audio-calibration-sources")
    parser.add_argument("--target-count", type=int, default=220)
    parser.add_argument("--common-voice-count", type=int, default=30)
    parser.add_argument("--real-count", type=int, default=110)
    parser.add_argument("--synthetic-count", type=int, default=80)
    return parser.parse_args()


def ffmpeg_path() -> str:
    if FFMPEG.exists():
        return str(FFMPEG)
    found = shutil.which("ffmpeg")
    if found:
        return found
    raise SystemExit("ffmpeg not found")


def decode_to_waveform(path: Path) -> np.ndarray:
    command = [
        ffmpeg_path(),
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        str(path),
        "-ac",
        "1",
        "-ar",
        str(SAMPLE_RATE),
        "-f",
        "f32le",
        "pipe:1",
    ]
    completed = subprocess.run(command, check=True, stdout=subprocess.PIPE)
    waveform = np.frombuffer(completed.stdout, dtype="<f4").astype(np.float32)
    if waveform.size == 0:
        raise ValueError(f"decoded empty waveform: {path}")
    waveform = np.nan_to_num(waveform, nan=0.0, posinf=1.0, neginf=-1.0)
    waveform = np.clip(waveform, -1.0, 1.0)
    if waveform.size < SAMPLE_COUNT:
        waveform = np.pad(waveform, (0, SAMPLE_COUNT - waveform.size))
    return waveform[:SAMPLE_COUNT]


def normalized_row(waveform: np.ndarray) -> np.ndarray:
    return ((waveform + 1.0) / 2.0).astype(np.float32)


def write_audio_bytes(audio: dict, output_dir: Path, prefix: str, index: int) -> Path:
    suffix = Path(audio.get("path") or "").suffix or ".wav"
    path = output_dir / f"{prefix}_{index:04d}{suffix}"
    raw = audio.get("bytes")
    if raw:
        path.write_bytes(raw)
    elif audio.get("path"):
        source = Path(audio["path"])
        if source.exists():
            shutil.copy2(source, path)
        else:
            raise FileNotFoundError(audio["path"])
    else:
        raise ValueError("audio row has neither bytes nor path")
    return path


def iter_hf_audio(dataset_name: str, config: str | None, split: str, limit: int):
    from datasets import Audio, load_dataset

    dataset = (
        load_dataset(dataset_name, config, split=split, streaming=True)
        if config
        else load_dataset(dataset_name, split=split, streaming=True)
    )
    dataset = dataset.cast_column("audio", Audio(decode=False))
    yielded = 0
    for row in dataset:
        audio = row.get("audio")
        if not isinstance(audio, dict):
            continue
        yield audio, row
        yielded += 1
        if yielded >= limit:
            break


def generate_sapi_wav(path: Path, text: str, rate: int, volume: int, voice_index: int) -> str:
    import win32com.client

    voice = win32com.client.Dispatch("SAPI.SpVoice")
    voices = voice.GetVoices()
    selected_voice = None
    if voices.Count:
        selected_voice = voices.Item(voice_index % voices.Count)
        voice.Voice = selected_voice
    stream = win32com.client.Dispatch("SAPI.SpFileStream")
    stream.Open(str(path), 3, False)  # SSFMCreateForWrite
    voice.AudioOutputStream = stream
    voice.Rate = rate
    voice.Volume = volume
    voice.Speak(text)
    stream.Close()
    return selected_voice.GetDescription() if selected_voice is not None else "SAPI default"


def synthetic_texts() -> list[str]:
    return [
        "The quick brown fox jumped over the lazy dog near the railway station.",
        "Please confirm the appointment for Tuesday afternoon at four thirty.",
        "A clean calibration set should include short pauses and varied speech rhythm.",
        "Weather reports mentioned heavy rain, low clouds, and delayed flights.",
        "She read the paragraph twice, first slowly, then with a faster cadence.",
        "Synthetic speech can sound polished, clipped, breathy, or unusually steady.",
        "The verification app analyzes audio without uploading private recordings.",
        "Numbers like fifteen, eighty two, and four thousand seven can stress phonemes.",
        "Low energy vowels and sharp consonants help cover the detector input range.",
        "This sample intentionally contains ordinary conversational wording.",
    ]


def add_decoded_sample(rows: list[np.ndarray], manifest: list[dict[str, str]], path: Path, source: str, label: str, note: str) -> None:
    waveform = decode_to_waveform(path)
    rows.append(normalized_row(waveform))
    manifest.append(
        {
            "index": str(len(rows) - 1),
            "label": label,
            "source": source,
            "path": str(path),
            "duration_seconds_after_normalization": f"{min(waveform.size, SAMPLE_COUNT) / SAMPLE_RATE:.3f}",
            "note": note,
        },
    )


def main() -> None:
    args = parse_args()
    output = Path(args.output)
    manifest_path = Path(args.manifest)
    work_dir = Path(args.work_dir)
    source_dir = work_dir / "raw"
    source_dir.mkdir(parents=True, exist_ok=True)

    rows: list[np.ndarray] = []
    manifest: list[dict[str, str]] = []

    fixture_dir = Path("feature-detect-audio/src/androidTest/assets/audio-samples")
    for path in sorted(fixture_dir.glob("*")):
        if path.is_file():
            add_decoded_sample(rows, manifest, path, "phase8-test-fixture", "fixture", "committed decode fixture")

    common_voice_source = "OpenSpeechHub/common-voice-asr-clean"
    for idx, (audio, _row) in enumerate(iter_hf_audio(common_voice_source, None, "train", args.common_voice_count)):
        path = write_audio_bytes(audio, source_dir, "common_voice", idx)
        add_decoded_sample(rows, manifest, path, common_voice_source, "real", "Common Voice-derived parquet mirror")

    real_source = "PolyAI/minds14"
    remaining_real = max(args.real_count - args.common_voice_count, 0)
    for idx, (audio, row) in enumerate(iter_hf_audio(real_source, "en-US", "train", remaining_real)):
        path = write_audio_bytes(audio, source_dir, "minds14", idx)
        intent = str(row.get("intent_class", ""))
        add_decoded_sample(rows, manifest, path, real_source, "real", f"spoken command intent={intent}")

    for idx in range(args.synthetic_count):
        path = source_dir / f"sapi_tts_{idx:04d}.wav"
        text = synthetic_texts()[idx % len(synthetic_texts())]
        rate = [-3, -2, -1, 0, 1, 2, 3][idx % 7]
        volume = [70, 82, 94][idx % 3]
        voice_name = generate_sapi_wav(path, text, rate, volume, idx)
        add_decoded_sample(rows, manifest, path, "Windows SAPI TTS", "synthetic", f"{voice_name}; rate={rate}; volume={volume}")

    if len(rows) < args.target_count:
        raise SystemExit(f"Only built {len(rows)} calibration rows; target is {args.target_count}")

    stacked = np.stack(rows[: args.target_count]).astype(np.float32)
    output.parent.mkdir(parents=True, exist_ok=True)
    np.save(output, stacked)

    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    with manifest_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(manifest[0].keys()))
        writer.writeheader()
        writer.writerows(manifest[: args.target_count])

    labels = {}
    sources = {}
    for item in manifest[: args.target_count]:
        labels[item["label"]] = labels.get(item["label"], 0) + 1
        sources[item["source"]] = sources.get(item["source"], 0) + 1

    print(
        {
            "output": str(output),
            "manifest": str(manifest_path),
            "shape": stacked.shape,
            "dtype": str(stacked.dtype),
            "min": float(stacked.min()),
            "max": float(stacked.max()),
            "labels": labels,
            "sources": sources,
        },
    )


if __name__ == "__main__":
    main()
