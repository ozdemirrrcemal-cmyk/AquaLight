#!/usr/bin/env python3
from pathlib import Path

ROOT = Path.cwd()

def patch(path: str, replacements: dict[str, str]) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    for old, new in replacements.items():
        if old not in text:
            raise RuntimeError(f"{path}: missing expected text {old!r}")
        text = text.replace(old, new)
    target.write_text(text, encoding="utf-8")

patch("tools/localization_accessibility_guard.py", {
    "if width is not None and max(width,minw)<48:": "if width is not None and width > 0 and max(width,minw)<48:",
    "if height is not None and max(height,minh)<48:": "if height is not None and height > 0 and max(height,minh)<48:"
})
patch("app/src/main/res/layout/fragment_edit_profile.xml", {
    "android:layout_width=\"@dimen/aqua_size_40\"": "android:layout_width=\"@dimen/aqua_size_48\"",
    "android:layout_height=\"@dimen/aqua_size_40\"": "android:layout_height=\"@dimen/aqua_size_48\""
})
patch("app/src/main/res/layout/fragment_tank_detail_devices.xml", {
    "android:layout_height=\"@dimen/aqua_size_34\"": "android:layout_height=\"@dimen/aqua_size_48\""
})
patch("app/src/main/res/layout/fragment_tank_detail_life.xml", {
    "android:layout_height=\"@dimen/aqua_size_34\"": "android:layout_height=\"@dimen/aqua_size_48\""
})
patch("app/src/main/res/layout/fragment_tank_photo.xml", {
    "android:layout_width=\"@dimen/aqua_size_44\"": "android:layout_width=\"@dimen/aqua_size_48\"",
    "android:layout_height=\"@dimen/aqua_size_44\"": "android:layout_height=\"@dimen/aqua_size_48\""
})
patch("app/src/main/res/layout/fragment_tank_settings_basic.xml", {
    "android:layout_width=\"@dimen/aqua_size_44\"": "android:layout_width=\"@dimen/aqua_size_48\"",
    "android:layout_height=\"@dimen/aqua_size_44\"": "android:layout_height=\"@dimen/aqua_size_48\""
})
