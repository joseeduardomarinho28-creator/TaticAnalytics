import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from field_model import (
    FIELD_POINTS,
    LEFT_GOALPOST_BOTTOM,
    LEFT_GOALPOST_TOP,
    LEFT_PENALTY_BOTTOM_INNER,
    LEFT_PENALTY_MARK,
    LEFT_PENALTY_TOP_INNER,
    TOP_LEFT_CORNER,
)


class FieldModelTest(unittest.TestCase):
    def test_official_measurements(self):
        self.assertAlmostEqual(7.32, LEFT_GOALPOST_BOTTOM.y - LEFT_GOALPOST_TOP.y, places=6)
        self.assertAlmostEqual(40.32, LEFT_PENALTY_BOTTOM_INNER.y - LEFT_PENALTY_TOP_INNER.y, places=6)
        self.assertAlmostEqual(16.5, LEFT_PENALTY_TOP_INNER.x - TOP_LEFT_CORNER.x, places=6)
        self.assertAlmostEqual(11.0, LEFT_PENALTY_MARK.x - TOP_LEFT_CORNER.x, places=6)

    def test_reference_names_are_unique_and_inside_field(self):
        self.assertEqual(39, len(FIELD_POINTS))
        for name, point in FIELD_POINTS.items():
            self.assertEqual(name, point.name)
            self.assertTrue(0.0 <= point.x <= 105.0)
            self.assertTrue(0.0 <= point.y <= 68.0)


if __name__ == "__main__":
    unittest.main()
