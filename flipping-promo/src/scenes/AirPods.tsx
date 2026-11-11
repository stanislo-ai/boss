import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame } from "remotion";
import { AmbientVideo } from "../components/AmbientVideo";
import { GRADIENTS, Reveal } from "../components/Text";
import { VideoCard } from "../components/VideoCard";
import { exitP, prog, sp } from "../anim";
import { C, FONT_DISPLAY, clamp } from "../theme";
import { s } from "../timing";

export const AirPods: React.FC<{ len: number }> = ({ len }) => {
  const f = useCurrentFrame();
  const out = exitP(f, len);
  const card = sp(f, 0, { damping: 17, stiffness: 110 });
  const tilt = Math.sin(f / 14) * 3;

  return (
    <AbsoluteFill>
      <AmbientVideo name="airpods" trimBefore={s(2.4)} opacity={0.5} />
      <AbsoluteFill
        style={{
          opacity: 1 - out,
          filter: `blur(${out * 24}px)`,
          scale: String(1 - out * 0.1),
        }}
      >
        {/* soft white halo behind the card */}
        <div
          style={{
            position: "absolute",
            left: 140,
            right: 140,
            top: 700,
            height: 800,
            borderRadius: "50%",
            background:
              "radial-gradient(closest-side, rgba(255,255,255,0.22), rgba(255,255,255,0))",
            filter: "blur(40px)",
            opacity: card,
          }}
        />
        <div style={{ position: "absolute", top: 250, left: 0, right: 0 }}>
          <Reveal
            text="AirPods."
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
            i inna elektronika.
          </div>
        </div>

        <div style={{ position: "absolute", inset: 0, perspective: 1600 }}>
          <div
            style={{
              position: "absolute",
              left: 200,
              top: 590,
              width: 680,
              height: 920,
              opacity: interpolate(f, [0, 4], [0, 1], clamp),
              scale: String(interpolate(card, [0, 1], [1.25, 1])),
              filter: `blur(${(1 - card) * 20}px)`,
              rotate: `y ${tilt}deg`,
            }}
          >
            <VideoCard
              clip="airpods"
              start={2.4}
              width={680}
              height={920}
              radius={64}
              sheen={prog(f, 10, 36)}
            />
          </div>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
