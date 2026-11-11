#!/usr/bin/env python3
"""Wyglad interfejsu "nowy" z kupionej paczki "Pixel RPG UI Kit - HUD, Buttons, Icons, Inventory & Slots (9-Slice)"
(Zidane42, itch.io).

    python3 tools/art/zidane_import.py [katalog assets]

Zrodla (tools/art/zidane/, poza repo - pliki paczki nie sa rozpowszechniane; spakowany oryginal w plikach projektu
pixel-zidane-ui/pack_ui_1.2.zip): rozpakuj zawartosc katalogu "pack ui" do tools/art/zidane/. Do gry trafiaja tylko
wyciete kawalki (assets/ui/zidane/), z ktorych Hud sklada elementy dowolnej wielkosci (ZidaneSkin):

- paski (zdrowie, mana, doswiadczenie; kondycja w grze to cienka kreska bez ramki): ramka 9-slice bez wnetrza, nacięcie ramki (co 25%), tlo pustego
  paska i kolor wypelnienia jako male 9-slice (lewa kolumna, srodek, prawa kolumna; gora, srodek, dol) - pasek
  rysuje dowolny procent, nie tylko progi 25% z paczki,
- okno: rogi 16 px, krawedzie 8 px i tlo 16 px zlozone w jeden obrazek 9-slice 40 x 40,
- przyciski: lewa i prawa krawedz po 6 px i 1 px srodka (szerokosc dowolna),
- pola: zwykle, wskazane i ramki rzadkosci 32 x 32 (9-slice 8 px),
- przelacznik wlacz/wylacz (ustawienia),
- okragla ramka minimapy 95 x 95 z przyciskami + i - (2026-11-11, Darek: "mapke zrob w kolku z nowych grafik z + i
  - oddalenia") i same przyciski + i - wyciete z niej (kolo o promieniu 11 px) - gra rysuje je jeszcze raz na ramce
  przyciemnione (koniec skali) albo rozjasnione (wcisniety).
Ikony 24 px paczki i pola wyboru nie sa uzywane (przedmioty maja ikony OTSP 32 px).
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "zidane")

BARS = {"life": "life", "mana": "mana", "exp": "experience"}
# kolumny i wiersze wnetrza paska 112 x 24 (wnetrze x 4..107, y 4..19)
BAR_COLS = (4, 30, 107)
BAR_ROWS = (4, 5, 10, 19)
BUTTONS = ("yellow", "green", "red", "disabled")
SLOTS = {"slot": "slot/normal", "slot_hover": "slot/houver"}
RARITY = ("common", "unusual", "rare", "epic", "legendary", "mythical")
TOGGLES = {"toggle_on": "button/checkbox/on", "toggle_off": "button/checkbox/off"}


def load(rel):
    return Image.open(os.path.join(SRC, rel + ".png")).convert("RGBA")


def pick(img, cols, rows):
    """Obrazek z wybranych kolumn i wierszy (w tej kolejnosci)."""
    out = Image.new("RGBA", (len(cols), len(rows)), (0, 0, 0, 0))
    for i, x in enumerate(cols):
        for j, y in enumerate(rows):
            out.putpixel((i, j), img.getpixel((x, y)))
    return out


def bars(out):
    empty = load("hud/empty hud")
    # ramka bez wnetrza: lewe 8 kolumn, 1 kolumna srodka (bez naciec), prawe 8; gorne 6 wierszy, 1 srodka, dolne 6
    frame = pick(empty, list(range(8)) + [30] + list(range(104, 112)), list(range(6)) + [10] + list(range(18, 24)))
    for x in range(frame.width):
        for y in range(frame.height):
            sx = x if x < 8 else (30 if x == 8 else 104 + x - 9)
            sy = y if y < 6 else (10 if y == 6 else 18 + y - 7)
            if 4 <= sx <= 107 and 4 <= sy <= 19:
                frame.putpixel((x, y), (0, 0, 0, 0))
    frame.save(os.path.join(out, "bar_frame.png"))
    # naciecie ramki (kolumna 27): gorne 6 i dolne 6 wierszy, bez wnetrza
    notch = pick(empty, [27], list(range(4)) + list(range(20, 24)))
    notch.save(os.path.join(out, "bar_notch.png"))
    pick(empty, BAR_COLS, BAR_ROWS).save(os.path.join(out, "bar_empty.png"))
    for key, name in BARS.items():
        pick(load("hud/hud " + name + " 100"), BAR_COLS, BAR_ROWS).save(os.path.join(out, "bar_fill_" + key + ".png"))


def window(out):
    d = "background/bg1/"
    tl, tr, bl, br = (load(d + "corner " + c) for c in ("tl", "tr", "bl", "br"))
    hor, ver, bg = load(d + "horizontal frame"), load(d + "vertical frame"), load(d + "BG")
    # 40 x 40: rogi 16, srodek 8 (krawedzie 8 px od zewnatrz, dalej tlo)
    img = Image.new("RGBA", (40, 40), (0, 0, 0, 0))
    for x in range(0, 40, 16):
        for y in range(0, 40, 16):
            img.paste(bg, (x, y))
    img.paste(hor.crop((0, 0, 8, 8)), (16, 0))
    img.paste(hor.transpose(Image.FLIP_TOP_BOTTOM).crop((0, 0, 8, 8)), (16, 32))
    img.paste(ver.crop((0, 0, 8, 8)), (0, 16))
    img.paste(ver.transpose(Image.FLIP_LEFT_RIGHT).crop((0, 0, 8, 8)), (32, 16))
    # rog na tle; przezroczyste piksele rogu od strony zewnetrznej (przekatna od rogu okna) zostaja przezroczyste,
    # od strony wnetrza widac tlo
    for c, (x, y), (fx, fy) in ((tl, (0, 0), (0, 0)), (tr, (24, 0), (1, 0)), (bl, (0, 24), (0, 1)), (br, (24, 24), (1, 1))):
        piece = bg.copy()
        piece.alpha_composite(c)
        for px in range(16):
            for py in range(16):
                ox, oy = (15 - px if fx else px), (15 - py if fy else py)
                if c.getpixel((px, py))[3] == 0 and ox + oy < 15:
                    piece.putpixel((px, py), (0, 0, 0, 0))
        img.paste(piece, (x, y))
    img.save(os.path.join(out, "window.png"))
    # pasek oddzielajacy tytul (krawedz pozioma 8 px)
    hor.crop((0, 0, 1, 8)).save(os.path.join(out, "divider.png"))


def buttons(out):
    for color in BUTTONS:
        src = load("button/buttons/" + color + " medium button")
        pick(src, list(range(6)) + [32] + list(range(58, 64)), list(range(32))).save(os.path.join(out, "button_" + color + ".png"))


# przyciski ramki minimapy: lewy gorny rog wycinka 22 x 21 i srodek kola przycisku (piksele ramki, y od gory)
MINIMAP_BUTTONS = {"minimap_plus": ((73, 0), (83.5, 10)), "minimap_minus": ((73, 74), (83.5, 84))}


def minimap(out):
    ring = load("background/minimap")
    ring.save(os.path.join(out, "minimap_ring.png"))
    for name, ((x0, y0), (cx, cy)) in MINIMAP_BUTTONS.items():
        piece = ring.crop((x0, y0, x0 + 22, y0 + 21))
        for x in range(piece.width):
            for y in range(piece.height):
                if (x0 + x - cx) ** 2 + (y0 + y - cy) ** 2 > 11 ** 2:
                    piece.putpixel((x, y), (0, 0, 0, 0))
        piece.save(os.path.join(out, name + ".png"))


def main():
    assets = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "assets")
    if not os.path.isdir(os.path.join(SRC, "hud")):
        sys.exit("Brak zrodel w tools/art/zidane/ (rozpakuj \"pack ui\" z plikow projektu pixel-zidane-ui/pack_ui_1.2.zip)")
    out = os.path.join(assets, "ui", "zidane")
    os.makedirs(out, exist_ok=True)
    bars(out)
    window(out)
    buttons(out)
    minimap(out)
    for name, rel in SLOTS.items():
        load(rel).save(os.path.join(out, name + ".png"))
    for r in RARITY:
        load("rarity/" + r).save(os.path.join(out, "rarity_" + r + ".png"))
    for name, rel in TOGGLES.items():
        load(rel).save(os.path.join(out, name + ".png"))
    print("Zidane UI: zapisano", len(os.listdir(out)), "plikow do", out)


if __name__ == "__main__":
    main()
