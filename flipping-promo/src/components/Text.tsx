import React from "react";
import { interpolate, useCurrentFrame } from "remotion";
import { C, EASE_OUT, FONT_DISPLAY, clamp } from "../theme";

export const GRADIENTS = {
  silver: "linear-gradient(180deg, #ffffff 0%, #e8e8ed 55%, #a9abb3 100%)",
  aqua: "linear-gradient(95deg, #5ff3e4 0%, #2fd6c8 25%, #2c9bff 60%, #8a5cff 100%)",
};

type RevealProps = {
  text: string;
  /** local frame where the first word starts */
  at?: number;
  stagger?: number;
  duration?: number;
  size?: number;
  weight?: number;
  gradient?: string;
  color?: string;
  tracking?: number;
  style?: React.CSSProperties;
  align?: "left" | "center";
  /** from y offset in px */
  rise?: number;
  blur?: number;
  lineHeight?: number;
};

/** Apple-style word-by-word reveal: blur + rise + fade. */
export const Reveal: React.FC<RevealProps> = ({
  text,
  at = 0,
  stagger = 4,
  duration = 16,
  size = 140,
  weight = 700,
  gradient,
  color = C.white,
  tracking = -0.035,
  style,
  align = "center",
  rise = 50,
  blur = 24,
  lineHeight = 1.02,
}) => {
  const f = useCurrentFrame();
  const words = text.split(" ");
  return (
    <div
      style={{
        fontFamily: FONT_DISPLAY,
        fontWeight: weight,
        fontSize: size,
        letterSpacing: `${tracking}em`,
        lineHeight,
        textAlign: align,
        color,
        whiteSpace: "pre-wrap",
        ...style,
      }}
    >
      {words.map((w, i) => {
        const start = at + i * stagger;
        const p = interpolate(f, [start, start + duration], [0, 1], {
          ...clamp,
          easing: EASE_OUT,
        });
        return (
          <span
            key={i}
            style={{
              display: "inline-block",
              opacity: interpolate(p, [0, 0.6], [0, 1], clamp),
              translate: `0px ${(1 - p) * rise}px`,
              filter: `blur(${(1 - p) * blur}px)`,
              marginRight: i < words.length - 1 ? "0.24em" : 0,
              backgroundImage: gradient,
              WebkitBackgroundClip: gradient ? "text" : undefined,
              backgroundClip: gradient ? "text" : undefined,
              color: gradient ? "transparent" : undefined,
              paddingBottom: "0.08em",
            }}
          >
            {w}
          </span>
        );
      })}
    </div>
  );
};
