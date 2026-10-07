import json
import sys
import tempfile
import unittest
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from calibration import (
    CalibrationPoint,
    bbox_foot_to_metre,
    calculate_homography,
    is_inside_field,
    load_calibration,
    pixel_to_metre,
    save_calibration,
)


class CalibrationTest(unittest.TestCase):
    def setUp(self):
        self.metre_points = np.float32(
            [[0, 0], [105, 0], [105, 68], [0, 68], [52.5, 0], [52.5, 68]]
        )
        pixel_corners = np.float32([[100, 100], [900, 120], [950, 650], [80, 680]])
        metre_corners = self.metre_points[:4]
        pixel_to_metre = cv2.getPerspectiveTransform(pixel_corners, metre_corners)
        self.pixel_points = cv2.perspectiveTransform(
            self.metre_points.reshape(-1, 1, 2), np.linalg.inv(pixel_to_metre)
        ).reshape(-1, 2)
        self.points = [
            CalibrationPoint(
                f"p{index}",
                tuple(float(value) for value in pixel),
                tuple(float(value) for value in metre),
            )
            for index, (pixel, metre) in enumerate(
                zip(self.pixel_points, self.metre_points, strict=True)
            )
        ]

    def test_calculates_pixel_to_metre_homography(self):
        result = calculate_homography(self.points)
        for pixel, expected in zip(self.pixel_points, self.metre_points, strict=True):
            actual = pixel_to_metre(result.matrix, *pixel)
            np.testing.assert_allclose(actual, expected, atol=0.02)
        self.assertLess(result.rmse_metres, 0.02)
        self.assertTrue(result.is_reliable())

    def test_uses_bottom_centre_of_bounding_box(self):
        result = calculate_homography(self.points)
        expected = pixel_to_metre(result.matrix, 500.0, 650.0)
        actual = bbox_foot_to_metre(result.matrix, 450.0, 400.0, 550.0, 650.0)
        np.testing.assert_allclose(actual, expected, atol=1e-6)

    def test_persists_complete_calibration(self):
        result = calculate_homography(self.points)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "calibration.json"
            save_calibration(
                path,
                result,
                frame_source="frame.jpg",
                frame_index=42,
                image_size=(1920, 1080),
            )
            payload = json.loads(path.read_text())
            loaded = load_calibration(path)
        self.assertEqual(1, payload["schema_version"])
        self.assertEqual(42, payload["frame_index"])
        self.assertEqual(6, len(payload["points"]))
        np.testing.assert_allclose(loaded.matrix, result.matrix)

    def test_rejects_too_few_points(self):
        with self.assertRaisesRegex(ValueError, "At least four"):
            calculate_homography(self.points[:3])

    def test_field_bounds(self):
        self.assertTrue(is_inside_field(0.0, 0.0))
        self.assertTrue(is_inside_field(105.0, 68.0))
        self.assertFalse(is_inside_field(-0.1, 34.0))
        self.assertTrue(is_inside_field(-0.1, 34.0, tolerance=0.2))


if __name__ == "__main__":
    unittest.main()
