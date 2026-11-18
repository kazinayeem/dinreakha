// A realistic phone with a real app screenshot inside. The screenshot is never stretched: the screen
// keeps the phone's own 1200×2664 aspect ratio, and zoom/pan only moves a camera over it.
import React from "react";
import { Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { theme } from "../styles/theme";

export const SHOT_RATIO = 2664 / 1200;

export type Focus = { x: number; y: number; w: number; h: number }; // in screenshot % (0–100)

/** Screens shown one after another inside the same phone, cross-fading with a small slide. */
export type ScreenStep = { src: string; at: number; zoom?: number; focusY?: number; highlight?: Focus; highlightAt?: number };

export const PhoneMockup: React.FC<{
  steps: ScreenStep[];
  width?: number;
  tilt?: number; // degrees of gentle 3D yaw
  enterAt?: number;
  float?: boolean;
}> = ({ steps, width = 440, tilt = 0, enterAt = 0, float = true }) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const bezel = Math.round(width * 0.03);
  const screenW = width - bezel * 2;
  const screenH = Math.round(screenW * SHOT_RATIO);
  const p = spring({ frame: f - enterAt, fps, config: theme.spring.smooth });
  const bob = float ? Math.sin(f / 34) * 5 : 0;
  const yaw = tilt * (1 - p) + Math.sin(f / 80) * (tilt ? 1.2 : 0);

  return (
    <div style={{
      width, height: screenH + bezel * 2, borderRadius: width * 0.13, background: "#121417",
      padding: bezel, boxSizing: "border-box", position: "relative",
      boxShadow: "0 60px 120px -30px rgba(22,24,29,0.45), 0 18px 40px -12px rgba(22,24,29,0.25), inset 0 0 0 2px #2A2E35",
      opacity: p,
      transform: `perspective(1800px) translateY(${interpolate(p, [0, 1], [120, 0]) + bob}px) rotateY(${yaw}deg) rotateX(${(1 - p) * 8}deg) scale(${interpolate(p, [0, 1], [0.92, 1])})`,
    }}>
      {/* side buttons */}
      <div style={{ position: "absolute", right: -5, top: width * 0.42, width: 5, height: width * 0.22, borderRadius: 3, background: "#2A2E35" }} />
      <div style={{ position: "absolute", left: -5, top: width * 0.36, width: 5, height: width * 0.14, borderRadius: 3, background: "#2A2E35" }} />
      <div style={{ width: screenW, height: screenH, borderRadius: width * 0.105, overflow: "hidden", position: "relative", background: "#fff" }}>
        {steps.map((st, i) => {
          const next = steps[i + 1];
          const inP = i === 0 ? 1 : interpolate(f, [st.at, st.at + 12], [0, 1], { easing: theme.ease.out, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
          const outP = next ? interpolate(f, [next.at, next.at + 10], [0, 1], { easing: theme.ease.in, extrapolateLeft: "clamp", extrapolateRight: "clamp" }) : 0;
          if (f < st.at - 1 && i !== 0) return null;
          if (outP >= 1) return null;
          const local = f - st.at;
          const zoom = st.zoom ?? 1.0;
          const kb = interpolate(local, [0, 150], [1, 1.035], { easing: theme.ease.inOut, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
          const fy = st.focusY ?? 0; // % of screenshot height to pan to
          const pan = interpolate(local, [0, 40], [0, fy], { easing: theme.ease.inOut, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
          return (
            <div key={i} style={{ position: "absolute", inset: 0, opacity: inP * (1 - outP),
              transform: `translateX(${(1 - inP) * 40 - outP * 40}px)` }}>
              <div style={{ width: screenW, height: screenH, transform: `scale(${zoom * kb}) translateY(${-pan / 100 * screenH}px)`, transformOrigin: "50% 0%" }}>
                <Img src={staticFile(`shots/${st.src}.png`)} style={{ width: screenW, height: screenH, display: "block" }} />
                {st.highlight ? <Highlight r={st.highlight} w={screenW} h={screenH} at={(st.highlightAt ?? st.at + 14) - st.at} local={local} /> : null}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

/** A soft mint ring that draws around a real control in the screenshot. */
const Highlight: React.FC<{ r: Focus; w: number; h: number; at: number; local: number }> = ({ r, w, h, at, local }) => {
  const { fps } = useVideoConfig();
  const p = spring({ frame: local - at, fps, config: theme.spring.snappy });
  const pulse = 1 + Math.sin((local - at) / 8) * 0.012;
  return (
    <div style={{
      position: "absolute", left: (r.x / 100) * w - 6, top: (r.y / 100) * h - 6, width: (r.w / 100) * w + 12, height: (r.h / 100) * h + 12,
      borderRadius: 18, border: `4px solid ${theme.colors.hero}`, boxShadow: `0 0 0 8px ${theme.colors.hero}22`,
      opacity: p, transform: `scale(${interpolate(p, [0, 1], [1.12, 1]) * pulse})`,
    }} />
  );
};
