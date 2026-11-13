#!/usr/bin/env python3
"""Generates the SF-style glyph set (24x24 vector drawables) used by both apps.
All glyphs are original drawings: rounded strokes / rounded fills in the iOS spirit."""
import math, os, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "core/src/main/res/drawable"
W = "#FFFFFFFF"

def f(x): return ("%.2f" % x).rstrip("0").rstrip(".")

def poly(pts, close=True):
    s = "M" + " L".join("%s,%s" % (f(x), f(y)) for x, y in pts)
    return s + ("Z" if close else "")

def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return ("M%s,%s h%s a%s,%s 0 0 1 %s,%s v%s a%s,%s 0 0 1 %s,%s h%s a%s,%s 0 0 1 %s,%s v%s a%s,%s 0 0 1 %s,%sZ" % (
        f(x + r), f(y), f(w - 2 * r), f(r), f(r), f(r), f(r), f(h - 2 * r), f(r), f(r), f(-r), f(r),
        f(-(w - 2 * r)), f(r), f(r), f(-r), f(-r), f(-(h - 2 * r)), f(r), f(r), f(r), f(-r)))

def circle(cx, cy, r):
    return "M%s,%s a%s,%s 0 1 0 %s,0 a%s,%s 0 1 0 %s,0Z" % (f(cx - r), f(cy), f(r), f(r), f(2 * r), f(r), f(r), f(-2 * r))

def fill(d, rounding=0.0, even_odd=False):
    extra = ""
    if rounding:
        extra = ' android:strokeColor="%s" android:strokeWidth="%s" android:strokeLineJoin="round"' % (W, f(rounding))
    fo = ' android:fillType="evenOdd"' if even_odd else ""
    return '<path android:fillColor="%s"%s%s android:pathData="%s"/>' % (W, extra, fo, d)

def stroke(d, w=2.0):
    return ('<path android:fillColor="#00000000" android:strokeColor="%s" android:strokeWidth="%s" '
            'android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="%s"/>' % (W, f(w), d))

def group(inner, rot=0, sx=1):
    return '<group android:pivotX="12" android:pivotY="12" android:rotation="%s" android:scaleX="%s">%s</group>' % (rot, sx, inner)

def write(name, *paths):
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" '
           'android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n  %s\n</vector>\n'
           % "\n  ".join(paths))
    with open(os.path.join(OUT, "ic_%s.xml" % name), "w") as fh:
        fh.write(xml)

def star(cx, cy, ro, ri, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        r = ro if i % 2 == 0 else ri
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts

def gear(cx, cy, ro, ri, teeth, hole):
    pts = []
    step = 2 * math.pi / teeth
    for i in range(teeth):
        a = i * step
        for da, r in ((-0.30, ri), (-0.17, ro), (0.17, ro), (0.30, ri)):
            pts.append((cx + r * math.cos(a + da * step * 1.6), cy + r * math.sin(a + da * step * 1.6)))
    return poly(pts) + " " + circle(cx, cy, hole)

def tri_right(x, y, w, h):
    return [(x, y), (x + w, y + h / 2), (x, y + h)]

os.makedirs(OUT, exist_ok=True)

# --- transport ---
write("play", fill(poly(tri_right(7, 4.5, 12.5, 15)), 2.2))
write("pause", fill(rrect(5.5, 4.5, 4.5, 15, 1.3)), fill(rrect(14, 4.5, 4.5, 15, 1.3)))
nxt = [fill(poly(tri_right(2.5, 6.5, 9.5, 11)), 1.8), fill(poly(tri_right(12, 6.5, 9.5, 11)), 1.8)]
write("next", *nxt)
write("prev", group("".join(nxt), sx=-1))
write("stop", fill(rrect(6, 6, 12, 12, 2.2)))
write("shuffle", stroke("M3,7h3.2c1.6,0 3.1,0.8 4,2.1l3.6,5.8c0.9,1.3 2.4,2.1 4,2.1H21"),
      stroke("M3,17h3.2c1.6,0 3.1,-0.8 4,-2.1l0.5,-0.8"), stroke("M13.3,9.9l0.5,-0.8c0.9,-1.3 2.4,-2.1 4,-2.1H21"),
      stroke("M18.5,4.5L21,7l-2.5,2.5"), stroke("M18.5,14.5L21,17l-2.5,2.5"))
write("repeat", stroke("M4,11.5V10a3,3 0 0 1 3,-3h13"), stroke("M17,4l3,3l-3,3"),
      stroke("M20,12.5V14a3,3 0 0 1 -3,3H4"), stroke("M7,20l-3,-3l3,-3"))
write("list", fill(circle(4.5, 6.5, 1.4)), fill(circle(4.5, 12, 1.4)), fill(circle(4.5, 17.5, 1.4)),
      stroke("M9,6.5h11"), stroke("M9,12h11"), stroke("M9,17.5h11"))
# --- app glyphs ---
PHONE = ("M19.23,15.26l-2.54,-0.29c-0.61,-0.07 -1.21,0.14 -1.64,0.57l-1.84,1.84c-2.83,-1.44 -5.15,-3.75 -6.59,-6.59"
         "l1.85,-1.85c0.43,-0.43 0.64,-1.03 0.57,-1.64l-0.29,-2.52c-0.12,-1.01 -0.97,-1.77 -1.99,-1.77H5.03"
         "c-1.13,0 -2.07,0.94 -2,2.07 0.53,8.54 7.36,15.36 15.89,15.89 1.13,0.07 2.07,-0.87 2.07,-2v-1.73"
         "c0.01,-1.01 -0.75,-1.86 -1.76,-1.98z")
write("phone", fill(PHONE))
write("phone_down", group(fill(PHONE), rot=135))
write("message", fill("M12,3.2C6.4,3.2 2,6.9 2,11.4c0,2.5 1.3,4.7 3.5,6.2 -0.2,1.3 -0.9,2.5 -1.9,3.3 -0.2,0.2 -0.1,0.6 0.2,0.6 "
                      "2,0 3.8,-0.6 5.2,-1.6 1,0.3 2,0.4 3,0.4 5.6,0 10,-3.7 10,-8.2S17.6,3.2 12,3.2z"))
write("music", fill("M19.5,3.6v11.6c0,1.9 -1.6,3.3 -3.6,3.3s-3.4,-1.2 -3.4,-2.8 1.6,-2.9 3.6,-2.9c0.5,0 1,0.1 1.4,0.2V7.6"
                    "l-7.6,1.8v8.6c0,1.9 -1.6,3.3 -3.6,3.3S3,20.1 3,18.5s1.6,-2.9 3.6,-2.9c0.5,0 1,0.1 1.4,0.2V6.6"
                    "c0,-0.6 0.4,-1.1 1,-1.2l9.3,-2.6C18.9,2.6 19.5,3 19.5,3.6z"))
write("gear", fill(gear(12, 12, 10, 7.6, 8, 3.1), even_odd=True), fill(circle(12, 12, 7.9) + " " + circle(12, 12, 3.1), even_odd=True))
write("grid", fill(rrect(3.5, 3.5, 7.5, 7.5, 2)), fill(rrect(13, 3.5, 7.5, 7.5, 2)),
      fill(rrect(3.5, 13, 7.5, 7.5, 2)), fill(rrect(13, 13, 7.5, 7.5, 2)))
dots = [fill(rrect(3.5 + c * 6.3, 3.5 + r * 6.3, 4.4, 4.4, 1.3)) for r in range(3) for c in range(3)]
write("apps", *dots)
write("search", stroke(circle(10.5, 10.5, 6.5), 2.2), stroke("M15.5,15.5L20.5,20.5", 2.4))
write("chevron_right", stroke("M9.5,5.5L16,12l-6.5,6.5", 2.4))
write("chevron_left", stroke("M14.5,5.5L8,12l6.5,6.5", 2.4))
write("chevron_down", stroke("M5.5,9.5L12,16l6.5,-6.5", 2.4))
write("star", fill(poly(star(12, 12.6, 10, 4.3)), 2.0))
write("star_outline", stroke(poly(star(12, 12.6, 9.5, 4.1)), 1.8))
write("clock_line", stroke(circle(12, 12, 9), 2), stroke("M12,7V12l3.2,2", 2))
write("person", fill(circle(12, 8, 4.3)), fill("M3.8,20.2c0,-3.7 3.7,-6.2 8.2,-6.2s8.2,2.5 8.2,6.2c0,0.5 -0.4,0.8 -0.9,0.8H4.7C4.2,21 3.8,20.7 3.8,20.2z"))
write("person_circle", fill(circle(12, 12, 10) + " " + circle(12, 9.5, 3.4) +
      " M6.2,18.2c1.3,-2 3.4,-3.1 5.8,-3.1s4.5,1.1 5.8,3.1c-1.5,1.4 -3.6,2.3 -5.8,2.3s-4.3,-0.9 -5.8,-2.3z", even_odd=True))
kp = [fill(circle(6 + c * 6, 4.5 + r * 5, 1.9)) for r in range(4) for c in range(3) if not (r == 3 and c != 1)]
write("keypad", *kp)
write("download", stroke(circle(12, 12, 9.2), 2), stroke("M12,7.5v8"), stroke("M8.5,12.5L12,16l3.5,-3.5"))
write("car", fill("M5.3,10.2l1.5,-4.1c0.4,-1.1 1.4,-1.8 2.6,-1.8h5.2c1.2,0 2.2,0.7 2.6,1.8l1.5,4.1c1.3,0.4 2.3,1.6 2.3,3v4.3"
                  "c0,0.6 -0.4,1 -1,1h-0.5v1.5c0,0.6 -0.4,1 -1,1h-1.5c-0.6,0 -1,-0.4 -1,-1v-1.5H8v1.5c0,0.6 -0.4,1 -1,1H5.5"
                  "c-0.6,0 -1,-0.4 -1,-1v-1.5H4c-0.6,0 -1,-0.4 -1,-1v-4.3C3,11.8 4,10.6 5.3,10.2z"
                  "M7.4,9.9h9.2l-1.1,-3.1c-0.1,-0.3 -0.4,-0.5 -0.7,-0.5H9.2c-0.3,0 -0.6,0.2 -0.7,0.5L7.4,9.9z"
                  "M7,15.6a1.4,1.4 0 1 0 0,-2.8a1.4,1.4 0 1 0 0,2.8z M17,15.6a1.4,1.4 0 1 0 0,-2.8a1.4,1.4 0 1 0 0,2.8z", even_odd=True))
write("xmark", stroke("M6.5,6.5L17.5,17.5M17.5,6.5L6.5,17.5", 2.4))
write("check", stroke("M5,12.5l4.5,4.5L19,7.5", 2.6))
write("plus", stroke("M12,5v14M5,12h14", 2.4))
write("trash", stroke("M4.5,6.5h15M9.5,6.5V4.8c0,-0.5 0.4,-0.8 0.8,-0.8h3.4c0.5,0 0.8,0.4 0.8,0.8v1.7", 1.9),
      stroke("M6.3,6.5l0.9,12.4c0.1,1.2 1,2.1 2.2,2.1h5.2c1.2,0 2.1,-0.9 2.2,-2.1l0.9,-12.4", 1.9), stroke("M10,10.5v6.5M14,10.5v6.5", 1.8))
write("ellipsis", fill(circle(5.5, 12, 1.9)), fill(circle(12, 12, 1.9)), fill(circle(18.5, 12, 1.9)))
write("speaker", fill("M3.5,9.3v5.4c0,0.6 0.4,1 1,1h3l4.6,3.9c0.6,0.5 1.6,0.1 1.6,-0.8V5.2c0,-0.8 -1,-1.3 -1.6,-0.8L7.5,8.3h-3C3.9,8.3 3.5,8.7 3.5,9.3z"),
      stroke("M16.5,8.6c0.9,0.9 1.4,2.1 1.4,3.4s-0.5,2.5 -1.4,3.4", 1.9), stroke("M19,6.2c1.5,1.5 2.4,3.5 2.4,5.8s-0.9,4.3 -2.4,5.8", 1.9))
write("wifi", stroke("M2.5,9a14,14 0 0 1 19,0", 2.2), stroke("M5.8,12.4a9.2,9.2 0 0 1 12.4,0", 2.2), stroke("M9,15.8a4.5,4.5 0 0 1 6,0", 2.2), fill(circle(12, 19, 1.5)))
write("bluetooth", stroke("M7,7.5l10,9L12,21V3l5,4.5L7,16.5", 2))
write("mic_off", fill(rrect(9, 2.5, 6, 11.5, 3)), stroke("M5.5,11a6.5,6.5 0 0 0 13,0M12,17.5V21", 2), stroke("M4,4l16,16", 2.2))
write("location", fill(poly([(20, 4), (12.7, 20.5), (11, 13), (3.5, 11.2)]), 2.0))
write("reply", fill("M10,5.2v3.3c6.2,0.4 10,4.1 10.5,10.3 0,0.4 -0.5,0.6 -0.8,0.3C17.6,16.4 14.6,15 10,15v3.4c0,0.6 -0.7,0.9 -1.2,0.5"
                    "L2.6,12.6c-0.4,-0.4 -0.4,-1 0,-1.3L8.8,4.7C9.3,4.3 10,4.6 10,5.2z"))
write("waveform", *[stroke("M%s,%sV%s" % (f(x), f(12 - h), f(12 + h)), 2.2) for x, h in ((4, 2), (8, 6), (12, 8.5), (16, 5), (20, 2.5))])
write("album", fill(rrect(3, 6, 15, 15, 2.5)), stroke("M7,3.2h11.3c1.5,0 2.7,1.2 2.7,2.7V17", 1.8))
write("mic", fill(rrect(8.5, 2.5, 7, 12, 3.5)), stroke("M5.5,11a6.5,6.5 0 0 0 13,0M12,17.5V21", 2))
write("battery", stroke(rrect(2.5, 7.5, 17, 9, 2.4), 1.6), fill(rrect(20.8, 10.3, 1.5, 3.4, 0.7)))
write("iphone", stroke(rrect(6.5, 2.5, 11, 19, 2.8), 1.9), fill(rrect(10, 4.6, 4, 1.4, 0.7)))
write("power", stroke("M12,3v8.5", 2.4), stroke("M7.2,6.3a7.5,7.5 0 1 0 9.6,0", 2.4))
write("moon", fill("M20.3,14.6c-1,0.4 -2.1,0.6 -3.2,0.6 -4.6,0 -8.3,-3.7 -8.3,-8.3 0,-1.1 0.2,-2.2 0.6,-3.2 0.2,-0.4 -0.3,-0.8 -0.7,-0.6"
                   "C5.2,4.6 2.8,8 2.8,11.9c0,5.1 4.2,9.3 9.3,9.3 3.9,0 7.3,-2.4 8.7,-5.9C21.1,14.8 20.7,14.4 20.3,14.6z"))
SUN_RAYS = "".join("M%s,%sL%s,%s" % (f(12 + 7.2 * math.cos(math.radians(a))), f(12 + 7.2 * math.sin(math.radians(a))),
                                    f(12 + 9.6 * math.cos(math.radians(a))), f(12 + 9.6 * math.sin(math.radians(a)))) for a in range(0, 360, 45))
write("sun", fill(circle(12, 12, 4.6)), stroke(SUN_RAYS, 2))
CLOUD = "M6.8,19.5h10.4c2.6,0 4.8,-2.1 4.8,-4.7 0,-2.4 -1.8,-4.4 -4.1,-4.7 -0.7,-2.9 -3.3,-5 -6.4,-5 -3.2,0 -5.8,2.3 -6.4,5.3C3,10.9 1.5,12.7 1.5,14.9c0,2.5 2.4,4.6 5.3,4.6z"
write("cloud", fill(CLOUD))
write("cloud_sun", fill("M9.2,21h8.6c2.3,0 4.2,-1.8 4.2,-4.1 0,-2.1 -1.6,-3.9 -3.6,-4.1 -0.6,-2.5 -2.9,-4.4 -5.6,-4.4 -2.8,0 -5.1,2 -5.6,4.6"
                       "C5.3,13.2 4,14.8 4,16.6c0,2.4 2.3,4.4 5.2,4.4z"), fill(circle(7.5, 7.5, 3)),
      stroke("M7.5,1.8v1.2M2.6,3l0.9,0.9M1,7.5h1.2M12.4,3l-0.9,0.9", 1.6))
write("cloud_rain", fill("M6.8,15.5h10.4c2.6,0 4.8,-2 4.8,-4.4 0,-2.3 -1.8,-4.1 -4.1,-4.4 -0.7,-2.7 -3.3,-4.7 -6.4,-4.7 -3.2,0 -5.8,2.2 -6.4,5C3,7.4 1.5,9.1 1.5,11.1c0,2.4 2.4,4.4 5.3,4.4z"),
      stroke("M8,18l-1,2.5M12.5,18l-1,2.5M17,18l-1,2.5", 2))
write("cloud_snow", fill("M6.8,15.5h10.4c2.6,0 4.8,-2 4.8,-4.4 0,-2.3 -1.8,-4.1 -4.1,-4.4 -0.7,-2.7 -3.3,-4.7 -6.4,-4.7 -3.2,0 -5.8,2.2 -6.4,5C3,7.4 1.5,9.1 1.5,11.1c0,2.4 2.4,4.4 5.3,4.4z"),
      fill(circle(7.5, 19.2, 1.2)), fill(circle(12, 20.5, 1.2)), fill(circle(16.5, 19.2, 1.2)))
write("cloud_bolt", fill("M6.8,15.5h10.4c2.6,0 4.8,-2 4.8,-4.4 0,-2.3 -1.8,-4.1 -4.1,-4.4 -0.7,-2.7 -3.3,-4.7 -6.4,-4.7 -3.2,0 -5.8,2.2 -6.4,5C3,7.4 1.5,9.1 1.5,11.1c0,2.4 2.4,4.4 5.3,4.4z"),
      fill(poly([(12.8, 15.5), (9.8, 19.6), (12, 19.6), (11, 22.8), (14.4, 18.2), (12.2, 18.2)]), 0.8))
write("fog", stroke("M3,8h18M5,12h14M3,16h18M7,20h10", 2))
write("back15", stroke("M12,4.5a7.5,7.5 0 1 1 -7.3,9.3", 2), stroke("M4,6.5l1.2,4.2l4.1,-1.3", 2))
write("info", stroke(circle(12, 12, 9.2), 2), stroke("M12,11v5.5", 2.4), fill(circle(12, 7.6, 1.4)))
write("parking", stroke("M9,18V6.5h4a3.6,3.6 0 0 1 0,7.2H9", 2.6))
write("drive", stroke(rrect(2.5, 6.5, 19, 11, 3), 1.9), fill(circle(17, 12, 1.4)))
write("speedometer", stroke("M4.3,17.5A9,9 0 1 1 19.7,17.5", 2.2), stroke("M12,14L16.2,8.6", 2.4), fill(circle(12, 14, 1.9)))
write("fuel", stroke(rrect(4.5, 3.5, 9, 17, 2), 2), stroke("M7,7.5h4", 2),
      stroke("M13.5,9.5h2.3c0.9,0 1.5,0.6 1.5,1.5v6c0,0.9 0.7,1.5 1.5,1.5s1.5,-0.6 1.5,-1.5V8.2l-2.6,-3", 1.9))
write("podcast", fill(circle(12, 9.8, 2.7)), stroke("M8.1,14.6a5.6,5.6 0 1 1 7.8,0", 2), stroke("M5.4,17.3a9.3,9.3 0 1 1 13.2,0", 2),
      fill(rrect(10.6, 14.2, 2.8, 7.6, 1.4)))
write("radio", stroke(rrect(3, 8.5, 18, 12, 3), 2), stroke("M7,8.5L17,3.5", 2), fill(circle(15.5, 14.5, 2.7)), stroke("M6.5,12.8h4M6.5,16.2h4", 1.9))
write("wrench", stroke("M14.6,4.3a4.6,4.6 0 0 0 -4.9,6.1L3.9,16.2a1.9,1.9 0 0 0 2.7,2.7l5.8,-5.8a4.6,4.6 0 0 0 6.1,-4.9l-2.6,2.6l-2.4,-0.4l-0.4,-2.4z", 1.9))
write("bag", stroke(rrect(4, 7.5, 16, 13.5, 3), 2), stroke("M8.5,10.5V7a3.5,3.5 0 0 1 7,0v3.5", 2))
write("play_circle", stroke(circle(12, 12, 9.3), 2), fill(poly(tri_right(9.6, 8, 6.4, 8)), 1.4))
write("calendar", stroke(rrect(3.5, 5, 17, 15.5, 3), 2), stroke("M3.5,10h17M8,3v4M16,3v4", 2))
write("gauge_reset", stroke("M4.5,12a7.5,7.5 0 1 0 2.2,-5.3", 2.2), stroke("M4,3.5v4h4", 2.2))
print("icons written to", OUT)
