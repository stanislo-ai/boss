import React from "react";
import {
  AbsoluteFill,
  Img,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";
import { AmbientVideo } from "../components/AmbientVideo";
import { Glass } from "../components/Glass";
import { GRADIENTS, Reveal } from "../components/Text";
import { prog, sp } from "../anim";
import { C, EASE_IN_OUT, EASE_OUT, FONT_DISPLAY, FONT_TEXT, clamp } from "../theme";
import { BEATS, s } from "../timing";

const ORB = 440;

export const Outro: React.FC<{ start: number }> = ({ start }) => {
  const f = useCurrentFrame();
  const orb = sp(f, 0, { damping: 11, stiffness: 150 });
  const logo = sp(f, 3, { damping: 14, stiffness: 120 });
  const sheenP = interpolate(f, [8, 34], [-20, 120], {
    ...clamp,
    easing: EASE_IN_OUT,
  });
  const ctaAt = s(BEATS[26]) - start;
  const tapAt = s(BEATS[28]) - start;
  const cta = sp(f, ctaAt, { damping: 13, stiffness: 170 });

  // finger travels in, presses on the beat
  const fingerIn = prog(f, tapAt - 12, tapAt - 1, EASE_OUT);
  const press = interpolate(f, [tapAt, tapAt + 3, tapAt + 10], [0, 1, 0], clamp);
  const ripple = prog(f, tapAt, tapAt + 22);
  const afterTap = prog(f, tapAt, tapAt + 8);
  const floatY = Math.sin(f / 16) * 6;

  return (
    <AbsoluteFill>
      <AmbientVideo name="lv" trimBefore={s(2)} opacity={0.35} />

      {/* rotating aura behind the orb */}
      <div
        style={{
          position: "absolute",
          left: 540 - 330,
          top: 690 - 330 + floatY,
          width: 660,
          height: 660,
          borderRadius: "50%",
          background:
            "conic-gradient(from 0deg, #2fd6c8, #2c7bff, #8a5cff, #ff6ad5, #2fd6c8)",
          filter: "blur(70px)",
          opacity: 0.55 * orb,
          rotate: `${f * 2.2}deg`,
          scale: String(0.6 + 0.4 * orb),
        }}
      />

      {/* liquid glass orb with the logo */}
      <div
        style={{
          position: "absolute",
          left: 540 - ORB / 2,
          top: 690 - ORB / 2 + floatY,
          width: ORB,
          height: ORB,
          scale: String(interpolate(orb, [0, 1], [0.15, 1])),
          opacity: interpolate(f, [0, 3], [0, 1], clamp),
        }}
      >
        <Glass radius={ORB / 2} style={{ inset: 0 }} liquid blur={20} />
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            scale: String(interpolate(logo, [0, 1], [0.6, 1])),
            filter: `blur(${(1 - Math.min(logo, 1)) * 20}px)`,
            opacity: interpolate(f, [3, 8], [0, 1], clamp),
          }}
        >
          <Img
            src={staticFile("img/logo-silver.png")}
            style={{
              height: 250,
              filter: "drop-shadow(0 10px 24px rgba(0,0,0,0.55))",
            }}
          />
          {/* travelling highlight across the metal */}
          <Img
            src={staticFile("img/logo-white.png")}
            style={{
              position: "absolute",
              height: 250,
              WebkitMaskImage: `linear-gradient(105deg, transparent ${sheenP - 14}%, #000 ${sheenP}%, transparent ${sheenP + 14}%)`,
              maskImage: `linear-gradient(105deg, transparent ${sheenP - 14}%, #000 ${sheenP}%, transparent ${sheenP + 14}%)`,
              opacity: 0.9,
            }}
          />
        </div>
      </div>

      {/* wordmark */}
      <div style={{ position: "absolute", top: 960, left: 0, right: 0 }}>
        <Reveal
          text="Flipping_now"
          at={8}
          duration={14}
          size={118}
          gradient={GRADIENTS.silver}
          tracking={-0.04}
        />
        <div
          style={{
            textAlign: "center",
            marginTop: 10,
            fontFamily: FONT_DISPLAY,
            fontWeight: 500,
            fontSize: 44,
            color: C.grey,
            letterSpacing: "-0.01em",
            opacity: prog(f, 14, 26),
            filter: `blur(${(1 - prog(f, 14, 26)) * 10}px)`,
            translate: `0px ${(1 - prog(f, 14, 30)) * 20}px`,
          }}
        >
          Perfumy · AirPods · Elektronika
        </div>
      </div>

      {/* CTA button */}
      <div
        style={{
          position: "absolute",
          left: 540 - 320,
          top: 1220,
          width: 640,
          height: 150,
          opacity: interpolate(f, [ctaAt, ctaAt + 4], [0, 1], clamp),
          scale: String(
            interpolate(cta, [0, 1], [0.6, 1]) * (1 - press * 0.06),
          ),
          translate: `0px ${(1 - cta) * 80}px`,
        }}
      >
        {/* glow */}
        <div
          style={{
            position: "absolute",
            inset: -30,
            borderRadius: 110,
            background: "linear-gradient(95deg, #2fd6c8, #2c7bff, #8a5cff)",
            filter: "blur(40px)",
            opacity: 0.35 + afterTap * 0.35 - press * 0.1,
          }}
        />
        <Glass
          radius={75}
          style={{ inset: 0 }}
          tint="linear-gradient(95deg, rgba(47,214,200,0.45), rgba(44,123,255,0.45) 55%, rgba(138,92,255,0.45))"
          sheen={prog(f, ctaAt + 6, ctaAt + 30)}
        />
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 20,
            fontFamily: FONT_TEXT,
            fontWeight: 700,
            fontSize: 58,
            color: "#fff",
            letterSpacing: "-0.02em",
          }}
        >
          Link w bio
          <svg
            width="46"
            height="46"
            viewBox="0 0 24 24"
            style={{ translate: `0px ${Math.sin(f / 4) * 3 * afterTap}px` }}
          >
            <path
              d="M12 4v14M5.5 11.5 12 18l6.5-6.5"
              fill="none"
              stroke="#fff"
              strokeWidth="2.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </div>
      </div>

      {/* tap ripple */}
      <div
        style={{
          position: "absolute",
          left: 700 - 60,
          top: 1310 - 60,
          width: 120,
          height: 120,
          borderRadius: "50%",
          border: "3px solid rgba(255,255,255,0.9)",
          scale: String(0.4 + ripple * 2.6),
          opacity: f >= tapAt ? 1 - ripple : 0,
        }}
      />
      {/* finger / touch indicator */}
      <div
        style={{
          position: "absolute",
          left: 700 - 48,
          top: 1310 - 48,
          width: 96,
          height: 96,
          borderRadius: "50%",
          background: "rgba(255,255,255,0.85)",
          boxShadow: "0 10px 30px rgba(0,0,0,0.45), inset 0 -6px 12px rgba(0,0,0,0.15)",
          translate: `${(1 - fingerIn) * 260}px ${(1 - fingerIn) * 380}px`,
          scale: String(1 - press * 0.22),
          opacity: fingerIn * interpolate(f, [tapAt + 10, tapAt + 20], [1, 0], clamp),
        }}
      />

      {/* handle */}
      <div
        style={{
          position: "absolute",
          top: 1400,
          left: 0,
          right: 0,
          textAlign: "center",
          fontFamily: FONT_TEXT,
          fontWeight: 600,
          fontSize: 38,
          color: C.white,
          opacity: 0.75 * prog(f, ctaAt + 6, ctaAt + 18),
          letterSpacing: "0em",
        }}
      >
        @flippingnow999
      </div>
    </AbsoluteFill>
  );
};
