"""Export calibrated tracking observations using the CV-to-Java v1 contract."""

from __future__ import annotations

import argparse
import json
import logging
import math
import os
import tempfile
from collections.abc import Iterable, Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path

FIELD_LENGTH_METRES = 105.0
FIELD_WIDTH_METRES = 68.0
DEFAULT_CLAMP_TOLERANCE_METRES = 2.0
ENTITY_TYPES = frozenset({"player", "ball", "referee"})

LOGGER = logging.getLogger(__name__)


class ExportContractError(ValueError):
    """Raised when an observation cannot satisfy the v1 output contract."""


@dataclass(frozen=True, slots=True)
class EntityObservation:
    """One detected entity whose position is already calibrated in metres."""

    tracker_id: int
    entity_type: str
    team_id: int | None
    x_metres: float
    y_metres: float

    @classmethod
    def from_mapping(cls, value: Mapping[str, object]) -> EntityObservation:
        required = {"tracker_id", "type", "team_id", "x", "y"}
        missing = required - value.keys()
        extra = value.keys() - required
        if missing or extra:
            details = []
            if missing:
                details.append(f"missing {sorted(missing)}")
            if extra:
                details.append(f"unexpected {sorted(extra)}")
            raise ExportContractError(
                f"Invalid observation fields: {', '.join(details)}"
            )
        return cls(
            tracker_id=value["tracker_id"],
            entity_type=value["type"],
            team_id=value["team_id"],
            x_metres=value["x"],
            y_metres=value["y"],
        )


@dataclass(frozen=True, slots=True)
class FrameObservation:
    """Detections for one decoded video frame."""

    entities: Sequence[EntityObservation | Mapping[str, object]] = ()
    calibration_reliable: bool = True


@dataclass(frozen=True, slots=True)
class ExportSummary:
    output_path: str
    total_frames: int
    empty_frames: int
    clamped_coordinates: int
    discarded_entities: int
    unique_entity_ids: int
    file_size_bytes: int

    def format(self) -> str:
        return (
            "Tracking export complete\n"
            f"Output: {self.output_path}\n"
            f"Total frames: {self.total_frames}\n"
            f"Frames with empty entities: {self.empty_frames}\n"
            f"Clamped coordinates: {self.clamped_coordinates}\n"
            f"Discarded entities outside field: {self.discarded_entities}\n"
            f"Unique entity IDs: {self.unique_entity_ids}\n"
            f"File size: {self.file_size_bytes} bytes"
        )


class TrackingJsonExporter:
    """Build a contract-v1 document one decoded frame at a time."""

    def __init__(
        self,
        *,
        video_name: str,
        frame_rate: float,
        resolution: tuple[int, int],
        clamp_tolerance_metres: float = DEFAULT_CLAMP_TOLERANCE_METRES,
        team_ids_zero_based: bool = False,
        expected_total_frames: int | None = None,
    ) -> None:
        if not isinstance(video_name, str) or not video_name.strip():
            raise ExportContractError("video_name must be a non-empty string")
        normalized_frame_rate = _finite_float(frame_rate)
        if normalized_frame_rate is None or normalized_frame_rate <= 0:
            raise ExportContractError("frame_rate must be a positive finite number")
        if (
            not isinstance(resolution, tuple)
            or len(resolution) != 2
            or any(not _is_integer(value) or value <= 0 for value in resolution)
        ):
            raise ExportContractError("resolution must contain two positive integers")
        normalized_tolerance = _finite_float(clamp_tolerance_metres)
        if normalized_tolerance is None or normalized_tolerance < 0:
            raise ExportContractError(
                "clamp_tolerance_metres must be finite and non-negative"
            )
        if expected_total_frames is not None and (
            not _is_integer(expected_total_frames) or expected_total_frames < 1
        ):
            raise ExportContractError(
                "expected_total_frames must be a positive integer"
            )

        self.video_name = video_name
        self.frame_rate = normalized_frame_rate
        self.width, self.height = resolution
        self.clamp_tolerance_metres = normalized_tolerance
        self.team_ids_zero_based = team_ids_zero_based
        self.expected_total_frames = expected_total_frames
        self._frames: list[dict[str, object]] = []
        self._empty_frames = 0
        self._clamped_coordinates = 0
        self._discarded_entities = 0
        self._unique_ids: set[int] = set()

    def add_frame(
        self,
        entities: Iterable[EntityObservation | Mapping[str, object]] = (),
        *,
        calibration_reliable: bool = True,
    ) -> None:
        """Add exactly one decoded frame, using an empty list when calibration is unreliable."""
        if not isinstance(calibration_reliable, bool):
            raise ExportContractError("calibration_reliable must be a boolean")

        frame_id = len(self._frames) + 1
        exported_entities: list[dict[str, object]] = []
        used_ids: set[int] = set()
        ball_count = 0
        clamped_coordinates = 0
        discarded_entities = 0
        unique_ids: set[int] = set()

        if calibration_reliable:
            for raw_entity in entities:
                entity = _coerce_observation(raw_entity)
                normalized, clamped_count, was_discarded = self._normalize_entity(
                    entity, frame_id
                )
                clamped_coordinates += clamped_count
                discarded_entities += int(was_discarded)
                if normalized is None:
                    continue

                entity_id = normalized["id"]
                if normalized["type"] == "ball":
                    ball_count += 1
                    if ball_count > 1:
                        raise ExportContractError(
                            f"Frame {frame_id}: at most one ball may be exported"
                        )
                if entity_id in used_ids:
                    raise ExportContractError(
                        f"Frame {frame_id}: entity id {entity_id} is duplicated"
                    )
                used_ids.add(entity_id)
                exported_entities.append(normalized)
                unique_ids.add(entity_id)

        if not exported_entities:
            self._empty_frames += 1

        self._frames.append(
            {
                "frame_id": frame_id,
                "timestamp": round(frame_id / self.frame_rate, 3),
                "entities": exported_entities,
            }
        )
        self._clamped_coordinates += clamped_coordinates
        self._discarded_entities += discarded_entities
        self._unique_ids.update(unique_ids)

    def write(self, output_path: str | Path) -> ExportSummary:
        """Validate the completed document and atomically replace the destination."""
        if not self._frames:
            raise ExportContractError("At least one frame must be exported")
        if (
            self.expected_total_frames is not None
            and len(self._frames) != self.expected_total_frames
        ):
            raise ExportContractError(
                "Decoded frame count does not match expected_total_frames: "
                f"expected {self.expected_total_frames}, got {len(self._frames)}"
            )

        destination = Path(output_path).expanduser()
        if destination.exists() and destination.is_dir():
            raise ExportContractError(f"Output path is a directory: {destination}")
        destination.parent.mkdir(parents=True, exist_ok=True)

        payload = {
            "match_info": {
                "video_name": self.video_name,
                "frame_rate": _compact_number(self.frame_rate),
                "resolution": {"width": self.width, "height": self.height},
            },
            "frames": self._frames,
        }
        _validate_generated_payload(payload)

        temporary_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w",
                encoding="utf-8",
                dir=destination.parent,
                prefix=f".{destination.name}.",
                suffix=".tmp",
                delete=False,
            ) as temporary:
                temporary_path = Path(temporary.name)
                json.dump(
                    payload, temporary, indent=2, ensure_ascii=False, allow_nan=False
                )
                temporary.write("\n")
                temporary.flush()
                os.fsync(temporary.fileno())
            os.replace(temporary_path, destination)
        finally:
            if temporary_path is not None and temporary_path.exists():
                temporary_path.unlink()

        summary = ExportSummary(
            output_path=str(destination.resolve()),
            total_frames=len(self._frames),
            empty_frames=self._empty_frames,
            clamped_coordinates=self._clamped_coordinates,
            discarded_entities=self._discarded_entities,
            unique_entity_ids=len(self._unique_ids),
            file_size_bytes=destination.stat().st_size,
        )
        LOGGER.info(summary.format())
        return summary

    def _normalize_entity(
        self, entity: EntityObservation, frame_id: int
    ) -> tuple[dict[str, object] | None, int, bool]:
        entity_type = entity.entity_type
        if not isinstance(entity_type, str) or entity_type not in ENTITY_TYPES:
            raise ExportContractError(
                f"Frame {frame_id}: unsupported entity type {entity_type!r}"
            )
        if not _is_integer(entity.tracker_id) or entity.tracker_id < 0:
            raise ExportContractError(
                f"Frame {frame_id}: tracker_id must be a non-negative integer"
            )

        entity_id, team_id = self._normalize_identity(entity, frame_id)
        x_result = _normalize_coordinate(
            entity.x_metres,
            maximum=FIELD_LENGTH_METRES,
            tolerance=self.clamp_tolerance_metres,
        )
        y_result = _normalize_coordinate(
            entity.y_metres,
            maximum=FIELD_WIDTH_METRES,
            tolerance=self.clamp_tolerance_metres,
        )

        if x_result is None or y_result is None:
            LOGGER.warning(
                "Frame %d: discarded %s tracker_id=%d at (%.3f, %.3f) metres",
                frame_id,
                entity_type,
                entity.tracker_id,
                entity.x_metres,
                entity.y_metres,
            )
            return None, 0, True

        x_value, x_clamped = x_result
        y_value, y_clamped = y_result
        return (
            {
                "id": entity_id,
                "type": entity_type,
                "team_id": team_id,
                "x": _round_coordinate(x_value),
                "y": _round_coordinate(y_value),
            },
            int(x_clamped) + int(y_clamped),
            False,
        )

    def _normalize_identity(
        self, entity: EntityObservation, frame_id: int
    ) -> tuple[int, int | None]:
        if entity.entity_type == "ball":
            if entity.team_id is not None:
                raise ExportContractError(
                    f"Frame {frame_id}: ball team_id must be null"
                )
            return 0, None

        if entity.entity_type == "referee":
            if entity.team_id is not None:
                raise ExportContractError(
                    f"Frame {frame_id}: referee team_id must be null"
                )
            return 1000 + entity.tracker_id, None

        if entity.tracker_id < 1:
            raise ExportContractError(
                f"Frame {frame_id}: player tracker_id must be 1 or greater"
            )
        if not _is_integer(entity.team_id):
            raise ExportContractError(
                f"Frame {frame_id}: player team_id must be an integer"
            )

        if self.team_ids_zero_based:
            if entity.team_id not in (0, 1):
                raise ExportContractError(
                    f"Frame {frame_id}: zero-based player team_id must be 0 or 1"
                )
            team_id = entity.team_id + 1
        else:
            if entity.team_id not in (1, 2):
                raise ExportContractError(
                    f"Frame {frame_id}: player team_id must be 1 or 2"
                )
            team_id = entity.team_id
        return entity.tracker_id, team_id


def export_tracking_json(
    *,
    output_path: str | Path,
    video_name: str,
    frame_rate: float,
    resolution: tuple[int, int],
    frames: Iterable[FrameObservation],
    clamp_tolerance_metres: float = DEFAULT_CLAMP_TOLERANCE_METRES,
    team_ids_zero_based: bool = False,
    expected_total_frames: int | None = None,
) -> ExportSummary:
    """Convenience API for exporting a complete sequence of decoded frames."""
    exporter = TrackingJsonExporter(
        video_name=video_name,
        frame_rate=frame_rate,
        resolution=resolution,
        clamp_tolerance_metres=clamp_tolerance_metres,
        team_ids_zero_based=team_ids_zero_based,
        expected_total_frames=expected_total_frames,
    )
    for frame in frames:
        if not isinstance(frame, FrameObservation):
            raise ExportContractError("frames must contain FrameObservation values")
        exporter.add_frame(
            frame.entities,
            calibration_reliable=frame.calibration_reliable,
        )
    return exporter.write(output_path)


def _coerce_observation(
    value: EntityObservation | Mapping[str, object],
) -> EntityObservation:
    if isinstance(value, EntityObservation):
        return value
    if isinstance(value, Mapping):
        return EntityObservation.from_mapping(value)
    raise ExportContractError(
        "entities must contain EntityObservation or mapping values"
    )


def _normalize_coordinate(
    value: object, *, maximum: float, tolerance: float
) -> tuple[float, bool] | None:
    numeric = _finite_float(value)
    if numeric is None:
        raise ExportContractError("entity coordinates must be finite numbers")
    if numeric < -tolerance or numeric > maximum + tolerance:
        return None
    clamped = min(max(numeric, 0.0), maximum)
    return clamped, clamped != numeric


def _round_coordinate(value: float) -> float:
    rounded = round(value, 2)
    return 0.0 if rounded == 0 else rounded


def _compact_number(value: float) -> int | float:
    return int(value) if value.is_integer() else value


def _is_number(value: object) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _finite_float(value: object) -> float | None:
    if not _is_number(value):
        return None
    try:
        numeric = float(value)
    except (OverflowError, ValueError):
        return None
    return numeric if math.isfinite(numeric) else None


def _is_integer(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _validate_generated_payload(payload: Mapping[str, object]) -> None:
    """Catch programming regressions before an invalid file replaces a valid one."""
    try:
        from validate_output import validate_payload
    except ImportError:  # pragma: no cover - package import used by downstream callers
        from .validate_output import validate_payload

    report = validate_payload(payload)
    if report.errors:
        details = "; ".join(issue.message for issue in report.errors[:3])
        raise ExportContractError(f"Generated payload failed validation: {details}")


def _parse_cli_input(
    path: str | Path,
) -> tuple[dict[str, object], list[FrameObservation]]:
    try:
        payload = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ExportContractError(
            f"Could not read observation input: {error}"
        ) from error
    if not isinstance(payload, dict):
        raise ExportContractError("Observation input root must be an object")

    required = {"video_name", "frame_rate", "resolution", "frames"}
    if set(payload) != required:
        raise ExportContractError(
            f"Observation input fields must be exactly {sorted(required)}"
        )
    resolution = payload["resolution"]
    if not isinstance(resolution, dict) or set(resolution) != {"width", "height"}:
        raise ExportContractError(
            "Observation resolution needs exactly width and height"
        )
    raw_frames = payload["frames"]
    if not isinstance(raw_frames, list):
        raise ExportContractError("Observation frames must be a list")

    frames = []
    for index, raw_frame in enumerate(raw_frames, start=1):
        if not isinstance(raw_frame, dict):
            raise ExportContractError(f"Observation frame {index} must be an object")
        if set(raw_frame) - {"entities", "calibration_reliable"}:
            raise ExportContractError(
                f"Observation frame {index} has unexpected fields"
            )
        entities = raw_frame.get("entities", [])
        if not isinstance(entities, list):
            raise ExportContractError(
                f"Observation frame {index} entities must be a list"
            )
        frames.append(
            FrameObservation(
                entities=entities,
                calibration_reliable=raw_frame.get("calibration_reliable", True),
            )
        )

    metadata = {
        "video_name": payload["video_name"],
        "frame_rate": payload["frame_rate"],
        "resolution": (resolution["width"], resolution["height"]),
    }
    return metadata, frames


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export calibrated observations using the CV-to-Java v1 contract"
    )
    parser.add_argument("--input", required=True, help="Intermediate observation JSON")
    parser.add_argument("--output", required=True, help="Destination tracking JSON")
    parser.add_argument(
        "--team-ids-zero-based",
        action="store_true",
        help="Convert classifier team ids 0/1 into contract ids 1/2",
    )
    parser.add_argument(
        "--clamp-tolerance-metres",
        type=float,
        default=DEFAULT_CLAMP_TOLERANCE_METRES,
    )
    return parser.parse_args()


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    args = parse_args()
    try:
        metadata, frames = _parse_cli_input(args.input)
        summary = export_tracking_json(
            output_path=args.output,
            video_name=metadata["video_name"],
            frame_rate=metadata["frame_rate"],
            resolution=metadata["resolution"],
            frames=frames,
            clamp_tolerance_metres=args.clamp_tolerance_metres,
            team_ids_zero_based=args.team_ids_zero_based,
        )
    except ExportContractError as error:
        print(f"ERROR: {error}")
        return 2
    print(summary.format())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
