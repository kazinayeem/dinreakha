// The Dinrekha mark, animated from its own SVG geometry (docs/dinrekha-logo.svg):
// the day-line draws left → right and turns into a tick, the sun rises, its rays pop.
import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { theme } from "../styles/theme";

export const LogoMark: React.FC<{ size?: number; at?: number }> = ({ size = 260, at = 0 }) => {
  const f = useCurrentFrame() - at;
  const { fps } = useVideoConfig();
  const tile = spring({ frame: f, fps, config: theme.spring.bouncy });
  const line = interpolate(f, [6, 30], [0, 1], { easing: theme.ease.out, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const sun = spring({ frame: f - 14, fps, config: theme.spring.smooth });
  const rays = [0, 1, 2].map((i) => spring({ frame: f - 26 - i * 3, fps, config: theme.spring.snappy }));
  const lineLen = 20 + 9.9 + 38.2; // path length of the tick
  const breathe = 1 + Math.sin(f / 26) * 0.01;
  return (
    <svg viewBox="0 0 108 108" width={size} height={size}
      style={{ transform: `scale(${interpolate(tile, [0, 1], [0.6, 1]) * breathe}) rotate(${(1 - tile) * -8}deg)`, opacity: Math.min(1, tile * 1.4),
        filter: "drop-shadow(0 30px 40px rgba(27,36,99,0.28))" }}>
      <defs>
        <linearGradient id="lbg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#1B2463" /><stop offset="1" stopColor="#0E7C7B" />
        </linearGradient>
        <clipPath id="hz"><rect x="0" y="0" width="108" height="62" /></clipPath>
      </defs>
      <rect width="108" height="108" rx="24" fill="url(#lbg)" />
      <g clipPath="url(#hz)">
        <path d="M33,56 A13,13 0 0,1 59,56 Z" fill={theme.colors.sun} transform={`translate(0 ${(1 - sun) * 16})`} />
      </g>
      {["M46,37 L46,33", "M34.5,41.5 L31.7,38.7", "M57.5,41.5 L60.3,38.7"].map((d, i) => (
        <path key={i} d={d} stroke={theme.colors.sun} strokeWidth={3} strokeLinecap="round" fill="none" opacity={rays[i]} />
      ))}
      <path d="M26,66 L46,66 L53,73 L80,46" stroke="#7CF5C8" strokeWidth={6} strokeLinecap="round" strokeLinejoin="round" fill="none"
        strokeDasharray={lineLen} strokeDashoffset={lineLen * (1 - line)} />
    </svg>
  );
};

export const Wordmark: React.FC<{ at?: number; size?: number; dark?: boolean }> = ({ at = 0, size = 110, dark }) => {
  const f = useCurrentFrame() - at;
  const { fps } = useVideoConfig();
  const p = spring({ frame: f, fps, config: theme.spring.smooth });
  const q = spring({ frame: f - 8, fps, config: theme.spring.smooth });
  return (
    <div style={{ textAlign: "center" }}>
      <div style={{ fontFamily: theme.fonts.bn, fontWeight: 700, fontSize: size, color: dark ? "#fff" : theme.colors.ink, lineHeight: 1.15,
        opacity: p, transform: `translateY(${(1 - p) * 30}px)`, letterSpacing: "-0.01em" }}>দিনরেখা</div>
      <div style={{ fontFamily: theme.fonts.latin, fontWeight: 600, fontSize: size * 0.3, color: dark ? "#AEB6C2" : theme.colors.inkDim,
        letterSpacing: "0.32em", marginTop: 6, opacity: q, transform: `translateY(${(1 - q) * 16}px)` }}>DINREKHA</div>
    </div>
  );
};
