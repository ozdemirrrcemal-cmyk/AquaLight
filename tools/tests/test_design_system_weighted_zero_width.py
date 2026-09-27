from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from design_system_resource_guard import RAW_DIMENSION, is_weighted_zero_width


class WeightedZeroWidthTest(unittest.TestCase):
    def test_only_weighted_width_accepts_android_required_zero(self) -> None:
        weighted = (
            '<TextView android:layout_width="0dp" '
            'android:layout_weight="1" android:layout_height="wrap_content" />'
        )
        bare = '<TextView android:layout_width="0dp" android:layout_height="wrap_content" />'
        height = (
            '<TextView android:layout_width="wrap_content" '
            'android:layout_height="0dp" android:layout_weight="1" />'
        )
        self.assertTrue(is_weighted_zero_width(weighted, RAW_DIMENSION.search(weighted)))
        self.assertFalse(is_weighted_zero_width(bare, RAW_DIMENSION.search(bare)))
        self.assertFalse(is_weighted_zero_width(height, RAW_DIMENSION.search(height)))


if __name__ == "__main__":
    unittest.main()
