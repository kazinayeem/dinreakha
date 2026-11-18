// Background, grade, grain and vignette — the bottom and top of every scene's layer stack.
import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame } from "remotion";
import { theme } from "../styles/theme";

/** Soft off-white with two very faint warm/cool light pools that drift — never a flat fill. */
export const LightBg: React.FC = () => {
  const f = useCurrentFrame();
  const d1 = Math.sin(f / 70) * 40;
  const d2 = Math.cos(f / 90) * 30;
  return (
    <AbsoluteFill style={{ background: theme.colors.bg }}>
      <div style={{ position: "absolute", width: 1300, height: 1300, borderRadius: "50%", top: -520, left: -380 + d1,
        background: `radial-gradient(circle, ${theme.colors.heroSoft}AA, transparent 62%)`, filter: "blur(40px)" }} />
      <div style={{ position: "absolute", width: 1100, height: 1100, borderRadius: "50%", bottom: -480, right: -360 - d2,
        background: "radial-gradient(circle, #E9ECF7CC, transparent 64%)", filter: "blur(50px)" }} />
    </AbsoluteFill>
  );
};

/** Dark, quiet backdrop for the "problem" scenes. */
export const NightBg: React.FC = () => {
  const f = useCurrentFrame();
  const d = Math.sin(f / 60) * 30;
  return (
    <AbsoluteFill style={{ background: theme.colors.night }}>
      <div style={{ position: "absolute", width: 1200, height: 1200, borderRadius: "50%", top: 300 + d, left: -60,
        background: "radial-gradient(circle, #24303D, transparent 60%)", filter: "blur(60px)" }} />
    </AbsoluteFill>
  );
};

export const Grain: React.FC<{ dark?: boolean }> = ({ dark }) => {
  const f = useCurrentFrame();
  const noise = `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='220' height='220'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='220' height='220' filter='url(%23n)' opacity='0.5'/%3E%3C/svg%3E")`;
  return (
    <AbsoluteFill style={{ pointerEvents: "none", backgroundImage: noise, backgroundSize: "220px",
      backgroundPosition: `${(f * 7) % 220}px ${(f * 13) % 220}px`, opacity: dark ? 0.07 : 0.035,
      mixBlendMode: dark ? "overlay" : "multiply" }} />
  );
};

export const Vignette: React.FC<{ dark?: boolean }> = ({ dark }) => (
  <AbsoluteFill style={{ pointerEvents: "none",
    background: `radial-gradient(ellipse at center, transparent 58%, rgba(0,0,0,${dark ? 0.35 : 0.07}) 100%)` }} />
);

/** Light grade that unifies the screenshots with the off-white world. */
export const Grade: React.FC = () => (
  <AbsoluteFill style={{ pointerEvents: "none" }}>
    <AbsoluteFill style={{ background: "linear-gradient(180deg, rgba(255,255,255,0.0), transparent 70%, rgba(22,24,29,0.04))" }} />
  </AbsoluteFill>
);

/** Scene wrapper: enters with fade + slight scale, exits faster with fade + lift. */
export const SceneShell: React.FC<{ dur: number; children: React.ReactNode; enter?: number; exit?: number }> = ({
  dur, children, enter = 9, exit = 6,
}) => {
  const f = useCurrentFrame();
  const inP = interpolate(f, [0, enter], [0, 1], { easing: theme.ease.out, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const outP = interpolate(f, [dur - exit, dur], [0, 1], { easing: theme.ease.in, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ opacity: inP * (1 - outP),
      transform: `scale(${interpolate(inP, [0, 1], [1.02, 1]) * interpolate(outP, [0, 1], [1, 0.985])})` }}>
      {children}
    </AbsoluteFill>
  );
};
