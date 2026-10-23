#!/usr/bin/env python3
"""Autokafelki podloza z paczki "Free 32X32 tiles and borders - Top down" (WsT, westudesign.itch.io).

    python3 tools/art/wst_import.py [katalog assets/art]   (po otsp_import.py: nadpisuje tiles/ tych podlozy)

Arkusze zrodlowe (tools/art/wst/, poza repo: licencja WsT zabrania rozpowszechniania samych plikow; kopia w
plikach projektu pixel-wst-zrodla/) maja jeden uklad 5x5 pol po 32 px: duzy ksztalt z wcieciem (rogi wewnetrzne),
pojedyncze pole (rogi zewnetrzne) i blok 2x2 (krawedzie i srodek). Z nich wycinane sa cwiartki 16x16 do atlasu
autotiles/klucz.png (5 kolumn x 4 wiersze): wiersz = cwiartka pola (TL, TR, BL, BR), kolumna = rodzaj
(0 rog zewnetrzny, 1 krawedz od gory/dolu, 2 krawedz z boku, 3 rog wewnetrzny, 4 srodek). Renderer sklada pole
z czterech cwiartek wg sasiadow (WorldRenderer). Pelne pole podloza (tiles/klucz.png) to cztery cwiartki srodka.
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
# podloze w grze -> arkusz paczki
SHEETS = {
    "grass": "tilegrass5", "jungle_grass": "tilegrass1", "field": "tilegrass3", "blighted_grass": "tilegrass4",
    "swamp": "tilegrass2", "dirt": "tiledirt", "sand": "sandtile", "dark_sand": "sanddirt", "snow": "snowtile2",
    "ice": "ice", "cobblestone": "tilestone", "abyss_floor": "tilestone3",
}
# zrodlo kazdej cwiartki: (kolumna, wiersz) pola w ukladzie 5x5; S = pojedyncze pole
S = (0, 4)
SOURCES = {
    "TL": (S, (4, 3), (3, 4), (3, 1), (4, 4)),
    "TR": (S, (3, 3), (4, 4), (1, 1), (3, 4)),
    "BL": (S, (4, 4), (3, 3), (1, 2), (4, 3)),
    "BR": (S, (3, 4), (4, 3), (2, 1), (3, 3)),
}
QUAD = {"TL": (0, 0), "TR": (16, 0), "BL": (0, 16), "BR": (16, 16)}
# przesuniecie okna zrodla na odslonieta strone (arkusze 192 px maja obwodke wystajaca 2 px poza pole)
EXPOSED = {
    "TL": ((-1, -1), (0, -1), (-1, 0)), "TR": ((1, -1), (0, -1), (1, 0)),
    "BL": ((-1, 1), (0, 1), (-1, 0)), "BR": ((1, 1), (0, 1), (1, 0)),
}


def quadrant(sheet, origin, shift, cell, q, kind):
    qx, qy = QUAD[q]
    dx, dy = EXPOSED[q][kind] if kind < 3 else (0, 0)
    x = origin + cell[0] * 32 + qx + dx * shift
    y = origin + cell[1] * 32 + qy + dy * shift
    return sheet.crop((x, y, x + 16, y + 16))


def main(art):
    os.makedirs(os.path.join(art, "autotiles"), exist_ok=True)
    for key, name in SHEETS.items():
        sheet = Image.open(os.path.join(HERE, "wst", name + ".png")).convert("RGBA")
        origin = 16 if sheet.width == 192 else 48
        shift = max(0, origin - sheet.getbbox()[0])   # obwodka wystajaca poza pole (piasek); lod i trawy maja ja w srodku
        atlas = Image.new("RGBA", (80, 64), (0, 0, 0, 0))
        full = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        for row, q in enumerate(("TL", "TR", "BL", "BR")):
            for kind, cell in enumerate(SOURCES[q]):
                atlas.alpha_composite(quadrant(sheet, origin, shift, cell, q, kind), (kind * 16, row * 16))
            full.alpha_composite(quadrant(sheet, origin, shift, SOURCES[q][4], q, 4), QUAD[q])
        atlas.save(os.path.join(art, "autotiles", key + ".png"), optimize=True)
        full.save(os.path.join(art, "tiles", key + ".png"), optimize=True)
    print("Zapisano", len(SHEETS), "autokafelkow do", art)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "assets", "art"))
