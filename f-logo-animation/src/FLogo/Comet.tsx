import React from "react";
import { getPointAtLength } from "@remotion/paths";
import { CREAM, RED } from "./geometry";

type Props = {
  d: string;
  length: number;
  // Position of the comet head along the path (in path units).
  head: number;
  // Length of the trail behind the head.
  tail: number;
  opacity: number;
  showHead?: boolean;
  width?: number;
};

// A light streak travelling along a path: hot white core, red smeared trail.
export const Comet: React.FC<Props> = ({
  d,
  length,
  head,
  tail,
  opacity,
  showHead = true,
  width = 1,
}) => {
  if (opacity <= 0.001 || tail <= 0.5) {
    return null;
  }
  const gap = length * 3 + tail;
  const window = (t: number) => ({
    strokeDasharray: `${t} ${gap}`,
    strokeDashoffset: t - head,
  });
  const point = getPointAtLength(d, Math.max(0, Math.min(length, head)));
  const headVisible =
    point !== null && showHead && head > 0 && head < length + 4;

  return (
    <g opacity={opacity} style={{ mixBlendMode: "screen" }}>
      <path
        d={d}
        fill="none"
        stroke={RED}
        strokeWidth={46 * width}
        strokeLinecap="round"
        filter="url(#blurL)"
        opacity={0.75}
        {...window(tail)}
      />
      <path
        d={d}
        fill="none"
        stroke={RED}
        strokeWidth={18 * width}
        strokeLinecap="round"
        filter="url(#blurS)"
        {...window(tail * 0.85)}
      />
      <path
        d={d}
        fill="none"
        stroke={CREAM}
        strokeWidth={14 * width}
        strokeLinecap="round"
        filter="url(#blurXS)"
        {...window(tail * 0.4)}
      />
      {headVisible && point ? (
        <>
          <circle
            cx={point.x}
            cy={point.y}
            r={46 * width}
            fill={RED}
            opacity={0.55}
            filter="url(#blurL)"
          />
          <circle
            cx={point.x}
            cy={point.y}
            r={20 * width}
            fill="#fff"
            filter="url(#blurS)"
          />
          <circle cx={point.x} cy={point.y} r={9 * width} fill="#fff" />
        </>
      ) : null}
    </g>
  );
};
