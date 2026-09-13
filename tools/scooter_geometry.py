"""
Geometry of the ScooterMC scooter, modelled on a KuKirin G2 Pro.

Everything in this file is expressed in REAL MILLIMETRES so it can be checked against the
manufacturer's spec sheet (see docs/RESEARCH.md).  The design coordinate system is:

    +X = rider's right        +Y = up        +Z = direction of travel

with the origin on the ground, directly below the middle of the wheelbase.  +Z lines up with the
Minecraft item-model axes at yaw 0, since MC yaw 0 faces +Z.

The X axis does NOT line up, and that is what MIRROR_X is for.  At yaw 0 the rider faces south,
so *their* right hand points west, i.e. towards -X in world coordinates - the opposite of the
model's +X.  Left it alone, every asymmetric detail would end up on the wrong side: the thumb
throttle on the left grip, the brake disc and kickstand on the offside.  So the whole design is
mirrored in X on the way out, letting this file stay written in natural rider-relative terms.

Reference dimensions (KuKirin G2 Pro):
    unfolded 1187 x 620 x 1315 mm, 9 x 3 in tyres, 120 mm discs, 118 x 73 mm dash.
"""

import math
from dataclasses import dataclass
from typing import Optional

# --------------------------------------------------------------------------------------------
# Key dimensions
# --------------------------------------------------------------------------------------------

TYRE_D = 229.0                  # 9 inch
TYRE_R = TYRE_D / 2.0           # 114.5
TYRE_W = 76.0                   # 3 inch
RIM_R = 78.0                    # 6 inch rim + lip

AXLE_Y = TYRE_R                 # axle height above ground
WHEELBASE = 860.0
FRONT_AXLE_Z = WHEELBASE / 2.0  # +430
REAR_AXLE_Z = -WHEELBASE / 2.0  # -430

# The deck is deliberately a little lower than the real 165 mm.  A Minecraft player's feet sit at
# world Y = 0, so every millimetre of deck height is a millimetre the rider's feet sink into the
# deck.  95 mm keeps that under 1.5 texels while still reading as a real stand-on deck.
DECK_TOP = 95.0
DECK_BOTTOM = 42.0
DECK_HALF_W = 95.0
DECK_Z0 = -330.0
DECK_Z1 = 250.0

STEER_AXIS_Z = 250.0            # the stem / steering axis
STEM_TOP = 1150.0
BAR_Y = 1230.0                  # handlebar centreline
BAR_HALF_W = 290.0              # 620 mm overall width minus lever knuckles
BAR_Z = 272.0
DASH_Y = 1272.0

HEADLIGHT_Y = 925.0
HEADLIGHT_Z = 318.0

TOTAL_H = 1315.0

FACES = ("down", "up", "north", "south", "west", "east")


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
    """Mirror a box across the X=0 plane (for the left/right pairs)."""
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


# --------------------------------------------------------------------------------------------
# Circles.  Element rotation is limited to one axis at one of -45/-22.5/0/22.5/45 degrees, so a
# disc is built from overlapping full-diameter bars.  A bar covers both phi and phi+90, hence the
# four angles below give 8 evenly spaced edge directions - a 16-gon, radius varying by <2%.
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
# body - deck, battery, rear end, stem, headlight housing.  Everything that does not steer.
# --------------------------------------------------------------------------------------------

def body_boxes():
    b = []

    # ---- deck ---------------------------------------------------------------------------------
    # Main deck plate; the top face is grip tape, the rest anodised frame.
    b.append(box("deck", -DECK_HALF_W, DECK_BOTTOM + 20, DECK_Z0, DECK_HALF_W, DECK_TOP, DECK_Z1,
                 {"*": "frame", "up": "grip"}))
    # Battery tray slung under the deck - the G2 Pro carries 48 V 15.6 Ah down here.
    b.append(box("battery", -82, DECK_BOTTOM - 22, DECK_Z0 + 70, 82, DECK_BOTTOM + 22, DECK_Z1 - 60,
                 "frame_dark"))
    # Orange pinstripe along each deck edge.
    rail = box("rail", -DECK_HALF_W - 6, DECK_BOTTOM + 26, DECK_Z0 + 18,
               -DECK_HALF_W + 1, DECK_TOP - 12, DECK_Z1 - 18, "accent")
    b.append(rail)
    b.append(mirror_x(rail))
    # Front and rear deck lips, slightly narrower, sell the folded-aluminium look.
    b.append(box("deck_lip_f", -78, DECK_TOP - 14, DECK_Z1, 78, DECK_TOP, DECK_Z1 + 34, "frame"))
    b.append(box("deck_lip_r", -78, DECK_TOP - 14, DECK_Z0 - 30, 78, DECK_TOP, DECK_Z0, "frame"))

    # ---- rear end -----------------------------------------------------------------------------
    # Riser from the deck up over the rear wheel.
    b.append(box("rear_riser", -72, DECK_BOTTOM + 10, REAR_AXLE_Z + 20, 72, 196, DECK_Z0 + 26,
                 "frame", rot=("x", 22.5, (0.0, DECK_TOP, DECK_Z0))))
    # Rear swingarm: two plates reaching down to the axle.
    arm = box("swingarm", -66, AXLE_Y - 16, REAR_AXLE_Z - 10, -48, AXLE_Y + 128, REAR_AXLE_Z + 78,
              "frame_dark")
    b.append(arm)
    b.append(mirror_x(arm))
    # Visible coil-over shocks, orange like the real four-arm suspension.
    spring = box("shock", -68, AXLE_Y + 18, REAR_AXLE_Z + 12, -46, AXLE_Y + 132,
                 REAR_AXLE_Z + 72, "spring")
    b.append(spring)
    b.append(mirror_x(spring))

    # Rear mudguard: five plates swept around the wheel to make an arc.
    for i, ang in enumerate((22.5, 0.0, -22.5, -45.0)):
        b.append(box(f"fender_r{i}", -56, AXLE_Y + 132, REAR_AXLE_Z - 54,
                     56, AXLE_Y + 158, REAR_AXLE_Z + 54,
                     {"*": "frame", "up": "frame_dark"},
                     rot=("x", ang, (0.0, AXLE_Y, REAR_AXLE_Z)), skip=("down",)))
    # Tail-light housing, hanging off the back of the mudguard (lens is its own glowing part).
    b.append(box("tail_housing", -42, 168, REAR_AXLE_Z - 168, 42, 226, REAR_AXLE_Z - 138,
                 "frame_dark"))
    b.append(box("reflector", -34, 178, REAR_AXLE_Z - 172, 34, 214, REAR_AXLE_Z - 168,
                 "reflector", skip=("north",)))

    # ---- kickstand, folded up against the left of the deck ------------------------------------
    # Folded up, so the foot must clear the ground once the 22.5 degree swing is applied.
    b.append(box("kickstand", -DECK_HALF_W - 16, 10.0, DECK_Z0 + 74,
                 -DECK_HALF_W - 6, DECK_BOTTOM + 18, DECK_Z0 + 96,
                 "chrome", rot=("z", 22.5, (-DECK_HALF_W, DECK_BOTTOM + 14, 0.0))))

    # ---- stem ---------------------------------------------------------------------------------
    # Folding hinge block at the base.
    b.append(box("hinge", -62, DECK_BOTTOM + 6, STEER_AXIS_Z - 54, 62, 168, STEER_AXIS_Z + 52,
                 "frame_dark"))
    # Orange folding clamp + hook, the signature one-step fold.
    b.append(box("fold_clamp", -52, 160, STEER_AXIS_Z - 48, 52, 210, STEER_AXIS_Z + 46, "accent"))
    b.append(box("fold_hook", -18, 196, STEER_AXIS_Z + 40, 18, 244, STEER_AXIS_Z + 58, "chrome"))
    # Main stem tube, then the thinner telescopic section.
    b.append(box("stem", -38, 200, STEER_AXIS_Z - 36, 38, STEM_TOP, STEER_AXIS_Z + 36, "frame"))
    b.append(box("stem_tele", -29, STEM_TOP - 8, STEER_AXIS_Z - 27,
                 29, BAR_Y - 18, STEER_AXIS_Z + 27, "chrome"))
    # Cable guide down the back of the stem.
    b.append(box("cables", -14, 250, STEER_AXIS_Z - 48, 14, STEM_TOP - 60, STEER_AXIS_Z - 36,
                 "rubber"))

    # ---- headlight housing on the stem (does not steer, like the real thing) -------------------
    b.append(box("lamp_housing", -46, HEADLIGHT_Y - 52, STEER_AXIS_Z + 30,
                 46, HEADLIGHT_Y + 52, HEADLIGHT_Z, "frame_dark", skip=("south",)))
    b.append(box("lamp_visor", -50, HEADLIGHT_Y + 44, STEER_AXIS_Z + 26,
                 50, HEADLIGHT_Y + 58, HEADLIGHT_Z + 12, "accent"))
    return b


# --------------------------------------------------------------------------------------------
# fork - everything that turns with the bars except the bars and the wheel itself.
# --------------------------------------------------------------------------------------------

def fork_boxes():
    b = []
    # Crown, bridging the steering axis forward to the fork legs.
    b.append(box("crown", -74, 396, STEER_AXIS_Z - 30, 74, 462, FRONT_AXLE_Z + 34, "frame_dark"))
    # Steerer tube joining the crown to the stem; the crown already reaches the steering axis.
    b.append(box("steerer", -34, 300, STEER_AXIS_Z - 30, 34, 452, STEER_AXIS_Z + 34, "frame_dark"))
    # Fork legs down to the axle.
    leg = box("leg", -74, AXLE_Y - 12, FRONT_AXLE_Z - 32, -52, 430, FRONT_AXLE_Z + 32, "frame")
    b.append(leg)
    b.append(mirror_x(leg))
    # Front coil-overs.
    spring = box("fspring", -76, 252, FRONT_AXLE_Z - 34, -50, 412, FRONT_AXLE_Z + 34, "spring")
    b.append(spring)
    b.append(mirror_x(spring))
    # Front mudguard, swept over the wheel.
    for i, ang in enumerate((-22.5, 0.0, 22.5, 45.0)):
        b.append(box(f"fender_f{i}", -56, AXLE_Y + 130, FRONT_AXLE_Z - 54,
                     56, AXLE_Y + 156, FRONT_AXLE_Z + 54,
                     {"*": "frame", "up": "frame_dark"},
                     rot=("x", ang, (0.0, AXLE_Y, FRONT_AXLE_Z)), skip=("down",)))
    # Mudguard stay.
    b.append(box("stay", -12, AXLE_Y + 120, FRONT_AXLE_Z - 12, 12, 404, FRONT_AXLE_Z + 12,
                 "chrome"))
    return b


# --------------------------------------------------------------------------------------------
# handlebar - bar, grips, levers, thumb throttle, dash housing.
# --------------------------------------------------------------------------------------------

def handlebar_boxes():
    b = []
    # Bar tube.
    b.append(box("bar", -BAR_HALF_W, BAR_Y - 16, BAR_Z - 16, BAR_HALF_W, BAR_Y + 16, BAR_Z + 16,
                 "chrome"))
    # Stem clamp.
    b.append(box("clamp", -46, BAR_Y - 26, BAR_Z - 26, 46, BAR_Y + 26, BAR_Z + 26, "accent"))
    # Rubber grips.
    grip = box("grip", -BAR_HALF_W - 4, BAR_Y - 21, BAR_Z - 21, -BAR_HALF_W + 112, BAR_Y + 21,
               BAR_Z + 21, "rubber")
    b.append(grip)
    b.append(mirror_x(grip))
    # Bar ends.
    end = box("bar_end", -BAR_HALF_W - 10, BAR_Y - 15, BAR_Z - 15, -BAR_HALF_W - 4, BAR_Y + 15,
              BAR_Z + 15, "accent")
    b.append(end)
    b.append(mirror_x(end))
    # Brake levers, angled down and forward off each side.
    lever = box("lever", -178, BAR_Y - 30, BAR_Z + 6, -104, BAR_Y - 18, BAR_Z + 46, "chrome",
                rot=("y", -22.5, (-178.0, BAR_Y, BAR_Z)))
    b.append(lever)
    b.append(mirror_x(lever))
    # Right-hand thumb throttle.
    b.append(box("throttle", 128, BAR_Y - 34, BAR_Z - 34, 168, BAR_Y - 20, BAR_Z - 6, "accent",
                 rot=("x", 22.5, (148.0, BAR_Y - 20, BAR_Z - 20))))
    # Left-hand control pod: light / horn / mode buttons.
    b.append(box("pod", -166, BAR_Y - 32, BAR_Z - 36, -124, BAR_Y - 18, BAR_Z - 8, "frame_dark"))
    b.append(box("pod_btn", -160, BAR_Y - 24, BAR_Z - 39, -152, BAR_Y - 20, BAR_Z - 35, "accent"))
    # Dash housing; the screen face itself is a separate glowing part.
    b.append(box("dash_housing", -66, DASH_Y - 42, BAR_Z - 40, 66, DASH_Y + 42, BAR_Z + 8,
                 {"*": "frame_dark", "up": "frame"}, skip=("north",)))
    b.append(box("dash_bezel", -70, DASH_Y - 46, BAR_Z - 44, 70, DASH_Y + 46, BAR_Z - 38,
                 "frame", skip=("north",)))
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
    b.extend(disc_x("hub", 50.0, -TYRE_W / 2 - 13, TYRE_W / 2 + 13, 0.0, 0.0, "motor",
                    angles=DISC_ANGLES_8, inset=0.8))
    # 120 mm brake disc on the left.
    b.extend(disc_x("disc", 60.0, -TYRE_W / 2 - 22, -TYRE_W / 2 - 17, 0.0, 0.0,
                    {"*": "disc_edge", "west": "disc", "east": "disc"},
                    angles=DISC_ANGLES_8, inset=0.6))
    # Axle stubs.
    b.append(box("axle", -TYRE_W / 2 - 30, -9, -9, TYRE_W / 2 + 30, 9, 9, "chrome"))
    return b


# --------------------------------------------------------------------------------------------
# Small glowing parts.  These get a Display brightness override so they read as real lights.
# --------------------------------------------------------------------------------------------

def lamp_front_boxes():
    return disc_z("lens", 33.0, HEADLIGHT_Z - 5, HEADLIGHT_Z + 4, 0.0, HEADLIGHT_Y, "led_white",
                  angles=DISC_ANGLES_8, inset=0.5)


def lamp_rear_boxes():
    z = REAR_AXLE_Z - 174.0
    return disc_z("lens", 25.0, z - 4, z + 4, 0.0, 197.0, "led_red",
                  angles=DISC_ANGLES_8, inset=0.5)


def dash_boxes():
    # 118 x 73 mm screen, facing the rider (-Z).
    return [box("screen", -59, DASH_Y - 36.5, BAR_Z - 46, 59, DASH_Y + 36.5, BAR_Z - 43,
                "lcd", skip=("south",))]


# --------------------------------------------------------------------------------------------
# Part table
# --------------------------------------------------------------------------------------------

def wheel_at(z):
    return translate(wheel_boxes(), dy=AXLE_Y, dz=z)


def mirrored(fn):
    """Wrap a part generator so its boxes come out in Minecraft's axes."""
    return lambda: apply_mirror(fn())


PARTS = {
    # key:            (boxes,                pivot mm,                     tex, texels/unit, tinted)
    "body": (mirrored(body_boxes), None, 512, 9.0, True),
    "fork": (mirrored(fork_boxes), (0.0, 300.0, STEER_AXIS_Z), 512, 9.0, True),
    "handlebar": (mirrored(handlebar_boxes), (0.0, BAR_Y, STEER_AXIS_Z), 512, 9.0, True),
    "wheel": (mirrored(wheel_boxes), (0.0, 0.0, 0.0), 512, 7.0, True),
    "lamp_front": (mirrored(lamp_front_boxes), None, 128, 6.0, False),
    "lamp_rear": (mirrored(lamp_rear_boxes), None, 128, 6.0, False),
    "dash": (mirrored(dash_boxes), (0.0, DASH_Y, STEER_AXIS_Z), 256, 8.0, False),
}


def full_boxes():
    """The whole scooter in one model - used for the inventory item and for parked scooters."""
    out = []
    out += body_boxes()
    out += fork_boxes()
    out += handlebar_boxes()
    out += wheel_at(FRONT_AXLE_Z)
    out += translate(wheel_boxes(), dy=AXLE_Y, dz=REAR_AXLE_Z, prefix="r_")
    out += lamp_front_boxes()
    out += lamp_rear_boxes()
    out += dash_boxes()
    return out


PARTS["scooter"] = (mirrored(full_boxes), None, 512, 5.2, True)


# Extra numbers the plugin needs; emitted into assembly.json so Java can never drift from the model.
GEOMETRY = {
    "tyre_radius_mm": TYRE_R,
    "tyre_width_mm": TYRE_W,
    "axle_y_mm": AXLE_Y,
    "front_axle_z_mm": FRONT_AXLE_Z,
    "rear_axle_z_mm": REAR_AXLE_Z,
    "wheelbase_mm": WHEELBASE,
    "steer_axis_z_mm": STEER_AXIS_Z,
    "deck_top_mm": DECK_TOP,
    "deck_z0_mm": DECK_Z0,
    "deck_z1_mm": DECK_Z1,
    "bar_y_mm": BAR_Y,
    "total_height_mm": TOTAL_H,
    "length_mm": 1187.0,
    "width_mm": 620.0,
}

# Which parts steer with the bars, and which roll.
ARTICULATION = {
    "body": {"steer": False, "roll": False},
    "fork": {"steer": True, "roll": False},
    "handlebar": {"steer": True, "roll": False},
    "lamp_front": {"steer": False, "roll": False},
    "lamp_rear": {"steer": False, "roll": False},
    "dash": {"steer": True, "roll": False},
    "wheel": {"steer": False, "roll": True},
}
