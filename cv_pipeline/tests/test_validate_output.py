import json
import subprocess
import sys
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from validate_output import validate_file, validate_payload


def valid_payload():
    return {
        "match_info": {
            "video_name": "trecho_mvp.mp4",
            "frame_rate": 30,
            "resolution": {"width": 1920, "height": 1080},
        },
        "frames": [
            {
                "frame_id": 1,
                "timestamp": 0.033,
                "entities": [
                    {"id": 0, "type": "ball", "team_id": None, "x": 38.2, "y": 30.5},
                    {"id": 7, "type": "player", "team_id": 1, "x": 30.2, "y": 12.8},
                    {
                        "id": 1003,
                        "type": "referee",
                        "team_id": None,
                        "x": 48.0,
                        "y": 40.1,
                    },
                ],
            },
            {"frame_id": 2, "timestamp": 0.067, "entities": []},
        ],
    }


class ValidateOutputTest(unittest.TestCase):
    def assert_error(self, payload, code):
        report = validate_payload(payload)
        self.assertIn(code, {issue.code for issue in report.errors}, report.format())

    def test_accepts_valid_contract_and_warns_about_empty_frame(self):
        report = validate_payload(valid_payload())
        self.assertTrue(report.is_valid, report.format())
        self.assertIn("quality.empty_frames", {issue.code for issue in report.warnings})
        self.assertEqual(2, report.metrics["total_frames"])
        self.assertEqual(1, report.metrics["maximum_players_per_frame"])

    def test_detects_pixel_coordinates(self):
        payload = valid_payload()
        payload["frames"][0]["entities"][0]["x"] = 960.5
        self.assert_error(payload, "entity.x_bounds")

    def test_detects_frame_id_gap(self):
        payload = valid_payload()
        payload["frames"][1]["frame_id"] = 3
        self.assert_error(payload, "frame.id_sequence")

    def test_detects_duplicate_id_between_types(self):
        payload = valid_payload()
        payload["frames"][0]["entities"][2]["id"] = 7
        self.assert_error(payload, "entity.duplicate_id")

    def test_detects_extra_field_at_every_level(self):
        changes = (
            lambda payload: payload.update({"metadata": {}}),
            lambda payload: payload["match_info"].update({"duration": 30}),
            lambda payload: payload["match_info"]["resolution"].update({"channels": 3}),
            lambda payload: payload["frames"][0].update({"confidence": 0.9}),
            lambda payload: payload["frames"][0]["entities"][0].update({"confidence": 0.9}),
        )
        for change in changes:
            with self.subTest(change=change):
                payload = valid_payload()
                change(payload)
                self.assert_error(payload, "structure.extra_fields")

    def test_detects_entity_without_x(self):
        payload = valid_payload()
        del payload["frames"][0]["entities"][1]["x"]
        self.assert_error(payload, "structure.missing_fields")

    def test_detects_invalid_timestamp_and_null_entities(self):
        payload = valid_payload()
        payload["frames"][0]["timestamp"] = 1.0
        payload["frames"][1]["entities"] = None
        report = validate_payload(payload)
        codes = {issue.code for issue in report.errors}
        self.assertIn("frame.timestamp_value", codes)
        self.assertIn("frame.entities_type", codes)

    def test_detects_team_id_type_and_id_rules(self):
        cases = (
            ("player", 0, 0, "entity.player_team"),
            ("ball", 5, None, "entity.ball_id"),
            ("ball", 0, 1, "entity.ball_team"),
            ("referee", 999, None, "entity.referee_id"),
            ("referee", 1000, 2, "entity.referee_team"),
            ("coach", 3, None, "entity.type"),
            (["player"], 3, 1, "entity.type"),
        )
        for entity_type, entity_id, team_id, expected_code in cases:
            with self.subTest(expected_code=expected_code):
                payload = valid_payload()
                payload["frames"][0]["entities"] = [
                    {
                        "id": entity_id,
                        "type": entity_type,
                        "team_id": team_id,
                        "x": 10.0,
                        "y": 20.0,
                    }
                ]
                self.assert_error(payload, expected_code)

    def test_detects_multiple_balls_and_too_many_players(self):
        payload = valid_payload()
        payload["frames"][0]["entities"] = [
            {"id": 0, "type": "ball", "team_id": None, "x": 10.0, "y": 10.0},
            {"id": 0, "type": "ball", "team_id": None, "x": 11.0, "y": 11.0},
            *[
                {"id": index, "type": "player", "team_id": 1, "x": 20.0, "y": 20.0}
                for index in range(1, 24)
            ],
        ]
        report = validate_payload(payload)
        codes = {issue.code for issue in report.errors}
        self.assertIn("entity.multiple_balls", codes)
        self.assertIn("entity.too_many_players", codes)

    def test_detects_implausible_speed_and_repeated_position(self):
        payload = valid_payload()
        payload["frames"][0]["entities"] = [
            {"id": 7, "type": "player", "team_id": 1, "x": 10.0, "y": 10.0},
            {"id": 8, "type": "player", "team_id": 1, "x": 20.0, "y": 20.0},
        ]
        payload["frames"][1]["entities"] = [
            {"id": 7, "type": "player", "team_id": 1, "x": 11.0, "y": 10.0},
            {"id": 8, "type": "player", "team_id": 1, "x": 20.0, "y": 20.0},
        ]
        report = validate_payload(payload)
        self.assertIn("physical.speed", {issue.code for issue in report.errors})
        self.assertIn(
            "quality.repeated_position",
            {issue.code for issue in report.warnings},
        )

    def test_warns_about_coordinate_precision(self):
        payload = valid_payload()
        payload["frames"][0]["entities"][0]["x"] = 38.234
        report = validate_payload(payload)
        self.assertTrue(report.is_valid, report.format())
        self.assertIn(
            "quality.coordinate_precision",
            {issue.code for issue in report.warnings},
        )

    def test_rejects_non_json_and_empty_frames(self):
        with tempfile.TemporaryDirectory() as directory:
            invalid_json = Path(directory) / "invalid.json"
            invalid_json.write_text("{invalid", encoding="utf-8")
            report = validate_file(invalid_json)
        self.assert_error({"match_info": {}, "frames": []}, "structure.frames_empty")
        self.assertIn("json.syntax", {issue.code for issue in report.errors})

    def test_cli_exit_codes_reflect_validation_result(self):
        script = str(Path(__file__).parents[1] / "src" / "validate_output.py")
        with tempfile.TemporaryDirectory() as directory:
            valid_path = Path(directory) / "valid.json"
            invalid_path = Path(directory) / "invalid.json"
            valid_path.write_text(json.dumps(valid_payload()), encoding="utf-8")
            invalid_payload = deepcopy(valid_payload())
            invalid_payload["frames"][0]["entities"][0]["x"] = 960
            invalid_path.write_text(json.dumps(invalid_payload), encoding="utf-8")
            valid_process = subprocess.run(
                [sys.executable, script, str(valid_path)],
                capture_output=True,
                text=True,
                check=False,
            )
            invalid_process = subprocess.run(
                [sys.executable, script, str(invalid_path)],
                capture_output=True,
                text=True,
                check=False,
            )
        self.assertEqual(0, valid_process.returncode, valid_process.stdout)
        self.assertEqual(1, invalid_process.returncode, invalid_process.stdout)
        self.assertIn("Tracking JSON validation: INVALID", invalid_process.stdout)


if __name__ == "__main__":
    unittest.main()
