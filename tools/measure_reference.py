#!/usr/bin/env python3
"""
Reads the scooter's proportions off a studio photograph.

A spec sheet gives overall size, wheel size and weight, but not the things that actually decide
whether a model reads as the right scooter: how far the stem leans, where the wheels sit under it,
how high the deck is, where the headlight and the folding collar are.  Guessing those is what made
the first version of the model look wrong.

This threshold-and-measure pass is how the numbers at the top of tools/scooter_geometry.py were
obtained.  Point it at a side-on photo against a plain background:

    python3 tools/measure_reference.py reference.jpg --height-mm 1315

Everything is scaled from one known dimension - overall height by default, since that is the figure
manufacturers quote most reliably.
"""

import argparse

import numpy as np
from PIL import Image


def load(path):
    rgb = np.asarray(Image.open(path).convert("RGB")).astype(int)
    lum = rgb.mean(axis=2)
    ink = lum < 170
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    # The accent colour is the easiest way to find the suspension links and the folding collar.
    accent = (r > 140) & (r - g > 45) & (g - b > 10)
    return ink, accent


def bbox(mask, label, x0=0, x1=None, y0=0, y1=None):
    sub = mask[y0:y1, x0:x1]
    ys, xs = np.nonzero(sub)
    if len(ys) == 0:
        print(f"{label}: nothing found")
        return None
    out = (xs.min() + x0, xs.max() + x0, ys.min() + y0, ys.max() + y0)
    print(f"{label}: x {out[0]}..{out[1]}  y {out[2]}..{out[3]}")
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("--height-mm", type=float, default=1315.0,
                    help="known overall height of the real machine")
    ap.add_argument("--stem-rows", type=int, nargs=2, default=(150, 620),
                    help="row range that contains only the stem, for the rake fit")
    args = ap.parse_args()

    ink, accent = load(args.image)
    h, w = ink.shape
    whole = bbox(ink, "whole scooter")
    ground, top = whole[3], whole[2]
    scale = args.height_mm / (ground - top)          # mm per pixel

    def mm(px):
        return px * scale

    print(f"\nscale        {scale:.3f} mm/px")
    print(f"overall      {mm(whole[1] - whole[0]):.0f} mm long x {args.height_mm:.0f} mm high")

    # ---- stem rake: fit a line through the widest dark run on each row ------------------------
    pts = []
    for y in range(args.stem_rows[0], args.stem_rows[1], 10):
        row = np.nonzero(ink[y])[0]
        if len(row) == 0:
            continue
        groups = np.split(row, np.nonzero(np.diff(row) > 12)[0] + 1)
        g = max(groups, key=len)
        pts.append((y, (g[0] + g[-1]) / 2.0))
    if len(pts) > 2:
        P = np.array(pts)
        slope = np.polyfit(P[:, 0], P[:, 1], 1)[0]
        print(f"stem rake    {np.degrees(np.arctan(-slope)):.1f} deg back from vertical")

    # ---- deck: the long horizontal band of ink between the wheels ------------------------------
    mid = ink[:, int(w * 0.35):int(w * 0.6)]
    solid = np.nonzero(mid.sum(axis=1) > 0.6 * mid.shape[1])[0]
    if len(solid):
        print(f"deck top     {mm(ground - solid.min()):.0f} mm    "
              f"deck bottom {mm(ground - solid.max()):.0f} mm")

    # ---- accents: collar high on the stem, suspension links down by the wheels -----------------
    ys, xs = np.nonzero(accent)
    if len(ys):
        print("\naccent-coloured parts, by height above the ground:")
        order = np.argsort(ys)
        band_start = None
        prev = None
        for y in ys[order]:
            if prev is None or y - prev > 25:
                if band_start is not None:
                    print(f"   band {mm(ground - prev):.0f}..{mm(ground - band_start):.0f} mm")
                band_start = y
            prev = y
        if band_start is not None:
            print(f"   band {mm(ground - prev):.0f}..{mm(ground - band_start):.0f} mm")

    print("\nFeed these into tools/scooter_geometry.py, then check the result with")
    print("    python3 tools/gen_models.py && python3 tools/preview.py")


if __name__ == "__main__":
    main()
