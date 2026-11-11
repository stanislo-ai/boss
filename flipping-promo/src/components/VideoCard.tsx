import React from "react";
import { Video } from "@remotion/media";
import { staticFile } from "remotion";
import { Glass } from "./Glass";
import { s } from "../timing";

type Props = {
  clip: string;
  /** seconds into the source clip to start from */
  start?: number;
  width: number;
  height: number;
  radius?: number;
  bezel?: number;
  style?: React.CSSProperties;
  sheen?: number;
  playbackRate?: number;
  /** css transform-origin / object-position for the footage */
  objectPosition?: string;
  videoScale?: number;
  videoStyle?: React.CSSProperties;
};

/** Footage inside a liquid-glass bezel, like a floating device screen. */
export const VideoCard: React.FC<Props> = ({
  clip,
  start = 0,
  width,
  height,
  radius = 56,
  bezel = 14,
  style,
  sheen,
  playbackRate = 1,
  videoScale = 1,
  videoStyle,
}) => {
  const inner = radius - bezel * 0.6;
  return (
    <div style={{ position: "absolute", width, height, ...style }}>
      <Glass radius={radius} style={{ inset: 0 }} sheen={sheen} />
      <div
        style={{
          position: "absolute",
          inset: bezel,
          borderRadius: inner,
          overflow: "hidden",
          background: "#000",
          boxShadow: "0 0 0 1px rgba(0,0,0,0.6)",
        }}
      >
        <Video
          src={staticFile(`clips/${clip}.mp4`)}
          trimBefore={s(start)}
          playbackRate={playbackRate}
          muted
          objectFit="cover"
          style={{
            width: width - bezel * 2,
            height: height - bezel * 2,
            scale: String(videoScale),
            ...videoStyle,
          }}
        />
        {/* glass reflection over the "screen" */}
        <div
          style={{
            position: "absolute",
            inset: 0,
            background:
              "linear-gradient(160deg, rgba(255,255,255,0.14) 0%, rgba(255,255,255,0.03) 30%, rgba(255,255,255,0) 50%, rgba(0,0,0,0.25) 100%)",
          }}
        />
        <div
          style={{
            position: "absolute",
            inset: 0,
            borderRadius: inner,
            boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.08)",
          }}
        />
      </div>
    </div>
  );
};
