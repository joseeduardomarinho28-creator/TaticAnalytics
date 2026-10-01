"""Interactive field calibration tool.

Select six to eight named field references, click them on a frame, inspect the
per-point error and visual overlay, then save the calibration as JSON.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import cv2

from calibration import CalibrationPoint, calculate_homography, draw_field_overlay, save_calibration
from field_model import FIELD_POINTS

WINDOW = "TaticAnalytics calibration"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", help="Frame image used for calibration")
    parser.add_argument("--output", required=True, help="Calibration JSON path")
    parser.add_argument("--overlay-output", help="Optional path for the visual verification image")
    parser.add_argument("--frame-index", type=int, default=1)
    parser.add_argument("--points", help="Comma-separated field point names; prompts when omitted")
    parser.add_argument("--ransac-threshold", type=float, default=1.0)
    parser.add_argument(
        "--allow-four",
        action="store_true",
        help="Allow 4-5 points; 6-8 is recommended",
    )
    return parser.parse_args()


def choose_points(raw: str | None, allow_four: bool) -> list[str]:
    if raw:
        names = [name.strip() for name in raw.split(",") if name.strip()]
    else:
        print("Available points (quality A is preferred):")
        for name, point in FIELD_POINTS.items():
            print(f"  {name:42} ({point.x:6.2f}, {point.y:5.2f}) quality {point.quality}")
        raw_names = input("Enter 6-8 point names, comma-separated: ")
        names = [name.strip() for name in raw_names.split(",")]

    unknown = [name for name in names if name not in FIELD_POINTS]
    if unknown:
        raise ValueError(f"Unknown field point(s): {', '.join(unknown)}")
    if len(names) != len(set(names)):
        raise ValueError("Each field point may be selected only once")
    minimum = 4 if allow_four else 6
    if not minimum <= len(names) <= 8:
        raise ValueError(f"Select between {minimum} and 8 points")
    return names


def collect_clicks(image, names: list[str]) -> list[tuple[float, float]]:
    clicks: list[tuple[float, float]] = []

    def mouse(event, x, y, _flags, _data):
        if event == cv2.EVENT_LBUTTONDOWN and len(clicks) < len(names):
            clicks.append((float(x), float(y)))

    cv2.namedWindow(WINDOW, cv2.WINDOW_NORMAL)
    cv2.setMouseCallback(WINDOW, mouse)
    while True:
        canvas = image.copy()
        for index, (x, y) in enumerate(clicks):
            cv2.circle(canvas, (int(x), int(y)), 5, (0, 255, 0), -1)
            cv2.putText(
                canvas,
                names[index],
                (int(x) + 7, int(y) - 7),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                (0, 255, 0),
                1,
                cv2.LINE_AA,
            )
        if len(clicks) < len(names):
            message = f"Click: {names[len(clicks)]} ({len(clicks) + 1}/{len(names)})"
        else:
            message = "All points captured - ENTER saves, U undoes, ESC cancels"
        cv2.putText(
            canvas, message, (20, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.75, (0, 255, 255), 2, cv2.LINE_AA
        )
        cv2.imshow(WINDOW, canvas)
        key = cv2.waitKey(20) & 0xFF
        if key == 27:
            raise KeyboardInterrupt("Calibration cancelled")
        if key in (ord("u"), ord("U")) and clicks:
            clicks.pop()
        if key in (10, 13) and len(clicks) == len(names):
            cv2.destroyWindow(WINDOW)
            return clicks


def main() -> int:
    args = parse_args()
    names = choose_points(args.points, args.allow_four)
    image = cv2.imread(args.image)
    if image is None:
        raise FileNotFoundError(f"Could not open image: {args.image}")

    clicks = collect_clicks(image, names)
    points = [
        CalibrationPoint.from_field_point(FIELD_POINTS[name], click)
        for name, click in zip(names, clicks, strict=True)
    ]
    result = calculate_homography(points, args.ransac_threshold)

    print("Calibration error by point:")
    for point, error, inlier in zip(
        result.points,
        result.errors_metres,
        result.inliers,
        strict=True,
    ):
        print(f"  {point.name:42} {error:7.3f} m  {'inlier' if inlier else 'OUTLIER'}")
    print(f"RMSE: {result.rmse_metres:.3f} m | maximum: {result.max_error_metres:.3f} m")

    save_calibration(
        args.output,
        result,
        frame_source=str(Path(args.image)),
        frame_index=args.frame_index,
        image_size=(image.shape[1], image.shape[0]),
    )
    overlay = draw_field_overlay(image, result.matrix)
    overlay_path = args.overlay_output or str(Path(args.output).with_suffix(".overlay.jpg"))
    if not cv2.imwrite(overlay_path, overlay):
        raise OSError(f"Could not save overlay image: {overlay_path}")
    print(f"Calibration saved to {args.output}")
    print(f"Visual verification saved to {overlay_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
