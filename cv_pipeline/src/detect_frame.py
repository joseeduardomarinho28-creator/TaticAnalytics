"""Detect objects in one video frame and save inspectable evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

import cv2
import numpy as np


def read_frame(video: Path, seconds: float):
    if not video.is_file():
        raise ValueError(f"Video not found: {video}")
    capture = cv2.VideoCapture(str(video))
    try:
        if not capture.isOpened():
            raise ValueError(f"Could not open video: {video}")
        if seconds:
            capture.set(cv2.CAP_PROP_POS_MSEC, seconds * 1000)
        ok, frame = capture.read()
        if not ok:
            raise ValueError(f"Could not read frame at {seconds:g} seconds")
        return frame
    finally:
        capture.release()


def serialize_boxes(result):
    detections = []
    for box in result.boxes:
        class_id = int(box.cls.item())
        name = result.names[class_id]
        x1, y1, x2, y2 = box.xyxy[0].tolist()
        detections.append(
            {
                "class_id": class_id,
                "class_name": name,
                "confidence": float(box.conf.item()),
                "xyxy_px": [x1, y1, x2, y2],
                "foot_point_px": [(x1 + x2) / 2, y2] if name == "person" else None,
            }
        )
    return detections


def load_field_region(path, frame, seconds):
    region = json.loads(path.read_text(encoding="utf-8"))
    if region.get("frame_sha256") != hashlib.sha256(frame.tobytes()).hexdigest():
        raise ValueError("Field region does not belong to this exact decoded frame")
    if region.get("frame_size_px") != [frame.shape[1], frame.shape[0]]:
        raise ValueError("Field region dimensions do not match this frame")
    if region.get("requested_seconds") != seconds:
        raise ValueError("Field region is for a different instant; camera movement invalidates it")
    polygon = np.asarray(region.get("polygon_px", []), dtype=np.float32)
    if polygon.ndim != 2 or polygon.shape[1] != 2 or len(polygon) < 3:
        raise ValueError("Field region needs at least three [x, y] vertices")
    if not np.isfinite(polygon).all() or not cv2.isContourConvex(polygon):
        raise ValueError("Field region must be a finite, convex polygon in boundary order")
    if (
        (polygon < 0).any()
        or (polygon[:, 0] >= frame.shape[1]).any()
        or (polygon[:, 1] >= frame.shape[0]).any()
        or cv2.contourArea(polygon) < 1
    ):
        raise ValueError("Field region must have positive area inside the frame")
    return polygon


def filter_field(detections, polygon):
    accepted, rejected = [], []
    for item in detections:
        x1, y1, x2, y2 = item["xyxy_px"]
        point = ((x1 + x2) / 2, y2 if item["class_name"] == "person" else (y1 + y2) / 2)
        if cv2.pointPolygonTest(polygon, point, False) >= 0:
            accepted.append(item)
        else:
            rejected.append({**item, "reason": "outside_field_region"})
    return accepted, rejected


def tile_starts(length, size):
    if length <= size:
        return [0]
    return sorted({*range(0, length - size + 1, max(1, int(size * 0.8))), length - size})


def merge_overlapping(detections, threshold=0.4):
    kept = []
    for candidate in sorted(detections, key=lambda item: item["confidence"], reverse=True):
        a = candidate["xyxy_px"]
        duplicate = False
        for item in kept:
            b = item["xyxy_px"]
            intersection = max(0, min(a[2], b[2]) - max(a[0], b[0])) * max(
                0, min(a[3], b[3]) - max(a[1], b[1])
            )
            union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1])
            union -= intersection
            if union > 0 and intersection / union > threshold:
                duplicate = True
                break
        if not duplicate:
            kept.append(candidate)
    return kept


def ball_classes(model):
    ids = [key for key, name in model.names.items() if name in {"ball", "sports ball"}]
    if not ids:
        raise ValueError("Ball model has no 'ball' or 'sports ball' class")
    return ids


def ball_detections(result):
    items = serialize_boxes(result)
    for item in items:
        item["source_class_name"] = item["class_name"]
        item["class_name"] = "sports ball"
    return items


def detect_ball_tiles(model, frame, tile_size, conf, device):
    candidates = []
    for y in tile_starts(frame.shape[0], tile_size):
        for x in tile_starts(frame.shape[1], tile_size):
            crop = frame[y : y + tile_size, x : x + tile_size]
            result = model(
                crop,
                imgsz=max(640, tile_size),
                conf=conf,
                classes=ball_classes(model),
                device=device,
                verbose=False,
            )[0]
            for item in ball_detections(result):
                a = item["xyxy_px"]
                item["xyxy_px"] = [a[0] + x, a[1] + y, a[2] + x, a[3] + y]
                item["source"] = "ball_tile"
                candidates.append(item)
    return candidates


def annotate(frame, detections, polygon=None):
    image = frame.copy()
    if polygon is not None:
        cv2.polylines(image, [polygon.astype(np.int32)], True, (255, 200, 0), 2)
    for item in detections:
        x1, y1, x2, y2 = map(round, item["xyxy_px"])
        color = (0, 255, 255) if item["class_name"] == "person" else (0, 100, 255)
        cv2.rectangle(image, (x1, y1), (x2, y2), color, 1)
        label = f"{item['class_name']} {item['confidence']:.2f}"
        cv2.putText(
            image,
            label,
            (x1, max(12, y1 - 3)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.35,
            color,
            1,
            cv2.LINE_AA,
        )
    return image


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--video", required=True, type=Path)
    parser.add_argument("--seconds", type=float, default=0)
    parser.add_argument("--model", default="yolo26n.pt")
    parser.add_argument("--imgsz", type=int, default=1280)
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--field-region", type=Path, help="Convex field polygon for this frame")
    parser.add_argument("--ball-tile-size", type=int, default=0, help="0 disables ball crop search")
    parser.add_argument("--ball-conf", type=float, default=0.25)
    parser.add_argument("--ball-model", help="Optional dedicated ball detector weights")
    parser.add_argument("--ball-imgsz", type=int, default=1280)
    parser.add_argument("--output", type=Path, default=Path("output/first_detection"))
    args = parser.parse_args(argv)
    if not math.isfinite(args.seconds) or args.seconds < 0:
        parser.error("--seconds must be finite and non-negative")
    if args.imgsz <= 0 or args.ball_imgsz <= 0:
        parser.error("--imgsz and --ball-imgsz must be positive")
    if not 0 < args.conf <= 1:
        parser.error("--conf must be in (0, 1]")
    if not 0 < args.ball_conf <= 1 or args.ball_tile_size < 0:
        parser.error("--ball-conf must be in (0, 1] and --ball-tile-size non-negative")

    try:
        frame = read_frame(args.video.expanduser(), args.seconds)
        polygon = (
            load_field_region(args.field_region, frame, args.seconds) if args.field_region else None
        )
        # Import only after validating input; invalid videos never download weights.
        from ultralytics import YOLO

        model = YOLO(args.model)
        result = model(
            frame,
            imgsz=args.imgsz,
            conf=min(args.conf, args.ball_conf),
            classes=[0, 32],
            device=args.device,
            verbose=False,
        )[0]
        raw = serialize_boxes(result)
        people = [d for d in raw if d["class_name"] == "person" and d["confidence"] >= args.conf]
        balls = [
            d for d in raw if d["class_name"] == "sports ball" and d["confidence"] >= args.ball_conf
        ]
        if args.ball_model:
            ball_model = YOLO(args.ball_model)
            ball_result = ball_model(
                frame,
                imgsz=args.ball_imgsz,
                conf=args.ball_conf,
                classes=ball_classes(ball_model),
                device=args.device,
                verbose=False,
            )[0]
            balls = ball_detections(ball_result)
        else:
            ball_model = model
        if args.ball_tile_size:
            # Search tiles with the selected ball model, then merge back into input pixels.
            balls = [d for d in balls if d.get("source") != "ball_tile"]
            balls += detect_ball_tiles(
                ball_model, frame, args.ball_tile_size, args.ball_conf, args.device
            )
        detections = merge_overlapping(people) + merge_overlapping(balls)
        rejected = []
        if polygon is not None:
            detections, rejected = filter_field(detections, polygon)
        person_count = sum(d["class_name"] == "person" for d in detections)
        ball_count = sum(d["class_name"] == "sports ball" for d in detections)
        warnings = []
        if polygon is None:
            warnings.append("No field region: off-field people may be included")
        if person_count > 23:
            warnings.append("More than 23 people: inspect false positives; no count cap applied")
        if ball_count != 1:
            warnings.append(f"Expected one ball when visible, found {ball_count}; review required")
        report = {
            "video": str(args.video.expanduser().resolve()),
            "requested_seconds": args.seconds,
            "frame_size_px": {"width": frame.shape[1], "height": frame.shape[0]},
            "frame_sha256": hashlib.sha256(frame.tobytes()).hexdigest(),
            "model": args.model,
            "imgsz": args.imgsz,
            "confidence_threshold": args.conf,
            "device": args.device,
            "field_region_px": polygon.tolist() if polygon is not None else None,
            "ball_tile_size": args.ball_tile_size,
            "ball_confidence_threshold": args.ball_conf,
            "ball_model": args.ball_model or args.model,
            "ball_imgsz": args.ball_imgsz if args.ball_model else args.imgsz,
            "people_count": person_count,
            "ball_candidates_count": ball_count,
            "ball_status": "per_frame_candidates_not_temporally_validated",
            "warnings": warnings,
            "rejected_detections": rejected,
            "coordinate_system": "pixels in the decoded input frame; xyxy are box corners",
            "person_position": "bottom-center of bounding box; not calibrated metres",
            "detections": detections,
            "raw_full_frame_detections": raw,
        }
        args.output.mkdir(parents=True, exist_ok=True)
        for filename, image in [
            ("original.jpg", frame),
            ("annotated.jpg", annotate(frame, detections, polygon)),
        ]:
            if not cv2.imwrite(str(args.output / filename), image):
                raise OSError(f"Could not write {filename}")
        (args.output / "detections.json").write_text(
            json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        for item in detections:
            print(
                f"class={item['class_id']} ({item['class_name']}) "
                f"confidence={item['confidence']:.3f} xyxy={item['xyxy_px']}"
            )
        print(f"Detections: {len(detections)}; evidence: {args.output.resolve()}")
        for warning in warnings:
            print(f"WARNING: {warning}")
    except (ValueError, OSError, RuntimeError, ImportError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
