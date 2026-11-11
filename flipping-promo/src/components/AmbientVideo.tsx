import React from "react";
import { Video } from "@remotion/media";
import { AbsoluteFill, staticFile } from "remotion";

/**
 * Pre-blurred low-res copy of a clip, scaled up to fill the frame.
 * Gives the glass something colourful to refract (Apple Music-style ambience).
 */
export const AmbientVideo: React.FC<{
  name: string;
  trimBefore?: number;
  opacity?: number;
}> = ({ name, trimBefore = 0, opacity = 0.45 }) => (
  <AbsoluteFill style={{ opacity, overflow: "hidden" }}>
    <Video
      src={staticFile(`clips/${name}_amb.mp4`)}
      trimBefore={trimBefore}
      muted
      objectFit="cover"
      style={{
        position: "absolute",
        inset: -120,
        width: 1320,
        height: 2160,
        filter: "blur(30px) saturate(1.5)",
      }}
    />
    <AbsoluteFill style={{ background: "rgba(3,3,5,0.45)" }} />
  </AbsoluteFill>
);
