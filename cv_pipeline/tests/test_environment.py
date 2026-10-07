from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "scripts"))

from check_environment import pinned_versions


class EnvironmentCheckTest(unittest.TestCase):
    def test_reads_exact_pins_and_normalizes_package_names(self):
        with tempfile.TemporaryDirectory() as directory:
            requirements = Path(directory) / "requirements.txt"
            requirements.write_text(
                "# frozen dependencies\nNumPy==2.4.6\nopencv-python==4.14.0.94\n",
                encoding="utf-8",
            )
            pins = pinned_versions(requirements)

        self.assertEqual("2.4.6", pins["numpy"])
        self.assertEqual("4.14.0.94", pins["opencv-python"])

    def test_ignores_comments_blank_lines_and_non_pinned_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            requirements = Path(directory) / "requirements.txt"
            requirements.write_text("\n# comment\npackage>=1.0\n-r base.txt\n", encoding="utf-8")
            pins = pinned_versions(requirements)

        self.assertEqual({}, pins)


if __name__ == "__main__":
    unittest.main()
