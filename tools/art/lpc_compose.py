#!/usr/bin/env python3
"""Postacie NPC z warstw Universal LPC Spritesheet Character Generator (2026-11-07, decyzja: LPC tylko dla NPC).

Generator: https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator (commit 86160e7).
Kazda warstwa ma wlasnych autorow i licencje (CREDITS.csv generatora); narzedzie zapisuje wyciag dla uzytych warstw
w assets/art/lpc/CREDITS-LPC.csv i odmawia uzycia warstwy bez wpisu. Zlozone postacie sa utworami zaleznymi
(czesc warstw tylko CC-BY-SA 3.0 / GPL) - udostepniane na CC-BY-SA 3.0, patrz assets/art/lpc/LICENSE.txt.

Uzycie:
  python3 -I tools/art/lpc_compose.py --dirs                    lista katalogow warstw (do pobrania)
  python3 -I tools/art/lpc_compose.py <katalog LPC> [--sheet plik.png] [--dirs4 plik.png]
<katalog LPC> zawiera CREDITS.csv i spritesheets/ generatora (tylko pobrane warstwy walk).

Kazdy NPC z shared/src/main/resources/maps/*/npcs.csv dostaje postac: stroj wg roli (kowal - fartuch i mlot,
kaplan - szata, straznik - zbroja i helm, kapitan - surdut i kapelusz...), kolor glowny z kolumny "kolor" npcs.csv,
akcent miasta, a skora, fryzura, kolor wlosow, broda i dodatki losowane deterministycznie z klucza NPC.
Arkusz LPC walk.png: 9 klatek 64x64 w wierszu (0 = postoj), wiersze: gora, lewo, dol, prawo. NPC w grze stoja
przodem, wiec zapisujemy klatke postoju "dol" jako assets/art/lpc/npcs/<klucz>.png (32x32, postac w wysokosci postaci
OTSP, stopy przy dolnej krawedzi pola - wariant b z proby).
"""
import csv
import glob
import os
import random
import re
import sys
import zlib

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "assets", "art", "lpc")
FRAME = 64
ROWS = {"up": 0, "left": 1, "down": 2, "right": 3}
DIRS = ("down", "up", "left", "right")
HEIGHT = 31          # wysokosc postaci OTSP w grze (mediana NPC z Varden po scisnieciu do pola 32 px)
BODY_TOP, FEET = 14, 62   # glowa i stopy golego ciala LPC w klatce postoju (y)

# Kolejnosc rysowania (od spodu), jak zPos generatora w uproszczeniu.
# Kamizelka i gorset (torso/clothes/vest, corset) leza na koszuli - osobno, po zbroi.
Z = [r"^cape/.*/bg", r"^hair/.*/bg$", r"^weapon/.*(background|behind)", r"^body/", r"^head/", r"^legs/", r"^feet/",
     r"^torso/clothes/(?!vest|corset)", r"^dress/", r"^torso/(armour|chainmail)", r"^torso/clothes/", r"^torso/(aprons|jacket)",
     r"^torso/waist", r"^neck/", r"^shoulders/", r"^arms/", r"^cape/", r"^beards/", r"^hair/", r"^facial/", r"^hat/",
     r"^(weapon|tools)/"]

C = lambda h: tuple(int(h.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4))
SKIN = {"light": None, "fair": (240, 205, 175), "tan": (205, 150, 105), "brown": (150, 100, 65), "dark": (105, 70, 50)}
HAIR = {"black": (45, 40, 40), "darkbrown": (85, 55, 35), "brown": (125, 85, 50), "auburn": (150, 75, 40),
        "blond": (220, 190, 110), "ginger": (190, 95, 45), "ash": (170, 160, 140)}
OLD_HAIR = {"gray": (165, 165, 170), "white": (230, 230, 225)}
# Akcent miasta (tabardy strazy, peleryny wladcow, pasy) i rozklad koloru skory.
TOWN = {
    "Varden": ((160, 40, 45), {"light": 4, "fair": 2, "tan": 2, "brown": 1}),
    "Valmir": ((55, 85, 150), {"light": 4, "fair": 2, "tan": 2, "brown": 1}),
    "Nerys": ((40, 130, 125), {"light": 3, "fair": 2, "tan": 2, "brown": 2}),
    "Qasirah": ((200, 135, 55), {"tan": 3, "brown": 4, "dark": 2}),
    "Selvaran": ((70, 130, 60), {"light": 3, "tan": 3, "brown": 2, "dark": 1}),
    "Frostholm": ((150, 185, 220), {"light": 5, "fair": 4}),
    "Durnkar": ((160, 95, 40), {"light": 3, "fair": 3, "tan": 3}),
}
# Krasnoludy (2026-11-11, Durnkar pod gorami): zawsze z broda, rude/brazowe/siwe wlosy, postac niska i kreca
# (to_tile: nizsza o ok. 1/5 i szersza). NPC z wlasnym strojem OTSP (elfy Elandoru) nie dostaja postaci LPC.
DWARF_TOWNS = {"Durnkar"}
DWARF_BEARDS = ["beards/beard/medium", "beards/beard/basic", "beards/beard/winter/male", "beards/beard/medium"]
OTSP_ONLY = ("elandor_",)
DEFAULT_TOWN = ((110, 90, 70), {"light": 3, "fair": 2, "tan": 3, "brown": 2, "dark": 1})
HELMETS = {"Varden": "hat/helmet/kettle/adult", "Valmir": "hat/helmet/nasal/adult", "Nerys": "hat/helmet/barbuta/{B}",
           "Qasirah": "hat/helmet/morion/adult", "Selvaran": "hat/helmet/norman/adult",
           "Frostholm": "hat/helmet/spangenhelm_viking/adult", "Durnkar": "hat/helmet/spangenhelm_viking/adult"}
MALE_HAIR = ["plain", "buzzcut", "parted", "messy1", "bedhead", "curly_short", "natural", "page", "swoop", "spiked"]
FEMALE_HAIR = ["long", "bob", "ponytail", "braid", "bunches", "pixie", "loose", "long_tied", "curly_long", "bangslong"]
TWO_PART_HAIR = {"ponytail", "braid", "bunches", "long_tied", "princess"}
WHITE, DARK, LEATHER, STEEL = (235, 232, 225), (55, 50, 55), (120, 80, 50), (165, 170, 180)


def female(name):
    """Plec z polskiego imienia i tytulu (Kaplanka, Strazniczka, Krolowa, Admiral Wenda...)."""
    words = name.split()
    title, last = words[0], words[-1]
    if re.search(r"(ka|wa|ni|yni)$", title):
        return True
    return title in ("Admirał", "Kapitan", "Sternik") and last.endswith("a") and last not in ("Mewa",)


class Npc:
    def __init__(self, key, name, role, town, color):
        self.key, self.name, self.role, self.town, self.color = key, name, role, town, C(color)
        self.female = female(name)
        self.rnd = random.Random(zlib.crc32(key.encode()))


def load_npcs():
    npcs = {}
    for path in sorted(glob.glob(os.path.join(ROOT, "shared", "src", "main", "resources", "maps", "*", "npcs.csv"))):
        with open(path, encoding="utf-8") as f:
            for line in f:
                if not line.strip() or line.startswith("#"):
                    continue
                c = line.rstrip("\n").split(";")
                npcs.setdefault(c[0], Npc(c[0], c[1], c[2], c[3], c[8]))
    return npcs


def darker(rgb, k=0.6):
    return tuple(int(v * k) for v in rgb)


def pick(rnd, weights):
    keys = list(weights)
    return rnd.choices(keys, [weights[k] for k in keys])[0]


def outfit(n):
    """Warstwy postaci: lista (katalog warstwy, barwa albo None). {B} = male/female (tulow), {L} = male/thin (nogi)."""
    r, main = n.rnd, n.color
    accent, skins = TOWN.get(n.town, DEFAULT_TOWN)
    f = n.female
    key, role, name = n.key, n.role, n.name
    old = role in ("KING", "PRIEST", "IMBUER") and r.random() < 0.6 or r.random() < 0.15
    skin = SKIN[pick(r, skins)]
    L = []
    add = lambda d, c=None: L.append((d, c))
    add("body/bodies/{B}", skin)
    if f:
        add("head/heads/human/female_elderly" if old else "head/heads/human/female", skin)
    else:
        # bez male_plump: w CREDITS.csv jeden z autorow tej glowy jest nieznany ("??")
        head = "male_elderly" if old else r.choice(["male", "male", "male", "male_gaunt"])
        add("head/heads/human/" + head, skin)
    hair_c = r.choice(list(OLD_HAIR.values())) if old else HAIR[r.choice(list(HAIR))]
    if n.town == "Qasirah" and not old:
        hair_c = r.choice([HAIR["black"], HAIR["darkbrown"]])
    style = r.choice(FEMALE_HAIR if f else MALE_HAIR)
    if not f and old and r.random() < 0.4:
        style = "balding"
    if role == "KING" and f:
        style = "princess"
    hats = []          # nakrycie glowy wyklucza fryzury z grzywka nad czapka? zostawiamy fryzure pod spodem
    beard = None
    if not f and (old or r.random() < 0.45):
        beard = r.choice(["beards/beard/basic", "beards/beard/medium", "beards/beard/trimmed", "beards/mustache/basic",
                          "beards/mustache/walrus"])
        if n.town == "Frostholm" and r.random() < 0.6:
            beard = "beards/beard/winter/male"
    if n.town in DWARF_TOWNS and not f:
        beard = r.choice(DWARF_BEARDS)
        if not old:
            hair_c = r.choice([HAIR["ginger"], HAIR["auburn"], HAIR["brown"], HAIR["darkbrown"], HAIR["black"]])
    boots = r.choice(["feet/boots/basic/{L}", "feet/boots/fold/{L}", "feet/shoes/basic/{L}"])

    def clothes_common(top, top_c, legs="legs/pants/{L}", legs_c=None, feet=boots, feet_c=LEATHER):
        add(top, top_c)
        add(legs, legs_c or darker(main, 0.55))
        add(feet, feet_c)

    if role == "GUARD":
        add("torso/armour/plate/{B}" if r.random() < 0.6 else "torso/chainmail/{B}", None)
        add("torso/jacket/tabard/{B}", accent)
        add("legs/armour/plate/{L}", None)
        add("feet/armour/plate/{L}", None)
        add("arms/armour/plate/{L}", None)
        hats.append((HELMETS.get(n.town, "hat/helmet/kettle/adult"), None))
        # bez broni: wlocznia i halabarda w klatce chodu sa trzymane w poprzek postaci i po zmniejszeniu do pola
        # wygladaja jak kij w pasie (luk i kusza nie maja w LPC klatek chodu)
    elif role == "KING":
        if f:
            add("dress/slit/female", darker(accent, 0.75) if n.town != "Varden" else main)
            add("feet/shoes/basic/thin", DARK)
            add("neck/necklace/simple/female", None)
        else:
            clothes_common("torso/clothes/longsleeve/longsleeve/male", accent, "legs/formal/male", DARK, "feet/boots/rimmed/male", DARK)
            add("torso/jacket/tabard/male", main)
            add("torso/waist/belt_leather/male", LEATHER)
        add("cape/solid/bg", darker(accent, 0.8))
        add("cape/solid/fg", darker(accent, 0.8))
        hats.append(("hat/formal/crown/adult", None))
    elif role == "PRIEST":
        if f:
            add("torso/clothes/robe/female", WHITE)
            add("feet/sandals/thin", LEATHER)
        else:
            add("torso/clothes/longsleeve/longsleeve/male", WHITE)
            add("legs/skirts/plain/male", WHITE)
            add("feet/sandals/male", LEATHER)
            add("torso/jacket/tabard/male", (240, 225, 170))
        add("torso/waist/sash/{B}", (215, 180, 70))
        add("neck/amulet/cross/{B}", None)
    elif role == "IMBUER" or key.endswith(("_mage", "_archmage")):
        robe = main
        if f:
            add("torso/clothes/robe/female", robe)
            add("feet/slippers/thin", darker(robe, 0.5))
        else:
            add("torso/clothes/longsleeve/longsleeve/male", robe)
            add("legs/skirts/plain/male", robe)
            add("feet/slippers/male", darker(robe, 0.5))
        add("torso/waist/belt_robe/{B}", (215, 180, 70) if role == "IMBUER" else darker(robe, 0.5))
        if key == "selv_mage":   # zielarka: kaptur
            hats.append(("hat/cloth/hood/adult", (90, 120, 60)))
        elif key == "qas_mage":  # wrozbitka
            hats.append(("hat/magic/celestial/adult", robe))
        else:
            hats.append(("hat/magic/wizard/base/adult", robe))
            hats.append(("hat/magic/wizard/belt/adult", (215, 180, 70)))
        if role == "IMBUER":
            add("weapon/magic/gnarled/universal/foreground", None)
            add("weapon/magic/gnarled/universal/background", None)
    elif role == "BANKER":
        if f:
            clothes_common("torso/clothes/blouse_longsleeve/female", WHITE, "legs/skirts/straight/thin", DARK,
                           "feet/shoes/basic/thin", DARK)
            add("torso/clothes/corset/female", main)
            add("neck/necklace/simple/female", None)
        else:
            clothes_common("torso/clothes/longsleeve/formal/male", WHITE, "legs/formal/male", DARK,
                           "feet/shoes/basic/male", DARK)
            add("torso/clothes/vest/male", main)
            add("neck/cravat/male", darker(accent, 0.9))
        if r.random() < 0.5:
            add("facial/glasses/halfmoon/adult", None)
    elif role == "HOUSING":
        if f:
            clothes_common("torso/clothes/blouse_longsleeve/female", main, "legs/skirts/plain/thin", DARK,
                           "feet/shoes/basic/thin", DARK)
            add("neck/necklace/simple/female", None)
        else:
            clothes_common("torso/clothes/longsleeve/formal/male", WHITE, "legs/formal/male", DARK,
                           "feet/shoes/basic/male", DARK)
            add("torso/jacket/frock/male", main)
            if r.random() < 0.6:
                hats.append(("hat/formal/bowler/adult", DARK))
    elif role == "GUIDE":
        clothes_common("torso/clothes/longsleeve/longsleeve/{B}", darker(main, 0.9), "legs/pants/{L}", LEATHER)
        add("torso/armour/leather/{B}", LEATHER)
        add("cape/solid/bg", main)
        add("cape/solid/fg", main)
        if r.random() < 0.5:
            hats.append(("hat/cloth/hood/adult", main))
    elif role == "CARRIER":
        clothes_common("torso/clothes/shortsleeve/shortsleeve/{B}", (215, 200, 170), "legs/pants/{L}", main)
        add("torso/aprons/suspenders/{B}", darker(main, 0.6))
        hats.append((r.choice(["hat/formal/bowler/adult", "hat/cloth/leather_cap/adult"]), darker(main, 0.6)))
    elif role == "CAPTAIN":
        if key == "pirate_captain":
            clothes_common("torso/clothes/longsleeve/longsleeve/{B}", WHITE, "legs/pantaloons/{L}", (60, 40, 40),
                           "feet/boots/rimmed/{L}", DARK)
            if not f:
                add("torso/jacket/frock/male", (40, 35, 40))
            add("facial/patches/eyepatch/left/adult", None)
            hats.append(("hat/pirate/tricorne/captain/skull/adult", None))
        elif key.endswith("_captain") and "Przewoźnik" not in name:
            if f:
                clothes_common("torso/clothes/blouse_longsleeve/female", WHITE, "legs/pants/thin", (230, 225, 210),
                               "feet/boots/rimmed/thin", DARK)
                add("torso/clothes/corset/female", main)
            else:
                clothes_common("torso/clothes/longsleeve/longsleeve/male", WHITE, "legs/pants/male", (230, 225, 210),
                               "feet/boots/rimmed/male", DARK)
                add("torso/jacket/frock/male", main)
            hats.append((r.choice(["hat/pirate/tricorne/captain/adult", "hat/pirate/bicorne/athwart/captain/adult"]),
                         darker(main, 0.7)))
        else:   # przewoznicy, latarniczka: marynarz w pasiastej koszuli i chuscie
            if f:
                clothes_common("torso/clothes/shortsleeve/tshirt/female", WHITE, "legs/pants/thin", main)
                add("torso/aprons/overalls/female", main)
            else:
                clothes_common("torso/clothes/sleeveless/striped/male", None, "legs/pants/male", main)
            hats.append((r.choice(["hat/pirate/kerchief/adult", "hat/cloth/bandana/adult"]), r.choice(
                [(170, 50, 50), (60, 90, 150), (200, 170, 70)])))
    else:   # MERCHANT wg klucza
        if key.endswith("_smith"):
            clothes_common("torso/clothes/sleeveless/sleeveless/{B}", (200, 190, 170), "legs/pants/{L}", DARK)
            add("torso/aprons/apron/{B}", (95, 65, 45))
            add("arms/bracers/{L}", LEATHER)
            add("tools/hammer/{L}", None)
        elif key.endswith("_innkeeper"):
            clothes_common("torso/clothes/longsleeve/longsleeve/{B}", main, "legs/pants/{L}", DARK)
            add("torso/aprons/apron/{B}", WHITE)
        elif key.endswith("_petkeeper"):
            clothes_common("torso/clothes/shortsleeve/shortsleeve/{B}", main, "legs/pants/{L}", LEATHER)
            add("torso/waist/belt_leather/{B}", LEATHER)
            hats.append(("hat/cloth/leather_cap/adult", (110, 85, 55)))
        elif key in ("selv_fishmonger", "wola_merchant"):
            clothes_common("torso/clothes/longsleeve/longsleeve/{B}", main, "legs/pants/{L}", DARK, "feet/boots/fold/{L}", DARK)
            add("torso/aprons/apron/{B}", (200, 200, 190))
            hats.append(("hat/cloth/bandana/adult", (60, 90, 150)))
        elif key in ("camp_merchant", "isle_merchant"):
            clothes_common("torso/clothes/longsleeve/longsleeve/{B}", main, "legs/pants/{L}", LEATHER)
            add("torso/armour/leather/{B}", LEATHER)
            add("torso/waist/belt_leather/{B}", DARK)
        elif key.endswith("_merchant") and key.split("_")[0] in ("oaza", "osada", "wierzbowo"):
            clothes_common("torso/clothes/shortsleeve/shortsleeve/{B}", (215, 200, 170), "legs/pants/{L}", main)
            add("torso/clothes/vest/male" if not f else "torso/clothes/corset/female", main)
            if n.town == "Mroźna Osada":
                hats.append(("hat/cloth/hood/adult", (150, 130, 110)))
        else:   # kupiec w miescie
            if f:
                clothes_common("torso/clothes/blouse_longsleeve/female", (225, 215, 195), "legs/skirts/plain/thin", main,
                               "feet/shoes/basic/thin", LEATHER)
                add("torso/clothes/corset/female", darker(main, 0.8))
            else:
                clothes_common("torso/clothes/longsleeve/longsleeve2_buttoned/male", (225, 215, 195), "legs/pants/male",
                               darker(main, 0.6))
                add("torso/clothes/vest/male", main)
            add("torso/waist/belt_leather/{B}", LEATHER)
            if r.random() < 0.5:
                hats.append(("hat/formal/bowler/adult", darker(main, 0.6)))
    if beard and not any(h[0].startswith("hat/helmet/barbuta") for h in hats):
        add(beard, hair_c)
    if style in TWO_PART_HAIR:
        add(f"hair/{style}/adult/bg", hair_c)
        add(f"hair/{style}/adult/fg", hair_c)
    else:
        add(f"hair/{style}/adult", hair_c)
    for h in hats:
        add(*h)
    b, l = ("female", "thin") if f else ("male", "male")
    return [(d.replace("{B}", b).replace("{L}", l), c) for d, c in L]


def zkey(path):
    for i, pat in enumerate(Z):
        if re.match(pat, path):
            return i
    return len(Z)


def tint(img, rgb):
    """Przebarwienie warstwy (biale/szare bazy LPC): barwa * jasnosc piksela, jak tint() w otsp_import.py."""
    a = np.asarray(img.convert("RGBA")).astype(np.float32)
    lum = 0.3 * a[..., 0] + 0.59 * a[..., 1] + 0.11 * a[..., 2]
    vis = a[..., 3] > 0
    top = max(np.percentile(lum[vis], 90) if vis.any() else 255, 40)
    k = np.clip(lum / top, 0, 1) ** 0.8
    out = a.copy()
    for c in range(3):
        out[..., c] = rgb[c] * k
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def sheet_file(src, d):
    """Plik walk warstwy: walk.png albo jeden z kolorow (walk/<kolor>.png - wtedy jasny, do przebarwienia)."""
    base = os.path.join(src, "spritesheets", d)
    if os.path.exists(base + "/walk.png"):
        return d + "/walk.png"
    for c in ("white", "gray", "base", "steel", "halberd", "brown"):
        if os.path.exists(f"{base}/walk/{c}.png"):
            return f"{d}/walk/{c}.png"
    raise SystemExit(f"Brak warstwy {d} w {src} (pobierz ja: --dirs)")


def compose(src, layers):
    sheet, used = None, []
    for d, rgb in sorted(layers, key=lambda l: zkey(l[0])):
        path = sheet_file(src, d)
        used.append(path)
        img = Image.open(os.path.join(src, "spritesheets", path)).convert("RGBA")
        if img.size != (576, 256):
            img = img.crop((0, 0, 576, 256))
        if rgb:
            img = tint(img, rgb)
        sheet = img if sheet is None else Image.alpha_composite(sheet, img)
    return sheet, used


def frame(sheet, d, f=0):
    r = ROWS[d]
    return sheet.crop((f * FRAME, r * FRAME, (f + 1) * FRAME, (r + 1) * FRAME))


def shrink(img, w, h):
    """Zmniejszenie grafiki pikselowej: srednia z pola (alfa premnozona), prog alfy 50% i kolory przyciagniete do
    palety oryginalu - bez polprzezroczystych krawedzi i nowych odcieni."""
    a = np.asarray(img).astype(np.float32) / 255.0
    pre = a.copy()
    pre[..., :3] *= pre[..., 3:4]
    small = np.asarray(Image.fromarray((pre * 255).astype(np.uint8), "RGBA").resize((w, h), Image.BOX)).astype(np.float32) / 255.0
    alpha = small[..., 3]
    rgb = np.where(alpha[..., None] > 0, small[..., :3] / np.maximum(alpha[..., None], 1e-6), 0)
    pal = np.unique((a[a[..., 3] > 0.5][:, :3] * 255).round().astype(np.int32), axis=0)
    out = np.zeros((h, w, 4), np.uint8)
    vis = alpha >= 0.5
    if pal.size and vis.any():
        px = (rgb[vis] * 255)
        dist = ((px[:, None, :] - pal[None, :, :]) ** 2).sum(-1)
        out[vis, :3] = pal[dist.argmin(1)]
        out[vis, 3] = 255
    return Image.fromarray(out, "RGBA")


def to_tile(fr, stocky=False):
    """Klatka 64x64 -> pole 32x32: stala skala (gole cialo = wysokosc postaci OTSP), stopy przy dolnej krawedzi,
    srodek klatki na srodku pola. Gdy kapelusz wystaje ponad pole - skala mniejsza, zeby sie zmiescic.
    stocky: krasnolud - nizszy o ok. 1/5 i nieco szerszy."""
    box = fr.getbbox()
    s = HEIGHT / (FEET - BODY_TOP)
    if box:
        s = min(s, 31.5 / (FEET + 1 - box[1]))   # szerokosc: wystajace boki (mlot) sa przycinane
    sy, sx = (s * 0.78, s * 1.06) if stocky else (s, s)
    n, nw = max(1, round(FRAME * sy)), max(1, round(FRAME * sx))
    small = shrink(fr, nw, n)
    tile = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    ox, oy = 16 - nw // 2, 32 - round((FEET + 1) * sy)
    tile.paste(small, (ox, oy), small)
    return outline(tile)


def outline(img, rgb=(24, 18, 16)):
    """Ciemny kontur 1 px wokol sylwetki (jak w strojach OTSP): po zmniejszeniu kontury LPC sie gubia, a postac
    bez konturu zlewa sie z bruku i podlog."""
    a = np.asarray(img).copy()
    vis = a[..., 3] > 0
    n = np.zeros_like(vis)
    n[1:, :] |= vis[:-1, :]
    n[:-1, :] |= vis[1:, :]
    n[:, 1:] |= vis[:, :-1]
    n[:, :-1] |= vis[:, 1:]
    edge = n & ~vis
    a[edge] = (*rgb, 255)
    return Image.fromarray(a, "RGBA")


def credits(src, used):
    rows = {}
    with open(os.path.join(src, "CREDITS.csv"), encoding="utf-8") as f:
        for r in csv.DictReader(f, skipinitialspace=True):
            rows[r["filename"].strip()] = {k: (v or "").strip() for k, v in r.items()}
    out = []
    for path in sorted(used):
        # warianty kolorow (walk/white.png) sa w CREDITS.csv pod nazwa arkusza (walk.png)
        r = rows.get(path) or rows.get(path.rsplit("/walk/", 1)[0] + "/walk.png")
        if r is None:
            raise SystemExit(f"Brak warstwy {path} w CREDITS.csv - nie mozna jej uzyc bez autorow i licencji")
        if "?" in r["authors"] or not r["licenses"]:
            raise SystemExit(f"Warstwa {path}: nieznany autor albo brak licencji w CREDITS.csv")
        out.append([path, str(len(used[path])), r["licenses"], r["authors"], r["urls"]])
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "CREDITS-LPC.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["layer", "npc_count", "licenses", "authors", "urls"])
        w.writerows(out)
    return out


def all_dirs():
    dirs = set()
    for n in load_npcs().values():
        if n.key.startswith(OTSP_ONLY):
            continue
        n.rnd = random.Random(zlib.crc32(n.key.encode()))
        dirs.update(d for d, _ in outfit(n))
    return sorted(dirs)


def contact_sheet(tiles, path, z=3, cols=16):
    from PIL import ImageDraw
    keys = sorted(tiles)
    grass = Image.open(os.path.join(ROOT, "assets", "art", "tiles", "cobblestone.png")).convert("RGBA").resize((32, 32), Image.NEAREST)
    cw, ch = 32 * z + 6, 32 * z + 16
    out = Image.new("RGBA", (cols * cw, ((len(keys) + cols - 1) // cols) * ch), (35, 35, 40, 255))
    d = ImageDraw.Draw(out)
    for i, k in enumerate(keys):
        cell = grass.copy()
        cell.alpha_composite(tiles[k])
        x, y = (i % cols) * cw, (i // cols) * ch
        out.alpha_composite(cell.resize((32 * z, 32 * z), Image.NEAREST), (x, y))
        d.text((x + 2, y + 32 * z + 2), k[:16], fill=(230, 230, 230, 255))
    out.save(path)


def dirs4(sheets, path, z=3):
    """Arkusz czterech kierunkow (postoj) dla kilku NPC - do porownania z OTSP."""
    keys = sorted(sheets)
    out = Image.new("RGBA", (len(DIRS) * 32 * z, len(keys) * 32 * z), (60, 90, 50, 255))
    for r, k in enumerate(keys):
        for c, dname in enumerate(DIRS):
            out.alpha_composite(to_tile(frame(sheets[k], dname)).resize((32 * z, 32 * z), Image.NEAREST), (c * 32 * z, r * 32 * z))
    out.save(path)


LICENSE = """Postacie NPC w tym katalogu (npcs/*.png) zlozono narzedziem tools/art/lpc_compose.py z warstw
Universal LPC Spritesheet Character Generator
https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator (commit 86160e7).

Autorzy, licencje i adresy zrodel kazdej uzytej warstwy: CREDITS-LPC.csv (wyciag z CREDITS.csv generatora).
Warstwy maja licencje do wyboru (np. "OGA-BY 3.0,CC-BY-SA 3.0,GPL 3.0"); czesc (korona, kapelusze, brody,
niektore fryzury i stroje) jest tylko na CC-BY-SA 3.0 / GPL. Dlatego zlozone obrazki NPC (utwory zalezne:
warstwy przebarwione, zlozone, zmniejszone do 32x32) sa udostepniane na licencji
Creative Commons Attribution-ShareAlike 3.0 (CC BY-SA 3.0): https://creativecommons.org/licenses/by-sa/3.0/
Mozesz je kopiowac i zmieniac pod warunkiem podania autorow (CREDITS-LPC.csv) i udostepnienia zmian na tej
samej licencji. Pozostale grafiki gry maja wlasne licencje (docs/CREDITS.md).

NPC characters in this directory are derived from Universal LPC Spritesheet Character Generator layers
(authors and licenses per layer: CREDITS-LPC.csv) and are licensed CC BY-SA 3.0.
"""


def main(argv):
    if "--dirs" in argv:
        print("\n".join(all_dirs()))
        return 0
    if len(argv) < 2:
        print(__doc__)
        return 1
    src = argv[1]
    npcs = load_npcs()
    used, tiles, sheets = {}, {}, {}
    for key, n in sorted(npcs.items()):
        if key.startswith(OTSP_ONLY):
            continue
        sheet, paths = compose(src, outfit(n))
        for p in paths:
            used.setdefault(p, set()).add(key)
        tiles[key] = to_tile(frame(sheet, "down"), stocky=n.town in DWARF_TOWNS)
        sheets[key] = sheet
    credits(src, used)
    for old in glob.glob(os.path.join(OUT, "npcs", "*.png")):
        os.remove(old)
    os.makedirs(os.path.join(OUT, "npcs"), exist_ok=True)
    for key, t in tiles.items():
        t.save(os.path.join(OUT, "npcs", key + ".png"), optimize=True)
    with open(os.path.join(OUT, "LICENSE.txt"), "w", encoding="utf-8") as f:
        f.write(LICENSE)
    if "--sheet" in argv:
        contact_sheet(tiles, argv[argv.index("--sheet") + 1])
    if "--dirs4" in argv:
        pick_keys = ["varden_smith", "varden_priest", "guard_21", "varden_king", "nerys_ruler", "varden_captain",
                     "varden_imbuer", "valmir_merchant", "isle3_ferryman", "pirate_captain"]
        dirs4({k: sheets[k] for k in pick_keys if k in sheets}, argv[argv.index("--dirs4") + 1])
    print(f"LPC: {len(tiles)} NPC w {OUT}/npcs, {len(used)} warstw")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
