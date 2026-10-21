#!/usr/bin/env python3
"""Wycina obrazki z OpenTibia Sprite Pack (CC BY 4.0, https://github.com/peonso/opentibia_sprite_pack).

Czyta client_files/otsp.dat + otsp.spr (format 10.41, extended, bez przezroczystosci, bez frame-groups)
i zapisuje kazdy przedmiot (items/<id>.png) oraz stroj (outfits/<looktype>_<kierunek>.png) jako PNG RGBA.
Uzycie: python3 otsp_extract.py <katalog client_files> <katalog wyjsciowy>
"""
import os
import struct
import sys

from PIL import Image

DIRS = ["up", "right", "down", "left"]  # kolejnosc patternX strojow w Tibii: polnoc, wschod, poludnie, zachod


class Reader:
    def __init__(self, data):
        self.d, self.p = data, 0

    def u8(self):
        self.p += 1
        return self.d[self.p - 1]

    def u16(self):
        self.p += 2
        return struct.unpack_from("<H", self.d, self.p - 2)[0]

    def u32(self):
        self.p += 4
        return struct.unpack_from("<I", self.d, self.p - 4)[0]


ARG16 = {0x00, 0x08, 0x09, 0x1A, 0x1D, 0x1E, 0x21, 0x23}
ARG32 = {0x16, 0x19}


def read_thing(r):
    while True:
        f = r.u8()
        if f == 0xFF:
            break
        if f in ARG16:
            r.u16()
        elif f in ARG32:
            r.u16(); r.u16()
        elif f == 0x22:  # market
            r.u16(); r.u16(); r.u16()
            n = r.u16(); r.p += n
            r.u16(); r.u16()
    w, h = r.u8(), r.u8()
    if w > 1 or h > 1:
        r.u8()
    layers, px, py, pz, frames = r.u8(), r.u8(), r.u8(), r.u8(), r.u8()
    count = w * h * layers * px * py * pz * frames
    sprites = [r.u32() for _ in range(count)]
    return dict(w=w, h=h, layers=layers, px=px, py=py, pz=pz, frames=frames, sprites=sprites)


def load_dat(path):
    r = Reader(open(path, "rb").read())
    r.u32()
    items, outfits, effects, missiles = r.u16(), r.u16(), r.u16(), r.u16()
    things = {"item": {}, "outfit": {}}
    for i in range(100, items + 1):
        things["item"][i] = read_thing(r)
    for i in range(1, outfits + 1):
        things["outfit"][i] = read_thing(r)
    return things


class Spr:
    def __init__(self, path):
        self.d = open(path, "rb").read()
        self.count = struct.unpack_from("<I", self.d, 4)[0]

    def image(self, sid):
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        if sid == 0 or sid > self.count:
            return img
        off = struct.unpack_from("<I", self.d, 8 + (sid - 1) * 4)[0]
        if off == 0:
            return img
        size = struct.unpack_from("<H", self.d, off + 3)[0]
        p, end, pos = off + 5, off + 5 + size, 0
        px = img.load()
        while p < end and pos < 1024:
            transparent, colored = struct.unpack_from("<HH", self.d, p)
            p += 4
            pos += transparent
            for _ in range(colored):
                if pos >= 1024:
                    break
                px[pos % 32, pos // 32] = (self.d[p], self.d[p + 1], self.d[p + 2], 255)
                p += 3
                pos += 1
        return img


def compose(spr, t, layer=0, x=0, y=0, z=0, frame=0):
    w, h = t["w"], t["h"]
    out = Image.new("RGBA", (w * 32, h * 32), (0, 0, 0, 0))
    for hh in range(h):
        for ww in range(w):
            idx = (((((frame * t["pz"] + z) * t["py"] + y) * t["px"] + x) * t["layers"] + layer) * h + hh) * w + ww
            if idx < len(t["sprites"]):
                tile = spr.image(t["sprites"][idx])
                out.alpha_composite(tile, ((w - 1 - ww) * 32, (h - 1 - hh) * 32))
    return out


def main(client, outdir):
    things = load_dat(os.path.join(client, "otsp.dat"))
    spr = Spr(os.path.join(client, "otsp.spr"))
    os.makedirs(os.path.join(outdir, "items"), exist_ok=True)
    os.makedirs(os.path.join(outdir, "outfits"), exist_ok=True)
    for i, t in things["item"].items():
        compose(spr, t).save(os.path.join(outdir, "items", f"{i}.png"))
    for i, t in things["outfit"].items():
        for d in range(min(4, t["px"])):
            compose(spr, t, x=d).save(os.path.join(outdir, "outfits", f"{i}_{DIRS[d]}.png"))
    print(len(things["item"]), "przedmiotow,", len(things["outfit"]), "strojow")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
