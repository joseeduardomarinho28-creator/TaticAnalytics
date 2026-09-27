"""Official 105 x 68 metre football-pitch reference model.

Coordinates follow the CV/Java v1 contract: origin at the top-left corner as
seen by the main camera, x increasing to the right and y increasing downward.
"""

from dataclasses import dataclass
from math import cos, pi, sin

FIELD_LENGTH = 105.0
FIELD_WIDTH = 68.0
CENTER_X = FIELD_LENGTH / 2
CENTER_Y = FIELD_WIDTH / 2
CENTER_CIRCLE_RADIUS = 9.15


@dataclass(frozen=True)
class FieldPoint:
    name: str
    x: float
    y: float
    quality: str

    @property
    def metre(self) -> tuple[float, float]:
        return self.x, self.y


TOP_LEFT_CORNER = FieldPoint("top_left_corner", 0.0, 0.0, "A")
TOP_RIGHT_CORNER = FieldPoint("top_right_corner", 105.0, 0.0, "A")
BOTTOM_LEFT_CORNER = FieldPoint("bottom_left_corner", 0.0, 68.0, "A")
BOTTOM_RIGHT_CORNER = FieldPoint("bottom_right_corner", 105.0, 68.0, "A")
HALFWAY_TOP = FieldPoint("halfway_top", 52.5, 0.0, "A")
HALFWAY_BOTTOM = FieldPoint("halfway_bottom", 52.5, 68.0, "A")
CENTER_MARK = FieldPoint("center_mark", 52.5, 34.0, "B")
CENTER_CIRCLE_TOP = FieldPoint("center_circle_top", 52.5, 24.85, "A")
CENTER_CIRCLE_BOTTOM = FieldPoint("center_circle_bottom", 52.5, 43.15, "A")
CENTER_CIRCLE_LEFT = FieldPoint("center_circle_left", 43.35, 34.0, "C")
CENTER_CIRCLE_RIGHT = FieldPoint("center_circle_right", 61.65, 34.0, "C")

LEFT_PENALTY_TOP_GOAL_LINE = FieldPoint("left_penalty_top_goal_line", 0.0, 13.84, "A")
LEFT_PENALTY_TOP_INNER = FieldPoint("left_penalty_top_inner", 16.5, 13.84, "A")
LEFT_PENALTY_BOTTOM_INNER = FieldPoint("left_penalty_bottom_inner", 16.5, 54.16, "A")
LEFT_PENALTY_BOTTOM_GOAL_LINE = FieldPoint("left_penalty_bottom_goal_line", 0.0, 54.16, "A")
LEFT_GOAL_AREA_TOP_GOAL_LINE = FieldPoint("left_goal_area_top_goal_line", 0.0, 24.84, "A")
LEFT_GOAL_AREA_TOP_INNER = FieldPoint("left_goal_area_top_inner", 5.5, 24.84, "A")
LEFT_GOAL_AREA_BOTTOM_INNER = FieldPoint("left_goal_area_bottom_inner", 5.5, 43.16, "A")
LEFT_GOAL_AREA_BOTTOM_GOAL_LINE = FieldPoint("left_goal_area_bottom_goal_line", 0.0, 43.16, "A")
LEFT_PENALTY_MARK = FieldPoint("left_penalty_mark", 11.0, 34.0, "B")
LEFT_ARC_TOP = FieldPoint("left_arc_top", 16.5, 26.69, "A")
LEFT_ARC_BOTTOM = FieldPoint("left_arc_bottom", 16.5, 41.31, "A")
LEFT_ARC_CENTER = FieldPoint("left_arc_center", 20.15, 34.0, "B")
LEFT_GOALPOST_TOP = FieldPoint("left_goalpost_top", 0.0, 30.34, "B")
LEFT_GOALPOST_BOTTOM = FieldPoint("left_goalpost_bottom", 0.0, 37.66, "B")

RIGHT_PENALTY_TOP_GOAL_LINE = FieldPoint("right_penalty_top_goal_line", 105.0, 13.84, "A")
RIGHT_PENALTY_TOP_INNER = FieldPoint("right_penalty_top_inner", 88.5, 13.84, "A")
RIGHT_PENALTY_BOTTOM_INNER = FieldPoint("right_penalty_bottom_inner", 88.5, 54.16, "A")
RIGHT_PENALTY_BOTTOM_GOAL_LINE = FieldPoint("right_penalty_bottom_goal_line", 105.0, 54.16, "A")
RIGHT_GOAL_AREA_TOP_GOAL_LINE = FieldPoint("right_goal_area_top_goal_line", 105.0, 24.84, "A")
RIGHT_GOAL_AREA_TOP_INNER = FieldPoint("right_goal_area_top_inner", 99.5, 24.84, "A")
RIGHT_GOAL_AREA_BOTTOM_INNER = FieldPoint("right_goal_area_bottom_inner", 99.5, 43.16, "A")
RIGHT_GOAL_AREA_BOTTOM_GOAL_LINE = FieldPoint("right_goal_area_bottom_goal_line", 105.0, 43.16, "A")
RIGHT_PENALTY_MARK = FieldPoint("right_penalty_mark", 94.0, 34.0, "B")
RIGHT_ARC_TOP = FieldPoint("right_arc_top", 88.5, 26.69, "A")
RIGHT_ARC_BOTTOM = FieldPoint("right_arc_bottom", 88.5, 41.31, "A")
RIGHT_ARC_CENTER = FieldPoint("right_arc_center", 84.85, 34.0, "B")
RIGHT_GOALPOST_TOP = FieldPoint("right_goalpost_top", 105.0, 30.34, "B")
RIGHT_GOALPOST_BOTTOM = FieldPoint("right_goalpost_bottom", 105.0, 37.66, "B")

FIELD_POINTS = {
    point.name: point
    for point in (
        TOP_LEFT_CORNER, TOP_RIGHT_CORNER, BOTTOM_LEFT_CORNER, BOTTOM_RIGHT_CORNER,
        HALFWAY_TOP, HALFWAY_BOTTOM, CENTER_MARK, CENTER_CIRCLE_TOP,
        CENTER_CIRCLE_BOTTOM, CENTER_CIRCLE_LEFT, CENTER_CIRCLE_RIGHT,
        LEFT_PENALTY_TOP_GOAL_LINE, LEFT_PENALTY_TOP_INNER,
        LEFT_PENALTY_BOTTOM_INNER, LEFT_PENALTY_BOTTOM_GOAL_LINE,
        LEFT_GOAL_AREA_TOP_GOAL_LINE, LEFT_GOAL_AREA_TOP_INNER,
        LEFT_GOAL_AREA_BOTTOM_INNER, LEFT_GOAL_AREA_BOTTOM_GOAL_LINE,
        LEFT_PENALTY_MARK, LEFT_ARC_TOP, LEFT_ARC_BOTTOM, LEFT_ARC_CENTER,
        LEFT_GOALPOST_TOP, LEFT_GOALPOST_BOTTOM,
        RIGHT_PENALTY_TOP_GOAL_LINE, RIGHT_PENALTY_TOP_INNER,
        RIGHT_PENALTY_BOTTOM_INNER, RIGHT_PENALTY_BOTTOM_GOAL_LINE,
        RIGHT_GOAL_AREA_TOP_GOAL_LINE, RIGHT_GOAL_AREA_TOP_INNER,
        RIGHT_GOAL_AREA_BOTTOM_INNER, RIGHT_GOAL_AREA_BOTTOM_GOAL_LINE,
        RIGHT_PENALTY_MARK, RIGHT_ARC_TOP, RIGHT_ARC_BOTTOM, RIGHT_ARC_CENTER,
        RIGHT_GOALPOST_TOP, RIGHT_GOALPOST_BOTTOM,
    )
}


def _circle(samples: int = 96) -> list[tuple[float, float]]:
    return [
        (
            CENTER_X + CENTER_CIRCLE_RADIUS * cos(2 * pi * i / samples),
            CENTER_Y + CENTER_CIRCLE_RADIUS * sin(2 * pi * i / samples),
        )
        for i in range(samples + 1)
    ]


FIELD_POLYLINES: tuple[list[tuple[float, float]], ...] = (
    [(0.0, 0.0), (105.0, 0.0), (105.0, 68.0), (0.0, 68.0), (0.0, 0.0)],
    [(52.5, 0.0), (52.5, 68.0)],
    [(0.0, 13.84), (16.5, 13.84), (16.5, 54.16), (0.0, 54.16)],
    [(0.0, 24.84), (5.5, 24.84), (5.5, 43.16), (0.0, 43.16)],
    [(105.0, 13.84), (88.5, 13.84), (88.5, 54.16), (105.0, 54.16)],
    [(105.0, 24.84), (99.5, 24.84), (99.5, 43.16), (105.0, 43.16)],
    _circle(),
)
