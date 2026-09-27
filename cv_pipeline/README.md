# TaticAnalytics CV Pipeline

## Field calibration (TA-58)

The calibration converts image pixels into the 105 x 68 metre coordinate
system consumed by the Java core. The origin is the top-left field corner as
seen by the main camera; x grows to the right and y grows downward.

Install the calibration dependencies in a virtual environment:

```bash
python -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install numpy opencv-python
```

Choose six to eight visible, well-spread field references (quality A whenever
possible), then run:

```bash
python src/calibrate_click.py frame.jpg \
  --output output/calibration-keyframe-001.json \
  --frame-index 1
```

The tool lists every named reference when `--points` is omitted. Enter the
selected names, then click each location in the requested order. Press `U` to
undo, `Esc` to cancel, or `Enter` after all points are captured.

The generated JSON records the source frame, clicked pixel positions, official
metre positions, pixel-to-metre matrix, RANSAC inliers and error per point. An
overlay image is also generated next to it. Always inspect that image: the
touchlines, halfway line, penalty areas, centre circle and red `(0,0)` marker
must align with the photographed field. This catches mirrored calibrations that
distance-only checks cannot detect.

Re-click any point whose error is much larger than the others. A 1 metre RANSAC
threshold is used by default. Four points are the mathematical minimum, but the
normal workflow enforces six to eight; `--allow-four` is reserved for frames
where additional trustworthy references are unavailable.

For detections, transform the bottom-centre of the bounding box (the player's
feet), never the box centre:

```python
from calibration import bbox_foot_to_metre, load_calibration

calibration = load_calibration("output/calibration-keyframe-001.json")
x_m, y_m = bbox_foot_to_metre(calibration.matrix, x1, y1, x2, y2)
```

Run the automated checks with:

```bash
python -m unittest discover -s tests -v
```

## Tracking JSON export (TA-60)

`src/exporter.py` is the contract boundary between the Python pipeline and the
Java core. It accepts one observation list for every decoded video frame and
generates the frame ids and timestamps itself. Call `add_frame` even when there
are no detections; pass `calibration_reliable=False` when that frame cannot be
converted safely to metres.

```python
from exporter import EntityObservation, TrackingJsonExporter

exporter = TrackingJsonExporter(
    video_name="trecho_mvp.mp4",
    frame_rate=30,
    resolution=(1920, 1080),
    expected_total_frames=900,
)

exporter.add_frame(
    [
        EntityObservation(0, "ball", None, 38.2, 30.5),
        EntityObservation(7, "player", 1, 30.2, 12.8),
        EntityObservation(3, "referee", None, 48.0, 40.1),
    ]
)
exporter.add_frame([])
summary = exporter.write("output/tracking.json")
print(summary.format())
```

Input positions must already be calibrated in field metres. The exporter:

- creates consecutive frame ids from 1 and timestamps rounded to 3 decimals;
- optionally verifies the final count against `expected_total_frames`, catching
  accidental frame sampling before writing;
- emits `entities: []` for empty or unreliably calibrated frames;
- converts every ball id to `0` and referee tracker ids to `1000 + tracker_id`;
- optionally converts zero-based team classes with `team_ids_zero_based=True`;
- rounds positions to 2 decimals, clamps deviations up to 2 metres to the
  105 × 68 metre field, and discards entities farther outside it;
- rejects duplicate ids, multiple balls, invalid teams, unknown types and
  unexpected input fields;
- validates the complete payload before replacing the output atomically.

For a command-line integration, prepare an intermediate observation JSON with
exactly `video_name`, `frame_rate`, `resolution` and `frames`. Each frame accepts
`entities` and optional `calibration_reliable`; each observation has
`tracker_id`, `type`, `team_id`, `x` and `y`:

```bash
python src/exporter.py \
  --input observations.json \
  --output output/tracking.json \
  --team-ids-zero-based
```

The output filename is always supplied by the caller. The final summary reports
total and empty frames, clamped coordinates, discarded entities, unique ids and
file size.

### Validate before Java consumes the file

Run the independent contract validator on every generated file:

```bash
python src/validate_output.py output/tracking.json
```

It checks the exact schema, time sequence, ids, teams, field bounds, coordinate
precision, maximum player and ball counts, physically implausible movement,
repeated positions and empty-frame percentage. Errors produce exit code `1`;
warnings are reported but keep exit code `0`, making the command suitable for
automation.

### Measured file size

A representative synthetic MVP export with 900 frames and 24 entities per
frame (22 players, one ball and one referee) produced **2,997,666 bytes**, about
**2.86 MiB**, using the readable indented JSON format. The exact size of a real
clip will vary with missed detections and numeric values, but remains in the
expected few-megabyte range. Full matches may require a future contract change;
do not change the v1 root shape or fields without coordinating with the Java
side.
