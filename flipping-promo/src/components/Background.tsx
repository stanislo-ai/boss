import React from "react";
import { AbsoluteFill, random, useCurrentFrame } from "remotion";
import { C } from "../theme";

/** Slowly drifting colour fields, like Apple keynote backdrops. */
export const Background: React.FC = () => {
  const f = useCurrentFrame();
  const blob = (
    color: string,
    size: number,
    x: number,
    y: number,
    ax: number,
    ay: number,
    speed: number,
    phase: number,
    opacity: number,
  ) => (
    <div
      style={{
        position: "absolute",
        width: size,
        height: size,
        left: x - size / 2 + Math.sin(f * speed + phase) * ax,
        top: y - size / 2 + Math.cos(f * speed * 0.8 + phase) * ay,
        borderRadius: "50%",
        background: `radial-gradient(circle, ${color} 0%, rgba(0,0,0,0) 68%)`,
        opacity,
      }}
    />
  );

  return (
    <AbsoluteFill style={{ backgroundColor: C.bg, overflow: "hidden" }}>
      {blob("rgba(44,123,255,0.55)", 1300, 200, 400, 120, 90, 0.018, 0, 0.55)}
      {blob("rgba(47,214,200,0.45)", 1200, 950, 1500, 140, 120, 0.015, 2, 0.5)}
      {blob("rgba(138,92,255,0.45)", 1000, 900, 300, 100, 140, 0.021, 4, 0.35)}
      {blob("rgba(255,255,255,0.12)", 900, 400, 1700, 80, 60, 0.012, 1, 0.5)}
    </AbsoluteFill>
  );
};

/** Ambient, heavily blurred version of a clip filling the frame. */
export { AmbientVideo } from "./AmbientVideo";

/** Film grain + vignette to glue everything together. */
export const Finish: React.FC = () => {
  const f = useCurrentFrame();
  const ox = Math.floor(random(`gx${f}`) * 200);
  const oy = Math.floor(random(`gy${f}`) * 200);
  return (
    <AbsoluteFill style={{ pointerEvents: "none" }}>
      <AbsoluteFill
        style={{
          background:
            "radial-gradient(120% 85% at 50% 45%, rgba(0,0,0,0) 55%, rgba(0,0,0,0.55) 100%)",
        }}
      />
      <svg
        width="1280"
        height="2120"
        style={{
          position: "absolute",
          left: -ox,
          top: -oy,
          opacity: 0.07,
          mixBlendMode: "overlay",
        }}
      >
        <filter id="grain">
          <feTurbulence
            type="fractalNoise"
            baseFrequency="0.9"
            numOctaves="2"
            stitchTiles="stitch"
          />
          <feColorMatrix type="saturate" values="0" />
        </filter>
        <rect width="100%" height="100%" filter="url(#grain)" />
      </svg>
    </AbsoluteFill>
  );
};
