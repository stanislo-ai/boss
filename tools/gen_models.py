#!/usr/bin/env python3
"""
Generates the ScooterMC resource-pack geometry and textures from tools/scooter_geometry.py.

For every part it
  * converts the millimetre design into item-model units (16 units = 1 block),
  * packs every visible face into one 16x16 UV square with a shelf packer,
  * paints each face procedurally from the true position of each texel on the scooter, and
  * writes the model JSON, the item-model definitions and one texture per paint colour.

It also writes assembly.json into the plugin's resources so the Java rig and the models share a
single source of truth for scales, pivots and wheel geometry.

The texel -> geometry mapping is the exact inverse of vanilla's FaceBakery.defaultFaceUV; see
docs/RESEARCH.md section 4.

Usage:  python3 tools/gen_models.py
"""

import json
import math
import os
import sys
import zlib

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scooter_geometry import (ARTICULATION, FACES, GEOMETRY, PARTS, Box)  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "resourcepack")
NS = "scootermc"
ASSETS = os.path.join(PACK, "assets", NS)

# Model-space budget.  Element coordinates must stay inside [-16, 32]; the model centre is 8, so
# 23 units of half-extent leaves a unit of margin on both sides.
HALF_EXTENT_UNITS = 23.0

PACK_FORMAT = 75           # 1.21.11, read from the server jar's version.json
PACK_PADDING = 1           # texels of gutter between packed faces, so mipmaps cannot bleed
POSTERIZE = 6              # colour quantisation step; keeps the pack small (see render())

COLORS = {
    "orange": "#FF6A00",   # stock KuKirin
    "red": "#E03024",
    "blue": "#2A6FE0",
    "cyan": "#12C2C0",
    "green": "#37B44A",
    "purple": "#8E4AE8",
    "yellow": "#F2C218",
    "white": "#E6E9ED",
    "pink": "#FF4FA3",
}
DEFAULT_COLOR = "orange"


# ============================================================================================
# small helpers
# ============================================================================================

def hex_rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def box_corners(b: Box):
    return [np.array([x, y, z]) for x in (b.p0[0], b.p1[0])
            for y in (b.p0[1], b.p1[1]) for z in (b.p0[2], b.p1[2])]


def rotated_corners(b: Box):
    """Corners after the element rotation, so bounding boxes account for swept fenders."""
    cs = box_corners(b)
    if b.rot is None:
        return cs
    axis, deg, origin = b.rot
    m = rot_matrix(axis, deg)
    o = np.array(origin)
    return [o + m @ (c - o) for c in cs]


def aabb(boxes):
    pts = [c for b in boxes for c in rotated_corners(b)]
    lo = np.min(np.array(pts), axis=0)
    hi = np.max(np.array(pts), axis=0)
    return lo, hi


# ============================================================================================
# UV packing
# ============================================================================================

class ShelfPacker:
    """Rows of decreasing height.  Plenty for a few hundred axis-aligned rectangles."""

    def __init__(self, size):
        self.size = size
        self.shelves = []          # [y, height, used_x]

    def place(self, w, h):
        w += PACK_PADDING
        h += PACK_PADDING
        for shelf in self.shelves:
            if shelf[1] >= h and shelf[2] + w <= self.size:
                x = shelf[2]
                shelf[2] += w
                return x, shelf[0]
        y = sum(s[1] for s in self.shelves)
        if y + h > self.size or w > self.size:
            return None
        self.shelves.append([y, h, w])
        return 0, y


# how a face's two in-plane axes relate to geometry, inverted from FaceBakery.defaultFaceUV
def face_uv_span(b: Box, face):
    """(width, height) of a face in millimetres, in the order the UV axes run."""
    dx = b.p1[0] - b.p0[0]
    dy = b.p1[1] - b.p0[1]
    dz = b.p1[2] - b.p0[2]
    if face in ("down", "up"):
        return dx, dz
    if face in ("north", "south"):
        return dx, dy
    return dz, dy                     # west, east


def face_geometry(b: Box, face, w, h):
    """mm coordinates of every texel centre of a face, shape (h, w) per axis."""
    s = (np.arange(w) + 0.5) / w
    t = (np.arange(h) + 0.5) / h
    S, T = np.meshgrid(s, t)
    x0, y0, z0 = b.p0
    x1, y1, z1 = b.p1
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    if face == "down":
        return x0 + S * dx, np.full(S.shape, y0), z1 - T * dz
    if face == "up":
        return x0 + S * dx, np.full(S.shape, y1), z0 + T * dz
    if face == "north":
        return x1 - S * dx, y1 - T * dy, np.full(S.shape, z0)
    if face == "south":
        return x0 + S * dx, y1 - T * dy, np.full(S.shape, z1)
    if face == "west":
        return np.full(S.shape, x0), y1 - T * dy, z0 + S * dz
    return np.full(S.shape, x1), y1 - T * dy, z1 - S * dz      # east


# ============================================================================================
# procedural materials
# ============================================================================================

# A minimal 5x7 bitmap font, just enough for the wordmark on the stem.
FONT = {
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "I": [".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "G": [".###.", "#...#", "#....", "#..##", "#...#", "#...#", ".###."],
    "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    "2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    " ": ["!....", ".....", ".....", ".....", ".....", ".....", "....."],
}
FONT[" "] = ["....."] * 7


def stamp_text(img, X, Y, text, y0, across0, across, cell, colour, axis="y"):
    """
    Paint `text` reading along +Y, with each glyph's rows running along the other in-plane axis.

    The stem is built from sixteen stacked segments, but every texel knows its true millimetre
    position on the scooter, so a wordmark laid out in millimetres crosses the segment joins
    without the generator having to know they exist.
    """
    advance = cell * 6
    for i, ch in enumerate(text.upper()):
        glyph = FONT.get(ch)
        if glyph is None:
            continue
        base = y0 + i * advance
        for row, bits in enumerate(glyph):
            for col, bit in enumerate(bits):
                if bit != "#":
                    continue
                # glyph column -> up the stem, glyph row -> across the face
                ly0 = base + col * cell
                lx0 = across0 + (6 - row) * cell
                m = ((Y >= ly0) & (Y < ly0 + cell)
                     & (across >= lx0) & (across < lx0 + cell))
                img[m] = colour


def radial_center(b: Box, plane):
    """
    Centre a radial pattern should be measured from.  Discs are built with their rotation origin
    on the axis, which survives translation into the combined model, so the wheels keep painting
    correctly once they are moved out to the axles.  Falls back to the box centre.
    """
    if b.rot is not None:
        o = b.rot[2]
    else:
        o = [(b.p0[i] + b.p1[i]) / 2.0 for i in range(3)]
    return (o[1], o[2]) if plane == "yz" else (o[0], o[1])


def _noise(shape, seed, scale=1.0):
    rng = np.random.default_rng(seed)
    return (rng.random(shape) - 0.5) * 2.0 * scale


def _shade(rgb, factor):
    return np.clip(rgb * factor, 0, 255)


def _solid(shape, color):
    out = np.empty(shape + (3,), dtype=np.float64)
    out[:] = color
    return out


def _edge_darken(img, strength=0.22):
    """Darken a one-texel border so adjacent elements read as separate pieces."""
    h, w = img.shape[:2]
    if h < 3 or w < 3:
        return img
    img = img.copy()
    img[0, :] *= 1 - strength
    img[-1, :] *= 1 - strength
    img[:, 0] *= 1 - strength
    img[:, -1] *= 1 - strength
    return img


FACE_LIGHT = {"up": 1.10, "down": 0.78, "north": 0.92, "south": 0.98, "west": 0.88, "east": 0.88}


def m_flat(base, grain=6.0, edge=0.22, streak_axis=None):
    def paint(X, Y, Z, face, b, ctx, seed):
        img = _solid(X.shape, base)
        img += _noise(X.shape, seed, grain)[..., None]
        if streak_axis is not None:
            axis = {"x": X, "y": Y, "z": Z}[streak_axis]
            img += (np.sin(axis * 0.9 + np.cos(axis * 0.31) * 2.0) * 3.0)[..., None]
        img = _shade(img, FACE_LIGHT[face])
        return _edge_darken(img, edge)
    return paint


def m_stem_face(X, Y, Z, face, b, ctx, seed):
    """
    The stem: anodised aluminium carrying the wordmark and the warning label.

    No edge darkening - the stem is sixteen stacked segments, and a dark border on each would draw
    a ladder up the middle of the scooter.
    """
    img = _solid(X.shape, hex_rgb("#474C55"))
    img += (np.sin(Y * 0.06) * 2.5)[..., None]
    img += _noise(X.shape, seed, 3.5)[..., None]
    if face in ("west", "east"):
        stamp_text(img, X, Y, "KUKIRIN", 640.0, -34.0, Z, 9.0, hex_rgb("#EDEFF2"))
        stamp_text(img, X, Y, "G2", 960.0, -26.0, Z, 7.0, hex_rgb("#EDEFF2"))
        # Yellow hazard label lower down, as on the real stem.
        label = (Y > 470) & (Y < 556) & (np.abs(Z - _centre(b, 2)) < 30)
        img[label] = hex_rgb("#E8C21A")
        bars = label & (np.mod(Y, 14.0) < 6.0)
        img[bars] = hex_rgb("#1A1A18")
    return _shade(img, FACE_LIGHT[face])


def _centre(b, i):
    return (b.p0[i] + b.p1[i]) / 2.0


def m_seg(base, grain=4.0, streak=None):
    """A plain material for segmented tubes: no edge darkening, so joins stay invisible."""
    def paint(X, Y, Z, face, b, ctx, seed):
        img = _solid(X.shape, base)
        img += _noise(X.shape, seed, grain)[..., None]
        if streak is not None:
            axis = {"x": X, "y": Y, "z": Z}[streak]
            img += (np.sin(axis * 0.5) * 3.0)[..., None]
        return _shade(img, FACE_LIGHT[face])
    return paint


def m_chrome_seg(X, Y, Z, face, b, ctx, seed):
    img = _solid(X.shape, hex_rgb("#BCC2CB"))
    img *= (0.80 + 0.34 * np.exp(-((np.mod(Z, 40.0) / 40.0 - 0.4) ** 2) / 0.05))[..., None]
    img += _noise(X.shape, seed, 4.0)[..., None]
    return _shade(img, FACE_LIGHT[face])


def m_grip(X, Y, Z, face, b, ctx, seed):
    """Deck grip tape: near-black with a coarse mineral speckle and moulded tread bars."""
    img = _solid(X.shape, hex_rgb("#33363C"))
    rng = np.random.default_rng(seed)
    speck = rng.random(X.shape)
    img[speck > 0.86] += 26
    img[speck > 0.975] += 44
    # moulded bars every 34 mm along the deck
    bar = (np.mod(Z, 34.0) < 16.0)
    img[bar] *= 1.14
    # a darker border, and orange edge stripes just inside it
    edge = np.abs(X) > 84.0
    img[edge] = ctx["accent"] * 0.85
    img += _noise(X.shape, seed + 1, 4.0)[..., None]
    return _edge_darken(_shade(img, FACE_LIGHT[face]), 0.18)


def m_accent(X, Y, Z, face, b, ctx, seed):
    img = _solid(X.shape, ctx["accent"])
    # a soft specular band across the middle of the piece
    span = Y if face not in ("up", "down") else Z
    lo, hi = span.min(), span.max()
    if hi - lo > 1e-6:
        u = (span - lo) / (hi - lo)
        img *= (0.84 + 0.30 * np.exp(-((u - 0.38) ** 2) / 0.06))[..., None]
    img += _noise(X.shape, seed, 4.0)[..., None]
    return _edge_darken(_shade(img, FACE_LIGHT[face]), 0.24)


def m_chrome(X, Y, Z, face, b, ctx, seed):
    img = _solid(X.shape, hex_rgb("#BCC2CB"))
    span = Y if face not in ("up", "down") else X
    lo, hi = span.min(), span.max()
    if hi - lo > 1e-6:
        u = (span - lo) / (hi - lo)
        img *= (0.62 + 0.72 * np.exp(-((u - 0.3) ** 2) / 0.05)
                + 0.18 * np.exp(-((u - 0.85) ** 2) / 0.02))[..., None]
    img += _noise(X.shape, seed, 5.0)[..., None]
    return _edge_darken(_shade(img, FACE_LIGHT[face]), 0.26)


def m_rubber(X, Y, Z, face, b, ctx, seed):
    """Handlebar grips: ribs running around the bar, i.e. periodic along X."""
    img = _solid(X.shape, hex_rgb("#2C2F35"))
    rib = np.sin(X * 0.55) * 0.5 + 0.5
    img *= (0.82 + 0.34 * rib)[..., None]
    img += _noise(X.shape, seed, 4.0)[..., None]
    return _edge_darken(_shade(img, FACE_LIGHT[face]), 0.2)


def m_spring(X, Y, Z, face, b, ctx, seed):
    """Coil-over: accent-coloured coils with dark gaps, pitched along the shock axis."""
    img = _solid(X.shape, ctx["accent"] * 0.92)
    coil = np.mod((Y + Z * 0.30) / 19.0, 1.0)   # ~19 mm coil pitch
    gap = coil < 0.34
    img[gap] = hex_rgb("#22262B")
    lit = np.abs(coil - 0.62) < 0.10
    img[lit] *= 1.32
    img += _noise(X.shape, seed, 4.0)[..., None]
    return _edge_darken(_shade(img, FACE_LIGHT[face]), 0.2)


def m_tread(X, Y, Z, face, b, ctx, seed):
    """
    Off-road tyre tread.  Painted from the angle around the axle in the element's own frame, so
    the pattern rotates with the wheel and lines up across all four bars of the 16-gon (the lug
    pitch divides the 22.5 degree bar spacing).
    """
    cy, cz = radial_center(b, "yz")
    ang = np.degrees(np.arctan2(Z - cz, Y - cy))
    half_w = max(1.0, (b.p1[0] - b.p0[0]) / 2.0)
    chevron = 4.0 * (X / half_w)                    # skew the lugs across the tyre width
    lug = np.mod(ang + chevron, 11.25) < 6.2
    img = _solid(X.shape, hex_rgb("#1C1F24"))
    img[lug] = hex_rgb("#383D46")
    # centre groove and two shoulder grooves
    img[np.abs(X) < 5.0] = hex_rgb("#15171B")
    img[np.abs(np.abs(X) - half_w * 0.62) < 3.0] *= 0.7
    img += _noise(X.shape, seed, 5.0)[..., None]
    return _shade(img, FACE_LIGHT[face])


def m_wheelside(X, Y, Z, face, b, ctx, seed):
    """
    Wheel sidewall as concentric rings.  Radial symmetry is what makes this work: each of the
    four rotated tyre bars shows a different slice of the same rings, and they line up exactly.
    """
    cy, cz = radial_center(b, "yz")
    r = np.hypot(Y - cy, Z - cz)
    img = _solid(X.shape, hex_rgb("#26292F"))

    def ring(lo, hi, color):
        img[(r >= lo) & (r < hi)] = color

    ring(0, 16, hex_rgb("#6E757F"))          # axle
    ring(16, 50, hex_rgb("#525863"))         # hub motor shell
    ring(50, 70, hex_rgb("#2F333A"))         # spoke face
    ring(70, 78, ctx["accent"])              # rim lip
    ring(78, 84, hex_rgb("#3E434B"))         # bead
    ring(84, 100, hex_rgb("#26292F"))        # sidewall
    ring(100, 200, hex_rgb("#1C1F24"))       # tread shoulder

    ang = np.degrees(np.arctan2(Z - cz, Y - cy))
    # cooling fins on the hub shell
    fin = (r >= 20) & (r < 48) & (np.mod(ang, 30.0) < 13.0)
    img[fin] *= 1.22
    # spokes
    spoke = (r >= 50) & (r < 70) & (np.mod(ang + 15.0, 60.0) < 26.0)
    img[spoke] = ctx["accent"] * 0.55
    # sidewall lettering band
    band = (np.abs(r - 92.0) < 2.0) & (np.mod(ang, 45.0) < 22.0)
    img[band] *= 1.5
    img += _noise(X.shape, seed, 4.0)[..., None]
    return _shade(img, FACE_LIGHT[face])


def m_motor(X, Y, Z, face, b, ctx, seed):
    cy, cz = radial_center(b, "yz")
    img = _solid(X.shape, hex_rgb("#5C636D"))
    ang = np.degrees(np.arctan2(Z - cz, Y - cy))
    fin = np.mod(ang, 22.5) < 10.0
    img[fin] *= 1.18
    r = np.hypot(Y - cy, Z - cz)
    img[r < 15] = hex_rgb("#63696F")
    img += _noise(X.shape, seed, 5.0)[..., None]
    return _edge_darken(_shade(img, FACE_LIGHT[face]), 0.2)


def m_disc(X, Y, Z, face, b, ctx, seed):
    """Drilled brake rotor."""
    cy, cz = radial_center(b, "yz")
    r = np.hypot(Y - cy, Z - cz)
    ang = np.degrees(np.arctan2(Z - cz, Y - cy))
    img = _solid(X.shape, hex_rgb("#B4BAC2"))
    img[r < 24] = hex_rgb("#3A3E44")                      # carrier
    img[(r >= 24) & (r < 33)] = hex_rgb("#6E747C")
    swirl = (np.mod(r, 3.0) < 1.4) & (r >= 36)
    img[swirl] *= 0.90
    hole = (np.abs(r - 46.0) < 6.0) & (np.mod(ang, 24.0) < 9.0)
    img[hole] = hex_rgb("#14161A")                        # drillings
    vane = (r >= 26) & (r < 38) & (np.mod(ang + 20.0, 72.0) < 34.0)
    img[vane] = hex_rgb("#25292E")
    img += _noise(X.shape, seed, 4.0)[..., None]
    return _shade(img, FACE_LIGHT[face])


def m_led(base_hot, base_cool):
    def paint(X, Y, Z, face, b, ctx, seed):
        # the lens discs lie in the XY plane, so the radius runs over X and Y
        cx, cy = radial_center(b, "xy")
        r = np.hypot(X - cx, Y - cy)
        rmax = max(1.0, r.max())
        u = np.clip(r / rmax, 0, 1)
        img = base_hot * (1 - u)[..., None] + base_cool * u[..., None]
        # fresnel rings
        img *= (0.90 + 0.16 * (np.mod(r, 6.0) < 3.0))[..., None]
        img[u > 0.94] = hex_rgb("#26282C")               # bezel
        return np.clip(img, 0, 255)
    return paint


def _seg_digit(X, Y, x0, y0, w, h, digit, thick):
    """Seven-segment digit as a boolean mask, drawn in millimetre space."""
    segs = {
        0: "abcdef", 1: "bc", 2: "abdeg", 3: "abcdg", 4: "bcfg",
        5: "acdfg", 6: "acdefg", 7: "abc", 8: "abcdefg", 9: "abcdfg",
    }[digit]
    x1, y1 = x0 + w, y0 + h
    ym = y0 + h / 2.0
    t = thick
    rects = {
        "a": (x0, y1 - t, x1, y1),                 # top
        "b": (x1 - t, ym, x1, y1),                 # top right
        "c": (x1 - t, y0, x1, ym),                 # bottom right
        "d": (x0, y0, x1, y0 + t),                 # bottom
        "e": (x0, y0, x0 + t, ym),                 # bottom left
        "f": (x0, ym, x0 + t, y1),                 # top left
        "g": (x0, ym - t / 2.0, x1, ym + t / 2.0),  # middle
    }
    mask = np.zeros(X.shape, dtype=bool)
    for s in segs:
        ax0, ay0, ax1, ay1 = rects[s]
        mask |= (X >= ax0) & (X <= ax1) & (Y >= ay0) & (Y <= ay1)
    return mask


def m_lcd(X, Y, Z, face, b, ctx, seed):
    """Dash: battery bar, big speed readout, mode letter, headlight tell-tale."""
    cy = (b.p0[1] + b.p1[1]) / 2.0
    img = _solid(X.shape, hex_rgb("#08212A"))
    on = hex_rgb("#49E8DC")
    dim = hex_rgb("#123642")

    # bezel
    img[(np.abs(X) > 54) | (np.abs(Y - cy) > 32)] = hex_rgb("#0A0C0E")

    # battery: five segments along the top
    for i in range(5):
        x0 = -44 + i * 18
        seg = (X >= x0) & (X <= x0 + 14) & (Y >= cy + 20) & (Y <= cy + 28)
        img[seg] = on if i < 4 else dim
    # battery nose
    img[(X >= 48) & (X <= 52) & (np.abs(Y - (cy + 24)) <= 3)] = on

    # speed, two digits centred
    for i, d in enumerate((4, 5)):
        mask = _seg_digit(X, Y, -30 + i * 32, cy - 16, 24, 30, d, 5.0)
        img[mask] = on
    # km/h tick marks down the right
    for i in range(4):
        y0 = cy - 14 + i * 8
        img[(X >= 38) & (X <= 48) & (Y >= y0) & (Y <= y0 + 3)] = dim if i else on

    # mode letter block and headlight tell-tale, bottom corners
    img[(X >= -48) & (X <= -34) & (Y >= cy - 28) & (Y <= cy - 18)] = on
    img[(X >= 34) & (X <= 48) & (Y >= cy - 28) & (Y <= cy - 18)] = dim

    # scanlines
    img *= (0.94 + 0.08 * (np.mod(Y, 2.0) < 1.0))[..., None]
    return np.clip(img, 0, 255)


def m_reflector(X, Y, Z, face, b, ctx, seed):
    img = _solid(X.shape, hex_rgb("#8E1014"))
    cell = (np.mod(X, 5.0) < 2.4) ^ (np.mod(Y, 5.0) < 2.4)
    img[cell] *= 1.55
    return _edge_darken(np.clip(img, 0, 255), 0.2)


MATERIALS = {
    "frame": m_flat(hex_rgb("#474C55"), grain=6.0, streak_axis="z"),
    "frame_dark": m_flat(hex_rgb("#2A2E34"), grain=5.0),
    "plastic": m_flat(hex_rgb("#34383F"), grain=4.0),
    "grip": m_grip,
    "stem_face": m_stem_face,
    "tube_dark": m_seg(hex_rgb("#2A2E34")),
    "chrome_seg": m_chrome_seg,
    "cable": m_seg(hex_rgb("#26292E"), grain=3.0),
    "rubber": m_rubber,
    "accent": m_accent,
    "chrome": m_chrome,
    "spring": m_spring,
    "tread": m_tread,
    "wheelside": m_wheelside,
    "motor": m_motor,
    "disc": m_disc,
    "disc_edge": m_flat(hex_rgb("#8A9098"), grain=4.0, edge=0.1),
    "lcd": m_lcd,
    "reflector": m_reflector,
    "led_white": m_led(hex_rgb("#FFFDF0"), hex_rgb("#FFC46A")),
    "led_red": m_led(hex_rgb("#FF8C7A"), hex_rgb("#9E0F0A")),
}

# Materials whose look depends on the paint colour; parts that use none of them need only one
# texture instead of one per colour.
TINTED_MATERIALS = {"accent", "grip", "spring", "wheelside"}


# ============================================================================================
# part builder
# ============================================================================================

def build_part(key, boxes, pivot, tex_size, target_tpu):
    lo, hi = aabb(boxes)
    if pivot is None:
        pivot = tuple((lo + hi) / 2.0)
    pivot = np.array(pivot, dtype=np.float64)

    # Model files store an element's corners BEFORE its rotation is applied, and a steeply rotated
    # element (a 45 degree fork leg, say) can sit much further from the pivot un-rotated than it
    # ever does in its final position.  The scale has to satisfy whichever is larger, or the raw
    # coordinates overflow Minecraft's [-16, 32] element range.
    corners = [c for b in boxes for c in (rotated_corners(b) + box_corners(b))]
    half = float(np.max(np.abs(np.array(corners) - pivot)))
    mm_per_unit = max(half / HALF_EXTENT_UNITS, 1e-6)

    def to_units(p):
        return 8.0 + (np.array(p) - pivot) / mm_per_unit

    # ---- pack every face --------------------------------------------------------------------
    faces = []
    for b in boxes:
        for face in b.faces():
            wmm, hmm = face_uv_span(b, face)
            if wmm <= 1e-6 or hmm <= 1e-6:
                continue
            faces.append((b, face, wmm / mm_per_unit, hmm / mm_per_unit))

    tpu = target_tpu
    placed = None
    while tpu > 0.6:
        packer = ShelfPacker(tex_size)
        out, ok = [], True
        for b, face, wu, hu in sorted(faces, key=lambda f: -f[3]):
            tw = max(1, int(round(wu * tpu)))
            th = max(1, int(round(hu * tpu)))
            spot = packer.place(tw, th)
            if spot is None:
                ok = False
                break
            out.append((b, face, spot[0], spot[1], tw, th))
        if ok:
            placed = out
            break
        tpu *= 0.88
    if placed is None:
        raise RuntimeError(f"could not pack part '{key}' into {tex_size}px")

    # ---- model JSON -------------------------------------------------------------------------
    uv_of = {}
    elements = []
    for b in boxes:
        f0, f1 = to_units(b.p0), to_units(b.p1)
        for v in (*f0, *f1):
            if v < -16.001 or v > 32.001:
                raise RuntimeError(f"{key}/{b.name}: coordinate {v:.2f} outside [-16, 32]")
        el = {"name": b.name,
              "from": [round(float(v), 4) for v in f0],
              "to": [round(float(v), 4) for v in f1],
              "faces": {}}
        if b.rot is not None:
            axis, deg, origin = b.rot
            el["rotation"] = {"origin": [round(float(v), 4) for v in to_units(origin)],
                              "axis": axis, "angle": deg}
        elements.append((b, el))

    for b, face, x, y, tw, th in placed:
        uv_of[(id(b), face)] = (x, y, tw, th)
    for b, el in elements:
        for face in b.faces():
            spot = uv_of.get((id(b), face))
            if spot is None:
                continue
            x, y, tw, th = spot
            k = 16.0 / tex_size
            el["faces"][face] = {
                "uv": [round(x * k, 5), round(y * k, 5),
                       round((x + tw) * k, 5), round((y + th) * k, 5)],
                "texture": "#body",
            }

    model = {
        "texture_size": [tex_size, tex_size],
        "textures": {"body": f"{NS}:scooter/{key}", "particle": f"{NS}:scooter/{key}"},
        "elements": [el for _, el in elements],
    }

    # ---- textures ---------------------------------------------------------------------------
    used_materials = {b.material(face) for b, face, *_ in placed}
    tinted = bool(used_materials & TINTED_MATERIALS)

    def render(accent_hex):
        img = np.zeros((tex_size, tex_size, 4), dtype=np.uint8)
        ctx = {"accent": hex_rgb(accent_hex), "pivot": pivot}
        for i, (b, face, x, y, tw, th) in enumerate(placed):
            X, Y, Z = face_geometry(b, face, tw, th)
            painter = MATERIALS[b.material(face)]
            seed = zlib.crc32(f"{key}/{b.name}/{face}".encode()) & 0xFFFF
            rgb = painter(X, Y, Z, face, b, ctx, seed=seed)
            # Posterising costs nothing visually at these sizes but roughly quarters the PNG:
            # procedural grain is high-entropy and deflate cannot do anything with it otherwise.
            rgb = np.round(np.clip(rgb, 0, 255) / POSTERIZE) * POSTERIZE
            img[y:y + th, x:x + tw, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
            img[y:y + th, x:x + tw, 3] = 255
        return Image.fromarray(img, "RGBA")

    return {
        "model": model,
        "render": render,
        "tinted": tinted,
        "scale": 16.0 * mm_per_unit / 1000.0,
        "pivot_mm": [round(float(v), 3) for v in pivot],
        "mm_per_unit": mm_per_unit,
        "tpu": tpu,
        "faces": len(placed),
        "elements": len(elements),
    }


# ============================================================================================
# output
# ============================================================================================

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, indent=2)
        fh.write("\n")


GUI_DISPLAY = {
    "gui": {"rotation": [30, 215, 0], "translation": [0, 1.5, 0], "scale": [0.40, 0.40, 0.40]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.26, 0.26, 0.26]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3.5, 1.5],
                              "scale": [0.24, 0.24, 0.24]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3.5, 1.5],
                             "scale": [0.24, 0.24, 0.24]},
    "firstperson_righthand": {"rotation": [0, 60, 0], "translation": [1, 2, 0],
                              "scale": [0.30, 0.30, 0.30]},
    "firstperson_lefthand": {"rotation": [0, 300, 0], "translation": [1, 2, 0],
                             "scale": [0.30, 0.30, 0.30]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.5, 0.5, 0.5]},
}


def item_model_name(part, color=None):
    stem = "scooter" if part == "scooter" else f"part_{part}"
    return stem if color is None else f"{stem}_{color}"


def main():
    built = {}
    for key, (fn, pivot, tex, tpu, _) in PARTS.items():
        built[key] = build_part(key, fn(), pivot, tex, tpu)
        b = built[key]
        print(f"  {key:<11} {b['elements']:>3} elements  {b['faces']:>4} faces  "
              f"{tex}px @ {b['tpu']:.2f} tex/unit  scale {b['scale']:.5f}  "
              f"{b['mm_per_unit']:.2f} mm/unit  {'tinted' if b['tinted'] else 'plain'}")

    # pack metadata
    write_json(os.path.join(PACK, "pack.mcmeta"), {
        "pack": {
            "pack_format": PACK_FORMAT,
            "description": "ScooterMC — electric scooters for Paper 1.21.11",
        },
    })

    for key, info in built.items():
        # geometry model, shared by every paint colour
        write_json(os.path.join(ASSETS, "models", "scooter", f"{key}_base.json"), info["model"])
        colors = list(COLORS) if info["tinted"] else [None]
        for color in colors:
            tex_name = key if color is None else f"{key}_{color}"
            child = {"parent": f"{NS}:scooter/{key}_base",
                     "textures": {"body": f"{NS}:scooter/{tex_name}",
                                  "particle": f"{NS}:scooter/{tex_name}"}}
            if key == "scooter":
                child["display"] = GUI_DISPLAY
            model_name = key if color is None else f"{key}_{color}"
            write_json(os.path.join(ASSETS, "models", "scooter", f"{model_name}.json"), child)
            # item model definition (1.21.4+ format)
            write_json(os.path.join(ASSETS, "items", f"{item_model_name(key, color)}.json"),
                       {"model": {"type": "minecraft:model",
                                  "model": f"{NS}:scooter/{model_name}"}})
            img = info["render"](COLORS[color] if color else COLORS[DEFAULT_COLOR])
            out = os.path.join(ASSETS, "textures", "scooter", f"{tex_name}.png")
            os.makedirs(os.path.dirname(out), exist_ok=True)
            img.save(out, optimize=True)

    # assembly manifest consumed by the plugin
    parts_meta = {}
    for key, info in built.items():
        art = ARTICULATION.get(key, {"steer": False, "roll": False})
        parts_meta[key] = {
            "scale": round(info["scale"], 6),
            "pivot_mm": info["pivot_mm"],
            "tinted": info["tinted"],
            "steer": art["steer"],
            "roll": art["roll"],
            "item_model": f"{NS}:{item_model_name(key, '%s') if info['tinted'] else item_model_name(key)}",
        }
    write_json(os.path.join(ROOT, "src", "main", "resources", "assembly.json"), {
        "_comment": "Generated by tools/gen_models.py - do not edit by hand.",
        "geometry": GEOMETRY,
        "colors": list(COLORS),
        "default_color": DEFAULT_COLOR,
        "rig_parts": [k for k in PARTS if k not in ("wheel", "scooter")],
        "wheel_part": "wheel",
        "full_part": "scooter",
        "parts": parts_meta,
    })

    total = sum(os.path.getsize(os.path.join(dp, f))
                for dp, _, fs in os.walk(PACK) for f in fs)
    print(f"\n  resource pack: {total / 1024:.0f} KiB in {PACK}")


if __name__ == "__main__":
    main()
