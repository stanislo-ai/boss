import React from "react";

type GlassProps = {
  radius?: number;
  style?: React.CSSProperties;
  children?: React.ReactNode;
  /** 0..1 position of the travelling specular sheen, or undefined for none */
  sheen?: number;
  /** extra tint layered on the glass, e.g. "rgba(47,214,200,0.18)" */
  tint?: string;
  /** use the SVG displacement filter for real refraction */
  liquid?: boolean;
  blur?: number;
  shadow?: boolean;
};

/**
 * Apple "Liquid Glass"-style surface: backdrop blur + saturation,
 * gradient rim light, inner specular highlights and an optional sheen sweep.
 */
export const Glass: React.FC<GlassProps> = ({
  radius = 48,
  style,
  children,
  sheen,
  tint,
  liquid = false,
  blur = 26,
  shadow = true,
}) => {
  const backdrop = liquid
    ? `url(#liquid-glass) blur(${blur * 0.5}px) saturate(190%) brightness(1.08)`
    : `blur(${blur}px) saturate(185%) brightness(1.06)`;

  return (
    <div
      style={{
        position: "absolute",
        borderRadius: radius,
        overflow: "hidden",
        backdropFilter: backdrop,
        WebkitBackdropFilter: backdrop,
        background:
          "linear-gradient(155deg, rgba(255,255,255,0.17) 0%, rgba(255,255,255,0.06) 38%, rgba(255,255,255,0.035) 62%, rgba(255,255,255,0.1) 100%)",
        boxShadow: [
          "inset 0 1.5px 0 rgba(255,255,255,0.55)",
          "inset 0 -1px 0 rgba(255,255,255,0.12)",
          "inset 0 0 32px rgba(255,255,255,0.05)",
          "inset 0 -18px 40px -20px rgba(255,255,255,0.12)",
          shadow ? "0 40px 90px -20px rgba(0,0,0,0.7)" : "",
          shadow ? "0 10px 30px -10px rgba(0,0,0,0.5)" : "",
        ]
          .filter(Boolean)
          .join(", "),
        ...style,
      }}
    >
      {tint ? (
        <div
          style={{
            position: "absolute",
            inset: 0,
            background: tint,
            mixBlendMode: "screen",
          }}
        />
      ) : null}
      {/* gradient rim (1.5px) */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          borderRadius: radius,
          padding: 1.5,
          background:
            "linear-gradient(150deg, rgba(255,255,255,0.75) 0%, rgba(255,255,255,0.12) 30%, rgba(255,255,255,0.04) 55%, rgba(255,255,255,0.35) 100%)",
          WebkitMask:
            "linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)",
          WebkitMaskComposite: "xor",
          maskComposite: "exclude",
          pointerEvents: "none",
          zIndex: 3,
        }}
      />
      {/* top-left specular bloom */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          background:
            "radial-gradient(120% 60% at 15% 0%, rgba(255,255,255,0.18) 0%, rgba(255,255,255,0) 55%)",
          pointerEvents: "none",
          zIndex: 3,
        }}
      />
      {children}
      {sheen !== undefined ? (
        <div
          style={{
            position: "absolute",
            top: "-50%",
            bottom: "-50%",
            width: "45%",
            left: `${-60 + sheen * 180}%`,
            rotate: "18deg",
            background:
              "linear-gradient(90deg, rgba(255,255,255,0) 0%, rgba(255,255,255,0.22) 45%, rgba(255,255,255,0.4) 50%, rgba(255,255,255,0.22) 55%, rgba(255,255,255,0) 100%)",
            mixBlendMode: "overlay",
            pointerEvents: "none",
            zIndex: 4,
          }}
        />
      ) : null}
    </div>
  );
};

/** SVG filter used by <Glass liquid /> for edge refraction. Mount once. */
export const LiquidGlassFilter: React.FC = () => (
  <svg width="0" height="0" style={{ position: "absolute" }}>
    <filter
      id="liquid-glass"
      x="0%"
      y="0%"
      width="100%"
      height="100%"
      colorInterpolationFilters="sRGB"
    >
      <feTurbulence
        type="fractalNoise"
        baseFrequency="0.008 0.012"
        numOctaves="2"
        seed="3"
        result="noise"
      />
      <feGaussianBlur in="noise" stdDeviation="2" result="soft" />
      <feDisplacementMap
        in="SourceGraphic"
        in2="soft"
        scale="70"
        xChannelSelector="R"
        yChannelSelector="G"
      />
    </filter>
  </svg>
);
