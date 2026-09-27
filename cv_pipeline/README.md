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
