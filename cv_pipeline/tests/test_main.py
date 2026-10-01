from __future__ import annotations

import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from main import VideoInspectionError, format_report, inspect_video, main


class VideoInspectionTest(unittest.TestCase):
    FPS = 12.0
    WIDTH = 160
    HEIGHT = 120
    FRAME_COUNT = 9

    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.video_path = Path(self.temporary_directory.name) / "synthetic.avi"
        writer = cv2.VideoWriter(
            str(self.video_path),
            cv2.VideoWriter_fourcc(*"MJPG"),
            self.FPS,
            (self.WIDTH, self.HEIGHT),
        )
        if not writer.isOpened():
            self.skipTest("OpenCV build does not provide the MJPG test codec")
        for index in range(self.FRAME_COUNT):
            frame = np.full((self.HEIGHT, self.WIDTH, 3), index * 10, dtype=np.uint8)
            writer.write(frame)
        writer.release()

    def test_reads_video_metadata(self):
        metadata = inspect_video(self.video_path)
        self.assertAlmostEqual(self.FPS, metadata.fps, places=2)
        self.assertEqual(self.WIDTH, metadata.width)
        self.assertEqual(self.HEIGHT, metadata.height)
        self.assertEqual(self.FRAME_COUNT, metadata.reported_frame_count)
        self.assertIsNone(metadata.decoded_frame_count)

    def test_verifies_decoded_frame_count(self):
        metadata = inspect_video(self.video_path, verify_frame_count=True)
        self.assertEqual(self.FRAME_COUNT, metadata.decoded_frame_count)
        self.assertAlmostEqual(self.FRAME_COUNT / self.FPS, metadata.duration_seconds, places=3)

    def test_report_contains_contract_metadata(self):
        report = format_report(inspect_video(self.video_path, verify_frame_count=True))
        self.assertIn("FPS: 12.000", report)
        self.assertIn("Resolution: 160x120", report)
        self.assertIn("Reported frames: 9", report)
        self.assertIn("Decoded frames: 9", report)

    def test_rejects_missing_video(self):
        with self.assertRaisesRegex(VideoInspectionError, "Video not found"):
            inspect_video(Path(self.temporary_directory.name) / "missing.mp4")

    def test_rejects_corrupt_video(self):
        corrupt = Path(self.temporary_directory.name) / "corrupt.mp4"
        corrupt.write_text("not a video", encoding="utf-8")
        with self.assertRaisesRegex(VideoInspectionError, "could not open"):
            inspect_video(corrupt)

    def test_cli_returns_actionable_error(self):
        stderr = io.StringIO()
        with mock.patch("sys.stderr", stderr):
            exit_code = main(["--video", str(Path(self.temporary_directory.name) / "missing.mp4")])
        self.assertEqual(2, exit_code)
        self.assertIn("ERROR: Video not found", stderr.getvalue())

    def test_cli_prints_metadata_on_success(self):
        stdout = io.StringIO()
        with mock.patch("sys.stdout", stdout):
            exit_code = main(["--video", str(self.video_path), "--verify-frame-count"])
        self.assertEqual(0, exit_code)
        self.assertIn("Decoded frames: 9", stdout.getvalue())


if __name__ == "__main__":
    unittest.main()
