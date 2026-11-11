import React from "react";
import { AbsoluteFill, Sequence, interpolate, useCurrentFrame } from "remotion";
import { AmbientVideo } from "../components/AmbientVideo";
import { GRADIENTS, Reveal } from "../components/Text";
import { VideoCard } from "../components/VideoCard";
import { exitP, prog } from "../anim";
import { EASE_OUT, clamp } from "../theme";
import { BEATS, s } from "../timing";

const CUTS = [
  { clip: "shelf", start: 3.1 },
  { clip: "ysl", start: 2.0 },
  { clip: "airpods", start: 1.0 },
  { clip: "lv", start: 1.2 },
];

export const Montage: React.FC<{ len: number; start: number }> = ({
  len,
  start,
}) => {
  const f = useCurrentFrame();
  const out = exitP(f, len);
  const cutAt = [12, 13, 14, 15].map((b) => s(BEATS[b]) - start);
  const idx = cutAt.filter((c) => f >= c).length - 1;
  const cur = Math.max(0, idx);
  const local = f - cutAt[cur];
  const punch = interpolate(local, [0, 9], [1.1, 1], {
    ...clamp,
    easing: EASE_OUT,
  });
  const flash = interpolate(local, [0, 5], [0.35, 0], clamp);
  const tiltSign = cur % 2 === 0 ? 1 : -1;
  const enter = prog(f, 0, 10);

  return (
    <AbsoluteFill>
      {CUTS.map((c, i) => (
        <Sequence
          key={`amb${i}`}
          from={cutAt[i]}
          durationInFrames={(cutAt[i + 1] ?? len) - cutAt[i]}
        >
          <AmbientVideo name={c.clip} trimBefore={s(c.start)} opacity={0.55} />
        </Sequence>
      ))}
      <AbsoluteFill
        style={{
          opacity: 1 - out,
          filter: `blur(${out * 26}px)`,
          scale: String(1 - out * 0.15),
        }}
      >
        <div style={{ position: "absolute", top: 230, left: 0, right: 0 }}>
          <Reveal
            text="Wszystko"
            at={0}
            duration={12}
            size={168}
            gradient={GRADIENTS.silver}
          />
          <Reveal
            text="w jednym miejscu."
            at={6}
            stagger={3}
            duration={12}
            size={86}
            weight={700}
            gradient={GRADIENTS.aqua}
            style={{ marginTop: 4 }}
          />
        </div>

        <div
          style={{
            position: "absolute",
            left: 140,
            top: 640,
            width: 800,
            height: 860,
            opacity: enter,
            scale: String(punch * interpolate(enter, [0, 1], [0.9, 1])),
            rotate: `${(punch - 1) * 20 * tiltSign}deg`,
          }}
        >
          {CUTS.map((c, i) => (
            <Sequence
              key={i}
              from={cutAt[i]}
              durationInFrames={(cutAt[i + 1] ?? len) - cutAt[i]}
              layout="none"
            >
              <VideoCard
                clip={c.clip}
                start={c.start}
                width={800}
                height={860}
                radius={64}
              />
            </Sequence>
          ))}
          <div
            style={{
              position: "absolute",
              inset: 14,
              borderRadius: 56,
              background: "#fff",
              opacity: flash,
              mixBlendMode: "screen",
            }}
          />
        </div>

        {/* beat progress dots */}
        <div
          style={{
            position: "absolute",
            top: 1540,
            left: 0,
            right: 0,
            display: "flex",
            justifyContent: "center",
            gap: 16,
            opacity: enter,
          }}
        >
          {CUTS.map((_, i) => (
            <div
              key={i}
              style={{
                width: i === cur ? 54 : 14,
                height: 14,
                borderRadius: 7,
                background:
                  i <= cur ? "rgba(255,255,255,0.95)" : "rgba(255,255,255,0.3)",
              }}
            />
          ))}
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
