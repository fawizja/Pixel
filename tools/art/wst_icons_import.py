#!/usr/bin/env python3
"""Ikony mikstur i ramki pol z kupionych paczek WsT (westudesign.itch.io):
"Alchemy & Potion Icons 100" i "Icon Frames & UI Slots".

    python3 tools/art/wst_icons_import.py [katalog assets]   (po otsp_import.py i wst_import.py)

Zrodla (tools/art/wst/alchemy/16/, tools/art/wst/ui_slots/, poza repo: licencja WsT zabrania rozpowszechniania
samych plikow; spakowane oryginaly w plikach projektu pixel-wst-zrodla/). Do gry trafiaja tylko gotowe elementy:
ikony 16x16 powiekszone 2x do 32x32 (assets/art/items/klucz.png) i ramki pol 22x22 (assets/ui/wst/).
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "wst")
# przedmiot w grze -> ikona paczki (zdrowie i mana w progach od najslabszej)
ICONS = {
    "health_potion": "minor_healing_potion", "great_health_potion": "healing_potion",
    "strong_health_potion": "greater_healing_potion", "supreme_health_potion": "superior_healing_potion",
    "ultimate_health_potion": "phoenix_draught",
    "mana_potion": "minor_mana_potion", "great_mana_potion": "mana_potion",
    "strong_mana_potion": "greater_mana_potion", "supreme_mana_potion": "superior_mana_potion",
    "ultimate_mana_potion": "sleeping_draught",
    "great_spirit_potion": "rejuvenation_potion", "ultimate_spirit_potion": "elixir_of_life",
    "strength_potion": "strength_potion", "stoneskin_potion": "stoneskin_potion",
    "swiftness_potion": "swiftness_potion", "luck_potion": "luck_potion",
    "mist_essence": "air_essence", "abyssal_ember": "fire_essence", "glacial_core": "water_essence",
    "shadow_essence": "quintessence", "powder_horn": "black_powder", "exp_scroll": "recipe_scroll",
    "healing_herb": "essential_oil",
}
UI_ICONS = {"icon_info": "grimoire"}
# ramki pol: (wyjscie, stan, wzor)
FRAMES = [
    ("slot", "normal", "classic_plate_neutral"), ("slot_empty", "empty", "classic_plate_neutral"),
    ("slot_selected", "selected", "classic_plate_neutral"), ("slot_pressed", "pressed", "classic_plate_neutral"),
    ("slot_common", "normal", "classic_plate_common"), ("slot_uncommon", "normal", "classic_plate_uncommon"),
    ("slot_rare", "normal", "classic_plate_rare"), ("slot_epic", "normal", "classic_plate_epic"),
    ("slot_legendary", "normal", "classic_plate_legendary"),
]


def main():
    assets = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "assets")
    icons = os.path.join(SRC, "alchemy", "16")
    frames = os.path.join(SRC, "ui_slots", "frames", "22")
    if not os.path.isdir(icons) or not os.path.isdir(frames):
        sys.exit("Brak zrodel WsT w tools/art/wst/ (rozpakuj paczki z plikow projektu pixel-wst-zrodla/)")
    n = 0
    for key, src in ICONS.items():
        img = Image.open(os.path.join(icons, src + ".png")).convert("RGBA")
        img.resize((32, 32), Image.NEAREST).save(os.path.join(assets, "art", "items", key + ".png"))
        n += 1
    out = os.path.join(assets, "ui", "wst")
    os.makedirs(out, exist_ok=True)
    for name, state, design in FRAMES:
        Image.open(os.path.join(frames, state, design + ".png")).convert("RGBA").save(os.path.join(out, name + ".png"))
    # piktogramy przyciskow interfejsu (16x16, rysowane w ramce pola)
    for name, src in UI_ICONS.items():
        Image.open(os.path.join(icons, src + ".png")).convert("RGBA").save(os.path.join(out, name + ".png"))
    print(f"WsT: {n} ikon przedmiotow, {len(FRAMES)} ramek pol")


if __name__ == "__main__":
    main()
