// Illustrations for the "problem" moments. They are deliberately generic (no real app's UI, no brand
// marks): abstract short-video cards, a whiteboard, a photo gallery, receipts.
import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { theme } from "../styles/theme";

const CARD_TONES = ["#3B4B5E", "#5B4A6B", "#2F5D57", "#6A4E3C", "#45506E", "#5E3F4F", "#3E5A3F", "#4E4466"];

/** A plain phone body (no screenshot) used for illustrations. */
export const BlankPhone: React.FC<{ width?: number; children: React.ReactNode; dark?: boolean }> = ({ width = 470, children, dark = true }) => {
  const bezel = Math.round(width * 0.03);
  const h = Math.round((width - bezel * 2) * (2664 / 1200));
  return (
    <div style={{ width, height: h + bezel * 2, borderRadius: width * 0.13, background: "#0B0D10", padding: bezel, boxSizing: "border-box",
      boxShadow: "0 60px 120px -30px rgba(0,0,0,0.6), inset 0 0 0 2px #2A2E35" }}>
      <div style={{ width: "100%", height: "100%", borderRadius: width * 0.105, overflow: "hidden", position: "relative", background: dark ? "#07080A" : "#fff" }}>
        {children}
      </div>
    </div>
  );
};

/** Endless short-video feed: full-height cards flick upward faster and faster. */
export const ReelFeed: React.FC<{ speedUp?: boolean; counter?: { from: number; to: number; at: number; dur: number }; frozen?: boolean }> = ({
  speedUp = true, counter, frozen,
}) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const cardH = 980;
  // each flick: hold, then a quick eased swipe; holds shrink over time
  let y = 0;
  let t = 0;
  let k = 0;
  const flicks: number[] = [];
  while (t < 2000) {
    const hold = speedUp ? Math.max(6, 22 - k * 2) : 16;
    flicks.push(t + hold);
    t += hold + 9;
    k++;
  }
  for (const start of flicks) {
    if (frozen) break;
    y += interpolate(f, [start, start + 9], [0, cardH], { easing: theme.ease.inOut, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  }
  const first = Math.floor(y / cardH);
  return (
    <div style={{ position: "absolute", inset: 0 }}>
      {[first - 1, first, first + 1, first + 2].map((i) => {
        if (i < 0) return null;
        const tone = CARD_TONES[i % CARD_TONES.length];
        return (
          <div key={i} style={{ position: "absolute", left: 0, right: 0, height: cardH, top: i * cardH - y, background: tone }}>
            {/* soft "subject" shapes */}
            <div style={{ position: "absolute", left: "18%", top: "26%", width: "64%", height: "38%", borderRadius: 40, background: "rgba(255,255,255,0.10)" }} />
            <div style={{ position: "absolute", left: "34%", top: "34%", width: 120, height: 120, borderRadius: "50%", background: "rgba(255,255,255,0.16)" }} />
            {/* action rail */}
            {[0, 1, 2].map((j) => (
              <div key={j} style={{ position: "absolute", right: 22, bottom: 210 - j * 86, width: 54, height: 54, borderRadius: 27, background: "rgba(255,255,255,0.22)" }} />
            ))}
            <div style={{ position: "absolute", left: 26, bottom: 120, width: "52%", height: 18, borderRadius: 9, background: "rgba(255,255,255,0.35)" }} />
            <div style={{ position: "absolute", left: 26, bottom: 88, width: "34%", height: 14, borderRadius: 7, background: "rgba(255,255,255,0.22)" }} />
          </div>
        );
      })}
      {counter ? <ReelCounter {...counter} /> : null}
      <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 140, background: "linear-gradient(180deg, rgba(0,0,0,0.45), transparent)" }} />
    </div>
  );
};

/** "রিলস ৩২/৫০" style chip that counts up to a configurable limit. */
const ReelCounter: React.FC<{ from: number; to: number; at: number; dur: number }> = ({ from, to, at, dur }) => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const v = Math.round(interpolate(f, [at, at + dur], [from, to], { easing: theme.ease.soft, extrapolateLeft: "clamp", extrapolateRight: "clamp" }));
  const p = spring({ frame: f - at + 6, fps, config: theme.spring.snappy });
  const full = v >= to;
  return (
    <div style={{ position: "absolute", top: 70, left: "50%", transform: `translateX(-50%) scale(${p})`, zIndex: 3,
      padding: "12px 26px", borderRadius: 40, background: full ? theme.colors.warn : "rgba(255,255,255,0.92)",
      color: full ? "#fff" : theme.colors.ink, fontFamily: theme.fonts.bn, fontWeight: 700, fontSize: 40,
      fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap", boxShadow: "0 12px 30px -10px rgba(0,0,0,0.5)" }}>
      রিলস {toBn(v)}/{toBn(to)}
    </div>
  );
};

export const toBn = (n: number) => String(n).replace(/\d/g, (d) => "০১২৩৪৫৬৭৮৯"[+d]);

/** A clock that runs fast: time slipping away while scrolling. */
export const FastClock: React.FC<{ startMin: number; endMin: number; dur: number; dark?: boolean }> = ({ startMin, endMin, dur, dark = true }) => {
  const f = useCurrentFrame();
  const m = Math.round(interpolate(f, [0, dur], [startMin, endMin], { easing: theme.ease.in, extrapolateLeft: "clamp", extrapolateRight: "clamp" }));
  const h = Math.floor(m / 60) % 12 || 12;
  const mm = String(m % 60).padStart(2, "0");
  return (
    <div style={{ fontFamily: theme.fonts.latin, fontWeight: 700, fontSize: 64, color: dark ? "#F4F5F7" : theme.colors.ink,
      fontVariantNumeric: "tabular-nums", letterSpacing: "-0.02em" }}>
      {toBn(h)}:{toBn(Number(mm)).padStart(2, "০")} <span style={{ fontSize: 30, opacity: 0.6 }}>রাত</span>
    </div>
  );
};

/** Whiteboard with handwritten-looking lines that draw in. */
export const Whiteboard: React.FC<{ at?: number }> = ({ at = 0 }) => {
  const f = useCurrentFrame() - at;
  const lines = [
    "M60 90 C 140 70, 220 110, 300 85", "M60 150 L 420 150", "M60 210 C 120 190, 180 230, 260 205 S 380 190, 440 210",
    "M80 290 L 180 290 L 230 340 L 330 250", "M360 300 m -40 0 a 40 40 0 1 0 80 0 a 40 40 0 1 0 -80 0",
  ];
  return (
    <div style={{ width: 860, height: 520, background: "#F7F8F6", borderRadius: 18, border: "16px solid #C9CDD3",
      boxShadow: "0 40px 80px -30px rgba(0,0,0,0.6)", position: "relative" }}>
      <svg viewBox="0 0 520 380" width="100%" height="100%">
        {lines.map((d, i) => {
          const p = interpolate(f, [i * 6, i * 6 + 22], [0, 1], { easing: theme.ease.out, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
          return <path key={i} d={d} stroke={i === 3 ? "#1F6FB2" : "#2B2F36"} strokeWidth={5} fill="none" strokeLinecap="round"
            pathLength={1} strokeDasharray={1} strokeDashoffset={1 - p} />;
        })}
      </svg>
      <div style={{ position: "absolute", bottom: -36, right: 60, width: 120, height: 18, borderRadius: 6, background: "#AEB3BA" }} />
    </div>
  );
};

/** Gallery grid: dozens of near-identical photos; a search lens wanders and finds nothing. */
export const GalleryGrid: React.FC<{ at?: number }> = ({ at = 0 }) => {
  const f = useCurrentFrame() - at;
  const { fps } = useVideoConfig();
  const scroll = interpolate(f, [0, 110], [0, 900], { easing: theme.ease.inOut, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const tones = ["#D9DCE1", "#E6E2DA", "#CFD8D4", "#DCD6E3", "#E2E2E2", "#D3DAE6"];
  const cells = Array.from({ length: 60 });
  const lensX = 140 + Math.sin(f / 9) * 120;
  const lensY = 380 + Math.cos(f / 13) * 260;
  const lp = spring({ frame: f - 20, fps, config: theme.spring.smooth });
  return (
    <div style={{ position: "absolute", inset: 0, background: "#fff" }}>
      <div style={{ position: "absolute", left: 6, right: 6, top: 90 - scroll, display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 6 }}>
        {cells.map((_, i) => (
          <div key={i} style={{ aspectRatio: "1", background: tones[i % tones.length], position: "relative" }}>
            {/* each "photo" is a tiny whiteboard or a random snap */}
            {i % 3 === 0 ? <div style={{ position: "absolute", inset: "22% 14%", border: "3px solid #9AA1AA", borderRadius: 4 }} /> : null}
          </div>
        ))}
      </div>
      <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 90, background: "#fff", borderBottom: `1px solid ${theme.colors.line}`,
        display: "flex", alignItems: "center", paddingLeft: 26, fontFamily: theme.fonts.bn, fontWeight: 600, fontSize: 30, color: theme.colors.ink }}>
        গ্যালারি · ২,৩৪১টি ছবি
      </div>
      <div style={{ position: "absolute", left: lensX, top: lensY, width: 120, height: 120, borderRadius: "50%", border: "8px solid #16181D",
        background: "rgba(255,255,255,0.25)", opacity: lp, transform: `scale(${lp})` }}>
        <div style={{ position: "absolute", width: 60, height: 12, background: "#16181D", borderRadius: 6, right: -48, bottom: -14, transform: "rotate(45deg)" }} />
      </div>
    </div>
  );
};

/** Receipts and small amounts drifting in — the month's spending you can't account for. */
export const Receipts: React.FC<{ at?: number }> = ({ at = 0 }) => {
  const f = useCurrentFrame() - at;
  const { fps } = useVideoConfig();
  const items = [
    { t: "চা-নাস্তা", a: "১২০", x: -250, y: -330, r: -8 },
    { t: "রিকশা", a: "৮০", x: 230, y: -250, r: 7 },
    { t: "মোবাইল রিচার্জ", a: "২০০", x: -220, y: -60, r: 5 },
    { t: "বই", a: "৪৫০", x: 240, y: 30, r: -6 },
    { t: "খাবার", a: "৩৫০", x: -40, y: 210, r: 3 },
    { t: "?", a: "???", x: 210, y: 300, r: -4 },
  ];
  return (
    <div style={{ position: "relative", width: 1, height: 1 }}>
      {items.map((it, i) => {
        const p = spring({ frame: f - i * 5, fps, config: theme.spring.smooth });
        const drift = Math.sin((f + i * 20) / 30) * 8;
        return (
          <div key={i} style={{ position: "absolute", left: it.x - 170, top: it.y - 60 + drift, width: 340, padding: "22px 26px", borderRadius: 14,
            background: "#FFFDF8", boxShadow: "0 24px 50px -20px rgba(0,0,0,0.6)", fontFamily: theme.fonts.bn,
            opacity: p, transform: `translateY(${(1 - p) * 80}px) rotate(${it.r * p}deg) scale(${interpolate(p, [0, 1], [0.9, 1])})`,
            display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
            <span style={{ fontSize: 34, color: theme.colors.inkDim, fontWeight: 500 }}>{it.t}</span>
            <span style={{ fontSize: 44, fontWeight: 700, color: theme.colors.ink }}>৳{it.a}</span>
          </div>
        );
      })}
    </div>
  );
};

/** Camera flash + shutter frame over the whiteboard. */
export const Shutter: React.FC<{ at: number }> = ({ at }) => {
  const f = useCurrentFrame() - at;
  const flash = interpolate(f, [0, 2, 10], [0, 0.85, 0], { easing: theme.ease.out, extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return <div style={{ position: "absolute", inset: 0, background: "#fff", opacity: flash, pointerEvents: "none" }} />;
};
