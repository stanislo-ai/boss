import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame } from "remotion";
import { AmbientVideo } from "../components/AmbientVideo";
import { Glass } from "../components/Glass";
import { GRADIENTS, Reveal } from "../components/Text";
import { VideoCard } from "../components/VideoCard";
import { exitP, prog, sp } from "../anim";
import { C, EASE_OUT, FONT_DISPLAY, FONT_TEXT, clamp } from "../theme";

// Real numbers from the @flippingnow999 profile.
const FOLLOWERS = 1976;
const LIKES = 5583;

const GX = 70;
const GY = 470;
const COL = 458;
const GAP = 24;

type TileDef = {
  x: number;
  y: number;
  w: number;
  h: number;
  at: number;
};

export const TILES = {
  lebeau: { x: 0, y: 0, w: COL, h: 470, at: 2 },
  followers: { x: COL + GAP, y: 0, w: COL, h: 223, at: 7 },
  likes: { x: COL + GAP, y: 247, w: COL, h: 223, at: 12 },
  airpods: { x: COL + GAP, y: 494, w: COL, h: 506, at: 17 },
  legit: { x: 0, y: 494, w: COL, h: 240, at: 22 },
  lv: { x: 0, y: 758, w: COL, h: 242, at: 27 },
} satisfies Record<string, TileDef>;

const tileStyle = (t: TileDef, f: number) => {
  const p = sp(f, t.at, { damping: 15, stiffness: 170 });
  return {
    position: "absolute" as const,
    left: GX + t.x,
    top: GY + t.y,
    width: t.w,
    height: t.h,
    opacity: interpolate(f, [t.at, t.at + 4], [0, 1], clamp),
    scale: String(interpolate(p, [0, 1], [0.72, 1])),
    filter: `blur(${(1 - Math.min(p, 1)) * 16}px)`,
    translate: `0px ${(1 - p) * 60}px`,
  };
};

const Counter: React.FC<{ value: number; at: number; f: number }> = ({
  value,
  at,
  f,
}) => {
  const p = interpolate(f, [at, at + 34], [0, 1], {
    ...clamp,
    easing: EASE_OUT,
  });
  return <>{Math.round(value * p)}</>;
};

const StatTile: React.FC<{
  t: TileDef;
  f: number;
  value: number;
  label: string;
  icon: React.ReactNode;
}> = ({ t, f, value, label, icon }) => (
  <div style={tileStyle(t, f)}>
    <Glass radius={44} style={{ inset: 0 }} />
    <div
      style={{
        position: "absolute",
        inset: 0,
        padding: "30px 34px",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        {icon}
        <span
          style={{
            fontFamily: FONT_TEXT,
            fontWeight: 600,
            fontSize: 33,
            color: C.grey,
          }}
        >
          {label}
        </span>
      </div>
      <div
        style={{
          fontFamily: FONT_DISPLAY,
          fontWeight: 700,
          fontSize: 112,
          lineHeight: 1,
          letterSpacing: "-0.04em",
          backgroundImage: GRADIENTS.silver,
          WebkitBackgroundClip: "text",
          backgroundClip: "text",
          color: "transparent",
          fontVariantNumeric: "tabular-nums",
        }}
      >
        <Counter value={value} at={t.at + 3} f={f} />
      </div>
    </div>
  </div>
);

const PersonIcon = () => (
  <svg width="34" height="34" viewBox="0 0 24 24" fill="none">
    <circle cx="12" cy="8" r="4" fill={C.teal} />
    <path d="M4 20c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5" fill={C.teal} />
  </svg>
);

const HeartIcon = () => (
  <svg width="34" height="34" viewBox="0 0 24 24">
    <path
      d="M12 21s-7.5-4.6-9.5-9.2C1.2 8.6 3.3 5 6.8 5c2 0 3.4 1.1 5.2 3 1.8-1.9 3.2-3 5.2-3 3.5 0 5.6 3.6 4.3 6.8C19.5 16.4 12 21 12 21z"
      fill="#ff4d6d"
    />
  </svg>
);

export const Bento: React.FC<{ len: number }> = ({ len }) => {
  const f = useCurrentFrame();
  const out = exitP(f, len, 6);
  const drift = 1 + f * 0.0003;
  const checkP = prog(f, TILES.legit.at + 4, TILES.legit.at + 20);

  return (
    <AbsoluteFill>
      <AmbientVideo name="shelf" trimBefore={60} opacity={0.4} />
      <AbsoluteFill
        style={{
          opacity: 1 - out,
          scale: String(drift * (1 - out * 0.35)),
          filter: `blur(${out * 30}px)`,
        }}
      >
        <div style={{ position: "absolute", top: 260, left: 0, right: 0 }}>
          <Reveal
            text="Sprawdź sam."
            at={0}
            duration={14}
            size={150}
            gradient={GRADIENTS.silver}
          />
        </div>

        <div style={tileStyle(TILES.lebeau, f)}>
          <VideoCard
            clip="lebeau"
            start={2.0}
            width={TILES.lebeau.w}
            height={TILES.lebeau.h}
            radius={44}
            bezel={10}
          />
        </div>

        <StatTile
          t={TILES.followers}
          f={f}
          value={FOLLOWERS}
          label="obserwujących"
          icon={<PersonIcon />}
        />
        <StatTile
          t={TILES.likes}
          f={f}
          value={LIKES}
          label="polubień"
          icon={<HeartIcon />}
        />

        <div style={tileStyle(TILES.airpods, f)}>
          <VideoCard
            clip="airpods"
            start={5.0}
            width={TILES.airpods.w}
            height={TILES.airpods.h}
            radius={44}
            bezel={10}
          />
        </div>

        <div style={tileStyle(TILES.legit, f)}>
          <Glass
            radius={44}
            style={{ inset: 0 }}
            tint="rgba(48,209,88,0.10)"
          />
          <div
            style={{
              position: "absolute",
              inset: 0,
              padding: "30px 34px",
              display: "flex",
              flexDirection: "column",
              justifyContent: "space-between",
            }}
          >
            <svg width="78" height="78" viewBox="0 0 78 78">
              <circle cx="39" cy="39" r="37" fill="#30d158" opacity={0.95} />
              <path
                d="M22 40 L34 52 L57 28"
                fill="none"
                stroke="#fff"
                strokeWidth="7"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeDasharray="60"
                strokeDashoffset={60 * (1 - checkP)}
              />
            </svg>
            <div>
              <div
                style={{
                  fontFamily: FONT_DISPLAY,
                  fontWeight: 700,
                  fontSize: 54,
                  letterSpacing: "-0.03em",
                  color: C.white,
                  lineHeight: 1.05,
                }}
              >
                Legit check
              </div>
              <div
                style={{
                  fontFamily: FONT_TEXT,
                  fontWeight: 500,
                  fontSize: 31,
                  color: C.grey,
                  marginTop: 4,
                }}
              >
                relacja na profilu
              </div>
            </div>
          </div>
        </div>

        <div style={tileStyle(TILES.lv, f)}>
          <VideoCard
            clip="lv"
            start={3.0}
            width={TILES.lv.w}
            height={TILES.lv.h}
            radius={44}
            bezel={10}
          />
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
