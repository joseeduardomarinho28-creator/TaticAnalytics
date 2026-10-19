import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from exporter import (
    EntityObservation,
    ExportContractError,
    FrameObservation,
    TrackingJsonExporter,
    export_tracking_json,
)
from validate_output import validate_file


class TrackingJsonExporterTest(unittest.TestCase):
    def setUp(self):
        self.exporter = TrackingJsonExporter(
            video_name="trecho_mvp.mp4",
            frame_rate=30,
            resolution=(1920, 1080),
        )

    def test_exports_exact_contract_and_empty_frames(self):
        self.exporter.add_frame(
            [
                EntityObservation(99, "ball", None, 38.234, 30.456),
                EntityObservation(7, "player", 1, 30.234, 12.806),
                EntityObservation(3, "referee", None, 48.0, 40.1),
            ]
        )
        self.exporter.add_frame([])
        self.exporter.add_frame(
            [EntityObservation(7, "player", 1, 30.3, 12.8)],
            calibration_reliable=False,
        )

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "custom-name.json"
            summary = self.exporter.write(output)
            with output.open(encoding="utf-8") as stream:
                payload = json.load(stream)
            report = validate_file(output)

        self.assertEqual({"match_info", "frames"}, set(payload))
        self.assertEqual(
            {"video_name", "frame_rate", "resolution"},
            set(payload["match_info"]),
        )
        self.assertEqual([1, 2, 3], [frame["frame_id"] for frame in payload["frames"]])
        self.assertEqual([0.033, 0.067, 0.1], [frame["timestamp"] for frame in payload["frames"]])
        self.assertEqual([], payload["frames"][1]["entities"])
        self.assertEqual([], payload["frames"][2]["entities"])
        self.assertEqual(
            {"id": 0, "type": "ball", "team_id": None, "x": 38.23, "y": 30.46},
            payload["frames"][0]["entities"][0],
        )
        self.assertEqual(1003, payload["frames"][0]["entities"][2]["id"])
        self.assertEqual(3, summary.total_frames)
        self.assertEqual(2, summary.empty_frames)
        self.assertGreater(summary.file_size_bytes, 0)
        self.assertTrue(report.is_valid, report.format())

    def test_clamps_small_violations_and_discards_large_ones(self):
        self.exporter.add_frame(
            [
                EntityObservation(1, "player", 1, -0.4, 68.7),
                EntityObservation(2, "player", 2, 107.01, 30.0),
                EntityObservation(3, "player", 2, 50.0, -2.01),
            ]
        )

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "tracking.json"
            summary = self.exporter.write(output)
            payload = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(1, len(payload["frames"][0]["entities"]))
        self.assertEqual(0.0, payload["frames"][0]["entities"][0]["x"])
        self.assertEqual(68.0, payload["frames"][0]["entities"][0]["y"])
        self.assertEqual(2, summary.clamped_coordinates)
        self.assertEqual(2, summary.discarded_entities)

    def test_converts_zero_based_team_ids_when_explicitly_requested(self):
        exporter = TrackingJsonExporter(
            video_name="video.mp4",
            frame_rate=25,
            resolution=(1280, 720),
            team_ids_zero_based=True,
        )
        exporter.add_frame(
            [
                EntityObservation(1, "player", 0, 10.0, 10.0),
                EntityObservation(2, "player", 1, 20.0, 20.0),
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "tracking.json"
            exporter.write(output)
            payload = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual([1, 2], [entity["team_id"] for entity in payload["frames"][0]["entities"]])

    def test_rejects_duplicate_ids_after_normalization(self):
        with self.assertRaisesRegex(ExportContractError, "duplicated"):
            self.exporter.add_frame(
                [
                    EntityObservation(1, "player", 1, 10.0, 10.0),
                    EntityObservation(1, "player", 2, 20.0, 20.0),
                ]
            )

    def test_rejected_frame_does_not_mutate_export_state(self):
        with self.assertRaises(ExportContractError):
            self.exporter.add_frame(
                [
                    EntityObservation(1, "player", 1, -0.5, 10.0),
                    EntityObservation(1, "player", 2, 20.0, 20.0),
                ]
            )
        self.exporter.add_frame([EntityObservation(2, "player", 2, 20.0, 20.0)])
        with tempfile.TemporaryDirectory() as directory:
            summary = self.exporter.write(Path(directory) / "tracking.json")
        self.assertEqual(1, summary.total_frames)
        self.assertEqual(0, summary.clamped_coordinates)
        self.assertEqual(1, summary.unique_entity_ids)

    def test_rejects_multiple_balls(self):
        with self.assertRaisesRegex(ExportContractError, "at most one ball"):
            self.exporter.add_frame(
                [
                    EntityObservation(8, "ball", None, 10.0, 10.0),
                    EntityObservation(9, "ball", None, 20.0, 20.0),
                ]
            )

    def test_rejects_invalid_identity_and_non_finite_coordinates(self):
        invalid = (
            EntityObservation(0, "player", 1, 10.0, 10.0),
            EntityObservation(1, "player", 0, 10.0, 10.0),
            EntityObservation(1, "coach", None, 10.0, 10.0),
            EntityObservation(1, "player", 1, float("nan"), 10.0),
            EntityObservation(1, "player", 1, 10**1000, 10.0),
            EntityObservation(1, "referee", 1, 10.0, 10.0),
        )
        for entity in invalid:
            with self.subTest(entity=entity), self.assertRaises(ExportContractError):
                exporter = TrackingJsonExporter(
                    video_name="video.mp4",
                    frame_rate=30,
                    resolution=(1920, 1080),
                )
                exporter.add_frame([entity])

    def test_rejects_unknown_observation_fields(self):
        with self.assertRaisesRegex(ExportContractError, "unexpected"):
            self.exporter.add_frame(
                [
                    {
                        "tracker_id": 1,
                        "type": "player",
                        "team_id": 1,
                        "x": 10,
                        "y": 20,
                        "confidence": 0.9,
                    }
                ]
            )

    def test_requires_at_least_one_frame(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            self.assertRaisesRegex(ExportContractError, "At least one frame"),
        ):
            self.exporter.write(Path(directory) / "tracking.json")

    def test_rejects_missing_decoded_frames_when_expected_count_is_known(self):
        exporter = TrackingJsonExporter(
            video_name="video.mp4",
            frame_rate=30,
            resolution=(1920, 1080),
            expected_total_frames=2,
        )
        exporter.add_frame([])
        with (
            tempfile.TemporaryDirectory() as directory,
            self.assertRaisesRegex(ExportContractError, "expected 2, got 1"),
        ):
            exporter.write(Path(directory) / "tracking.json")

    def test_failed_final_validation_preserves_existing_output(self):
        exporter = TrackingJsonExporter(
            video_name="video.mp4",
            frame_rate=30,
            resolution=(1920, 1080),
        )
        exporter.add_frame([EntityObservation(1, "player", 1, 10.0, 10.0)])
        exporter.add_frame([EntityObservation(1, "player", 1, 11.0, 10.0)])
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "tracking.json"
            output.write_text("existing valid artifact", encoding="utf-8")
            with self.assertRaisesRegex(ExportContractError, "failed validation"):
                exporter.write(output)
            preserved = output.read_text(encoding="utf-8")
        self.assertEqual("existing valid artifact", preserved)

    def test_convenience_api_accepts_generators(self):
        frames = (
            FrameObservation([EntityObservation(index, "player", 1, index, 10.0)])
            for index in range(1, 4)
        )
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "nested" / "result.json"
            summary = export_tracking_json(
                output_path=output,
                video_name="video.mp4",
                frame_rate=30,
                resolution=(1920, 1080),
                frames=frames,
            )
            self.assertTrue(output.exists())
        self.assertEqual(3, summary.total_frames)

    def test_cli_exports_to_configurable_destination(self):
        input_payload = {
            "video_name": "video.mp4",
            "frame_rate": 30,
            "resolution": {"width": 1920, "height": 1080},
            "frames": [
                {
                    "entities": [
                        {
                            "tracker_id": 4,
                            "type": "player",
                            "team_id": 0,
                            "x": 10.0,
                            "y": 20.0,
                        }
                    ]
                },
                {"calibration_reliable": False, "entities": []},
            ],
        }
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "observations.json"
            output = Path(directory) / "chosen-output.json"
            source.write_text(json.dumps(input_payload), encoding="utf-8")
            process = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).parents[1] / "src" / "exporter.py"),
                    "--input",
                    str(source),
                    "--output",
                    str(output),
                    "--team-ids-zero-based",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            payload = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(0, process.returncode, process.stderr)
        self.assertIn("Total frames: 2", process.stdout)
        self.assertEqual(1, payload["frames"][0]["entities"][0]["team_id"])


if __name__ == "__main__":
    unittest.main()
