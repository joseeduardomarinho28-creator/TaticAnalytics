"""Inspect a video before running detection or tracking.

This is intentionally the first pipeline command: it validates the input and
reports stable metadata before expensive models are loaded.
"""

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path

import cv2


class VideoInspectionError(RuntimeError):
    """Raised when a video cannot be opened or has invalid metadata."""


@dataclass(frozen=True)
class VideoMetadata:
    path: Path
    fps: float
    width: int
    height: int
    reported_frame_count: int
    decoded_frame_count: int | None = None

    @property
    def duration_seconds(self) -> float:
        frame_count = self.decoded_frame_count or self.reported_frame_count
        return frame_count / self.fps


def inspect_video(video_path: str | Path, verify_frame_count: bool = False) -> VideoMetadata:
    path = Path(video_path).expanduser().resolve()
    if not path.exists():
        raise VideoInspectionError(f"Video not found: {path}")
    if not path.is_file():
        raise VideoInspectionError(f"Video path is not a file: {path}")

    capture = cv2.VideoCapture(str(path))
    try:
        if not capture.isOpened():
            raise VideoInspectionError(
                f"OpenCV could not open the video: {path}. "
                "Check the file format, codec, and read permissions."
            )

        fps = float(capture.get(cv2.CAP_PROP_FPS))
        width = round(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = round(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
        reported_frames = round(capture.get(cv2.CAP_PROP_FRAME_COUNT))

        if not math.isfinite(fps) or fps <= 0:
            raise VideoInspectionError(f"Video reports an invalid frame rate: {fps}")
        if width <= 0 or height <= 0:
            raise VideoInspectionError(f"Video reports an invalid resolution: {width}x{height}")
        if reported_frames <= 0:
            raise VideoInspectionError(f"Video reports an invalid frame count: {reported_frames}")

        decoded_frames = _count_decodable_frames(capture) if verify_frame_count else None
        if decoded_frames is not None and decoded_frames <= 0:
            raise VideoInspectionError("The video opened but no frame could be decoded")

        return VideoMetadata(
            path=path,
            fps=fps,
            width=width,
            height=height,
            reported_frame_count=reported_frames,
            decoded_frame_count=decoded_frames,
        )
    finally:
        capture.release()


def _count_decodable_frames(capture: cv2.VideoCapture) -> int:
    capture.set(cv2.CAP_PROP_POS_FRAMES, 0)
    count = 0
    while True:
        success, _frame = capture.read()
        if not success:
            return count
        count += 1


def format_report(metadata: VideoMetadata) -> str:
    lines = [
        "TaticAnalytics — video inspection",
        f"Video: {metadata.path}",
        f"FPS: {metadata.fps:.3f}",
        f"Resolution: {metadata.width}x{metadata.height}",
        f"Reported frames: {metadata.reported_frame_count}",
        f"Duration: {metadata.duration_seconds:.3f} s",
    ]
    if metadata.decoded_frame_count is not None:
        lines.append(f"Decoded frames: {metadata.decoded_frame_count}")
        if metadata.decoded_frame_count != metadata.reported_frame_count:
            lines.append(
                "WARNING: container metadata and decoded frame count differ; "
                "use the decoded count when processing this video."
            )
    return "\n".join(lines)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate a video and print the metadata required by the CV pipeline."
    )
    parser.add_argument("--video", required=True, help="Path to the input video")
    parser.add_argument(
        "--verify-frame-count",
        action="store_true",
        help="Decode the entire video to verify its real frame count (slower)",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        metadata = inspect_video(args.video, verify_frame_count=args.verify_frame_count)
    except VideoInspectionError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    print(format_report(metadata))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
