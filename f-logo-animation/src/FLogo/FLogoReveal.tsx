import React from "react";
import { AbsoluteFill, Easing, interpolate, useCurrentFrame } from "remotion";
import { evolvePath } from "@remotion/paths";
import { Comet } from "./Comet";
import {
  ARROW_HEAD,
  ARROW_PIVOT,
  ARROW_TIP,
  CENTER,
  CREAM,
  DEEP_RED,
  LEN_A,
  LEN_B,
  LEN_ORBIT,
  ORBIT,
  RED,
  STROKE_A,
  STROKE_B,
  VIEW,
  sparklePath,
} from "./geometry";
import { LOGO_PATH } from "./logoPath";

const clamp = {
  extrapolateLeft: "clamp",
  extrapolateRight: "clamp",
} as const;

// Timeline in frames @ 60fps (2s = 120 frames)
const T = {
  sparkleIn: 0,
  sparkleFill: 7,
  ringIn: 10,
  stretch: 20,
  orbitStart: 22,
  orbitEnd: 50,
  drawStart: 44,
  drawEnd: 84,
  arrowPop: 80,
  silver: 86,
  shine: 94,
};

const inOut = Easing.bezier(0.55, 0, 0.25, 1);
const outExpo = Easing.bezier(0.16, 1, 0.3, 1);

export const FLogoReveal: React.FC = () => {
  const frame = useCurrentFrame();

  // ---------- 1. Sparkle ----------
  const sparkleR = 105;
  const sparkleOutline = evolvePath(
    interpolate(frame, [T.sparkleIn, T.sparkleFill + 2], [0, 1], {
      ...clamp,
      easing: outExpo,
    }),
    sparklePath(sparkleR),
  );
  const sparkleFill = interpolate(
    frame,
    [T.sparkleFill, T.sparkleFill + 6],
    [0, 1],
    clamp,
  );
  const sparkleScale = interpolate(frame, [0, 12], [0.35, 1], {
    ...clamp,
    easing: outExpo,
  });
  const sparkleRotate =
    interpolate(frame, [0, 14], [-50, 0], { ...clamp, easing: outExpo }) +
    interpolate(frame, [T.stretch - 4, T.stretch + 8], [0, 140], {
      ...clamp,
      easing: Easing.in(Easing.cubic),
    });
  const stretchX = interpolate(frame, [T.stretch, T.stretch + 8], [1, 3.4], {
    ...clamp,
    easing: Easing.in(Easing.cubic),
  });
  const stretchY = interpolate(frame, [T.stretch, T.stretch + 8], [1, 0.08], {
    ...clamp,
    easing: Easing.in(Easing.cubic),
  });
  const sparkleOpacity = interpolate(
    frame,
    [T.stretch + 4, T.stretch + 9],
    [1, 0],
    clamp,
  );
  const redOutline = interpolate(
    frame,
    [T.sparkleFill, T.sparkleFill + 5, T.stretch, T.stretch + 4],
    [0, 1, 1, 0],
    clamp,
  );

  // Dotted red ring around the sparkle
  const ringProgress = interpolate(frame, [T.ringIn, T.ringIn + 8], [0, 1], {
    ...clamp,
    easing: outExpo,
  });
  const ringOpacity = interpolate(
    frame,
    [T.ringIn, T.ringIn + 3, T.stretch + 1, T.stretch + 6],
    [0, 1, 1, 0],
    clamp,
  );
  const ringScale = interpolate(
    frame,
    [T.ringIn, T.ringIn + 8, T.stretch + 6],
    [0.7, 1, 1.25],
    clamp,
  );

  // ---------- 2. Orbiting light streak ----------
  const orbitHead = interpolate(
    frame,
    [T.orbitStart, T.orbitEnd],
    [LEN_ORBIT * 0.22, LEN_ORBIT * 0.86],
    { ...clamp, easing: Easing.bezier(0.3, 0.1, 0.6, 1) },
  );
  const orbitTail = interpolate(
    frame,
    [T.orbitStart, T.orbitStart + 12, T.orbitEnd],
    [40, LEN_ORBIT * 0.22, LEN_ORBIT * 0.08],
    clamp,
  );
  const orbitOpacity = interpolate(
    frame,
    [T.orbitStart, T.orbitStart + 3, T.orbitEnd - 6, T.orbitEnd],
    [0, 1, 1, 0],
    clamp,
  );
  const orbitTilt = interpolate(
    frame,
    [T.orbitStart, T.orbitEnd],
    [-24, -6],
    clamp,
  );
  const orbitSquash = interpolate(
    frame,
    [T.orbitStart, T.orbitEnd],
    [1, 0.55],
    clamp,
  );

  // ---------- 3. Tracing the logo ----------
  const total = LEN_A + LEN_B;
  const traced = interpolate(frame, [T.drawStart, T.drawEnd], [0, total], {
    ...clamp,
    easing: inOut,
  });
  const drawnA = Math.min(traced, LEN_A);
  const drawnB = Math.max(0, traced - LEN_A);
  const cometTail = interpolate(
    frame,
    [T.drawStart, T.drawStart + 10, T.drawEnd - 8, T.drawEnd + 4],
    [10, 420, 360, 0],
    clamp,
  );
  const cometOpacity = interpolate(
    frame,
    [T.drawStart, T.drawStart + 2, T.drawEnd + 2, T.drawEnd + 8],
    [0, 1, 1, 0],
    clamp,
  );

  // ---------- 4. Arrow pop + flash ----------
  const arrowScale = interpolate(
    frame,
    [T.arrowPop, T.arrowPop + 6, T.arrowPop + 14],
    [0, 1.18, 1],
    { ...clamp, easing: Easing.out(Easing.cubic) },
  );
  const flash = interpolate(
    frame,
    [T.arrowPop + 2, T.arrowPop + 5, T.arrowPop + 22],
    [0, 1, 0],
    clamp,
  );
  const tipSparkScale = interpolate(
    frame,
    [T.arrowPop + 2, T.arrowPop + 8, T.arrowPop + 26],
    [0, 1, 0],
    { ...clamp, easing: Easing.out(Easing.quad) },
  );
  const tipSparkRotate = interpolate(
    frame,
    [T.arrowPop + 2, T.arrowPop + 26],
    [0, 90],
    clamp,
  );

  // ---------- 5. Settle ----------
  const glowLevel = interpolate(
    frame,
    [T.drawStart, T.drawEnd, T.silver + 4, 120],
    [1, 1, 0.55, 0.28],
    clamp,
  );
  const silver = interpolate(frame, [T.silver, T.silver + 14], [0, 1], {
    ...clamp,
    easing: Easing.inOut(Easing.quad),
  });
  const redEdge = interpolate(
    frame,
    [T.drawStart, T.drawStart + 6, T.silver, T.silver + 16],
    [0, 0.9, 0.9, 0],
    clamp,
  );
  const punch = interpolate(
    frame,
    [T.arrowPop + 2, T.arrowPop + 7, 120],
    [1, 1.045, 1],
    { ...clamp, easing: Easing.out(Easing.cubic) },
  );
  const drift = interpolate(frame, [0, 120], [0.96, 1], clamp);
  const shineX = interpolate(frame, [T.shine, T.shine + 20], [-350, 1250], {
    ...clamp,
    easing: Easing.inOut(Easing.quad),
  });
  const shineOpacity = interpolate(
    frame,
    [T.shine, T.shine + 3, T.shine + 17, T.shine + 20],
    [0, 0.9, 0.9, 0],
    clamp,
  );

  const maskStroke = (d: string, drawn: number, len: number) =>
    drawn > 0.5 ? (
      <path
        d={d}
        fill="none"
        stroke="#fff"
        strokeWidth={104}
        strokeLinecap="butt"
        strokeLinejoin="round"
        strokeDasharray={`${drawn} ${len + 10}`}
      />
    ) : null;

  return (
    <AbsoluteFill>
      <svg
        viewBox={`${VIEW.x} ${VIEW.y} ${VIEW.size} ${VIEW.size}`}
        style={{ width: "100%", height: "100%", overflow: "visible" }}
      >
        <defs>
          <filter id="blurXS" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation={2.5} />
          </filter>
          <filter id="blurS" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation={7} />
          </filter>
          <filter id="blurL" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation={20} />
          </filter>
          <radialGradient id="bgGlow">
            <stop offset="0" stopColor={DEEP_RED} stopOpacity={0.55} />
            <stop offset="0.35" stopColor={DEEP_RED} stopOpacity={0.22} />
            <stop offset="0.7" stopColor={DEEP_RED} stopOpacity={0.05} />
            <stop offset="1" stopColor={DEEP_RED} stopOpacity={0} />
          </radialGradient>
          <filter id="blurXL" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation={42} />
          </filter>

          <linearGradient
            id="silver"
            gradientUnits="userSpaceOnUse"
            x1="150"
            y1="150"
            x2="850"
            y2="850"
          >
            <stop offset="0" stopColor="#d9d9dc" />
            <stop offset="0.35" stopColor="#f4f4f6" />
            <stop offset="0.55" stopColor="#c4c4c8" />
            <stop offset="0.8" stopColor="#e6e6e9" />
            <stop offset="1" stopColor="#a9a9ae" />
          </linearGradient>
          <linearGradient
            id="shine"
            gradientUnits="userSpaceOnUse"
            x1={shineX - 120}
            y1="0"
            x2={shineX + 120}
            y2="0"
            gradientTransform={`rotate(-18 ${CENTER.x} ${CENTER.y})`}
          >
            <stop offset="0" stopColor="#fff" stopOpacity={0} />
            <stop offset="0.5" stopColor="#fff" stopOpacity={1} />
            <stop offset="1" stopColor="#fff" stopOpacity={0} />
          </linearGradient>

          <mask
            id="drawn"
            maskUnits="userSpaceOnUse"
            x={VIEW.x}
            y={VIEW.y}
            width={VIEW.size}
            height={VIEW.size}
          >
            {maskStroke(STROKE_A, drawnA, LEN_A)}
            {maskStroke(STROKE_B, drawnB, LEN_B)}
            {arrowScale > 0.001 ? (
              <path
                d={ARROW_HEAD}
                fill="#fff"
                style={{
                  transformOrigin: `${ARROW_PIVOT.x}px ${ARROW_PIVOT.y}px`,
                  scale: String(arrowScale),
                }}
              />
            ) : null}
          </mask>
          <clipPath id="logoClip">
            <path d={LOGO_PATH} clipRule="evenodd" />
          </clipPath>
        </defs>

        {/* warm glow behind the mark, pulses with the flash */}
        <circle
          cx={CENTER.x}
          cy={CENTER.y}
          r={620}
          fill="url(#bgGlow)"
          opacity={0.22 + flash * 0.5}
        />

        {/* ---------- Sparkle + ring ---------- */}
        <g
          transform={`translate(${CENTER.x} ${CENTER.y})`}
          opacity={sparkleOpacity}
        >
          <g
            transform={`rotate(${sparkleRotate}) scale(${sparkleScale * stretchX} ${sparkleScale * stretchY})`}
          >
            <path
              d={sparklePath(sparkleR * 1.2)}
              fill="none"
              stroke={RED}
              strokeWidth={9}
              opacity={redOutline}
              transform="rotate(8) translate(6 4)"
              filter="url(#blurXS)"
            />
            <path
              d={sparklePath(sparkleR)}
              fill={RED}
              opacity={redOutline * 0.8}
              filter="url(#blurL)"
            />
            <path
              d={sparklePath(sparkleR)}
              fill={CREAM}
              opacity={sparkleFill}
            />
            <path
              d={sparklePath(sparkleR)}
              fill="none"
              stroke={CREAM}
              strokeWidth={6}
              strokeLinejoin="round"
              strokeDasharray={sparkleOutline.strokeDasharray}
              strokeDashoffset={sparkleOutline.strokeDashoffset}
            />
          </g>
          <g
            opacity={ringOpacity}
            transform={`rotate(${frame * 6}) scale(${ringScale})`}
          >
            <circle
              r={200}
              fill="none"
              stroke={RED}
              strokeWidth={5}
              strokeLinecap="round"
              strokeDasharray="1 16"
              pathLength={1257}
              strokeDashoffset={1257 * (1 - ringProgress)}
            />
            <circle
              r={200}
              fill="none"
              stroke={DEEP_RED}
              strokeWidth={2}
              opacity={0.6}
              strokeDasharray={`${1257 * ringProgress} 1257`}
              filter="url(#blurXS)"
            />
          </g>
        </g>

        {/* ---------- Orbiting streak ---------- */}
        <g
          transform={`rotate(${orbitTilt} ${CENTER.x} ${CENTER.y}) translate(0 ${CENTER.y * (1 - orbitSquash)}) scale(1 ${orbitSquash})`}
        >
          <Comet
            d={ORBIT}
            length={LEN_ORBIT}
            head={orbitHead}
            tail={orbitTail}
            opacity={orbitOpacity}
            width={1.1}
          />
        </g>

        {/* ---------- Logo being drawn ---------- */}
        <g
          style={{
            transformOrigin: `${CENTER.x}px ${CENTER.y}px`,
            scale: String(punch * drift),
          }}
        >
          {/* soft bloom */}
          <g mask="url(#drawn)" opacity={glowLevel}>
            <path
              d={LOGO_PATH}
              fillRule="evenodd"
              fill={RED}
              filter="url(#blurXL)"
              opacity={0.55}
            />
          </g>
          {/* red chromatic edge */}
          <g mask="url(#drawn)" opacity={redEdge}>
            <path
              d={LOGO_PATH}
              fillRule="evenodd"
              fill={RED}
              transform="translate(7 5)"
              filter="url(#blurXS)"
            />
          </g>
          {/* hot white core -> silver */}
          <g mask="url(#drawn)">
            <path
              d={LOGO_PATH}
              fillRule="evenodd"
              fill={CREAM}
              filter={silver < 0.5 ? "url(#blurXS)" : undefined}
              opacity={1 - silver}
            />
            <path
              d={LOGO_PATH}
              fillRule="evenodd"
              fill="url(#silver)"
              opacity={silver}
            />
            <path
              d={LOGO_PATH}
              fillRule="evenodd"
              fill={CREAM}
              opacity={glowLevel * 0.5 * (1 - silver)}
              filter="url(#blurS)"
            />
          </g>
          {/* shine sweep */}
          {shineOpacity > 0 ? (
            <g clipPath="url(#logoClip)" opacity={shineOpacity}>
              <rect
                x={VIEW.x}
                y={VIEW.y}
                width={VIEW.size}
                height={VIEW.size}
                fill="url(#shine)"
              />
            </g>
          ) : null}
        </g>

        {/* ---------- Comet drawing the strokes ---------- */}
        <Comet
          d={STROKE_A}
          length={LEN_A}
          head={traced}
          tail={cometTail}
          opacity={cometOpacity}
          showHead={traced < LEN_A}
        />
        <Comet
          d={STROKE_B}
          length={LEN_B}
          head={traced - LEN_A}
          tail={cometTail}
          opacity={cometOpacity}
          showHead={traced >= LEN_A}
        />

        {/* ---------- Flash + sparkle at the arrow tip ---------- */}
        <g style={{ mixBlendMode: "screen" }}>
          <circle
            cx={ARROW_TIP.x}
            cy={ARROW_TIP.y}
            r={140}
            fill={RED}
            opacity={flash * 0.7}
            filter="url(#blurXL)"
          />
          <circle
            cx={ARROW_TIP.x}
            cy={ARROW_TIP.y}
            r={40}
            fill="#fff"
            opacity={flash}
            filter="url(#blurL)"
          />
          <g
            transform={`translate(${ARROW_TIP.x + 6} ${ARROW_TIP.y}) rotate(${tipSparkRotate}) scale(${tipSparkScale})`}
          >
            <path d={sparklePath(110, 0.09)} fill={RED} filter="url(#blurS)" />
            <path d={sparklePath(80, 0.08)} fill="#fff" />
          </g>
        </g>
      </svg>
    </AbsoluteFill>
  );
};

// Static layers kept outside the motion blur (blurring them causes banding).
export const Background: React.FC = () => (
  <AbsoluteFill
    style={{
      background:
        "radial-gradient(circle at 50% 50%, #0d0a0e 0%, #060508 45%, #020203 100%)",
    }}
  />
);

export const Vignette: React.FC = () => (
  <AbsoluteFill
    style={{
      background:
        "radial-gradient(circle at 50% 50%, rgba(0,0,0,0) 50%, rgba(0,0,0,0.55) 100%)",
    }}
  />
);
