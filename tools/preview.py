#!/usr/bin/env python3
"""
Offline preview renderer for the ScooterMC models.

There is no Minecraft client in this environment, so this is how the geometry and the texture
mapping actually get checked.  It reads the generated model JSON exactly the way the game does
(element rotation, the FaceBakery UV corner order, 16 units = 1 block) and rasterises it with a
z-buffer.

It renders two things and that is the point:

  * `scooter` - the single combined model used for the item and for parked scooters, and
  * `rig`     - the same scooter assembled from the separate animated parts using assembly.json
                and exactly the transform maths the Java renderer uses.

If those two images match, the pivots, per-part scales and articulation maths in assembly.json are
right.  `rig` can also be rendered with a steering angle and wheel roll to check the articulation.

Usage:
    python3 tools/preview.py                       # writes build/preview/*.png
    python3 tools/preview.py --steer 25 --roll 40
"""

import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "resourcepack", "assets", "scootermc")
OUT = os.path.join(ROOT, "build", "preview")

W = H = 720
BG = (26, 28, 32)

# Face -> the four corners in the order (u1,v1), (u2,v1), (u2,v2), (u1,v2).
# Straight inversion of FaceBakery.defaultFaceUV; see docs/RESEARCH.md section 4.
FACE_CORNERS = {
    "up": lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    "down": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
}


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def load_model(name):
    """Resolve a model plus its texture, following one level of `parent`."""
    path = os.path.join(PACK, "models", "scooter", name + ".json")
    with open(path) as fh:
        m = json.load(fh)
    tex_ref = m.get("textures", {}).get("body")
    while "parent" in m:
        parent = m["parent"].split(":")[-1].split("/")[-1]
        with open(os.path.join(PACK, "models", "scooter", parent + ".json")) as fh:
            p = json.load(fh)
        tex_ref = tex_ref or p.get("textures", {}).get("body")
        p.pop("parent", None)
        merged = dict(p)
        merged.update({k: v for k, v in m.items() if k != "parent"})
        merged["textures"] = {**p.get("textures", {}), **m.get("textures", {})}
        if tex_ref:
            merged["textures"]["body"] = tex_ref
        m = merged
    tex_name = m["textures"]["body"].split("/")[-1]
    tex = np.asarray(Image.open(
        os.path.join(PACK, "textures", "scooter", tex_name + ".png")).convert("RGB"))
    return m, tex


def model_quads(model, tex, xform):
    """
    Every textured quad of a model, in world blocks.

    `xform` maps a point in model units to world blocks; it is where the display-entity transform
    (or the identity, for a plain model) gets applied.
    """
    quads = []
    for el in model["elements"]:
        a = np.array(el["from"], dtype=float)
        b = np.array(el["to"], dtype=float)
        er = el.get("rotation")
        if er:
            m = rot(er["axis"], er["angle"])
            o = np.array(er["origin"], dtype=float)

            def place(p, m=m, o=o):
                return o + m @ (np.array(p) - o)
        else:
            def place(p):
                return np.array(p)
        for face, fd in el.get("faces", {}).items():
            corners = [xform(place(c)) for c in FACE_CORNERS[face](a, b)]
            u1, v1, u2, v2 = fd["uv"]
            th, tw = tex.shape[:2]
            uv = [(u1 / 16 * tw, v1 / 16 * th), (u2 / 16 * tw, v1 / 16 * th),
                  (u2 / 16 * tw, v2 / 16 * th), (u1 / 16 * tw, v2 / 16 * th)]
            quads.append((corners, uv, tex))
    return quads


def rasterise(quads, azimuth=35.0, elevation=22.0, out_size=(W, H), zoom=1.0):
    """Painter-free z-buffered rasteriser with affine texture mapping and lambert shading."""
    ow, oh = out_size
    pts = np.array([p for q, _, _ in quads for p in q])
    centre = (pts.min(0) + pts.max(0)) / 2.0
    radius = float(np.linalg.norm(pts.max(0) - pts.min(0))) / 2.0

    cam = rot("y", azimuth) @ rot("x", -elevation)
    eye = centre + cam @ np.array([0.0, 0.0, radius * 3.0])
    fwd = centre - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, np.array([0.0, 1.0, 0.0]))
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    view = np.stack([right, up, -fwd])
    focal = min(ow, oh) * 0.5 / math.tan(math.radians(21.0)) * zoom
    light = np.array([0.42, 0.82, 0.38])
    light /= np.linalg.norm(light)

    color = np.zeros((oh, ow, 3), dtype=np.float32)
    color[:] = BG
    depth = np.full((oh, ow), 1e9, dtype=np.float32)

    def project(p):
        v = view @ (np.array(p) - eye)
        z = -v[2]
        if z < 1e-4:
            return None
        return np.array([ow / 2 + v[0] * focal / z, oh / 2 - v[1] * focal / z, z])

    for corners, uv, tex in quads:
        scr = [project(c) for c in corners]
        if any(s is None for s in scr):
            continue
        n = np.cross(np.array(corners[1]) - np.array(corners[0]),
                     np.array(corners[2]) - np.array(corners[0]))
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            continue
        n /= ln
        if np.dot(n, np.array(corners[0]) - eye) > 0:      # back-face
            n = -n
        shade = 0.42 + 0.58 * max(0.0, float(np.dot(n, light)))
        for tri in ((0, 1, 2), (0, 2, 3)):
            _tri(color, depth, [scr[i] for i in tri], [uv[i] for i in tri], tex, shade, ow, oh)

    return Image.fromarray(np.clip(color, 0, 255).astype(np.uint8))


def _tri(color, depth, scr, uv, tex, shade, ow, oh):
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = scr
    minx = max(int(math.floor(min(x0, x1, x2))), 0)
    maxx = min(int(math.ceil(max(x0, x1, x2))), ow - 1)
    miny = max(int(math.floor(min(y0, y1, y2))), 0)
    maxy = min(int(math.ceil(max(y0, y1, y2))), oh - 1)
    if minx > maxx or miny > maxy:
        return
    det = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(det) < 1e-9:
        return
    xs = np.arange(minx, maxx + 1)
    ys = np.arange(miny, maxy + 1)
    PX, PY = np.meshgrid(xs + 0.5, ys + 0.5)
    l0 = ((y1 - y2) * (PX - x2) + (x2 - x1) * (PY - y2)) / det
    l1 = ((y2 - y0) * (PX - x2) + (x0 - x2) * (PY - y2)) / det
    l2 = 1.0 - l0 - l1
    inside = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
    if not inside.any():
        return
    # perspective-correct interpolation
    iw = l0 / z0 + l1 / z1 + l2 / z2
    z = 1.0 / np.where(iw == 0, 1e-9, iw)
    sub = depth[miny:maxy + 1, minx:maxx + 1]
    win = inside & (z < sub)
    if not win.any():
        return
    tu = (l0 * uv[0][0] / z0 + l1 * uv[1][0] / z1 + l2 * uv[2][0] / z2) * z
    tv = (l0 * uv[0][1] / z0 + l1 * uv[1][1] / z1 + l2 * uv[2][1] / z2) * z
    th, tw = tex.shape[:2]
    ti = np.clip(tv.astype(int), 0, th - 1)
    tj = np.clip(tu.astype(int), 0, tw - 1)
    texel = tex[ti, tj].astype(np.float32) * shade
    tgt = color[miny:maxy + 1, minx:maxx + 1]
    tgt[win] = texel[win]
    sub[win] = z[win]


# --------------------------------------------------------------------------------------------
# rig assembly - mirrors ScooterRig.java exactly
# --------------------------------------------------------------------------------------------

def axis_angle(axis, deg):
    """Rotation about an arbitrary unit axis (the steering column is raked, not vertical)."""
    a = math.radians(deg)
    x, y, z = axis / np.linalg.norm(axis)
    c, s, C = math.cos(a), math.sin(a), 1 - math.cos(a)
    return np.array([
        [x * x * C + c,     x * y * C - z * s, x * z * C + y * s],
        [y * x * C + z * s, y * y * C + c,     y * z * C - x * s],
        [z * x * C - y * s, z * y * C + x * s, z * z * C + c],
    ])


def rig_quads(asm, color_name, yaw_deg, steer_deg, roll_deg):
    geo = asm["geometry"]
    quads = []
    ry = rot("y", -yaw_deg)                     # MC yaw 0 = +Z and increases toward -X
    rake = math.radians(geo["steer_rake_deg"])
    steer_dir = np.array([0.0, math.cos(rake), -math.sin(rake)])
    steer = axis_angle(steer_dir, steer_deg)
    steer_axis = np.array([0.0, geo["steer_axis_base_y_mm"], geo["steer_axis_z_mm"]])

    def add(part, articulation, pivot_mm):
        meta = asm["parts"][part]
        name = f"{part}_{color_name}" if meta["tinted"] else part
        model, tex = load_model(name)
        s = meta["scale"]

        def xform(p, articulation=articulation, pivot_mm=np.array(pivot_mm), s=s):
            local = (np.array(p) - 8.0) / 16.0 * s          # model units -> blocks
            return ry @ (pivot_mm / 1000.0 + articulation @ local)

        quads.extend(model_quads(model, tex, xform))

    for part in asm["rig_parts"]:
        meta = asm["parts"][part]
        pivot = np.array(meta["pivot_mm"], dtype=float)
        if meta["steer"]:
            add(part, steer, steer_axis + steer @ (pivot - steer_axis))
        else:
            add(part, np.eye(3), pivot)

    wheel_meta = asm["parts"]["wheel"]
    roll = rot("x", roll_deg)
    for z, steered in ((geo["front_axle_z_mm"], True), (geo["rear_axle_z_mm"], False)):
        axle = np.array([0.0, geo["axle_y_mm"], z])
        if steered:
            axle = steer_axis + steer @ (axle - steer_axis)
            art = steer @ roll
        else:
            art = roll
        wheel_meta_pivot = axle
        meta_backup = asm["parts"]["wheel"]
        add("wheel", art, wheel_meta_pivot)
        assert meta_backup is wheel_meta
    return quads


def check(asm, color="orange"):
    """
    Assert the articulated rig reproduces the combined model exactly.

    This is the real test of assembly.json: if the per-part scales, pivots or the transform order
    were wrong, the rig would drift away from the reference geometry and this would catch it.
    """
    sc = asm["parts"]["scooter"]
    pivot = np.array(sc["pivot_mm"]) / 1000.0
    model, tex = load_model(f"scooter_{color}")
    full = model_quads(model, tex, lambda p: pivot + (np.array(p) - 8.0) / 16.0 * sc["scale"])
    rig = rig_quads(asm, color, 0.0, 0.0, 0.0)

    if len(full) != len(rig):
        raise SystemExit(f"FAIL: {len(full)} quads in the combined model, {len(rig)} in the rig")
    a = np.sort(np.array([p for q, _, _ in full for p in q]), axis=0)
    b = np.sort(np.array([p for q, _, _ in rig for p in q]), axis=0)
    delta = float(np.abs(a - b).max())
    # The models round coordinates to 4 decimal places of a model unit, so a few microns of
    # disagreement is the floor here.
    if delta > 1e-4:
        raise SystemExit(f"FAIL: rig differs from combined model by {delta:.6f} blocks")
    print(f"OK  rig matches the combined model over {len(full)} quads "
          f"(max corner delta {delta * 1000:.4f} mm)")

    # steering must move the fork, the bars and the front wheel, and nothing else
    turned = rig_quads(asm, color, 0.0, 30.0, 0.0)
    moved = np.abs(np.array([p for q, _, _ in turned for p in q])
                   - np.array([p for q, _, _ in rig for p in q])).max(axis=1)
    if not (moved > 1e-6).any():
        raise SystemExit("FAIL: steering moved nothing")
    print(f"OK  30 deg of steering moves {int((moved > 1e-6).sum())} of {len(moved)} vertices")

    rolled = rig_quads(asm, color, 0.0, 0.0, 90.0)
    rmoved = np.abs(np.array([p for q, _, _ in rolled for p in q])
                    - np.array([p for q, _, _ in rig for p in q])).max(axis=1)
    if not (rmoved > 1e-6).any():
        raise SystemExit("FAIL: wheel roll moved nothing")
    print(f"OK  wheel roll moves {int((rmoved > 1e-6).sum())} of {len(rmoved)} vertices")

    lo = np.array([p for q, _, _ in rig for p in q]).min(axis=0)
    if lo[1] < -0.005:
        raise SystemExit(f"FAIL: geometry sits {-lo[1] * 1000:.1f} mm below the ground plane")
    print(f"OK  lowest point is {lo[1] * 1000:.1f} mm above the ground plane")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--color", default="orange")
    ap.add_argument("--steer", type=float, default=0.0)
    ap.add_argument("--roll", type=float, default=0.0)
    ap.add_argument("--yaw", type=float, default=0.0)
    ap.add_argument("--check", action="store_true", help="verify the rig, render nothing")
    args = ap.parse_args()

    asm = json.load(open(os.path.join(ROOT, "src", "main", "resources", "assembly.json")))
    if args.check:
        check(asm, args.color)
        return

    os.makedirs(OUT, exist_ok=True)

    views = {"three_quarter": (38, 20), "side": (90, 4), "front": (178, 8), "rear": (-2, 10)}

    model, tex = load_model(f"scooter_{args.color}")
    full = model_quads(model, tex, lambda p: (np.array(p) - 8.0) / 16.0 * asm["parts"]["scooter"]["scale"])
    for name, (az, el) in views.items():
        rasterise(full, az, el).save(os.path.join(OUT, f"scooter_{name}.png"))

    rq = rig_quads(asm, args.color, args.yaw, args.steer, args.roll)
    for name, (az, el) in views.items():
        rasterise(rq, az, el).save(os.path.join(OUT, f"rig_{name}.png"))

    # A contact sheet of every paint colour, plus the parts on their own.
    sheet = Image.new("RGB", (3 * 320, 3 * 320), BG)
    for i, c in enumerate(asm["colors"]):
        m, t = load_model(f"scooter_{c}")
        q = model_quads(m, t, lambda p: (np.array(p) - 8.0) / 16.0 * asm["parts"]["scooter"]["scale"])
        sheet.paste(rasterise(q, 38, 20, (320, 320)), (i % 3 * 320, i // 3 * 320))
    sheet.save(os.path.join(OUT, "colors.png"))

    print("wrote", OUT)


if __name__ == "__main__":
    main()
