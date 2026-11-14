import { getLength } from "@remotion/paths";

// All coordinates live in the 1000x1000 space the logo was traced in.
// The logo's visual centre in that space:
export const CENTER = { x: 480, y: 490 };

// SVG viewBox that centres the logo in a square frame with breathing room.
export const VIEW = { x: -170, y: -160, size: 1300 };

// Centre-lines of the two strokes that make up the logo, used to "draw" it.
// A: outer stem -> top bar -> right tip -> inner bar -> inner stem.
export const STROKE_A =
  "M 205 545 L 245 330 C 260 240, 310 165, 420 165 L 815 165 C 800 240, 740 310, 670 310 L 440 310 C 380 310, 350 360, 342 400 L 331 458";
// B: lower inner stem -> bottom loop -> left tip -> up into the arrow shaft.
export const STROKE_B =
  "M 335 578 L 320 700 C 308 770, 260 812, 190 812 L 140 818 C 146 760, 152 720, 162 680 C 185 570, 280 520, 380 505 C 440 497, 480 494, 540 482";

// Arrow head, revealed with a pop once the stroke arrives.
export const ARROW_HEAD = "M 482 392 L 668 462 L 532 610 Z";
export const ARROW_PIVOT = { x: 520, y: 480 };
export const ARROW_TIP = { x: 655, y: 462 };

// The orbiting light streak around the logo centre (before it draws the logo).
const RX = 330;
const RY = 95;
// Two full loops so the trail can wrap past the starting point.
const halfLoop = `A ${RX} ${RY} 0 1 1 ${CENTER.x + RX} ${CENTER.y} A ${RX} ${RY} 0 1 1 ${CENTER.x - RX} ${CENTER.y}`;
export const ORBIT = `M ${CENTER.x - RX} ${CENTER.y} ${halfLoop} ${halfLoop}`;

export const LEN_A = getLength(STROKE_A);
export const LEN_B = getLength(STROKE_B);
export const LEN_ORBIT = getLength(ORBIT);

// Four-point sparkle with concave sides, centred on 0,0.
export const sparklePath = (r: number, pinch = 0.16) => {
  const k = r * pinch;
  return `M 0 ${-r} Q ${k} ${-k} ${r} 0 Q ${k} ${k} 0 ${r} Q ${-k} ${k} ${-r} 0 Q ${-k} ${-k} 0 ${-r} Z`;
};

export const RED = "#ff2b2b";
export const DEEP_RED = "#c40f16";
export const CREAM = "#fbf6ee";
