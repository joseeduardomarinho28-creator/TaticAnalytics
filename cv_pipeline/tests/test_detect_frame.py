from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from itertools import pairwise
from pathlib import Path
from types import SimpleNamespace

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from detect_frame import (
    ball_classes,
    filter_field,
    load_field_region,
    merge_overlapping,
    read_frame,
    serialize_boxes,
    tile_starts,
)


class DetectionTest(unittest.TestCase):
    def test_field_filter_uses_feet_and_does_not_cap_count(self):
        polygon = np.array([[0, 20], [100, 20], [100, 100], [0, 100]], dtype=np.float32)
        inside = {"class_name": "person", "xyxy_px": [10, 0, 20, 30], "confidence": 0.8}
        outside = {"class_name": "person", "xyxy_px": [10, 90, 20, 110], "confidence": 0.9}
        accepted, rejected = filter_field([inside] * 24 + [outside], polygon)
        self.assertEqual(24, len(accepted))
        self.assertEqual("outside_field_region", rejected[0]["reason"])

    def test_deduplicates_same_person_but_preserves_adjacent_player(self):
        def item(conf, box):
            return {"confidence": conf, "xyxy_px": box}

        detections = [
            item(0.7, [0, 0, 10, 20]),
            item(0.5, [1, 0, 11, 20]),
            item(0.6, [8, 0, 18, 20]),
        ]
        self.assertEqual(2, len(merge_overlapping(detections)))

    def test_ball_class_is_resolved_by_name_not_coco_index(self):
        self.assertEqual([0], ball_classes(SimpleNamespace(names={0: "ball"})))
        self.assertEqual(
            [32], ball_classes(SimpleNamespace(names={0: "person", 32: "sports ball"}))
        )
        with self.assertRaisesRegex(ValueError, "no 'ball'"):
            ball_classes(SimpleNamespace(names={0: "person"}))

    def test_tiles_cover_right_edge_and_small_frames(self):
        self.assertEqual([0], tile_starts(200, 400))
        starts = tile_starts(1920, 400)
        self.assertEqual(1520, starts[-1])
        self.assertTrue(all(b - a <= 400 for a, b in pairwise(starts)))

    def test_region_rejects_other_frame_or_instant(self):
        frame = np.zeros((100, 100, 3), dtype=np.uint8)
        data = {
            "frame_size_px": [100, 100],
            "requested_seconds": 0,
            "frame_sha256": hashlib.sha256(frame.tobytes()).hexdigest(),
            "polygon_px": [[0, 0], [99, 0], [99, 99], [0, 99]],
        }
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "region.json"
            path.write_text(json.dumps(data))
            self.assertEqual((4, 2), load_field_region(path, frame, 0).shape)
            with self.assertRaisesRegex(ValueError, "different instant"):
                load_field_region(path, frame, 1)
            with self.assertRaisesRegex(ValueError, "exact decoded frame"):
                load_field_region(path, frame + 1, 0)

    def test_box_coordinates_and_foot_point_remain_in_input_pixels(self):
        def box(class_id, confidence, xyxy):
            return SimpleNamespace(
                cls=np.array([class_id]), conf=np.array([confidence]), xyxy=np.array([xyxy])
            )

        result = SimpleNamespace(
            names={0: "person", 32: "sports ball"},
            boxes=[box(0, 0.8, [10, 20, 30, 80]), box(32, 0.6, [40, 50, 44, 54])],
        )
        people, ball = serialize_boxes(result)
        self.assertEqual([10, 20, 30, 80], people["xyxy_px"])
        self.assertEqual([20, 80], people["foot_point_px"])
        self.assertEqual("sports ball", ball["class_name"])
        self.assertIsNone(ball["foot_point_px"])

    def test_reads_requested_video_frame_and_reports_out_of_range(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "video.avi"
            writer = cv2.VideoWriter(str(path), cv2.VideoWriter_fourcc(*"MJPG"), 10, (64, 48))
            if not writer.isOpened():
                self.skipTest("MJPG codec unavailable")
            for level in (0, 50, 100, 150):
                writer.write(np.full((48, 64, 3), level, dtype=np.uint8))
            writer.release()
            self.assertAlmostEqual(100, read_frame(path, 0.2).mean(), delta=3)
            with self.assertRaisesRegex(ValueError, "Could not read frame"):
                read_frame(path, 100)

    def test_missing_video_has_actionable_error(self):
        with self.assertRaisesRegex(ValueError, "Video not found"):
            read_frame(Path("/nonexistent/ta53-video.mp4"), 0)
