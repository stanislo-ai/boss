import React from "react";
import {
  AbsoluteFill,
  Img,
  Sequence,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";
import { Audio } from "@remotion/media";
import { Glass } from "../components/Glass";
import { GRADIENTS } from "../components/Text";
import { H, MaskLine } from "../v1/KeynoteScenes";
import { prog, shake, sp } from "../anim";
import { C, EASE_IN_OUT, EASE_OUT, FONT_TEXT, clamp } from "../theme";
import "../fonts";

export const BIO_OUTRO_FRAMES = 60; // 2 s @ 30 fps

const SFX = [
  { at: 0, sfx: "impact", vol: 0.55 },
  { at: 4, sfx: "shimmer", vol: 0.4 },
  { at: 22, sfx: "pop", vol: 0.35 },
  { at: 36, sfx: "tick", vol: 0.3 },
];

/** Topographic contour lines: revealed from the light streak, slowly drifting,
 *  with an accent-coloured light passing across them. Stays mostly black. */
const Topo: React.FC<{ f: number }> = ({ f }) => {
  const r = interpolate(f, [0, 20], [0, 95], { ...clamp, easing: EASE_OUT });
  const reveal = `radial-gradient(ellipse 80% 60% at 50% 36%, #000 ${r}%, transparent ${r + 25}%)`;
  const drift = {
    position: "absolute" as const,
    left: -160,
    top: -190,
    width: 1400,
    height: 2300,
    translate: `${f * 0.5}px ${-f * 0.7}px`,
    scale: String(1 + f * 0.0012),
    rotate: `${f * 0.02}deg`,
  };
  const lx = interpolate(f, [0, 60], [-10, 110]);
  return (
    <AbsoluteFill
      style={{
        WebkitMaskImage: reveal,
        maskImage: reveal,
      }}
    >
      <div style={{ ...drift, opacity: 0.17 }}>
        <Img src={staticFile("img/topo-white.svg")} style={{ width: 1400, height: 2300 }} />
      </div>
      <div
        style={{
          ...drift,
          opacity: 0.55,
          WebkitMaskImage: `radial-gradient(circle 420px at ${lx}% 45%, #000 0%, transparent 100%)`,
          maskImage: `radial-gradient(circle 420px at ${lx}% 45%, #000 0%, transparent 100%)`,
        }}
      >
        <Img src={staticFile("img/topo-accent.svg")} style={{ width: 1400, height: 2300 }} />
      </div>
      {/* keep the type area clean and the edges dark */}
      <AbsoluteFill
        style={{
          background:
            "radial-gradient(ellipse 55% 30% at 50% 55%, rgba(0,0,0,0.75) 0%, rgba(0,0,0,0) 100%), radial-gradient(130% 90% at 50% 45%, rgba(0,0,0,0) 55%, rgba(0,0,0,0.85) 100%)",
        }}
      />
    </AbsoluteFill>
  );
};

/** 2-second end card: black, logo from a light streak, "Dostawca w bio." */
export const BioOutro: React.FC<{ sfx: boolean }> = ({ sfx }) => {
  const f = useCurrentFrame();
  const streak = interpolate(f, [0, 3, 14], [0, 1, 0], clamp);
  const streakW = interpolate(f, [0, 7], [0.1, 1.5], { ...clamp, easing: EASE_OUT });
  const reveal = prog(f, 1, 16);
  const rot = interpolate(sp(f, 1, { damping: 16, stiffness: 110 }), [0, 1], [35, 0]);
  const sheenP = interpolate(f, [6, 28], [-20, 120], { ...clamp, easing: EASE_IN_OUT });
  const pill = sp(f, 22, { damping: 13, stiffness: 180 });
  const bounce = Math.max(0, Math.sin((f - 30) / 3)) * 16 * prog(f, 30, 34);
  const pillSheen = prog(f, 32, 54);
  const sh = shake(f, 0, 12);
  const glow = 0.25 + 0.15 * Math.sin(f / 6);

  return (
    <AbsoluteFill style={{ background: "#000" }}>
      <Topo f={f} />
      <AbsoluteFill style={{ translate: `${sh.x}px ${sh.y}px` }}>
        {/* light streak */}
        <div
          style={{
            position: "absolute",
            left: 540 - 700 * streakW,
            width: 1400 * streakW,
            top: 690 - 3,
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
            left: 540 - 150,
            top: 545,
            width: 300,
            height: 292,
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
                width: 300,
                filter: "drop-shadow(0 0 28px rgba(120,170,255,0.3))",
              }}
            />
            <Img
              src={staticFile("img/logo-white.png")}
              style={{
                position: "absolute",
                left: 0,
                top: 0,
                width: 300,
                WebkitMaskImage: `linear-gradient(105deg, transparent ${sheenP - 12}%, #000 ${sheenP}%, transparent ${sheenP + 12}%)`,
                maskImage: `linear-gradient(105deg, transparent ${sheenP - 12}%, #000 ${sheenP}%, transparent ${sheenP + 12}%)`,
              }}
            />
          </div>
        </div>

        {/* headline */}
        <div style={{ position: "absolute", top: 900, left: 0, right: 0 }}>
          <MaskLine at={6} dur={12}>
            <div
              style={{
                ...H,
                fontSize: 176,
                textAlign: "center",
                backgroundImage: GRADIENTS.silver,
                WebkitBackgroundClip: "text",
                color: "transparent",
              }}
            >
              Dostawca
            </div>
          </MaskLine>
          <MaskLine at={11} dur={12}>
            <div
              style={{
                ...H,
                fontSize: 176,
                textAlign: "center",
                backgroundImage: GRADIENTS.aqua,
                WebkitBackgroundClip: "text",
                color: "transparent",
                filter: `drop-shadow(0 0 30px rgba(47,214,200,${glow}))`,
              }}
            >
              w bio.
            </div>
          </MaskLine>
        </div>

        {/* handle pill */}
        <div
          style={{
            position: "absolute",
            left: 540 - 270,
            top: 1290,
            width: 540,
            height: 110,
            opacity: interpolate(f, [22, 25], [0, 1], clamp),
            scale: String(interpolate(pill, [0, 1], [0.6, 1])),
            translate: `0px ${(1 - pill) * 50}px`,
          }}
        >
          <div
            style={{
              position: "absolute",
              inset: -24,
              borderRadius: 80,
              background: "linear-gradient(95deg, #2fd6c8, #2c7bff, #8a5cff)",
              filter: "blur(36px)",
              opacity: 0.35,
            }}
          />
          <div
            style={{
              position: "absolute",
              inset: 0,
              borderRadius: 55,
              background: "#0b0c10",
            }}
          />
          <Glass radius={55} style={{ inset: 0 }} sheen={pillSheen} />
          <div
            style={{
              position: "absolute",
              inset: 0,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: 16,
              fontFamily: FONT_TEXT,
              fontWeight: 600,
              fontSize: 42,
              color: C.white,
            }}
          >
            @flippingnow999
            <svg
              width="40"
              height="40"
              viewBox="0 0 24 24"
              style={{ translate: `0px ${bounce}px` }}
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

      {sfx
        ? SFX.map((h, i) => (
            <Sequence key={i} from={h.at} layout="none">
              <Audio src={staticFile(`sfx/${h.sfx}.wav`)} volume={h.vol} />
            </Sequence>
          ))
        : null}
    </AbsoluteFill>
  );
};
