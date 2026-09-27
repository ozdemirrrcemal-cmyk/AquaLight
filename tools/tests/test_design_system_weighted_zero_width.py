from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from design_system_resource_guard import RAW_DIMENSION, is_weighted_zero_dimension


class WeightedZeroDimensionTest(unittest.TestCase):
    def test_weighted_width_and_height_accept_android_required_zero(self) -> None:
        weighted = (
            '<TextView android:layout_width="0dp" '
            'android:layout_weight="1" android:layout_height="wrap_content" />'
        )
        bare = '<TextView android:layout_width="0dp" android:layout_height="wrap_content" />'
        height = (
            '<TextView android:layout_width="wrap_content" '
            'android:layout_height="0dp" android:layout_weight="1" />'
        )
        self.assertTrue(is_weighted_zero_dimension(weighted, RAW_DIMENSION.search(weighted)))
        self.assertFalse(is_weighted_zero_dimension(bare, RAW_DIMENSION.search(bare)))
        self.assertTrue(is_weighted_zero_dimension(height, RAW_DIMENSION.search(height)))

    def test_other_raw_dimensions_remain_rejected(self) -> None:
        for attribute, value in (
            ('android:padding', '0dp'),
            ('android:layout_width', '1dp'),
            ('android:layout_height', '0sp'),
        ):
            with self.subTest(attribute=attribute, value=value):
                text = f'<TextView {attribute}="{value}" android:layout_weight="1" />'
                self.assertFalse(is_weighted_zero_dimension(text, RAW_DIMENSION.search(text)))


if __name__ == "__main__":
    unittest.main()
