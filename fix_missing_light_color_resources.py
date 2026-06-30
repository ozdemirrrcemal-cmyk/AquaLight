#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path.cwd()
colors_path = ROOT / "app/src/main/res/values/colors.xml"
res_root = ROOT / "app/src/main/res"

if not colors_path.exists():
    raise SystemExit("ERROR: app/src/main/res/values/colors.xml bulunamadı. Repo kökünde çalıştır.")

palette = {
    "light_gold": "#FFD166",
    "light_text_tertiary": "#8EA0B8",
    "light_accent": "#64FFDA",
    "light_accent_soft": "#2264FFDA",

    "light_channel_blue": "#4DA3FF",
    "light_channel_green": "#5CDB95",
    "light_channel_red": "#FF6B6B",
    "light_channel_white": "#F4F7FF",

    "light_chart_panel_top": "#1B3658",
    "light_chart_panel_middle": "#162C48",
    "light_chart_panel_bottom": "#10243B",
    "light_chart_panel_stroke": "#33506F",

    "light_header_gradient_top": "#1C3B63",
    "light_header_gradient_bottom": "#10243B",

    "light_action_blue_start": "#2F80ED",
    "light_action_blue_end": "#56CCF2",
    "light_action_blue_stroke": "#7DCBFF",

    "light_action_gold_start": "#F2C94C",
    "light_action_gold_end": "#F2994A",
    "light_action_gold_stroke": "#FFE08A",

    "light_action_green_start": "#27AE60",
    "light_action_green_end": "#6FCF97",
    "light_action_green_stroke": "#8FE6B0",

    "light_action_red_start": "#EB5757",
    "light_action_red_end": "#FF8A80",
    "light_action_red_stroke": "#FFB0A8",

    "light_action_purple_start": "#9B51E0",
    "light_action_purple_end": "#BB6BD9",
    "light_action_purple_stroke": "#D9A7FF",

    "light_action_cyan_start": "#00B8D9",
    "light_action_cyan_end": "#64D8FF",
    "light_action_cyan_stroke": "#9EEBFF",

    "light_card": "#172B45",
    "light_card_elevated": "#1D3656",
    "light_card_stroke": "#2E4A68",
    "light_text_primary": "#F4F7FF",
    "light_text_secondary": "#B8C7DA",
    "light_surface": "#152B45",
    "light_surface_soft": "#1C3252",
    "light_surface_deep": "#0F1318",
    "light_chip_auto": "#1A3A63",
}

ref_pattern = re.compile(r"@color/(light_[A-Za-z0-9_]+)")
defined_pattern = re.compile(r'<color\s+name="([^"]+)"\s*>')

refs = set()
for path in res_root.rglob("*.xml"):
    if path == colors_path:
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    refs.update(ref_pattern.findall(text))

colors_text = colors_path.read_text(encoding="utf-8")
defined = set(defined_pattern.findall(colors_text))
missing = sorted(name for name in refs if name not in defined)

if not missing:
    print("OK: Missing light color resource yok.")
    sys.exit(0)

unknown = [name for name in missing if name not in palette]
if unknown:
    print("ERROR: Palette içinde olmayan light color referansları var:")
    for name in unknown:
        print(f"  - {name}")
    print("\nBu isimler için renk değeri eklenmeden colors.xml yazılmadı.")
    sys.exit(2)

block_lines = [
    "",
    "    <!-- Light device module palette -->",
]
for name in missing:
    block_lines.append(f'    <color name="{name}">{palette[name]}</color>')
block_lines.append("")

insert_block = "\n".join(block_lines)

if "</resources>" not in colors_text:
    raise SystemExit("ERROR: colors.xml içinde </resources> bulunamadı.")

colors_text = colors_text.replace("\n</resources>", insert_block + "\n</resources>", 1)
colors_path.write_text(colors_text, encoding="utf-8", newline="\n")

print("OK: colors.xml içine eksik light color resource tanımları eklendi:")
for name in missing:
    print(f"  - {name} = {palette[name]}")
