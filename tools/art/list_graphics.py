#!/usr/bin/env python3
"""Generuje docs/GRAPHICS.md: pelna lista nazw plikow PNG, ktore podmieniaja grafiki gry (assets/art/)."""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DATA = os.path.join(ROOT, "shared/src/main/resources/data")
WORLD = os.path.join(ROOT, "shared/src/main/resources/maps/world")


def rows(path):
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line and not line.startswith("#"):
                yield line.split(";")


def has(folder, key):
    return os.path.exists(os.path.join(ROOT, "assets/art", folder, key + ".png"))


def section(title, folder, entries, note=""):
    out = [f"## {title} (`assets/art/{folder}/`)", ""]
    if note:
        out += [note, ""]
    out += ["| Plik | Co to jest | Jest |", "|---|---|---|"]
    out += [f"| `{folder}/{k}.png` | {d} | {'tak' if has(folder, k) else ''} |" for k, d in entries]
    return out + [""]


tiles = [(r[1], f"{r[2].lower()}, {r[5]}") for r in rows(os.path.join(DATA, "tiles.csv"))]
items = sorted(((r[1], f"{r[2]} ({r[3].lower()})") for r in rows(os.path.join(DATA, "items.csv"))), key=lambda x: x[0])
monsters = sorted((r[0], f"{r[1]}, poziom {r[2]}" + (", boss" if len(r) > 17 and r[17] == "1" else "")) for r in rows(os.path.join(DATA, "monsters.csv")))
npcs = sorted((r[0], r[1]) for r in rows(os.path.join(WORLD, "npcs.csv")))
dirs = [("down", "przód"), ("up", "tył"), ("left", "w lewo"), ("right", "w prawo")]
chars = [(f"{v}_{d}", f"{name}, {dn}") for v, name in [("paladin", "Paladyn"), ("druid", "Druid"), ("sorcerer", "Czarodziej"), ("monk", "Mnich")] for d, dn in dirs]
objects = [("chest_closed", "skrzynia skarbów, zamknięta"), ("chest_open", "skrzynia skarbów, otwarta"),
           ("training_dummy", "manekin treningowy"), ("spike_trap", "pułapka z kolcami"),
           ("roof_red", "dach z dachówki (drewniane domy)"), ("roof_slate", "dach z łupku (kamienne budynki)"),
           ("roof_sand", "dach z piaskowej dachówki (budynki z piaskowca)"),
           ("horse_right", "koń, w prawo"), ("horse_left", "koń, w lewo")]
pets = [("pet_dog", "szczenię"), ("pet_cat", "kot"), ("pet_raven", "kruk"), ("pet_fennec", "fenek")]

done = sum(has(f, k) for f, entries in (("tiles", tiles), ("items", items), ("monsters", monsters), ("npcs", npcs),
            ("characters", chars), ("objects", objects), ("pets", pets)) for k, _ in entries)
lines = [
    "# Grafiki do podmiany",
    "",
    "Obrazki gry są rysowane przez kod (`TileArt`). Plik PNG o nazwie z tej listy, położony w",
    "`assets/art/`, zastępuje wygenerowany obrazek; brakujące pliki zostają wygenerowane, więc można podmieniać",
    "po kawałku.",
    "",
    "- PNG z przezroczystym tłem, kwadrat. 16×16 px albo 32×32 px (większe gra zmniejsza do 32 bez rozmywania).",
    "- Kafelki podłoża wypełniają cały kwadrat; postacie, potwory i przedmioty stoją na przezroczystym tle,",
    "  stopami przy dolnej krawędzi.",
    "- Kolorowe stroje (/strój) są generowane z kolorów; profesja z podmienioną postacią zawsze wygląda jak na grafice.",
    "- Grafiki muszą być własne albo na wolnej licencji (bez kopii z Tibii i innych gier komercyjnych).",
    "- Kolumna „Jest”: plik już istnieje. Obecne pliki pochodzą z OpenTibia Sprite Pack (CC BY 4.0), autorzy w",
    "  docs/CREDITS.md i w grze (/autorzy). Mapowanie nazw: `tools/art/otsp_import.py`.",
    "- Podłoża z krawędziami (trawy, ziemia, piasek, glina, śnieg, lód, bruk) to autokafelki z paczki WsT",
    "  (`assets/art/autotiles/`, `tools/art/wst_import.py`, warunki w docs/CREDITS.md). Pozostałe obrzeża: kawałki OTSP",
    "  w `assets/art/borders/` albo składane z maski. Własne rysunki zostały tylko tam, gdzie paczka nic nie ma: stragan, łódź,",
    "  ster, namiot orków, wiatrak (`tools/art/draw_town_props.py`).",
    "",
    f"Razem: {len(tiles)} kafelków, {len(items)} przedmiotów, {len(monsters)} potworów, {len(npcs)} NPC, "
    f"{len(chars)} obrazków postaci, {len(objects)} obiektów, {len(pets)} pupili. Podmienione: {done}.",
    "",
    "Lista jest generowana: `python3 tools/art/list_graphics.py`.",
    "",
]
lines += section("Postacie graczy", "characters", chars, "Każda profesja w 4 kierunkach.")
lines += section("Potwory", "monsters", monsters)
lines += section("NPC", "npcs", npcs)
lines += section("Przedmioty", "items", items)
lines += section("Kafelki mapy", "tiles", tiles, "Warstwa: ground = podłoże, decoration = trawa i kwiaty, object = drzewa i meble, wall = ściany.")
lines += section("Obiekty", "objects", objects)
lines += section("Pupile", "pets", pets)
with open(os.path.join(ROOT, "docs/GRAPHICS.md"), "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("docs/GRAPHICS.md:", len(tiles) + len(items) + len(monsters) + len(npcs) + len(chars) + len(objects) + len(pets), "nazw")
