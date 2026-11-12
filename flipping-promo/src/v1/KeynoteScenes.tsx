import React from "react";
import {
  AbsoluteFill,
  Img,
  Sequence,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";
import { AmbientVideo } from "../components/AmbientVideo";
import { Background } from "../components/Background";
import { Glass } from "../components/Glass";
import { GRADIENTS } from "../components/Text";
import { FullVideo, VideoInText } from "../shared/FullVideo";
import { exitP, prog, shake, sp } from "../anim";
import {
  C,
  EASE_IN,
  EASE_IN_OUT,
  EASE_OUT,
  FONT_DISPLAY,
  FONT_TEXT,
  clamp,
} from "../theme";
import { BEATS, s } from "../timing";

const b = (i: number) => s(BEATS[i]);

/* ------------------------------------------------------------------ */
/* Masked line reveal (text slides up from behind a hard mask)         */
/* ------------------------------------------------------------------ */
export const MaskLine: React.FC<{
  at: number;
  children: React.ReactNode;
  style?: React.CSSProperties;
  dur?: number;
}> = ({ at, children, style, dur = 14 }) => {
  const f = useCurrentFrame();
  const p = prog(f, at, at + dur);
  return (
    <div style={{ overflow: "hidden", paddingBottom: "0.22em", marginBottom: "-0.1em", ...style }}>
      <div
        style={{
          translate: `0px ${(1 - p) * 110}%`,
          rotate: `${(1 - p) * 4}deg`,
          transformOrigin: "0% 100%",
        }}
      >
        {children}
      </div>
    </div>
  );
};

export const H: React.CSSProperties = {
  fontFamily: FONT_DISPLAY,
  fontWeight: 700,
  letterSpacing: "-0.045em",
  lineHeight: 0.95,
  color: C.white,
};

/* ------------------------------------------------------------------ */
/* 1. HOOK – giant type, footage inside "dostawcy?", zoom-through      */
/* ------------------------------------------------------------------ */
// point inside the stem of the "d" in "dostawcy?" (zoom-through target)
const ZOOM_X = 137;
const ZOOM_Y = 858;

const MARQUEE = [
  { clip: "lebeau", start: 0.5 },
  { clip: "airpods", start: 3.0 },
  { clip: "lv", start: 1.0 },
  { clip: "ysl", start: 1.0 },
  { clip: "shelf", start: 6.2 },
];

export const K1Hook: React.FC<{ len: number }> = ({ len }) => {
  const f = useCurrentFrame();
  const zoomStart = len - 16;
  const z = interpolate(f, [zoomStart, len], [0, 1], {
    ...clamp,
    easing: EASE_IN,
  });
  const zoom = Math.pow(60, z);
  const hit = b(0);
  const slam = sp(f, hit, { damping: 13, stiffness: 190 });
  const words = ["Perfumy.", "AirPods.", "Elektronika."];
  const wordAt = [b(2), b(2) + 6, b(3)];

  return (
    <AbsoluteFill>
      {/* top spotlight */}
      <AbsoluteFill
        style={{
          background:
            "radial-gradient(70% 45% at 50% 0%, rgba(120,140,255,0.22), rgba(0,0,0,0) 70%)",
          opacity: 1 - z,
        }}
      />
      <AbsoluteFill
        style={{
          scale: String(zoom),
          transformOrigin: `${ZOOM_X}px ${ZOOM_Y}px`,
          translate: `${shake(f, hit).x}px ${shake(f, hit).y}px`,
        }}
      >
        {/* line 1 */}
        <div style={{ position: "absolute", left: 76, top: 560 }}>
          <MaskLine at={-8} dur={14}>
            <div style={{ ...H, fontSize: 200 }}>Szukasz</div>
          </MaskLine>
        </div>
        {/* soft white base so dark footage never makes letters vanish */}
        <div
          style={{
            position: "absolute",
            left: 62,
            top: 770,
            ...H,
            fontSize: 178,
            color: "rgba(255,255,255,0.42)",
            scale: String(interpolate(slam, [0, 1], [1.35, 1])),
            transformOrigin: "0% 50%",
            opacity: interpolate(f, [hit - 2, hit + 1], [0, 1], clamp),
            filter: `blur(${interpolate(slam, [0, 0.6], [24, 0], clamp)}px)`,
          }}
        >
          dostawcy?
        </div>
        {/* line 2: footage inside the letters */}
        <div
          style={{
            position: "absolute",
            inset: 0,
            opacity: interpolate(f, [hit - 2, hit + 1], [0, 1], clamp),
          }}
        >
          <VideoInText clip="shelf" start={3.0} zoom={1.15} filter="brightness(1.9) contrast(1.15) saturate(1.6)">
            <div
              style={{
                position: "absolute",
                left: 62,
                top: 770,
                ...H,
                fontSize: 178,
                color: "#fff",
                scale: String(interpolate(slam, [0, 1], [1.35, 1])),
                transformOrigin: "0% 50%",
                filter: `blur(${interpolate(slam, [0, 0.6], [24, 0], clamp)}px)`,
              }}
            >
              dostawcy?
            </div>
          </VideoInText>
        </div>
        {/* thin outline so the footage-letters read on dark */}
        <div
          style={{
            position: "absolute",
            left: 62,
            top: 770,
            ...H,
            fontSize: 178,
            color: "transparent",
            WebkitTextStroke: "1.5px rgba(255,255,255,0.35)",
            scale: String(interpolate(slam, [0, 1], [1.35, 1])),
            transformOrigin: "0% 50%",
            opacity: interpolate(f, [hit, hit + 4], [0, 1], clamp),
          }}
        >
          dostawcy?
        </div>

        {/* product words */}
        <div
          style={{
            position: "absolute",
            left: 80,
            top: 1040,
            display: "flex",
            gap: 22,
            fontFamily: FONT_DISPLAY,
            fontWeight: 600,
            fontSize: 54,
            letterSpacing: "-0.02em",
          }}
        >
          {words.map((w, i) => (
            <MaskLine key={w} at={wordAt[i]} dur={8}>
              <span style={{ color: i === 1 ? C.white : C.grey }}>{w}</span>
            </MaskLine>
          ))}
        </div>

        {/* product marquee – products visible from frame 0 */}
        <div
          style={{
            position: "absolute",
            top: 1230,
            left: 0,
            width: 1080,
            height: 380,
            translate: `0px ${(1 - sp(f, -8, { damping: 16, stiffness: 110 })) * 500}px`,
          }}
        >
          {MARQUEE.map((m, i) => {
            const x = 40 + i * 290 - f * 4.2;
            return (
              <div
                key={i}
                style={{
                  position: "absolute",
                  left: x,
                  top: i % 2 === 0 ? 0 : 24,
                  width: 260,
                  height: 350,
                  borderRadius: 40,
                  overflow: "hidden",
                  boxShadow:
                    "0 30px 60px rgba(0,0,0,0.6), inset 0 0 0 1px rgba(255,255,255,0.2)",
                }}
              >
                <FullVideo clip={m.clip} start={m.start} width={260} height={350} />
                <AbsoluteFill
                  style={{
                    borderRadius: 40,
                    boxShadow: "inset 0 1.5px 0 rgba(255,255,255,0.45)",
                    background:
                      "linear-gradient(160deg, rgba(255,255,255,0.18), rgba(255,255,255,0) 40%)",
                  }}
                />
              </div>
            );
          })}
        </div>

        {/* brand line */}
        <div
          style={{
            position: "absolute",
            left: 80,
            top: 420,
            display: "flex",
            alignItems: "center",
            gap: 16,
            opacity: prog(f, -4, 8),
          }}
        >
          <Img src={staticFile("img/logo-silver.png")} style={{ height: 54 }} />
          <span
            style={{
              fontFamily: FONT_TEXT,
              fontWeight: 600,
              fontSize: 36,
              color: C.grey,
              letterSpacing: "-0.01em",
            }}
          >
            Flipping_now
          </span>
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ opacity: interpolate(z, [0.55, 1], [0, 1], clamp) }}>
        <FullVideo
          clip="shelf"
          start={3.0}
          zoom={1.15}
          style={{ filter: "brightness(1.5) saturate(1.4)" }}
        />
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

/* ------------------------------------------------------------------ */
/* 2. PERFUMY – full-bleed footage + liquid glass magnifier lens       */
/* ------------------------------------------------------------------ */
const LENS_R = 165;

export const K2Perfumes: React.FC<{ len: number }> = ({ len }) => {
  const f = useCurrentFrame();
  const out = exitP(f, len);
  const zoom = interpolate(f, [0, len], [1.18, 1.04], clamp);
  const origin = "35% 55%";
  const lensIn = sp(f, 8, { damping: 13, stiffness: 140 });
  const t = prog(f, 8, len, EASE_IN_OUT);
  const cx = interpolate(t, [0, 1], [330, 520]);
  const cy = interpolate(t, [0, 1], [820, 1000]);
  const mag = 1.75;
  const fadeIn = interpolate(f, [0, 8], [0, 1], clamp);

  return (
    <AbsoluteFill style={{ opacity: 1 - out * 0.0 }}>
      <AbsoluteFill style={{ opacity: fadeIn }}>
        <FullVideo clip="lebeau" start={0.3} zoom={zoom} origin={origin} />
        <AbsoluteFill
          style={{
            background:
              "linear-gradient(180deg, rgba(0,0,0,0.35) 0%, rgba(0,0,0,0) 25%, rgba(0,0,0,0) 45%, rgba(0,0,0,0.85) 78%, rgba(0,0,0,0.9) 100%)",
          }}
        />
        {/* magnifier lens */}
        <div
          style={{
            position: "absolute",
            left: cx - LENS_R,
            top: cy - LENS_R,
            width: LENS_R * 2,
            height: LENS_R * 2,
            borderRadius: "50%",
            overflow: "hidden",
            scale: String(lensIn),
            boxShadow:
              "0 30px 60px rgba(0,0,0,0.55), inset 0 0 0 2px rgba(255,255,255,0.55)",
          }}
        >
          <div
            style={{
              position: "absolute",
              left: -(cx - LENS_R),
              top: -(cy - LENS_R),
              width: 1080,
              height: 1920,
              scale: String(mag),
              transformOrigin: `${cx}px ${cy}px`,
            }}
          >
            <FullVideo clip="lebeau" start={0.3} zoom={zoom} origin={origin} />
          </div>
          {/* glass optics: edge refraction tint + specular */}
          <div
            style={{
              position: "absolute",
              inset: 0,
              borderRadius: "50%",
              boxShadow:
                "inset 0 0 30px rgba(255,255,255,0.35), inset 8px 10px 18px rgba(255,255,255,0.35), inset -10px -12px 24px rgba(47,214,200,0.35)",
            }}
          />
          <div
            style={{
              position: "absolute",
              left: "16%",
              top: "10%",
              width: "42%",
              height: "24%",
              borderRadius: "50%",
              background:
                "radial-gradient(closest-side, rgba(255,255,255,0.55), rgba(255,255,255,0))",
              rotate: "-25deg",
            }}
          />
        </div>

        <div style={{ position: "absolute", left: 76, top: 1150 }}>
          <MaskLine at={4} dur={14}>
            <div
              style={{
                ...H,
                fontSize: 210,
                backgroundImage: GRADIENTS.silver,
                WebkitBackgroundClip: "text",
                color: "transparent",
              }}
            >
              Perfumy.
            </div>
          </MaskLine>
          <MaskLine at={12} dur={12}>
            <div
              style={{
                fontFamily: FONT_DISPLAY,
                fontWeight: 500,
                fontSize: 50,
                color: "rgba(255,255,255,0.75)",
                letterSpacing: "-0.015em",
                marginTop: 4,
              }}
            >
              Topowe marki. Jedno źródło.
            </div>
          </MaskLine>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

/* ------------------------------------------------------------------ */
/* 3. AIRPODS – flips to light "product page" with a circle wipe       */
/* ------------------------------------------------------------------ */
export const K3AirPods: React.FC<{ len: number }> = ({ len }) => {
  const f = useCurrentFrame();
  const wipe = prog(f, 0, 10, EASE_OUT);
  const card = sp(f, 4, { damping: 16, stiffness: 120 });
  const out = exitP(f, len);
  const DARK = "#1d1d1f";

  return (
    <AbsoluteFill
      style={{
        clipPath: `circle(${wipe * 125}% at 50% 52%)`,
        background: "#f5f5f7",
        scale: String(1 - out * 0.06),
      }}
    >
      <AbsoluteFill
        style={{
          background:
            "radial-gradient(60% 40% at 50% 60%, rgba(255,255,255,1), rgba(245,245,247,0) 70%)",
        }}
      />
      <div
        style={{
          position: "absolute",
          top: 230,
          left: 0,
          right: 0,
          textAlign: "center",
        }}
      >
        <MaskLine at={3} dur={14}>
          <div
            style={{
              ...H,
              fontSize: 190,
              color: DARK,
              textAlign: "center",
            }}
          >
            AirPods.
          </div>
        </MaskLine>
        <MaskLine at={10} dur={12}>
          <div
            style={{
              fontFamily: FONT_DISPLAY,
              fontWeight: 600,
              fontSize: 64,
              letterSpacing: "-0.02em",
              backgroundImage:
                "linear-gradient(90deg, #0a84ff, #5e5ce6, #bf5af2)",
              WebkitBackgroundClip: "text",
              color: "transparent",
              textAlign: "center",
            }}
          >
            i inna elektronika.
          </div>
        </MaskLine>
      </div>
      <div
        style={{
          position: "absolute",
          left: 190,
          top: 560,
          width: 700,
          height: 920,
          borderRadius: 64,
          overflow: "hidden",
          translate: `0px ${(1 - card) * 500}px`,
          scale: String(interpolate(card, [0, 1], [0.92, 1])),
          boxShadow:
            "0 50px 90px -20px rgba(0,0,0,0.35), 0 12px 30px -10px rgba(0,0,0,0.2)",
        }}
      >
        <FullVideo
          clip="airpods"
          start={2.4}
          width={700}
          height={920}
          zoom={interpolate(f, [0, len], [1.12, 1.0], clamp)}
        />
        <AbsoluteFill
          style={{
            background:
              "linear-gradient(160deg, rgba(255,255,255,0.18) 0%, rgba(255,255,255,0) 35%)",
          }}
        />
      </div>
    </AbsoluteFill>
  );
};

/* ------------------------------------------------------------------ */
/* 4. MONTAGE – 4 strips, one per beat                                 */
/* ------------------------------------------------------------------ */
const STRIPS = [
  { clip: "shelf", start: 3.1 },
  { clip: "ysl", start: 2.0 },
  { clip: "airpods", start: 1.0 },
  { clip: "lv", start: 1.2 },
];

export const K4Montage: React.FC<{ len: number; start: number }> = ({
  len,
  start,
}) => {
  const f = useCurrentFrame();
  const at = [12, 13, 14, 15].map((i) => b(i) - start);
  const outT = interpolate(f, [len - 10, len], [0, 1], {
    ...clamp,
    easing: EASE_IN,
  });
  const W = 1080 / 4;
  const beatK = at.filter((a) => f >= a).length - 1;
  const local = f - at[Math.max(0, beatK)];
  const punch = interpolate(local, [0, 8], [1.08, 1], { ...clamp, easing: EASE_OUT });
  const flash = interpolate(local, [0, 5], [0.28, 0], clamp);

  return (
    <AbsoluteFill>
      {[0, 1, 2, 3].map((i) => {
        const p = prog(f, i * 2 - 2, i * 2 + 5, EASE_OUT);
        const dir = i % 2 === 0 ? -1 : 1;
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: i * W,
              top: 0,
              width: W + 1,
              height: 1920,
              overflow: "hidden",
              clipPath: dir < 0 ? `inset(0 0 ${(1 - p) * 100}% 0)` : `inset(${(1 - p) * 100}% 0 0 0)`,
              translate: `0px ${outT * 1920 * dir}px`,
            }}
          >
            {at.map((a, k) => {
              const st = STRIPS[(i + k) % 4];
              return (
                <Sequence
                  key={k}
                  from={a}
                  durationInFrames={(at[k + 1] ?? len) - a}
                  layout="none"
                >
                  <div
                    style={{
                      position: "absolute",
                      left: -(540 - W / 2),
                      top: 0,
                      width: 1080,
                      height: 1920,
                      scale: String(punch),
                    }}
                  >
                    <FullVideo clip={st.clip} start={st.start} zoom={1.1} />
                  </div>
                </Sequence>
              );
            })}
            <AbsoluteFill
              style={{
                background: `rgba(255,255,255,${flash})`,
                borderRight: "3px solid rgba(0,0,0,0.7)",
              }}
            />
          </div>
        );
      })}

      {/* center glass bar with title */}
      <div
        style={{
          position: "absolute",
          left: 50,
          right: 50,
          top: 760,
          height: 360,
          opacity: prog(f, at[0], at[0] + 6) * (1 - outT),
          scale: String(interpolate(sp(f, at[0]), [0, 1], [0.9, 1])),
        }}
      >
        <Glass radius={60} style={{ inset: 0 }} blur={40} />
        <div
          style={{
            position: "absolute",
            inset: 0,
            borderRadius: 60,
            background: "rgba(0,0,0,0.42)",
          }}
        />
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <MaskLine at={at[0] + 2} dur={10}>
            <div style={{ ...H, fontSize: 170, textAlign: "center" }}>
              Wszystko
            </div>
          </MaskLine>
          <MaskLine at={at[2]} dur={10}>
            <div
              style={{
                ...H,
                fontSize: 78,
                letterSpacing: "-0.03em",
                backgroundImage: GRADIENTS.aqua,
                WebkitBackgroundClip: "text",
                color: "transparent",
                textAlign: "center",
              }}
            >
              w jednym miejscu.
            </div>
          </MaskLine>
        </div>
      </div>
    </AbsoluteFill>
  );
};

/* ------------------------------------------------------------------ */
/* 5. PROOF – rolling digits (real profile numbers)                    */
/* ------------------------------------------------------------------ */
const DIGIT_H = 300;

const Roller: React.FC<{
  from: string;
  to: string;
  at: number;
  f: number;
}> = ({ from, to, at, f }) => {
  const cols = to.length;
  return (
    <div style={{ display: "flex", justifyContent: "center" }}>
      {to.split("").map((d, i) => {
        const a = Number(from[i] ?? 0);
        const target = Number(d) + 10 * (2 + (i % 2));
        const startIdx = a;
        const t0 = at + i * 3;
        const pNow = prog(f, t0, t0 + 22, EASE_OUT);
        const pPrev = prog(f - 1, t0, t0 + 22, EASE_OUT);
        const idx = startIdx + (target - startIdx) * pNow;
        const vel = Math.abs((target - startIdx) * (pNow - pPrev));
        return (
          <div
            key={`${i}-${cols}`}
            style={{
              height: DIGIT_H,
              overflow: "hidden",
              width: DIGIT_H * 0.58,
              WebkitMaskImage:
                "linear-gradient(180deg, transparent 0%, #000 18%, #000 82%, transparent 100%)",
            }}
          >
            <div
              style={{
                translate: `0px ${-idx * DIGIT_H}px`,
                filter: `blur(${Math.min(vel * 9, 14)}px)`,
              }}
            >
              {Array.from({ length: 50 }).map((_, k) => (
                <div
                  key={k}
                  style={{
                    height: DIGIT_H,
                    lineHeight: `${DIGIT_H}px`,
                    textAlign: "center",
                    ...H,
                    fontSize: 300,
                    backgroundImage: GRADIENTS.silver,
                    WebkitBackgroundClip: "text",
                    color: "transparent",
                    fontVariantNumeric: "tabular-nums",
                  }}
                >
                  {k % 10}
                </div>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
};

export const K5Proof: React.FC<{ len: number; start: number }> = ({
  len,
  start,
}) => {
  const f = useCurrentFrame();
  const out = exitP(f, len, 6);
  const swap = b(20) - start;
  const legitAt = b(22) - start;
  const first = f < swap;
  const label = first ? "obserwujących" : "polubień";
  const labelP = first ? prog(f, 6, 18) : prog(f, swap, swap + 10);
  const legit = sp(f, legitAt, { damping: 12, stiffness: 180 });

  return (
    <AbsoluteFill style={{ background: "#030305" }}>
      <AmbientVideo name="lv" trimBefore={s(2)} opacity={0.4} />
      <AbsoluteFill
        style={{
          opacity: 1 - out,
          scale: String(1 + out * 0.3),
          filter: `blur(${out * 30}px)`,
        }}
      >
        <div style={{ position: "absolute", top: 330, left: 0, right: 0 }}>
          <MaskLine at={0} dur={12}>
            <div style={{ ...H, fontSize: 120, textAlign: "center" }}>
              Liczby nie kłamią.
            </div>
          </MaskLine>
        </div>

        <div style={{ position: "absolute", top: 640, left: 0, right: 0 }}>
          {first ? (
            <Roller from="0000" to="1976" at={2} f={f} />
          ) : (
            <Roller from="1976" to="5583" at={swap} f={f} />
          )}
          <div
            style={{
              marginTop: 10,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: 16,
              fontFamily: FONT_DISPLAY,
              fontWeight: 600,
              fontSize: 64,
              color: C.grey,
              letterSpacing: "-0.02em",
              opacity: labelP,
              translate: `0px ${(1 - labelP) * 30}px`,
            }}
          >
            {first ? (
              <svg width="58" height="58" viewBox="0 0 24 24">
                <circle cx="12" cy="8" r="4" fill={C.teal} />
                <path d="M4 20c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5" fill={C.teal} />
              </svg>
            ) : (
              <svg width="58" height="58" viewBox="0 0 24 24">
                <path
                  d="M12 21s-7.5-4.6-9.5-9.2C1.2 8.6 3.3 5 6.8 5c2 0 3.4 1.1 5.2 3 1.8-1.9 3.2-3 5.2-3 3.5 0 5.6 3.6 4.3 6.8C19.5 16.4 12 21 12 21z"
                  fill="#ff4d6d"
                />
              </svg>
            )}
            {label}
          </div>
          <div
            style={{
              textAlign: "center",
              marginTop: 8,
              fontFamily: FONT_TEXT,
              fontSize: 34,
              fontWeight: 500,
              color: C.dim,
              opacity: labelP,
            }}
          >
            na @flippingnow999
          </div>
        </div>

        {/* legit check pill */}
        <div
          style={{
            position: "absolute",
            left: 540 - 300,
            top: 1240,
            width: 600,
            height: 130,
            opacity: interpolate(f, [legitAt, legitAt + 3], [0, 1], clamp),
            scale: String(interpolate(legit, [0, 1], [0.5, 1])),
          }}
        >
          <Glass radius={65} style={{ inset: 0 }} tint="rgba(48,209,88,0.16)" />
          <div
            style={{
              position: "absolute",
              inset: 0,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: 20,
              fontFamily: FONT_DISPLAY,
              fontWeight: 700,
              fontSize: 52,
              color: C.white,
              letterSpacing: "-0.02em",
            }}
          >
            <svg width="60" height="60" viewBox="0 0 78 78">
              <circle cx="39" cy="39" r="37" fill="#30d158" />
              <path
                d="M22 40 L34 52 L57 28"
                fill="none"
                stroke="#fff"
                strokeWidth="8"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeDasharray="60"
                strokeDashoffset={60 * (1 - prog(f, legitAt + 3, legitAt + 14))}
              />
            </svg>
            Legit check na profilu
          </div>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

/* ------------------------------------------------------------------ */
/* 6. OUTRO – logo born from a light streak, CTA                       */
/* ------------------------------------------------------------------ */
export const K6Outro: React.FC<{ start: number }> = ({ start }) => {
  const f = useCurrentFrame();
  const streak = interpolate(f, [0, 5, 18], [0, 1, 0], clamp);
  const streakW = interpolate(f, [0, 8], [0.1, 1.4], { ...clamp, easing: EASE_OUT });
  const reveal = prog(f, 2, 22);
  const rot = interpolate(sp(f, 2, { damping: 16, stiffness: 90 }), [0, 1], [35, 0]);
  const sheenP = interpolate(f, [10, 36], [-20, 120], { ...clamp, easing: EASE_IN_OUT });
  const ctaAt = b(26) - start;
  const tapAt = b(28) - start;
  const cta = sp(f, ctaAt, { damping: 13, stiffness: 170 });
  const pulse = interpolate(f, [tapAt, tapAt + 4, tapAt + 14], [0, 1, 0], clamp);
  const arrowBounce = Math.max(0, Math.sin((f - tapAt) / 3.2)) * 18 * prog(f, tapAt, tapAt + 4);
  const floatY = Math.sin(f / 18) * 8;

  return (
    <AbsoluteFill
      style={{
        background: "#020203",
        translate: `${shake(f, 0, 16).x}px ${shake(f, 0, 16).y}px`,
      }}
    >
      <AbsoluteFill style={{ opacity: reveal * 0.55 }}>
        <Background />
      </AbsoluteFill>
      <AbsoluteFill
        style={{
          background:
            "radial-gradient(55% 35% at 50% 34%, rgba(44,123,255,0.28), rgba(0,0,0,0) 70%)",
          opacity: reveal,
        }}
      />
      {/* light streak */}
      <div
        style={{
          position: "absolute",
          left: 540 - 700 * streakW,
          width: 1400 * streakW,
          top: 640 - 3,
          height: 6,
          borderRadius: 3,
          background:
            "linear-gradient(90deg, rgba(255,255,255,0), #fff 45%, #fff 55%, rgba(255,255,255,0))",
          boxShadow: "0 0 40px 12px rgba(120,180,255,0.8)",
          opacity: streak,
        }}
      />
      {/* logo */}
      <div
        style={{
          position: "absolute",
          left: 540 - 200,
          top: 450 + floatY,
          width: 400,
          height: 390,
          perspective: 1200,
        }}
      >
        <div
          style={{
            position: "absolute",
            inset: 0,
            rotate: `y ${rot}deg`,
            WebkitMaskImage: `radial-gradient(circle at 50% 50%, #000 ${reveal * 70}%, transparent ${reveal * 70 + 18}%)`,
            maskImage: `radial-gradient(circle at 50% 50%, #000 ${reveal * 70}%, transparent ${reveal * 70 + 18}%)`,
          }}
        >
          <Img
            src={staticFile("img/logo-silver.png")}
            style={{
              width: 400,
              filter: "drop-shadow(0 20px 40px rgba(0,0,0,0.7)) drop-shadow(0 0 30px rgba(120,170,255,0.25))",
            }}
          />
          <Img
            src={staticFile("img/logo-white.png")}
            style={{
              position: "absolute",
              left: 0,
              top: 0,
              width: 400,
              WebkitMaskImage: `linear-gradient(105deg, transparent ${sheenP - 12}%, #000 ${sheenP}%, transparent ${sheenP + 12}%)`,
              maskImage: `linear-gradient(105deg, transparent ${sheenP - 12}%, #000 ${sheenP}%, transparent ${sheenP + 12}%)`,
            }}
          />
        </div>
      </div>

      <div style={{ position: "absolute", top: 900, left: 0, right: 0 }}>
        <MaskLine at={10} dur={14}>
          <div
            style={{
              ...H,
              fontSize: 120,
              textAlign: "center",
              letterSpacing: "-0.04em",
              backgroundImage: GRADIENTS.silver,
              WebkitBackgroundClip: "text",
              color: "transparent",
            }}
          >
            Flipping_now
          </div>
        </MaskLine>
      </div>

      <div
        style={{
          position: "absolute",
          top: 1080,
          left: 0,
          right: 0,
          opacity: interpolate(f, [ctaAt, ctaAt + 3], [0, 1], clamp),
          scale: String(interpolate(cta, [0, 1], [0.7, 1]) * (1 + pulse * 0.05)),
        }}
      >
        <div
          style={{
            ...H,
            fontSize: 150,
            textAlign: "center",
            backgroundImage: GRADIENTS.aqua,
            WebkitBackgroundClip: "text",
            color: "transparent",
            filter: `drop-shadow(0 0 ${20 + pulse * 30}px rgba(47,214,200,${0.25 + pulse * 0.3}))`,
          }}
        >
          Link w bio.
        </div>
      </div>

      <div
        style={{
          position: "absolute",
          left: 540 - 260,
          top: 1290,
          width: 520,
          height: 104,
          opacity: prog(f, ctaAt + 6, ctaAt + 14),
          translate: `0px ${(1 - prog(f, ctaAt + 6, ctaAt + 18)) * 40}px`,
        }}
      >
        <Glass radius={52} style={{ inset: 0 }} sheen={prog(f, tapAt, tapAt + 24)} />
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 14,
            fontFamily: FONT_TEXT,
            fontWeight: 600,
            fontSize: 38,
            color: C.white,
          }}
        >
          @flippingnow999
          <svg
            width="36"
            height="36"
            viewBox="0 0 24 24"
            style={{ translate: `0px ${arrowBounce}px` }}
          >
            <path
              d="M12 4v14M5.5 11.5 12 18l6.5-6.5"
              fill="none"
              stroke="#fff"
              strokeWidth="2.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </div>
      </div>
    </AbsoluteFill>
  );
};
