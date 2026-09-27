"""Validate a tracking JSON against the CV-to-Java v1 contract."""

from __future__ import annotations

import argparse
import json
import math
from collections import Counter
from collections.abc import Mapping
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from pathlib import Path

FIELD_LENGTH_METRES = 105.0
FIELD_WIDTH_METRES = 68.0
MAX_SPEED_KMH = 40.0
TIMESTAMP_TOLERANCE_SECONDS = 0.002
ENTITY_TYPES = frozenset({"player", "ball", "referee"})


@dataclass(frozen=True, slots=True)
class ValidationIssue:
    code: str
    path: str
    message: str

    def format(self) -> str:
        return f"[{self.code}] {self.path}: {self.message}"


@dataclass(slots=True)
class ValidationReport:
    errors: list[ValidationIssue] = field(default_factory=list)
    warnings: list[ValidationIssue] = field(default_factory=list)
    metrics: dict[str, object] = field(default_factory=dict)

    @property
    def is_valid(self) -> bool:
        return not self.errors

    def add_error(self, code: str, path: str, message: str) -> None:
        self.errors.append(ValidationIssue(code, path, message))

    def add_warning(self, code: str, path: str, message: str) -> None:
        self.warnings.append(ValidationIssue(code, path, message))

    def format(self) -> str:
        status = "VALID" if self.is_valid else "INVALID"
        error_counts = Counter(issue.code for issue in self.errors)
        warning_counts = Counter(issue.code for issue in self.warnings)
        lines = [
            f"Tracking JSON validation: {status}",
            f"Errors: {len(self.errors)}",
            f"Warnings: {len(self.warnings)}",
        ]
        if error_counts:
            lines.append(
                "Error categories: "
                + ", ".join(
                    f"{code}={count}" for code, count in sorted(error_counts.items())
                )
            )
        if warning_counts:
            lines.append(
                "Warning categories: "
                + ", ".join(
                    f"{code}={count}" for code, count in sorted(warning_counts.items())
                )
            )
        details = [
            *(f"ERROR {issue.format()}" for issue in self.errors),
            *(f"WARNING {issue.format()}" for issue in self.warnings),
        ]
        maximum_details = 50
        lines.extend(details[:maximum_details])
        if len(details) > maximum_details:
            lines.append(
                f"... {len(details) - maximum_details} additional messages omitted"
            )
        if self.metrics:
            lines.append("Metrics:")
            lines.extend(f"  {key}: {value}" for key, value in self.metrics.items())
        return "\n".join(lines)


def validate_file(path: str | Path) -> ValidationReport:
    source = Path(path)
    try:
        with source.open(encoding="utf-8") as stream:
            payload = json.load(stream)
    except OSError as error:
        report = ValidationReport()
        report.add_error("file.read", str(source), str(error))
        return report
    except json.JSONDecodeError as error:
        report = ValidationReport()
        report.add_error(
            "json.syntax",
            str(source),
            f"Invalid JSON at line {error.lineno}, column {error.colno}: {error.msg}",
        )
        return report
    return validate_payload(payload)


def validate_payload(payload: object) -> ValidationReport:
    report = ValidationReport()
    if not isinstance(payload, Mapping):
        report.add_error("structure.root", "$", "root must be an object")
        return report

    _check_exact_keys(payload, {"match_info", "frames"}, "$", report)
    frame_rate = _validate_match_info(payload.get("match_info"), report)
    frames = payload.get("frames")
    if not isinstance(frames, list):
        report.add_error("structure.frames", "$.frames", "must be a list")
        return report
    if not frames:
        report.add_error("structure.frames_empty", "$.frames", "must not be empty")
        return report

    unique_by_type = {entity_type: set() for entity_type in ENTITY_TYPES}
    previous_positions: dict[int, tuple[int, str, float, float]] = {}
    previous_timestamp: float | None = None
    empty_frames = 0
    maximum_players = 0
    repeated_positions: Counter[int] = Counter()

    for frame_index, frame in enumerate(frames, start=1):
        frame_path = f"$.frames[{frame_index - 1}]"
        frame_result = _validate_frame_structure(
            frame,
            frame_index,
            frame_rate,
            previous_timestamp,
            frame_path,
            report,
        )
        if frame_result is None:
            continue
        timestamp, entities = frame_result
        previous_timestamp = timestamp

        if not entities:
            empty_frames += 1
        players_in_frame = 0
        balls_in_frame = 0
        ids_in_frame: set[int] = set()

        for entity_index, entity in enumerate(entities):
            entity_path = f"{frame_path}.entities[{entity_index}]"
            parsed = _validate_entity(entity, entity_path, report)
            if parsed is None:
                continue
            entity_id, entity_type, x_metres, y_metres = parsed

            if entity_id in ids_in_frame:
                report.add_error(
                    "entity.duplicate_id",
                    f"{entity_path}.id",
                    f"id {entity_id} is duplicated in frame {frame_index}",
                )
            ids_in_frame.add(entity_id)
            unique_by_type[entity_type].add(entity_id)

            if entity_type == "player":
                players_in_frame += 1
            elif entity_type == "ball":
                balls_in_frame += 1

            previous = previous_positions.get(entity_id)
            if previous is not None and previous[0] == frame_index - 1:
                previous_type, previous_x, previous_y = previous[1:]
                if previous_type != entity_type:
                    report.add_error(
                        "entity.type_changed",
                        entity_path,
                        f"id {entity_id} changed from {previous_type} to {entity_type}",
                    )
                distance = math.hypot(x_metres - previous_x, y_metres - previous_y)
                speed_kmh = distance * frame_rate * 3.6 if frame_rate else 0.0
                if speed_kmh > MAX_SPEED_KMH:
                    report.add_error(
                        "physical.speed",
                        entity_path,
                        f"id {entity_id} implies {speed_kmh:.2f} km/h (> {MAX_SPEED_KMH:.0f})",
                    )
                if x_metres == previous_x and y_metres == previous_y:
                    repeated_positions[entity_id] += 1
            previous_positions[entity_id] = (
                frame_index,
                entity_type,
                x_metres,
                y_metres,
            )

        if balls_in_frame > 1:
            report.add_error(
                "entity.multiple_balls",
                f"{frame_path}.entities",
                f"contains {balls_in_frame} balls; maximum is one",
            )
        if players_in_frame > 22:
            report.add_error(
                "entity.too_many_players",
                f"{frame_path}.entities",
                f"contains {players_in_frame} players; maximum is 22",
            )
        maximum_players = max(maximum_players, players_in_frame)

    empty_percentage = empty_frames * 100.0 / len(frames)
    if empty_frames:
        report.add_warning(
            "quality.empty_frames",
            "$.frames",
            f"{empty_frames}/{len(frames)} frames are empty ({empty_percentage:.2f}%)",
        )
    for entity_id, count in sorted(repeated_positions.items()):
        report.add_warning(
            "quality.repeated_position",
            "$.frames",
            f"id {entity_id} repeats its previous position in {count} frame transitions",
        )

    report.metrics.update(
        {
            "total_frames": len(frames),
            "empty_frames": empty_frames,
            "empty_frames_percent": round(empty_percentage, 2),
            "maximum_players_per_frame": maximum_players,
            "unique_player_ids": len(unique_by_type["player"]),
            "unique_ball_ids": len(unique_by_type["ball"]),
            "unique_referee_ids": len(unique_by_type["referee"]),
        }
    )
    return report


def _validate_match_info(value: object, report: ValidationReport) -> float | None:
    path = "$.match_info"
    if not isinstance(value, Mapping):
        report.add_error("structure.match_info", path, "must be an object")
        return None
    _check_exact_keys(value, {"video_name", "frame_rate", "resolution"}, path, report)

    video_name = value.get("video_name")
    if not isinstance(video_name, str) or not video_name.strip():
        report.add_error(
            "match_info.video_name", f"{path}.video_name", "must be non-empty text"
        )

    frame_rate = value.get("frame_rate")
    valid_frame_rate = _number(frame_rate)
    if valid_frame_rate is None or valid_frame_rate <= 0:
        report.add_error(
            "match_info.frame_rate",
            f"{path}.frame_rate",
            "must be a positive finite number",
        )
        numeric_frame_rate = None
    else:
        numeric_frame_rate = valid_frame_rate

    resolution = value.get("resolution")
    if not isinstance(resolution, Mapping):
        report.add_error(
            "match_info.resolution", f"{path}.resolution", "must be an object"
        )
    else:
        _check_exact_keys(
            resolution,
            {"width", "height"},
            f"{path}.resolution",
            report,
        )
        for dimension in ("width", "height"):
            size = resolution.get(dimension)
            if not _integer(size) or size <= 0:
                report.add_error(
                    f"match_info.resolution_{dimension}",
                    f"{path}.resolution.{dimension}",
                    "must be a positive integer",
                )
    return numeric_frame_rate


def _validate_frame_structure(
    frame: object,
    expected_frame_id: int,
    frame_rate: float | None,
    previous_timestamp: float | None,
    path: str,
    report: ValidationReport,
) -> tuple[float, list[object]] | None:
    if not isinstance(frame, Mapping):
        report.add_error("structure.frame", path, "must be an object")
        return None
    _check_exact_keys(frame, {"frame_id", "timestamp", "entities"}, path, report)

    frame_id = frame.get("frame_id")
    if not _integer(frame_id):
        report.add_error("frame.id_type", f"{path}.frame_id", "must be an integer")
    elif frame_id != expected_frame_id:
        report.add_error(
            "frame.id_sequence",
            f"{path}.frame_id",
            f"expected {expected_frame_id}, got {frame_id}",
        )

    timestamp = _number(frame.get("timestamp"))
    if timestamp is None:
        report.add_error(
            "frame.timestamp_type", f"{path}.timestamp", "must be a finite number"
        )
        numeric_timestamp = math.nan
    else:
        numeric_timestamp = timestamp
        if previous_timestamp is not None and timestamp <= previous_timestamp:
            report.add_error(
                "frame.timestamp_order",
                f"{path}.timestamp",
                "must be strictly greater than the previous timestamp",
            )
        if frame_rate is not None:
            expected_timestamp = round(expected_frame_id / frame_rate, 3)
            if abs(timestamp - expected_timestamp) > TIMESTAMP_TOLERANCE_SECONDS:
                report.add_error(
                    "frame.timestamp_value",
                    f"{path}.timestamp",
                    f"expected {expected_timestamp:.3f}, got {timestamp}",
                )

    entities = frame.get("entities")
    if not isinstance(entities, list):
        report.add_error("frame.entities_type", f"{path}.entities", "must be a list")
        return None
    return numeric_timestamp, entities


def _validate_entity(
    entity: object,
    path: str,
    report: ValidationReport,
) -> tuple[int, str, float, float] | None:
    if not isinstance(entity, Mapping):
        report.add_error("structure.entity", path, "must be an object")
        return None
    required = {"id", "type", "team_id", "x", "y"}
    _check_exact_keys(entity, required, path, report)
    if not required.issubset(entity):
        return None

    entity_id = entity["id"]
    entity_type = entity["type"]
    team_id = entity["team_id"]
    x_metres = _number(entity["x"])
    y_metres = _number(entity["y"])
    valid = True

    if not _integer(entity_id) or entity_id < 0:
        report.add_error("entity.id", f"{path}.id", "must be a non-negative integer")
        valid = False
    if not isinstance(entity_type, str) or entity_type not in ENTITY_TYPES:
        report.add_error(
            "entity.type",
            f"{path}.type",
            f"must be one of {sorted(ENTITY_TYPES)}",
        )
        valid = False
    if x_metres is None or not 0 <= x_metres <= FIELD_LENGTH_METRES:
        report.add_error(
            "entity.x_bounds",
            f"{path}.x",
            f"must be between 0 and {FIELD_LENGTH_METRES:g} metres",
        )
        valid = False
    if y_metres is None or not 0 <= y_metres <= FIELD_WIDTH_METRES:
        report.add_error(
            "entity.y_bounds",
            f"{path}.y",
            f"must be between 0 and {FIELD_WIDTH_METRES:g} metres",
        )
        valid = False

    if entity_type == "player":
        if team_id not in (1, 2) or isinstance(team_id, bool):
            report.add_error("entity.player_team", f"{path}.team_id", "must be 1 or 2")
        if _integer(entity_id) and entity_id < 1:
            report.add_error("entity.player_id", f"{path}.id", "must be 1 or greater")
    elif entity_type == "ball":
        if team_id is not None:
            report.add_error("entity.ball_team", f"{path}.team_id", "must be null")
        if entity_id != 0:
            report.add_error("entity.ball_id", f"{path}.id", "must be 0")
    elif entity_type == "referee":
        if team_id is not None:
            report.add_error("entity.referee_team", f"{path}.team_id", "must be null")
        if not _integer(entity_id) or entity_id < 1000:
            report.add_error(
                "entity.referee_id", f"{path}.id", "must be 1000 or greater"
            )

    for coordinate_name, coordinate in (("x", entity["x"]), ("y", entity["y"])):
        if _has_more_than_two_decimal_places(coordinate):
            report.add_warning(
                "quality.coordinate_precision",
                f"{path}.{coordinate_name}",
                "has more than two decimal places",
            )

    if not valid:
        return None
    return entity_id, entity_type, x_metres, y_metres


def _check_exact_keys(
    value: Mapping[str, object],
    expected: set[str],
    path: str,
    report: ValidationReport,
) -> None:
    actual = set(value)
    missing = expected - actual
    extra = actual - expected
    if missing:
        report.add_error(
            "structure.missing_fields",
            path,
            f"missing fields: {', '.join(sorted(missing))}",
        )
    if extra:
        report.add_error(
            "structure.extra_fields",
            path,
            f"unexpected fields: {', '.join(sorted(extra))}",
        )


def _number(value: object) -> float | None:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return None
    try:
        numeric = float(value)
    except (OverflowError, ValueError):
        return None
    return numeric if math.isfinite(numeric) else None


def _integer(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _has_more_than_two_decimal_places(value: object) -> bool:
    if _number(value) is None:
        return False
    try:
        return Decimal(str(value)).as_tuple().exponent < -2
    except InvalidOperation:
        return False


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("json_path", help="Tracking JSON to validate")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    report = validate_file(args.json_path)
    print(report.format())
    return 0 if report.is_valid else 1


if __name__ == "__main__":
    raise SystemExit(main())
