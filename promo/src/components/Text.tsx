// Kinetic Bangla headline (word-by-word rise) and the subtitle track.
import React from "react";
import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { theme } from "../styles/theme";
import { FPS, HIGHLIGHT, SEGMENTS } from "../utils/timing";

/** Headline: words rise in with a 3-frame stagger; one optional word sits on a mint pill. */
export const Headline: React.FC<{
  text: string;
  at?: number;
  until?: number;
  size?: number;
  dark?: boolean;
  accent?: string; // the one emphasised word
  top?: number;
  align?: "center" | "left";
}> = ({ text, at = 0, until, size = 68, dark, accent, top = theme.layout.headlineTop, align = "center" }) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const out = until !== undefined
    ? interpolate(f, [until - 8, until], [0, 1], { easing: theme.ease.in, extrapolateLeft: "clamp", extrapolateRight: "clamp" })
    : 0;
  const words = text.split(" ");
  return (
    <div style={{ position: "absolute", left: 70, right: 70, top, display: "flex", flexWrap: "wrap", columnGap: size * 0.28, rowGap: 4,
      justifyContent: align === "center" ? "center" : "flex-start", fontFamily: theme.fonts.bn, fontWeight: 700, fontSize: size,
      lineHeight: 1.28, letterSpacing: "-0.01em", color: dark ? "#F4F5F7" : theme.colors.ink,
      opacity: 1 - out, transform: `translateY(${-out * 24}px)` }}>
      {words.map((w, i) => {
        const p = spring({ frame: f - at - i * 3, fps, config: theme.spring.snappy });
        const isAccent = accent && w.includes(accent);
        const pill = spring({ frame: f - at - i * 3 - 6, fps, config: theme.spring.smooth });
        return (
          <span key={i} style={{ position: "relative", display: "inline-block", opacity: p,
            transform: `translateY(${interpolate(p, [0, 1], [34, 0])}px) scale(${interpolate(p, [0, 1], [0.96, 1])})`,
            color: isAccent ? (dark ? theme.colors.heroSoft : theme.colors.hero) : undefined }}>
            {isAccent ? (
              <span style={{ position: "absolute", left: -10, right: -10, bottom: size * 0.08, height: size * 0.32, borderRadius: 10,
                background: dark ? "rgba(124,245,200,0.18)" : theme.colors.heroSoft, transform: `scaleX(${pill})`, transformOrigin: "0% 50%", zIndex: -1 }} />
            ) : null}
            {w}
          </span>
        );
      })}
    </div>
  );
};

/** Small caption line under a headline (dim). */
export const Kicker: React.FC<{ text: string; at?: number; top: number; dark?: boolean; size?: number }> = ({ text, at = 0, top, dark, size = 34 }) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = spring({ frame: f - at, fps, config: theme.spring.smooth });
  return (
    <div style={{ position: "absolute", left: 80, right: 80, top, textAlign: "center", fontFamily: theme.fonts.bn, fontWeight: 500,
      fontSize: size, color: dark ? "#AEB6C2" : theme.colors.inkDim, opacity: p, transform: `translateY(${(1 - p) * 16}px)` }}>
      {text}
    </div>
  );
};

/**
 * Subtitles from the real narration timing. Max two lines, a soft card for contrast on any background,
 * keywords in mint. They sit at the subtitle band, below the phone, inside the platform safe area.
 */
export const Subtitles: React.FC<{ darkUntil: number; hideFrom?: number }> = ({ darkUntil, hideFrom = Infinity }) => {
  const f = useCurrentFrame();
  const t = f / FPS;
  const seg = SEGMENTS.find((sg) => t >= sg.from - 0.05 && t <= sg.to + 0.15);
  if (!seg || t >= hideFrom) return null; // the closing lines are already on screen as type
  const local = f - Math.round(seg.from * FPS);
  const dur = Math.round((seg.to - seg.from) * FPS);
  const inP = interpolate(local, [0, 6], [0, 1], { easing: theme.ease.out, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const outP = interpolate(local, [dur, dur + 5], [0, 1], { easing: theme.ease.in, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const dark = t < darkUntil;
  const words = seg.text.split(" ");
  // progress through the line, so each word brightens as it is (roughly) spoken
  const spoken = interpolate(local, [0, dur], [0, words.length], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ pointerEvents: "none" }}>
      <div style={{ position: "absolute", left: 60, right: 60, top: theme.layout.subtitleTop, display: "flex", justifyContent: "center",
        opacity: inP * (1 - outP), transform: `translateY(${(1 - inP) * 10}px)` }}>
        <div style={{ maxWidth: 940, padding: "14px 26px", borderRadius: 22, textAlign: "center",
          background: dark ? "rgba(255,255,255,0.08)" : "rgba(255,255,255,0.86)",
          boxShadow: dark ? "none" : "0 10px 30px -14px rgba(22,24,29,0.25)",
          fontFamily: theme.fonts.bn, fontWeight: 600, fontSize: 44, lineHeight: 1.42,
          display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>
          {words.map((w, i) => {
            const key = HIGHLIGHT.some((h) => w.includes(h));
            const lit = i < spoken + 0.6;
            const base = dark ? "#F4F5F7" : theme.colors.ink;
            const color = key ? (dark ? "#7CF5C8" : theme.colors.hero) : base;
            return (
              <span key={i} style={{ color, opacity: lit ? 1 : 0.45 }}>
                {w}{i < words.length - 1 ? " " : ""}
              </span>
            );
          })}
        </div>
      </div>
    </AbsoluteFill>
  );
};
