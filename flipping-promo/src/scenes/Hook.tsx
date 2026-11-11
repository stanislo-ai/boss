import React from "react";
import {
  AbsoluteFill,
  Img,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";
import { Glass } from "../components/Glass";
import { GRADIENTS, Reveal } from "../components/Text";
import { AmbientVideo } from "../components/AmbientVideo";
import { VideoCard } from "../components/VideoCard";
import { exitP, prog, sp } from "../anim";
import { C, FONT_TEXT, clamp } from "../theme";
import { BEATS, s } from "../timing";

const CHIPS = ["Perfumy", "AirPods", "Elektronika"];

const FAN = [
  { clip: "ysl", start: 1.0, x: 255, y: 1330, rot: -11 },
  { clip: "airpods", start: 3.0, x: 540, y: 1290, rot: 0 },
  { clip: "lv", start: 4.4, x: 825, y: 1330, rot: 11 },
];

export const Hook: React.FC<{ len: number }> = ({ len }) => {
  const f = useCurrentFrame();
  const out = exitP(f, len);
  const hit = s(BEATS[0]); // "dostawcy?" lands on the first beat
  const slam = sp(f, hit, { damping: 12, stiffness: 180 });
  const chipAt = [s(BEATS[2]), s(BEATS[2]) + 6, s(BEATS[3])];

  // whole-scene slow push
  const push = interpolate(f, [0, len], [1, 1.06], clamp);

  return (
    <AbsoluteFill>
      <AmbientVideo name="shelf" trimBefore={s(3)} opacity={0.35} />
      <AbsoluteFill
        style={{
          scale: String(push * (1 + out * 0.12)),
          opacity: 1 - out,
          filter: `blur(${out * 24}px)`,
        }}
      >
        {/* product fan rising from the bottom – products visible from frame 0 */}
        <div style={{ position: "absolute", inset: 0, perspective: 1600 }}>
          {FAN.map((c, i) => {
            const p = sp(f, i * 3 - 6, { damping: 16, stiffness: 120 });
            const float = Math.sin((f + i * 20) / 18) * 10;
            return (
              <div
                key={c.clip}
                style={{
                  position: "absolute",
                  left: c.x - 165,
                  top: c.y + float,
                  width: 330,
                  height: 470,
                  translate: `0px ${(1 - p) * 700}px`,
                  rotate: `${c.rot * interpolate(p, [0, 1], [2, 1])}deg`,
                  zIndex: i === 1 ? 2 : 1,
                }}
              >
                <VideoCard
                  clip={c.clip}
                  start={c.start}
                  width={330}
                  height={470}
                  radius={46}
                  bezel={10}
                />
              </div>
            );
          })}
        </div>

        {/* eyebrow pill */}
        <div
          style={{
            position: "absolute",
            top: 360,
            left: 0,
            right: 0,
            display: "flex",
            justifyContent: "center",
            opacity: prog(f, -4, 8),
            translate: `0px ${(1 - prog(f, 2, 16)) * -30}px`,
          }}
        >
          <div style={{ position: "relative", width: 420, height: 96 }}>
            <Glass radius={48} style={{ inset: 0 }} sheen={prog(f, 6, 40)} />
            <div
              style={{
                position: "absolute",
                inset: 0,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: 18,
                fontFamily: FONT_TEXT,
                fontWeight: 600,
                fontSize: 38,
                color: C.white,
                letterSpacing: "-0.01em",
              }}
            >
              <Img
                src={staticFile("img/logo-silver.png")}
                style={{ height: 50 }}
              />
              Flipping_now
            </div>
          </div>
        </div>

        {/* headline */}
        <div
          style={{
            position: "absolute",
            top: 600,
            left: 0,
            right: 0,
          }}
        >
          <Reveal text="Szukasz" at={-7} duration={14} size={190} rise={70} />
          <div
            style={{
              scale: String(interpolate(slam, [0, 1], [1.45, 1])),
              opacity: interpolate(f, [hit - 1, hit + 2], [0, 1], clamp),
              filter: `blur(${interpolate(slam, [0, 0.7], [30, 0], clamp)}px)`,
              marginTop: 6,
            }}
          >
            <Reveal
              text="dostawcy?"
              at={hit}
              duration={6}
              size={190}
              gradient={GRADIENTS.aqua}
              rise={0}
              blur={0}
            />
          </div>
          {/* glow under the gradient word */}
          <div
            style={{
              position: "absolute",
              left: 140,
              right: 140,
              top: 230,
              height: 160,
              background:
                "radial-gradient(closest-side, rgba(47,214,200,0.35), rgba(44,123,255,0))",
              filter: "blur(30px)",
              opacity: interpolate(slam, [0, 1], [0, 1]),
              zIndex: -1,
            }}
          />
        </div>

        {/* chips */}
        <div
          style={{
            position: "absolute",
            top: 1130,
            left: 0,
            right: 0,
            display: "flex",
            justifyContent: "center",
            gap: 22,
          }}
        >
          {CHIPS.map((label, i) => {
            const p = sp(f, chipAt[i], { damping: 13, stiffness: 200 });
            const w = label.length * 25 + 90;
            return (
              <div
                key={label}
                style={{
                  position: "relative",
                  width: w,
                  height: 100,
                  scale: String(interpolate(p, [0, 1], [0.6, 1])),
                  opacity: interpolate(f, [chipAt[i], chipAt[i] + 4], [0, 1], clamp),
                  translate: `0px ${(1 - p) * 40}px`,
                }}
              >
                <Glass radius={50} style={{ inset: 0 }} />
                <div
                  style={{
                    position: "absolute",
                    inset: 0,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontFamily: FONT_TEXT,
                    fontWeight: 600,
                    fontSize: 40,
                    color: C.white,
                    letterSpacing: "-0.01em",
                  }}
                >
                  {label}
                </div>
              </div>
            );
          })}
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
