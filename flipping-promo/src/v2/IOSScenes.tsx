import React from "react";
import {
  AbsoluteFill,
  Img,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";
import { AmbientVideo } from "../components/AmbientVideo";
import { Glass } from "../components/Glass";
import { GRADIENTS } from "../components/Text";
import { FullVideo } from "../shared/FullVideo";
import { prog, sp } from "../anim";
import { C, EASE_IN_OUT, EASE_OUT, FONT_DISPLAY, FONT_TEXT, clamp } from "../theme";
import { BEATS, s } from "../timing";

const b = (i: number) => s(BEATS[i]);
const IOS_BLUE = "#0a84ff";

/* ---------------- shared bits ---------------- */
export const AppIcon: React.FC<{ size: number; style?: React.CSSProperties }> = ({
  size,
  style,
}) => (
  <div
    style={{
      width: size,
      height: size,
      borderRadius: size * 0.225,
      background: "linear-gradient(160deg, #2a2d36 0%, #0b0c10 100%)",
      boxShadow:
        "inset 0 1px 0 rgba(255,255,255,0.25), 0 8px 20px rgba(0,0,0,0.45)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      flexShrink: 0,
      ...style,
    }}
  >
    <Img src={staticFile("img/logo-silver.png")} style={{ height: size * 0.62 }} />
  </div>
);

const Finger: React.FC<{ x: number; y: number; at: number; f: number }> = ({
  x,
  y,
  at,
  f,
}) => {
  const inP = prog(f, at - 12, at - 1, EASE_OUT);
  const press = interpolate(f, [at, at + 3, at + 9], [0, 1, 0], clamp);
  const ripple = prog(f, at, at + 18);
  return (
    <>
      <div
        style={{
          position: "absolute",
          left: x - 60,
          top: y - 60,
          width: 120,
          height: 120,
          borderRadius: "50%",
          border: "3px solid rgba(255,255,255,0.9)",
          scale: String(0.4 + ripple * 2.2),
          opacity: f >= at ? 1 - ripple : 0,
        }}
      />
      <div
        style={{
          position: "absolute",
          left: x - 44,
          top: y - 44,
          width: 88,
          height: 88,
          borderRadius: "50%",
          background: "rgba(255,255,255,0.8)",
          boxShadow: "0 10px 30px rgba(0,0,0,0.45)",
          translate: `${(1 - inP) * 240}px ${(1 - inP) * 360}px`,
          scale: String(1 - press * 0.22),
          opacity: inP * interpolate(f, [at + 8, at + 16], [1, 0], clamp),
        }}
      />
    </>
  );
};

/* ================================================================== */
/* 1. SPOTLIGHT SEARCH                                                */
/* ================================================================== */
export const QUERY = "perfumy i airpods";
export const TYPE_START = 2;
export const TYPE_STEP = 2;
const RESULTS = [
  { clip: "lebeau", start: 1.0, title: "Perfumy", sub: "Flipping_now · wideo" },
  { clip: "airpods", start: 3.0, title: "AirPods", sub: "Flipping_now · wideo" },
  { clip: "shelf", start: 6.2, title: "Elektronika i więcej", sub: "Flipping_now · wideo" },
];

const SUGGEST = [
  { clip: "lebeau", start: 0.5, label: "Perfumy" },
  { clip: "airpods", start: 3.0, label: "AirPods" },
  { clip: "lv", start: 1.0, label: "Perfumy" },
  { clip: "ysl", start: 1.0, label: "Perfumy" },
];

export const I1Search: React.FC<{ len: number }> = ({ len }) => {
  const f = useCurrentFrame();
  const typed = Math.max(0, Math.min(QUERY.length, Math.floor((f - TYPE_START) / TYPE_STEP) + 1));
  const caretOn = Math.floor(f / 8) % 2 === 0 || typed < QUERY.length;
  const resAt = [b(2), b(2) + 4, b(2) + 8, b(3)];
  const tapAt = len - 18;
  const press = interpolate(f, [tapAt, tapAt + 3, tapAt + 8], [0, 1, 0], clamp);
  const barIn = sp(f, -8, { damping: 16, stiffness: 140 });

  return (
    <AbsoluteFill>
      <AmbientVideo name="shelf" trimBefore={s(3)} opacity={0.3} />
      {/* large title */}
      <div
        style={{
          position: "absolute",
          left: 64,
          top: 300,
          fontFamily: FONT_DISPLAY,
          fontWeight: 700,
          fontSize: 112,
          letterSpacing: "-0.035em",
          lineHeight: 1,
          color: C.white,
          opacity: prog(f, -6, 6),
          translate: `0px ${(1 - prog(f, -6, 10)) * 30}px`,
        }}
      >
        Szukasz
        <br />
        <span
          style={{
            backgroundImage: GRADIENTS.aqua,
            WebkitBackgroundClip: "text",
            color: "transparent",
          }}
        >
          dostawcy?
        </span>
      </div>

      {/* search bar */}
      <div
        style={{
          position: "absolute",
          left: 56,
          top: 580,
          width: 968,
          height: 130,
          scale: String(interpolate(barIn, [0, 1], [0.9, 1])),
          opacity: barIn,
        }}
      >
        <Glass radius={65} style={{ inset: 0 }} blur={30} />
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "flex",
            alignItems: "center",
            gap: 22,
            paddingLeft: 42,
            fontFamily: FONT_TEXT,
            fontSize: 52,
            fontWeight: 500,
            color: C.white,
          }}
        >
          <svg width="46" height="46" viewBox="0 0 24 24">
            <circle cx="10.5" cy="10.5" r="6.5" stroke={C.grey} strokeWidth="2.4" fill="none" />
            <path d="M15.5 15.5 21 21" stroke={C.grey} strokeWidth="2.4" strokeLinecap="round" />
          </svg>
          <span>
            {typed === 0 ? (
              <span style={{ color: C.dim }}>Szukaj</span>
            ) : (
              QUERY.slice(0, typed)
            )}
            <span
              style={{
                display: "inline-block",
                width: 4,
                height: 58,
                marginLeft: 4,
                background: IOS_BLUE,
                verticalAlign: "middle",
                opacity: caretOn ? 1 : 0,
                borderRadius: 2,
              }}
            />
          </span>
        </div>
      </div>

      {/* Siri suggestions – products on screen from frame 0, replaced by results */}
      <div
        style={{
          position: "absolute",
          left: 0,
          top: 0,
          width: 1080,
          height: 1920,
          opacity: interpolate(f, [resAt[0] - 4, resAt[0] + 2], [1, 0], clamp),
          translate: `0px ${prog(f, resAt[0] - 4, resAt[0] + 4) * 60}px`,
        }}
      >
        <div
          style={{
            position: "absolute",
            left: 64,
            top: 770,
            fontFamily: FONT_TEXT,
            fontWeight: 600,
            fontSize: 32,
            color: C.grey,
            letterSpacing: "0.04em",
          }}
        >
          SUGESTIE
        </div>
        {SUGGEST.map((sg, i) => {
          const p = sp(f, -18 + i * 3, { damping: 15, stiffness: 150 });
          return (
            <div
              key={i}
              style={{
                position: "absolute",
                left: 56 + (i % 2) * 494,
                top: 830 + Math.floor(i / 2) * 354,
                width: 474,
                height: 330,
                borderRadius: 40,
                overflow: "hidden",
                translate: `0px ${(1 - p) * 300}px`,
                opacity: Math.min(1, p * 1.5),
                boxShadow:
                  "0 24px 50px rgba(0,0,0,0.55), inset 0 0 0 1px rgba(255,255,255,0.2)",
              }}
            >
              <FullVideo clip={sg.clip} start={sg.start} width={474} height={330} />
              <AbsoluteFill
                style={{
                  background:
                    "linear-gradient(180deg, rgba(0,0,0,0) 55%, rgba(0,0,0,0.65) 100%)",
                }}
              />
              <div
                style={{
                  position: "absolute",
                  left: 26,
                  bottom: 22,
                  fontFamily: FONT_TEXT,
                  fontWeight: 600,
                  fontSize: 38,
                  color: "#fff",
                }}
              >
                {sg.label}
              </div>
            </div>
          );
        })}
      </div>

      {/* top hit */}
      <div
        style={{
          position: "absolute",
          left: 64,
          top: 760,
          fontFamily: FONT_TEXT,
          fontWeight: 600,
          fontSize: 32,
          color: C.grey,
          letterSpacing: "0.04em",
          opacity: prog(f, resAt[0], resAt[0] + 6),
        }}
      >
        NAJLEPSZE TRAFIENIE
      </div>
      <div
        style={{
          position: "absolute",
          left: 56,
          top: 810,
          width: 968,
          height: 210,
          opacity: interpolate(f, [resAt[0], resAt[0] + 4], [0, 1], clamp),
          translate: `0px ${(1 - sp(f, resAt[0])) * 60}px`,
          scale: String(1 - press * 0.035),
        }}
      >
        <Glass radius={48} style={{ inset: 0 }} tint={`rgba(10,132,255,${0.05 + press * 0.2})`} sheen={prog(f, resAt[0] + 4, resAt[0] + 26)} />
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "flex",
            alignItems: "center",
            gap: 30,
            padding: "0 36px",
          }}
        >
          <AppIcon size={140} />
          <div style={{ flex: 1 }}>
            <div
              style={{
                fontFamily: FONT_DISPLAY,
                fontWeight: 700,
                fontSize: 58,
                color: C.white,
                letterSpacing: "-0.025em",
              }}
            >
              Flipping_now
            </div>
            <div
              style={{
                fontFamily: FONT_TEXT,
                fontWeight: 500,
                fontSize: 34,
                color: C.grey,
                marginTop: 4,
              }}
            >
              Perfumy · AirPods · Elektronika
            </div>
          </div>
          <div
            style={{
              padding: "16px 34px",
              borderRadius: 40,
              background: IOS_BLUE,
              fontFamily: FONT_TEXT,
              fontWeight: 700,
              fontSize: 34,
              color: "#fff",
            }}
          >
            OTWÓRZ
          </div>
        </div>
      </div>

      {/* result rows */}
      {RESULTS.map((r, i) => {
        const at = resAt[i + 1];
        const p = sp(f, at, { damping: 16, stiffness: 170 });
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: 56,
              top: 1050 + i * 150,
              width: 968,
              height: 130,
              display: "flex",
              alignItems: "center",
              gap: 28,
              opacity: interpolate(f, [at, at + 4], [0, 1], clamp),
              translate: `0px ${(1 - p) * 50}px`,
            }}
          >
            <div
              style={{
                width: 120,
                height: 120,
                borderRadius: 26,
                overflow: "hidden",
                position: "relative",
                flexShrink: 0,
                boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.2)",
              }}
            >
              <FullVideo clip={r.clip} start={r.start} width={120} height={120} />
            </div>
            <div style={{ flex: 1, borderBottom: "1px solid rgba(255,255,255,0.12)", paddingBottom: 18 }}>
              <div
                style={{
                  fontFamily: FONT_TEXT,
                  fontWeight: 600,
                  fontSize: 42,
                  color: C.white,
                }}
              >
                {r.title}
              </div>
              <div
                style={{
                  fontFamily: FONT_TEXT,
                  fontWeight: 400,
                  fontSize: 30,
                  color: C.grey,
                  marginTop: 2,
                }}
              >
                {r.sub}
              </div>
            </div>
          </div>
        );
      })}

      <Finger x={880} y={915} at={tapAt} f={f} />
      <EdgeGlow
        opacity={
          interpolate(f, [-4, 4], [0, 1], clamp) *
          interpolate(f, [resAt[0], resAt[0] + 10], [1, 0], clamp)
        }
        thickness={14}
        blur={22}
        radius={0}
      />
    </AbsoluteFill>
  );
};

/* ================================================================== */
/* 2. APP: segmented control + Cover Flow                             */
/* ================================================================== */
const ITEMS = [
  { clip: "lebeau", start: 0.3 },
  { clip: "lv", start: 0.9 },
  { clip: "airpods", start: 2.4 },
  { clip: "airpods", start: 4.4 },
  { clip: "shelf", start: 2.6 },
  { clip: "ysl", start: 0.0 },
  { clip: "lebeau", start: 0.0 },
];
const SEGMENTS = ["Perfumy", "Elektronika", "Wszystko"];
const TITLES = ["Perfumy.", "Elektronika.", "Wszystko."];

export const I2App: React.FC<{ len: number; start: number }> = ({ len, start }) => {
  const f = useCurrentFrame();
  // when each item becomes the centre one (local frames)
  const steps = [b(6), b(8), b(10), b(12), b(13), b(14)].map((x) => x - start);
  const idx = steps.reduce(
    (acc, at) => acc + sp(f, at, { damping: 18, stiffness: 170 }),
    0,
  );
  const segSteps = [b(8), b(12)].map((x) => x - start);
  const seg = segSteps.reduce(
    (acc, at) => acc + sp(f, at, { damping: 17, stiffness: 190 }),
    0,
  );
  const segIdx = f >= segSteps[1] ? 2 : f >= segSteps[0] ? 1 : 0;

  // iOS app-open: grows from the tapped card
  const open = prog(f, 0, 12, EASE_IN_OUT);
  const top = interpolate(open, [0, 1], [810, 0]);
  const left = interpolate(open, [0, 1], [56, 0]);
  const right = interpolate(open, [0, 1], [56, 0]);
  const bottom = interpolate(open, [0, 1], [1920 - 1020, 0]);
  const radius = interpolate(open, [0, 1], [48, 0]);
  const outP = interpolate(f, [len - 10, len], [0, 1], clamp);

  const SEG_W = 300;
  return (
    <AbsoluteFill
      style={{
        clipPath: `inset(${top}px ${right}px ${bottom}px ${left}px round ${radius}px)`,
        background: "#05060a",
      }}
    >
      <AmbientVideo name={ITEMS[Math.min(ITEMS.length - 1, Math.round(idx))].clip} trimBefore={s(2)} opacity={0.5} />
      <AbsoluteFill style={{ opacity: interpolate(open, [0.15, 0.7], [0, 1], clamp) * (1 - outP), filter: `blur(${outP * 20}px)` }}>
        {/* nav */}
        <div
          style={{
            position: "absolute",
            left: 64,
            top: 210,
            display: "flex",
            alignItems: "center",
            gap: 16,
            fontFamily: FONT_TEXT,
            fontWeight: 600,
            fontSize: 36,
            color: C.grey,
          }}
        >
          <AppIcon size={64} />
          Flipping_now
        </div>
        <div style={{ position: "absolute", left: 64, top: 290, height: 130, overflow: "hidden", width: 960 }}>
          {TITLES.map((t, i) => {
            const d = seg - i;
            return (
              <div
                key={t}
                style={{
                  position: "absolute",
                  top: 0,
                  fontFamily: FONT_DISPLAY,
                  fontWeight: 700,
                  fontSize: 116,
                  letterSpacing: "-0.04em",
                  color: C.white,
                  translate: `0px ${d * -130}px`,
                  opacity: 1 - Math.min(1, Math.abs(d)),
                  filter: `blur(${Math.min(1, Math.abs(d)) * 12}px)`,
                }}
              >
                {t}
              </div>
            );
          })}
        </div>

        {/* segmented control */}
        <div style={{ position: "absolute", left: 540 - (SEG_W * 3) / 2 - 10, top: 460, width: SEG_W * 3 + 20, height: 100 }}>
          <Glass radius={50} style={{ inset: 0 }} />
          <div
            style={{
              position: "absolute",
              top: 10,
              left: 10 + seg * SEG_W,
              width: SEG_W,
              height: 80,
              borderRadius: 40,
              background: "rgba(255,255,255,0.92)",
              boxShadow: "0 6px 16px rgba(0,0,0,0.3)",
              scale: `${1 + Math.sin(Math.PI * (seg % 1)) * 0.12} ${1 - Math.sin(Math.PI * (seg % 1)) * 0.06}`,
            }}
          />
          <div style={{ position: "absolute", inset: 10, display: "flex" }}>
            {SEGMENTS.map((label, i) => (
              <div
                key={label}
                style={{
                  width: SEG_W,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontFamily: FONT_TEXT,
                  fontWeight: 600,
                  fontSize: 36,
                  color: i === segIdx ? "#111" : C.white,
                }}
              >
                {label}
              </div>
            ))}
          </div>
        </div>

        {/* cover flow */}
        <div style={{ position: "absolute", inset: 0, perspective: 1500 }}>
          {ITEMS.map((it, i) => {
            const d = i - idx;
            if (Math.abs(d) > 2.2) return null;
            const ad = Math.abs(d);
            const sign = Math.sign(d);
            const x = sign * (Math.min(ad, 1) * 400 + Math.max(0, ad - 1) * 170);
            const rotY = -sign * Math.min(ad, 1) * 58;
            const sc = 1 - Math.min(ad, 1) * 0.2;
            return (
              <React.Fragment key={i}>
              {/* Cover Flow floor reflection */}
              <div
                style={{
                  position: "absolute",
                  left: 540 - 290 + x,
                  top: 640 + 14,
                  width: 580,
                  height: 800,
                  rotate: `y ${rotY}deg`,
                  scale: `${sc} ${-sc}`,
                  transformOrigin: "50% 100%",
                  zIndex: 100 - Math.round(ad * 10),
                  borderRadius: 54,
                  overflow: "hidden",
                  opacity: 0.32,
                  WebkitMaskImage:
                    "linear-gradient(0deg, rgba(0,0,0,1) 0%, rgba(0,0,0,0) 45%)",
                  maskImage:
                    "linear-gradient(0deg, rgba(0,0,0,1) 0%, rgba(0,0,0,0) 45%)",
                }}
              >
                <FullVideo clip={it.clip} start={it.start} width={580} height={800} />
              </div>
              <div
                style={{
                  position: "absolute",
                  left: 540 - 290 + x,
                  top: 640,
                  width: 580,
                  height: 800,
                  rotate: `y ${rotY}deg`,
                  scale: String(sc),
                  zIndex: 100 - Math.round(ad * 10),
                  borderRadius: 54,
                  overflow: "hidden",
                  boxShadow:
                    "0 40px 80px rgba(0,0,0,0.6), inset 0 0 0 1px rgba(255,255,255,0.18)",
                }}
              >
                <FullVideo clip={it.clip} start={it.start} width={580} height={800} />
                <AbsoluteFill
                  style={{
                    background: `linear-gradient(160deg, rgba(255,255,255,0.16) 0%, rgba(255,255,255,0) 40%), rgba(0,0,0,${Math.min(ad, 1) * 0.45})`,
                    borderRadius: 54,
                    boxShadow: "inset 0 1.5px 0 rgba(255,255,255,0.4)",
                  }}
                />
              </div>
              </React.Fragment>
            );
          })}
        </div>

        {/* page dots */}
        <div style={{ position: "absolute", top: 1480, left: 0, right: 0, display: "flex", justifyContent: "center", gap: 14 }}>
          {ITEMS.map((_, i) => {
            const on = 1 - Math.min(1, Math.abs(i - idx));
            return (
              <div
                key={i}
                style={{
                  width: 14 + on * 30,
                  height: 14,
                  borderRadius: 7,
                  background: `rgba(255,255,255,${0.3 + on * 0.65})`,
                }}
              />
            );
          })}
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

/* ================================================================== */
/* 3. LOCK SCREEN NOTIFICATIONS (real profile facts)                  */
/* ================================================================== */
const NOTIFS = [
  { title: "1976 obserwujących", body: "Tylu ludzi już śledzi @flippingnow999." },
  { title: "5583 polubień", body: "Zobacz filmy na profilu." },
  { title: "Legit check ✓", body: "Relacja „legit check” na profilu." },
  { title: "Najlepszy dostawca", body: "Perfumy i elektronika – link w bio." },
];

export const I3Notifs: React.FC<{ len: number; start: number }> = ({ len, start }) => {
  const f = useCurrentFrame();
  const at = [b(16), b(18), b(20), b(22)].map((x) => x - start);
  const out = interpolate(f, [len - 8, len], [0, 1], clamp);
  const clockIn = prog(f, 0, 12);
  const N_H = 228;
  const GAP = 22;

  return (
    <AbsoluteFill>
      <AmbientVideo name="lebeau" trimBefore={s(1)} opacity={0.6} />
      <AbsoluteFill style={{ opacity: 1 - out, scale: String(1 - out * 0.06), filter: `blur(${out * 18}px)` }}>
        <div
          style={{
            position: "absolute",
            top: 230,
            left: 0,
            right: 0,
            textAlign: "center",
            opacity: clockIn,
            translate: `0px ${(1 - clockIn) * -40}px`,
          }}
        >
          <div
            style={{
              fontFamily: FONT_DISPLAY,
              fontWeight: 600,
              fontSize: 250,
              lineHeight: 1,
              letterSpacing: "-0.03em",
              color: "rgba(255,255,255,0.92)",
            }}
          >
            9:41
          </div>
        </div>

        {NOTIFS.map((n, i) => {
          if (f < at[i] - 1) return null;
          const p = sp(f, at[i], { damping: 15, stiffness: 160 });
          // how many newer notifications arrived after this one (push down)
          const pushed = at
            .slice(i + 1)
            .reduce((acc, a) => acc + sp(f, a, { damping: 16, stiffness: 160 }), 0);
          const y = 560 + pushed * (N_H + GAP);
          return (
            <div
              key={i}
              style={{
                position: "absolute",
                left: 50,
                top: y,
                width: 980,
                height: N_H,
                translate: `0px ${(1 - p) * -260}px`,
                scale: String(interpolate(p, [0, 1], [0.85, 1])),
                opacity: interpolate(f, [at[i], at[i] + 4], [0, 1], clamp),
                zIndex: 10 + i,
              }}
            >
              <Glass radius={52} style={{ inset: 0 }} blur={34} />
              <div
                style={{
                  position: "absolute",
                  inset: 0,
                  display: "flex",
                  alignItems: "center",
                  gap: 28,
                  padding: "0 36px",
                }}
              >
                <AppIcon size={118} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      fontFamily: FONT_TEXT,
                      fontSize: 30,
                      fontWeight: 600,
                      color: "rgba(255,255,255,0.6)",
                    }}
                  >
                    <span>FLIPPING_NOW</span>
                    <span style={{ fontWeight: 500 }}>teraz</span>
                  </div>
                  <div
                    style={{
                      fontFamily: FONT_TEXT,
                      fontSize: 52,
                      fontWeight: 700,
                      color: C.white,
                      marginTop: 4,
                      letterSpacing: "-0.01em",
                    }}
                  >
                    {n.title}
                  </div>
                  <div
                    style={{
                      fontFamily: FONT_TEXT,
                      fontSize: 37,
                      fontWeight: 500,
                      color: "rgba(255,255,255,0.78)",
                      marginTop: 2,
                      whiteSpace: "nowrap",
                    }}
                  >
                    {n.body}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

/* ================================================================== */
/* 4. DYNAMIC ISLAND -> CTA                                           */
/* ================================================================== */
export const I4Island: React.FC<{ start: number }> = ({ start }) => {
  const f = useCurrentFrame();
  const ex = sp(f, 3, { damping: 15, stiffness: 120 });
  const w = interpolate(ex, [0, 1], [250, 960]);
  const h = interpolate(ex, [0, 1], [74, 1150]);
  const top = interpolate(ex, [0, 1], [60, 300]);
  const r = interpolate(ex, [0, 1], [37, 96]);
  const content = prog(f, 12, 24);
  const ctaAt = b(26) - start;
  const tapAt = b(28) - start;
  const cta = sp(f, ctaAt, { damping: 13, stiffness: 170 });
  const press = interpolate(f, [tapAt, tapAt + 3, tapAt + 9], [0, 1, 0], clamp);
  const glow = prog(f, tapAt, tapAt + 8);
  const sheenP = interpolate(f, [14, 40], [-20, 120], { ...clamp, easing: EASE_IN_OUT });

  return (
    <AbsoluteFill>
      <AmbientVideo name="lv" trimBefore={s(2)} opacity={0.45} />
      {/* aura */}
      <div
        style={{
          position: "absolute",
          left: 540 - 520,
          top: 380,
          width: 1040,
          height: 1040,
          borderRadius: "50%",
          background: "conic-gradient(from 0deg, #2fd6c8, #2c7bff, #8a5cff, #ff6ad5, #2fd6c8)",
          filter: "blur(120px)",
          opacity: 0.45 * ex,
          rotate: `${f * 2}deg`,
        }}
      />
      {/* glow around the island */}
      <div
        style={{
          position: "absolute",
          left: 540 - w / 2 - 6,
          top: top - 6,
          width: w + 12,
          height: h + 12,
          opacity: content,
        }}
      >
        <EdgeGlow opacity={1} radius={r + 6} thickness={8} blur={16} />
      </div>
      {/* the island */}
      <div
        style={{
          position: "absolute",
          left: 540 - w / 2,
          top,
          width: w,
          height: h,
          borderRadius: r,
          background: "#000",
          boxShadow: `0 40px 120px rgba(0,0,0,${0.6 * ex}), inset 0 0 0 1.5px rgba(255,255,255,${0.12 * ex})`,
          overflow: "hidden",
        }}
      >
        <AbsoluteFill
          style={{
            background:
              "radial-gradient(80% 50% at 50% 0%, rgba(44,123,255,0.25), rgba(0,0,0,0) 70%)",
            opacity: content,
          }}
        />
        <AbsoluteFill
          style={{
            opacity: content,
            translate: `0px ${(1 - content) * 30}px`,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            paddingTop: 110,
          }}
        >
          <div style={{ position: "relative" }}>
            <AppIcon size={300} />
            <Img
              src={staticFile("img/logo-white.png")}
              style={{
                position: "absolute",
                left: 150 - (300 * 0.62 * (806 / 782)) / 2,
                top: 150 - (300 * 0.62) / 2,
                height: 300 * 0.62,
                WebkitMaskImage: `linear-gradient(105deg, transparent ${sheenP - 12}%, #000 ${sheenP}%, transparent ${sheenP + 12}%)`,
                maskImage: `linear-gradient(105deg, transparent ${sheenP - 12}%, #000 ${sheenP}%, transparent ${sheenP + 12}%)`,
              }}
            />
          </div>
          <div
            style={{
              marginTop: 60,
              fontFamily: FONT_DISPLAY,
              fontWeight: 700,
              fontSize: 116,
              letterSpacing: "-0.04em",
              backgroundImage: GRADIENTS.silver,
              WebkitBackgroundClip: "text",
              color: "transparent",
            }}
          >
            Flipping_now
          </div>
          <div
            style={{
              marginTop: 6,
              fontFamily: FONT_TEXT,
              fontWeight: 500,
              fontSize: 40,
              color: C.grey,
            }}
          >
            Perfumy · AirPods · Elektronika
          </div>

          <div
            style={{
              marginTop: 90,
              width: 720,
              height: 150,
              borderRadius: 75,
              background: "linear-gradient(95deg, #2fd6c8, #0a84ff 55%, #8a5cff)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: 20,
              fontFamily: FONT_TEXT,
              fontWeight: 700,
              fontSize: 60,
              color: "#fff",
              opacity: interpolate(f, [ctaAt, ctaAt + 3], [0, 1], clamp),
              scale: String(interpolate(cta, [0, 1], [0.6, 1]) * (1 - press * 0.06)),
              boxShadow: `0 0 ${40 + glow * 60}px rgba(10,132,255,${0.35 + glow * 0.35}), inset 0 2px 0 rgba(255,255,255,0.4)`,
            }}
          >
            Link w bio
            <svg width="50" height="50" viewBox="0 0 24 24" style={{ translate: `0px ${Math.sin(f / 4) * 3 * glow}px` }}>
              <path d="M12 4v14M5.5 11.5 12 18l6.5-6.5" fill="none" stroke="#fff" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <div
            style={{
              marginTop: 34,
              fontFamily: FONT_TEXT,
              fontWeight: 600,
              fontSize: 38,
              color: "rgba(255,255,255,0.7)",
              opacity: prog(f, ctaAt + 6, ctaAt + 16),
            }}
          >
            @flippingnow999
          </div>
        </AbsoluteFill>
      </div>
      <Finger x={760} y={1153} at={tapAt} f={f} />
    </AbsoluteFill>
  );
};

/* ---------------- Apple Intelligence edge glow ---------------- */
export const EdgeGlow: React.FC<{
  opacity: number;
  inset?: number;
  radius?: number;
  thickness?: number;
  blur?: number;
  style?: React.CSSProperties;
}> = ({ opacity, inset = 0, radius = 70, thickness = 16, blur = 18, style }) => {
  const f = useCurrentFrame();
  const ring = (b: number, o: number) => (
    <div
      style={{
        position: "absolute",
        inset,
        borderRadius: radius,
        padding: thickness,
        background: `conic-gradient(from ${f * 4}deg, #ff6ad5, #ffb36a, #2fd6c8, #2c7bff, #8a5cff, #ff6ad5)`,
        WebkitMask:
          "linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)",
        WebkitMaskComposite: "xor",
        maskComposite: "exclude",
        filter: `blur(${b}px)`,
        opacity: o,
      }}
    />
  );
  return (
    <AbsoluteFill style={{ opacity, pointerEvents: "none", ...style }}>
      {ring(blur, 0.9)}
      {ring(2, 0.8)}
    </AbsoluteFill>
  );
};

/* ---------------- status bar (whole video) ---------------- */
export const StatusBar: React.FC = () => (
  <div
    style={{
      position: "absolute",
      top: 52,
      left: 96,
      right: 84,
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      fontFamily: FONT_TEXT,
      fontWeight: 600,
      fontSize: 40,
      color: "#fff",
      zIndex: 50,
    }}
  >
    <span>9:41</span>
    <span style={{ display: "flex", gap: 14, alignItems: "center" }}>
      <svg width="44" height="28" viewBox="0 0 22 14">
        {[0, 1, 2, 3].map((i) => (
          <rect key={i} x={i * 5.5} y={10 - i * 3} width="4" height={4 + i * 3} rx="1" fill="#fff" />
        ))}
      </svg>
      <svg width="62" height="30" viewBox="0 0 31 15">
        <rect x="0.5" y="0.5" width="26" height="14" rx="4" fill="none" stroke="rgba(255,255,255,0.5)" />
        <rect x="2.5" y="2.5" width="22" height="10" rx="2.5" fill="#fff" />
        <rect x="28" y="5" width="2" height="5" rx="1" fill="rgba(255,255,255,0.5)" />
      </svg>
    </span>
  </div>
);

export const IslandIdle: React.FC = () => (
  <div
    style={{
      position: "absolute",
      left: 540 - 125,
      top: 60,
      width: 250,
      height: 74,
      borderRadius: 37,
      background: "#000",
      zIndex: 49,
    }}
  />
);
