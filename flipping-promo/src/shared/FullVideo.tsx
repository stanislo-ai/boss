import React from "react";
import { Video } from "@remotion/media";
import { AbsoluteFill, staticFile } from "remotion";
import { s } from "../timing";

/** Full-bleed footage with an optional zoom (Ken Burns) around an origin. */
export const FullVideo: React.FC<{
  clip: string;
  start: number;
  zoom?: number;
  origin?: string;
  style?: React.CSSProperties;
  width?: number;
  height?: number;
}> = ({
  clip,
  start,
  zoom = 1,
  origin = "50% 50%",
  style,
  width = 1080,
  height = 1920,
}) => (
  <AbsoluteFill style={{ overflow: "hidden", ...style }}>
    <div
      style={{
        position: "absolute",
        inset: 0,
        scale: String(zoom),
        transformOrigin: origin,
      }}
    >
      <Video
        src={staticFile(`clips/${clip}.mp4`)}
        trimBefore={s(start)}
        muted
        objectFit="cover"
        style={{ width, height }}
      />
    </div>
  </AbsoluteFill>
);

/**
 * Footage visible only inside text: multiply a white-on-black text layer over
 * the video, then screen the isolated result onto the page (black vanishes).
 */
export const VideoInText: React.FC<{
  clip: string;
  start: number;
  children: React.ReactNode;
  zoom?: number;
  filter?: string;
}> = ({ clip, start, children, zoom = 1, filter }) => (
  <AbsoluteFill style={{ isolation: "isolate", mixBlendMode: "screen" }}>
    <FullVideo clip={clip} start={start} zoom={zoom} style={{ filter }} />
    <AbsoluteFill style={{ background: "#000", mixBlendMode: "multiply" }}>
      {children}
    </AbsoluteFill>
  </AbsoluteFill>
);
