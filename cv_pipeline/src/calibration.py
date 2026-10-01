"""Pixel-to-metre homography calculation and calibration persistence."""

from __future__ import annotations

import json
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

from field_model import FIELD_LENGTH, FIELD_POLYLINES, FIELD_WIDTH, FieldPoint


@dataclass(frozen=True)
class CalibrationPoint:
    name: str
    pixel: tuple[float, float]
    metre: tuple[float, float]
    quality: str = "A"

    @classmethod
    def from_field_point(cls, point: FieldPoint, pixel: Sequence[float]) -> CalibrationPoint:
        return cls(point.name, (float(pixel[0]), float(pixel[1])), point.metre, point.quality)


@dataclass(frozen=True)
class CalibrationResult:
    matrix: np.ndarray
    points: tuple[CalibrationPoint, ...]
    errors_metres: tuple[float, ...]
    inliers: tuple[bool, ...]
    ransac_threshold_metres: float

    @property
    def rmse_metres(self) -> float:
        errors = np.asarray(self.errors_metres, dtype=np.float64)
        return float(np.sqrt(np.mean(np.square(errors))))

    @property
    def max_error_metres(self) -> float:
        return max(self.errors_metres, default=0.0)

    def is_reliable(self, max_rmse_metres: float = 1.0, min_inliers: int = 4) -> bool:
        return sum(self.inliers) >= min_inliers and self.rmse_metres <= max_rmse_metres


def calculate_homography(
    points: Sequence[CalibrationPoint], ransac_threshold_metres: float = 1.0
) -> CalibrationResult:
    if len(points) < 4:
        raise ValueError("At least four calibration points are required")
    if ransac_threshold_metres <= 0:
        raise ValueError("RANSAC threshold must be positive")

    pixels = np.asarray([point.pixel for point in points], dtype=np.float32)
    metres = np.asarray([point.metre for point in points], dtype=np.float32)
    if len(np.unique(pixels, axis=0)) < 4 or len(np.unique(metres, axis=0)) < 4:
        raise ValueError("Calibration requires at least four distinct point pairs")

    matrix, mask = cv2.findHomography(
        pixels, metres, method=cv2.RANSAC, ransacReprojThreshold=ransac_threshold_metres
    )
    if matrix is None or mask is None or not np.isfinite(matrix).all():
        raise ValueError("OpenCV could not calculate a valid homography")
    if abs(float(np.linalg.det(matrix))) < 1e-12:
        raise ValueError("Calculated homography is singular")

    projected = cv2.perspectiveTransform(pixels.reshape(-1, 1, 2), matrix).reshape(-1, 2)
    errors = np.linalg.norm(projected - metres, axis=1)
    return CalibrationResult(
        matrix=matrix,
        points=tuple(points),
        errors_metres=tuple(float(value) for value in errors),
        inliers=tuple(bool(value) for value in mask.reshape(-1)),
        ransac_threshold_metres=float(ransac_threshold_metres),
    )


def pixel_to_metre(matrix: np.ndarray, x_pixel: float, y_pixel: float) -> tuple[float, float]:
    point = np.asarray([[[x_pixel, y_pixel]]], dtype=np.float32)
    x_metre, y_metre = cv2.perspectiveTransform(point, _valid_matrix(matrix))[0][0]
    return float(x_metre), float(y_metre)


def bbox_foot_to_metre(
    matrix: np.ndarray, x1: float, y1: float, x2: float, y2: float
) -> tuple[float, float]:
    del y1
    return pixel_to_metre(matrix, (x1 + x2) / 2.0, y2)


def metre_to_pixel(matrix: np.ndarray, x_metre: float, y_metre: float) -> tuple[float, float]:
    inverse = np.linalg.inv(_valid_matrix(matrix))
    point = np.asarray([[[x_metre, y_metre]]], dtype=np.float32)
    x_pixel, y_pixel = cv2.perspectiveTransform(point, inverse)[0][0]
    return float(x_pixel), float(y_pixel)


def is_inside_field(x_metre: float, y_metre: float, tolerance: float = 0.0) -> bool:
    return (
        -tolerance <= x_metre <= FIELD_LENGTH + tolerance
        and -tolerance <= y_metre <= FIELD_WIDTH + tolerance
    )


def draw_field_overlay(
    image: np.ndarray,
    matrix: np.ndarray,
    color: tuple[int, int, int] = (0, 255, 255),
    thickness: int = 2,
) -> np.ndarray:
    output = image.copy()
    inverse = np.linalg.inv(_valid_matrix(matrix))
    for polyline in FIELD_POLYLINES:
        metres = np.asarray(polyline, dtype=np.float32).reshape(-1, 1, 2)
        pixels = cv2.perspectiveTransform(metres, inverse).reshape(-1, 2)
        if np.isfinite(pixels).all():
            cv2.polylines(output, [np.rint(pixels).astype(np.int32)], False, color, thickness)

    origin = metre_to_pixel(matrix, 0.0, 0.0)
    if np.isfinite(origin).all():
        origin_int = tuple(np.rint(origin).astype(int))
        cv2.circle(output, origin_int, 7, (0, 0, 255), -1)
        cv2.putText(
            output,
            "(0,0)",
            (origin_int[0] + 9, origin_int[1] - 9),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (0, 0, 255),
            2,
            cv2.LINE_AA,
        )
    return output


def save_calibration(
    path: str | Path,
    result: CalibrationResult,
    *,
    frame_source: str,
    frame_index: int,
    image_size: tuple[int, int],
) -> None:
    payload = {
        "schema_version": 1,
        "frame_source": frame_source,
        "frame_index": int(frame_index),
        "image_size": {"width": int(image_size[0]), "height": int(image_size[1])},
        "coordinate_system": {
            "unit": "metre",
            "origin": "top_left",
            "x_axis": "right",
            "y_axis": "down",
            "field_length": FIELD_LENGTH,
            "field_width": FIELD_WIDTH,
        },
        "ransac_threshold_metres": result.ransac_threshold_metres,
        "matrix_pixel_to_metre": result.matrix.tolist(),
        "rmse_metres": result.rmse_metres,
        "max_error_metres": result.max_error_metres,
        "points": [
            {
                "name": point.name,
                "pixel": [float(value) for value in point.pixel],
                "metre": [float(value) for value in point.metre],
                "quality": point.quality,
                "error_metres": error,
                "inlier": inlier,
            }
            for point, error, inlier in zip(
                result.points,
                result.errors_metres,
                result.inliers,
                strict=True,
            )
        ],
    }
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def load_calibration(path: str | Path) -> CalibrationResult:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if payload.get("schema_version") != 1:
        raise ValueError("Unsupported calibration schema version")
    points = tuple(
        CalibrationPoint(
            item["name"], tuple(item["pixel"]), tuple(item["metre"]), item.get("quality", "A")
        )
        for item in payload["points"]
    )
    result = CalibrationResult(
        matrix=_valid_matrix(np.asarray(payload["matrix_pixel_to_metre"], dtype=np.float64)),
        points=points,
        errors_metres=tuple(float(item["error_metres"]) for item in payload["points"]),
        inliers=tuple(bool(item["inlier"]) for item in payload["points"]),
        ransac_threshold_metres=float(payload["ransac_threshold_metres"]),
    )
    return result


def _valid_matrix(matrix: np.ndarray) -> np.ndarray:
    value = np.asarray(matrix, dtype=np.float64)
    if value.shape != (3, 3) or not np.isfinite(value).all():
        raise ValueError("Homography matrix must be a finite 3 x 3 matrix")
    if abs(float(np.linalg.det(value))) < 1e-12:
        raise ValueError("Homography matrix is singular")
    return value
