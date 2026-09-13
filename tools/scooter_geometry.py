"""
Geometry of the ScooterMC scooter, modelled on a KuKirin G2 Pro.

Everything here is in REAL MILLIMETRES so it can be checked against the manufacturer's spec sheet
and against a photograph.  The design coordinate system is:

    +X = rider's right        +Y = up        +Z = direction of travel

with the origin on the ground, directly below the middle of the wheelbase.  +Z lines up with the
Minecraft item-model axes at yaw 0, since MC yaw 0 faces +Z.

The X axis does NOT line up, and that is what MIRROR_X is for.  At yaw 0 the rider faces south,
so *their* right hand points west, i.e. towards -X in world coordinates - the opposite of the
model's +X.  Left alone, every asymmetric detail would end up on the wrong side: the thumb
throttle on the left grip, the brake disc and kickstand on the offside.  So the whole design is
mirrored in X on the way out, letting this file stay written in natural rider-relative terms.

Reference dimensions (KuKirin G2 Pro): unfolded 1187 x 620 x 1315 mm, 9 x 3 in tyres, 120 mm
discs, 118 x 73 mm dash.  The proportions that no spec sheet gives were measured off the
manufacturer's studio side-view photograph by thresholding it and reading pixel positions
(tools/measure_reference.py):

    stem rake       10.1 deg back from vertical   <- the single biggest shape cue
    deck top        ~196 mm above the ground
    wheelbase       ~960 mm
    headlight       ~430 mm up, low on the steering column, not high on the stem
    fold collar     ~980 mm up

Two departures from the real machine, both deliberate:

  * The deck sits at 165 mm rather than 196 mm.  A Minecraft player's feet are at world Y = 0, so
    deck height is exactly how far the rider's feet sink into the deck; 165 mm keeps that to about
    two and a half texels while still reading as a real stand-on deck.
  * Tyres are drawn a little wider than 3 in, because at this scale a scale-width tyre reads as a
    thin disc rather than the fat off-road rubber the scooter actually has.
"""

import math
from dataclasses import dataclass
from typing import Optional

# --------------------------------------------------------------------------------------------
# Key dimensions
# --------------------------------------------------------------------------------------------

TYRE_D = 229.0                  # 9 inch
TYRE_R = TYRE_D / 2.0           # 114.5
TYRE_W = 84.0                   # 3 in nominal, drawn slightly fat (see module docstring)

AXLE_Y = TYRE_R                 # the tyres stand on the ground
WHEELBASE = 960.0
FRONT_AXLE_Z = WHEELBASE / 2.0  # +450
REAR_AXLE_Z = -WHEELBASE / 2.0  # -450

DECK_TOP = 165.0
DECK_BOTTOM = 92.0
DECK_HALF_W = 100.0
DECK_Z0 = -230.0
DECK_Z1 = 300.0

# ---- steering column ------------------------------------------------------------------------
# The stem leans back 10 degrees, and it is the whole column - stem, bars, fork and front wheel -
# that turns, not just the fork.  The steering axis is therefore tilted too, and the Java rig
# rotates about that tilted axis rather than a vertical one, so the bars turn in place instead of
# swinging sideways.
STEM_RAKE_DEG = 10.0
STEM_BASE_Y = 205.0             # a point on the steering axis
STEER_AXIS_Z = 410.0            # z of the steering axis at STEM_BASE_Y
STEM_TOP_Y = 1150.0
BAR_Y = 1215.0

FOLD_COLLAR_Y = 975.0
HEADLIGHT_Y = 430.0

BAR_HALF_W = 285.0
DASH_Y = 1268.0

TOTAL_H = 1315.0

FACES = ("down", "up", "north", "south", "west", "east")


def axis_z(y):
    """Z of the steering axis at height y - the centreline the whole column is built around."""
    return STEER_AXIS_Z - math.tan(math.radians(STEM_RAKE_DEG)) * (y - STEM_BASE_Y)


@dataclass
class Box:
    """One model element.  `mat` is either a material name or a per-face dict with a '*' default."""

    name: str
    p0: tuple            # (x, y, z) minimum corner, mm
    p1: tuple            # (x, y, z) maximum corner, mm
    mat: object
    rot: Optional[tuple] = None          # (axis, degrees, (ox, oy, oz) in mm)
    skip: tuple = ()                     # faces not to emit at all

    def material(self, face: str) -> str:
        if isinstance(self.mat, str):
            return self.mat
        return self.mat.get(face, self.mat["*"])

    def faces(self):
        return [f for f in FACES if f not in self.skip]


def box(name, x0, y0, z0, x1, y1, z1, mat, rot=None, skip=()):
    return Box(name, (min(x0, x1), min(y0, y1), min(z0, z1)),
               (max(x0, x1), max(y0, y1), max(z0, z1)), mat, rot, skip)


def mirror_x(b: Box, suffix="_m") -> Box:
    """Mirror a box across the X=0 plane (for the left/right pairs, and for MIRROR_X)."""
    x0, x1 = -b.p1[0], -b.p0[0]
    rot = None
    if b.rot is not None:
        axis, ang, o = b.rot
        # Mirroring X flips the sense of rotations about Y and Z, and moves the origin.
        ang = -ang if axis in ("y", "z") else ang
        rot = (axis, ang, (-o[0], o[1], o[2]))
    mat = b.mat
    if isinstance(mat, dict):
        swapped = {k: v for k, v in mat.items() if k not in ("west", "east")}
        if "west" in mat:
            swapped["east"] = mat["west"]
        if "east" in mat:
            swapped["west"] = mat["east"]
        mat = swapped
    skip = tuple({"west": "east", "east": "west"}.get(f, f) for f in b.skip)
    return Box(b.name + suffix, (x0, b.p0[1], b.p0[2]), (x1, b.p1[1], b.p1[2]), mat, rot, skip)


MIRROR_X = True


def apply_mirror(boxes):
    """Flip the design into Minecraft's world axes; see the module docstring."""
    if not MIRROR_X:
        return boxes
    return [mirror_x(b, suffix="") for b in boxes]


def pair(b: Box):
    """A box and its mirror image - the left/right halves of a symmetric detail."""
    return [b, mirror_x(b)]


def translate(boxes, dx=0.0, dy=0.0, dz=0.0, prefix=""):
    out = []
    for b in boxes:
        rot = None
        if b.rot is not None:
            axis, ang, o = b.rot
            rot = (axis, ang, (o[0] + dx, o[1] + dy, o[2] + dz))
        out.append(Box(prefix + b.name,
                       (b.p0[0] + dx, b.p0[1] + dy, b.p0[2] + dz),
                       (b.p1[0] + dx, b.p1[1] + dy, b.p1[2] + dz),
                       b.mat, rot, b.skip))
    return out


def raked_column(name, y0, y1, half_w0, half_w1, depth0, depth1, mat, segments=16,
                 z_at=axis_z, dz=0.0):
    """
    A tube leaning at the stem rake, built as a stack of short boxes.

    Element rotation only offers -45/-22.5/0/22.5/45 degrees, and the stem's 10 degrees is none of
    those, so the lean is approximated by stepping each segment back along Z.  Segment count is
    what controls how visible that staircase is: at forty segments the step is about 4 mm, which
    is under a pixel on screen at any sane viewing distance, while the silhouette - which is what
    the eye actually reads - comes out at the right angle.

    The tube also tapers, wide at the base and narrow at the top, like the real one.
    """
    out = []
    for i in range(segments):
        t0 = i / segments
        t1 = (i + 1) / segments
        ya, yb = y0 + (y1 - y0) * t0, y0 + (y1 - y0) * t1
        t = (t0 + t1) / 2
        hw = half_w0 + (half_w1 - half_w0) * t
        dp = depth0 + (depth1 - depth0) * t
        zc = z_at((ya + yb) / 2) + dz
        # Interior caps are hidden inside the neighbouring segments, so do not spend texture on them.
        skip = ()
        if i > 0 and i < segments - 1:
            skip = ("up", "down")
        elif i == 0:
            skip = ("up",)
        else:
            skip = ("down",)
        out.append(box(f"{name}{i}", -hw, ya, zc - dp / 2, hw, yb, zc + dp / 2, mat, skip=skip))
    return out


# --------------------------------------------------------------------------------------------
# Circles.  Element rotation is limited to one axis per element at one of -45/-22.5/0/22.5/45
# degrees, so a disc is built from overlapping full-diameter bars.
# --------------------------------------------------------------------------------------------

DISC_ANGLES_16 = (0.0, 22.5, 45.0, -22.5)
DISC_ANGLES_8 = (0.0, 45.0)


def _bar_half_sizes(radius, n_bars):
    """
    Half-length and half-width of one bar so that `n_bars` of them union to a *regular* polygon.

    n bars give 2n edge directions and hence a 4n-gon whose vertices sit half-way between two
    directions, at alpha = 45/n degrees off a bar's long axis.  Putting each bar's corners exactly
    on those vertices means half_len = R*cos(alpha), half_wid = R*sin(alpha).  Get this wrong (for
    instance by using the full radius as the half-length) and the corners poke out past the rim,
    which is what turns a wheel into a star.
    """
    alpha = math.radians(45.0 / n_bars)
    return radius * math.cos(alpha), radius * math.sin(alpha)


def disc_x(name, radius, x0, x1, cy, cz, mat, angles=DISC_ANGLES_16, inset=0.0):
    """A disc lying in the YZ plane (i.e. a wheel), spun about the X axis.

    `inset` shrinks each successive bar along X so no two bars ever share a face plane - without
    it the coplanar sidewalls z-fight.  The steps are fractions of a millimetre at model scale but
    plenty for the depth buffer.
    """
    half_len, half_wid = _bar_half_sizes(radius, len(angles))
    out = []
    for i, ang in enumerate(angles):
        d = inset * i
        out.append(box(f"{name}{i}", x0 + d, cy - half_len, cz - half_wid,
                       x1 - d, cy + half_len, cz + half_wid,
                       mat, rot=("x", ang, (0.0, cy, cz))))
    return out


def disc_z(name, radius, z0, z1, cx, cy, mat, angles=DISC_ANGLES_8, inset=0.0):
    """A disc lying in the XY plane (a headlight lens), spun about the Z axis."""
    half_len, half_wid = _bar_half_sizes(radius, len(angles))
    out = []
    for i, ang in enumerate(angles):
        d = inset * i
        out.append(box(f"{name}{i}", cx - half_len, cy - half_wid, z0 + d,
                       cx + half_len, cy + half_wid, z1 - d,
                       mat, rot=("z", ang, (cx, cy, 0.0))))
    return out


# --------------------------------------------------------------------------------------------
# frame - everything that does NOT turn with the bars: deck, battery, rear end, head tube.
# --------------------------------------------------------------------------------------------

def frame_boxes():
    b = []

    # ---- deck --------------------------------------------------------------------------------
    b.append(box("deck", -DECK_HALF_W, DECK_BOTTOM + 26, DECK_Z0, DECK_HALF_W, DECK_TOP, DECK_Z1,
                 {"*": "frame", "up": "grip"}))
    # Battery tray slung under the deck - 48 V 15.6 Ah lives down here.
    b.append(box("battery", -88, DECK_BOTTOM - 34, DECK_Z0 + 55, 88, DECK_BOTTOM + 30, DECK_Z1 - 45,
                 "frame_dark"))
    # Orange side rails, the scooter's most recognisable line.
    b += pair(box("rail", -DECK_HALF_W - 9, DECK_BOTTOM + 30, DECK_Z0 + 30,
                  -DECK_HALF_W + 2, DECK_TOP - 20, DECK_Z1 - 30, "accent"))
    # Amber side reflector towards the back of the deck.
    b += pair(box("reflector_side", -DECK_HALF_W - 11, DECK_BOTTOM + 36, DECK_Z0 + 62,
                  -DECK_HALF_W - 8, DECK_TOP - 28, DECK_Z0 + 190, "reflector"))
    # Deck lips front and rear.
    b.append(box("deck_lip_f", -84, DECK_TOP - 16, DECK_Z1, 84, DECK_TOP, DECK_Z1 + 40, "frame"))
    b.append(box("deck_lip_r", -84, DECK_TOP - 16, DECK_Z0 - 34, 84, DECK_TOP, DECK_Z0, "frame"))

    # ---- head tube: the short socket the steering column turns inside -------------------------
    b += raked_column("headtube", 128.0, 322.0, 62.0, 58.0, 118.0, 112.0, "tube_dark",
                      segments=4)
    b.append(box("headtube_gusset", -50, 120, STEER_AXIS_Z - 96, 50, 205, STEER_AXIS_Z - 40,
                 "frame"))

    # ---- rear end ----------------------------------------------------------------------------
    # Frame tail carrying the rear wheel.
    b.append(box("tail_frame", -78, DECK_BOTTOM + 4, REAR_AXLE_Z + 30, 78, DECK_TOP - 6, DECK_Z0 + 30,
                 "frame"))
    # Swing arms down to the axle.
    b += pair(box("swingarm", -70, AXLE_Y - 18, REAR_AXLE_Z + 4, -54, AXLE_Y + 54, REAR_AXLE_Z + 104,
                  "frame_dark"))
    # The orange suspension link - one of the four arms, and very visible on the real scooter.
    b += pair(box("rear_link", -76, AXLE_Y - 16, REAR_AXLE_Z, -54, AXLE_Y + 16, REAR_AXLE_Z + 178,
                  "accent", rot=("x", -22.5, (0.0, AXLE_Y, REAR_AXLE_Z))))
    # Coil-over tucked above the link.
    b += pair(box("rear_shock", -58, AXLE_Y + 34, REAR_AXLE_Z + 52, -42, AXLE_Y + 116,
                  REAR_AXLE_Z + 100, "spring"))

    # Rear mudguard: four plates swept around the wheel, slim enough to leave the tyre visible.
    for i, ang in enumerate((45.0, 22.5, 0.0, -22.5)):
        b.append(box(f"fender_r{i}", -48, AXLE_Y + 146, REAR_AXLE_Z - 58,
                     48, AXLE_Y + 168, REAR_AXLE_Z + 58,
                     {"*": "frame", "up": "frame_dark"},
                     rot=("x", ang, (0.0, AXLE_Y, REAR_AXLE_Z)), skip=("down",)))
    b.append(box("fender_stay", -14, AXLE_Y + 96, REAR_AXLE_Z + 96, 14, AXLE_Y + 152,
                 REAR_AXLE_Z + 130, "frame_dark"))
    # Mudguard tail, carrying the light down and back behind the wheel.
    b.append(box("fender_tail", -46, AXLE_Y + 96, REAR_AXLE_Z - 176, 46, AXLE_Y + 142,
                 REAR_AXLE_Z - 96, "frame", rot=("x", -22.5, (0.0, AXLE_Y, REAR_AXLE_Z))))
    b.append(box("tail_housing", -44, 168, REAR_AXLE_Z - 182, 44, 226, REAR_AXLE_Z - 150,
                 "frame_dark"))
    b.append(box("tail_reflector", -37, 178, REAR_AXLE_Z - 186, 37, 214, REAR_AXLE_Z - 182,
                 "reflector", skip=("north",)))

    # ---- kickstand, folded up along the left of the deck --------------------------------------
    b.append(box("kickstand", -DECK_HALF_W - 18, 12.0, DECK_Z0 + 96,
                 -DECK_HALF_W - 8, DECK_BOTTOM + 24, DECK_Z0 + 122,
                 "chrome", rot=("z", 22.5, (-DECK_HALF_W, DECK_BOTTOM + 20, 0.0))))
    return b


# --------------------------------------------------------------------------------------------
# column - the whole steering assembly: stem, fork, front mudguard, headlight.
# --------------------------------------------------------------------------------------------

def column_boxes():
    b = []

    # ---- stem: raked back 10 degrees, tapering as it rises -------------------------------------
    b += raked_column("stem", 196.0, STEM_TOP_Y, 44.0, 31.0, 92.0, 62.0, "stem_face", segments=40)
    # Telescopic upper section into the bar clamp.
    b += raked_column("stem_top", STEM_TOP_Y, BAR_Y - 14, 29.0, 27.0, 58.0, 56.0, "chrome_seg",
                      segments=2)
    # Orange folding collar.
    b.append(box("fold_collar", -52, FOLD_COLLAR_Y - 34, axis_z(FOLD_COLLAR_Y) - 58,
                 52, FOLD_COLLAR_Y + 34, axis_z(FOLD_COLLAR_Y) + 58, "accent"))
    b.append(box("fold_hook", -17, FOLD_COLLAR_Y + 30, axis_z(FOLD_COLLAR_Y) + 46,
                 17, FOLD_COLLAR_Y + 78, axis_z(FOLD_COLLAR_Y) + 64, "chrome"))
    # Brake and throttle cables looping down the front of the stem.
    b += raked_column("cable", 300.0, 1140.0, 11.0, 9.0, 18.0, 16.0, "cable", segments=8,
                      dz=62.0)

    # ---- headlight, low on the column just above the wheel -------------------------------------
    hz = axis_z(HEADLIGHT_Y)
    b.append(box("lamp_bracket", -34, HEADLIGHT_Y - 30, hz + 24, 34, HEADLIGHT_Y + 30, hz + 52,
                 "frame"))
    b.append(box("lamp_housing", -50, HEADLIGHT_Y - 54, hz + 46, 50, HEADLIGHT_Y + 54, hz + 96,
                 "frame_dark", skip=("south",)))
    b.append(box("lamp_visor", -54, HEADLIGHT_Y + 46, hz + 36, 54, HEADLIGHT_Y + 62, hz + 104,
                 "accent"))

    # ---- fork ------------------------------------------------------------------------------------
    # The fork is a pair of legs raked forward at 45 degrees from a compact crown down to the
    # axle.  Keeping the crown small matters: a big one sits exactly where the front wheel should
    # be visible and turns the whole front of the scooter into one dark lump.
    # The leg length and crown position are derived from the chosen rotation so the leg always
    # lands exactly on the axle - which is why the crown is not a magic number.
    FORK_ANGLE = -22.5
    CROWN_Y = 340.0
    leg = (CROWN_Y - AXLE_Y) / math.cos(math.radians(FORK_ANGLE))
    CROWN_Z = FRONT_AXLE_Z - leg * math.sin(math.radians(-FORK_ANGLE))
    b.append(box("crown", -74, CROWN_Y - 48, CROWN_Z - 40, 74, CROWN_Y + 26, CROWN_Z + 40,
                 "frame_dark"))
    b += pair(box("fork_leg", -68, CROWN_Y - leg, CROWN_Z - 25, -48, CROWN_Y, CROWN_Z + 25,
                  "frame", rot=("x", FORK_ANGLE, (0.0, CROWN_Y, CROWN_Z))))
    # The orange suspension link runs alongside the lower half of the leg, as on the real machine.
    b += pair(box("front_link", -84, CROWN_Y - leg + 6, CROWN_Z - 19, -66, CROWN_Y - leg * 0.42,
                  CROWN_Z + 19, "accent", rot=("x", FORK_ANGLE, (0.0, CROWN_Y, CROWN_Z))))
    b += pair(box("front_shock", -62, CROWN_Y - leg * 0.62, CROWN_Z - 15, -46,
                  CROWN_Y - leg * 0.18, CROWN_Z + 15, "spring",
                  rot=("x", FORK_ANGLE, (0.0, CROWN_Y, CROWN_Z))))
    b.append(box("axle_clamp", -66, AXLE_Y - 20, FRONT_AXLE_Z - 24, 66, AXLE_Y + 20,
                 FRONT_AXLE_Z + 24, "frame_dark"))

    # Front mudguard: thin, narrow and held clear of the tyre so the wheel still reads as a wheel.
    for i, ang in enumerate((-45.0, -22.5, 0.0, 22.5)):
        b.append(box(f"fender_f{i}", -48, AXLE_Y + 146, FRONT_AXLE_Z - 58,
                     48, AXLE_Y + 168, FRONT_AXLE_Z + 58,
                     {"*": "frame", "up": "frame_dark"},
                     rot=("x", ang, (0.0, AXLE_Y, FRONT_AXLE_Z)), skip=("down",)))
    return b


# --------------------------------------------------------------------------------------------
# handlebar
# --------------------------------------------------------------------------------------------

def handlebar_boxes():
    b = []
    bz = axis_z(BAR_Y)
    b.append(box("bar", -BAR_HALF_W, BAR_Y - 17, bz - 17, BAR_HALF_W, BAR_Y + 17, bz + 17,
                 "chrome"))
    b.append(box("bar_clamp", -48, BAR_Y - 28, bz - 28, 48, BAR_Y + 28, bz + 28, "accent"))
    b += pair(box("grip", -BAR_HALF_W - 5, BAR_Y - 23, bz - 23, -BAR_HALF_W + 118, BAR_Y + 23,
                  bz + 23, "rubber"))
    b += pair(box("bar_end", -BAR_HALF_W - 12, BAR_Y - 16, bz - 16, -BAR_HALF_W - 5, BAR_Y + 16,
                  bz + 16, "accent"))
    # Brake levers, angled down and forward off each side.
    b += pair(box("lever", -186, BAR_Y - 33, bz + 8, -108, BAR_Y - 19, bz + 52, "chrome",
                  rot=("y", -22.5, (-186.0, BAR_Y, bz))))
    # Right-hand thumb throttle.
    b.append(box("throttle", 126, BAR_Y - 38, bz - 38, 170, BAR_Y - 22, bz - 6, "accent",
                 rot=("x", 22.5, (148.0, BAR_Y - 22, bz - 22))))
    # Left-hand control pod: lights, horn, mode.
    b.append(box("pod", -172, BAR_Y - 35, bz - 40, -126, BAR_Y - 19, bz - 8, "frame_dark"))
    b.append(box("pod_btn", -166, BAR_Y - 27, bz - 43, -157, BAR_Y - 22, bz - 38, "accent"))
    # Dash housing; the screen itself is a separate glowing part.
    b.append(box("dash_housing", -68, DASH_Y - 44, bz - 42, 68, DASH_Y + 44, bz + 10,
                 {"*": "frame_dark", "up": "frame"}, skip=("north",)))
    b.append(box("dash_bezel", -72, DASH_Y - 48, bz - 46, 72, DASH_Y + 48, bz - 40,
                 "frame", skip=("north",)))
    b.append(box("dash_stalk", -26, BAR_Y + 14, bz - 34, 26, DASH_Y - 40, bz - 6, "frame_dark"))
    return b


# --------------------------------------------------------------------------------------------
# wheel - one model, instanced at both axles.  Origin is the axle centre.
# --------------------------------------------------------------------------------------------

def wheel_boxes():
    b = []
    # Tyre carcass: four bars forming a 16-gon.  Sidewalls are painted as concentric rings, so
    # they stay correct whichever bar a given texel belongs to.
    b.extend(disc_x("tyre", TYRE_R, -TYRE_W / 2, TYRE_W / 2, 0.0, 0.0,
                    {"*": "tread", "west": "wheelside", "east": "wheelside"},
                    angles=DISC_ANGLES_16, inset=1.0))
    # Hub motor, proud of the tyre on both sides.
    b.extend(disc_x("hub", 52.0, -TYRE_W / 2 - 15, TYRE_W / 2 + 15, 0.0, 0.0, "motor",
                    angles=DISC_ANGLES_8, inset=0.8))
    # 120 mm brake disc on the left, clearly visible on the real machine.
    b.extend(disc_x("disc", 62.0, -TYRE_W / 2 - 26, -TYRE_W / 2 - 20, 0.0, 0.0,
                    {"*": "disc_edge", "west": "disc", "east": "disc"},
                    angles=DISC_ANGLES_8, inset=0.6))
    b.append(box("axle", -TYRE_W / 2 - 34, -10, -10, TYRE_W / 2 + 34, 10, 10, "chrome"))
    return b


# --------------------------------------------------------------------------------------------
# Small glowing parts.  These get a Display brightness override so they read as real lights.
# --------------------------------------------------------------------------------------------

def lamp_front_boxes():
    hz = axis_z(HEADLIGHT_Y)
    return disc_z("lens", 36.0, hz + 92, hz + 101, 0.0, HEADLIGHT_Y, "led_white",
                  angles=DISC_ANGLES_8, inset=0.5)


def lamp_rear_boxes():
    z = REAR_AXLE_Z - 188.0
    return disc_z("lens", 26.0, z - 4, z + 4, 0.0, 196.0, "led_red",
                  angles=DISC_ANGLES_8, inset=0.5)


def dash_boxes():
    # 118 x 73 mm screen, facing the rider (-Z).
    bz = axis_z(BAR_Y)
    return [box("screen", -59, DASH_Y - 36.5, bz - 49, 59, DASH_Y + 36.5, bz - 46,
                "lcd", skip=("south",))]


# --------------------------------------------------------------------------------------------
# Part table
# --------------------------------------------------------------------------------------------

def wheel_at(z):
    return translate(wheel_boxes(), dy=AXLE_Y, dz=z)


def full_boxes():
    """The whole scooter in one model - used for the inventory item and for parked scooters."""
    out = []
    out += frame_boxes()
    out += column_boxes()
    out += handlebar_boxes()
    out += wheel_at(FRONT_AXLE_Z)
    out += translate(wheel_boxes(), dy=AXLE_Y, dz=REAR_AXLE_Z, prefix="r_")
    out += lamp_front_boxes()
    out += lamp_rear_boxes()
    out += dash_boxes()
    return out


def mirrored(fn):
    """Wrap a part generator so its boxes come out in Minecraft's axes."""
    return lambda: apply_mirror(fn())


PARTS = {
    # key:            (boxes,                        pivot mm, tex, texels/unit, tinted)
    "frame": (mirrored(frame_boxes), None, 512, 9.0, True),
    "column": (mirrored(column_boxes), None, 512, 8.0, True),
    "handlebar": (mirrored(handlebar_boxes), (0.0, BAR_Y, axis_z(BAR_Y)), 512, 9.0, True),
    "wheel": (mirrored(wheel_boxes), (0.0, 0.0, 0.0), 512, 7.0, True),
    "lamp_front": (mirrored(lamp_front_boxes), None, 128, 6.0, False),
    "lamp_rear": (mirrored(lamp_rear_boxes), None, 128, 6.0, False),
    "dash": (mirrored(dash_boxes), (0.0, DASH_Y, axis_z(BAR_Y)), 256, 8.0, False),
    "scooter": (mirrored(full_boxes), None, 512, 5.0, True),
}


# Extra numbers the plugin needs; emitted into assembly.json so Java can never drift from the model.
GEOMETRY = {
    "tyre_radius_mm": TYRE_R,
    "tyre_width_mm": TYRE_W,
    "axle_y_mm": AXLE_Y,
    "front_axle_z_mm": FRONT_AXLE_Z,
    "rear_axle_z_mm": REAR_AXLE_Z,
    "wheelbase_mm": WHEELBASE,
    "steer_axis_z_mm": STEER_AXIS_Z,
    "steer_axis_base_y_mm": STEM_BASE_Y,
    "steer_rake_deg": STEM_RAKE_DEG,
    "deck_top_mm": DECK_TOP,
    "deck_z0_mm": DECK_Z0,
    "deck_z1_mm": DECK_Z1,
    "bar_y_mm": BAR_Y,
    "total_height_mm": TOTAL_H,
    "length_mm": 1187.0,
    "width_mm": 620.0,
}

# Which parts turn with the bars, and which roll.  On a real scooter the stem is the steering
# column, so everything above and in front of the head tube turns - not just the fork.
ARTICULATION = {
    "frame": {"steer": False, "roll": False},
    "column": {"steer": True, "roll": False},
    "handlebar": {"steer": True, "roll": False},
    "lamp_front": {"steer": True, "roll": False},
    "lamp_rear": {"steer": False, "roll": False},
    "dash": {"steer": True, "roll": False},
    "wheel": {"steer": False, "roll": True},
}
