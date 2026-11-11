import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame } from "remotion";
import { AmbientVideo } from "../components/AmbientVideo";
import { GRADIENTS, Reveal } from "../components/Text";
import { VideoCard } from "../components/VideoCard";
import { exitP, prog, sp } from "../anim";
import { C, FONT_DISPLAY } from "../theme";
import { BEATS, s } from "../timing";

export const Perfumes: React.FC<{ len: number; start: number }> = ({
  len,
  start,
}) => {
  const f = useCurrentFrame();
  const out = exitP(f, len);
  const main = sp(f, 0, { damping: 16, stiffness: 120 });
  const secondAt = s(BEATS[6]) - start; // 3.20s
  const second = sp(f, secondAt, { damping: 14, stiffness: 160 });

  return (
    <AbsoluteFill>
      <AmbientVideo name="lebeau" trimBefore={s(0.3)} opacity={0.55} />
      <AbsoluteFill
        style={{
          opacity: 1 - out,
          filter: `blur(${out * 26}px)`,
          translate: `0px ${out * -220}px`,
        }}
      >
        <div style={{ position: "absolute", top: 250, left: 0, right: 0 }}>
          <Reveal
            text="Perfumy."
            at={0}
            duration={14}
            size={168}
            gradient={GRADIENTS.silver}
          />
          <div
            style={{
              marginTop: 6,
              textAlign: "center",
              fontFamily: FONT_DISPLAY,
              fontWeight: 500,
              fontSize: 50,
              letterSpacing: "-0.015em",
              color: C.grey,
              opacity: prog(f, 6, 18),
              translate: `0px ${(1 - prog(f, 6, 22)) * 24}px`,
              filter: `blur(${(1 - prog(f, 6, 20)) * 10}px)`,
            }}
          >
            Topowe marki. Jedno źródło.
          </div>
        </div>

        <div
          style={{
            position: "absolute",
            inset: 0,
            perspective: 1800,
          }}
        >
          {/* main card */}
          <div
            style={{
              position: "absolute",
              left: 230,
              top: 590,
              width: 620,
              height: 900,
              translate: `0px ${(1 - main) * 700}px`,
              rotate: `x ${(1 - main) * 40}deg`,
              scale: String(interpolate(main, [0, 1], [0.85, 1]) * (1 + f * 0.0012)),
              transformStyle: "preserve-3d",
            }}
          >
            <VideoCard
              clip="lebeau"
              start={0.3}
              width={620}
              height={900}
              radius={64}
              sheen={prog(f, 8, 34)}
              videoScale={1.22}
              videoStyle={{ transformOrigin: "30% 62%" }}
            />
          </div>

          {/* second card */}
          <div
            style={{
              position: "absolute",
              left: 60,
              top: 1060,
              width: 320,
              height: 440,
              opacity: interpolate(f, [secondAt, secondAt + 3], [0, 1], {
                extrapolateLeft: "clamp",
                extrapolateRight: "clamp",
              }),
              translate: `${(1 - second) * -300}px ${(1 - second) * 120}px`,
              rotate: `${interpolate(second, [0, 1], [-22, -7])}deg`,
              scale: String(interpolate(second, [0, 1], [0.7, 1])),
            }}
          >
            <VideoCard
              clip="lv"
              start={0.9}
              width={320}
              height={440}
              radius={44}
              bezel={10}
            />
          </div>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
