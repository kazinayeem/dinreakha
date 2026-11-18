// Single source of truth: colours, type, easings, springs. Never inline these in components.
import { Easing } from "remotion";
import { loadFont as loadHind } from "@remotion/google-fonts/HindSiliguri";
import { loadFont as loadInter } from "@remotion/google-fonts/Inter";

const hind = loadHind("normal", { weights: ["400", "500", "600", "700"], subsets: ["bengali", "latin"] });
const inter = loadInter("normal", { weights: ["500", "600", "700"], subsets: ["latin"] });

export const theme = {
  colors: {
    bg: "#FBFBF9", // soft off-white
    bgAlt: "#F2F3F0",
    ink: "#16181D", // charcoal type
    inkDim: "#5E6470",
    line: "#E4E6EA",
    // The app's own accents, used sparingly. Mint is the one hero colour.
    hero: "#0F8A68",
    heroSoft: "#DDF3EA",
    indigo: "#1B2463",
    sun: "#FFC27A",
    warn: "#C2410C",
    // Problem scenes are darker, so the clean white product reveal feels like relief.
    night: "#101418",
    nightAlt: "#1A1F26",
  },
  fonts: {
    bn: `${hind.fontFamily}, "Kohinoor Bangla", sans-serif`,
    latin: `${inter.fontFamily}, ${hind.fontFamily}, sans-serif`,
  },
  ease: {
    out: Easing.bezier(0.16, 1, 0.3, 1),
    inOut: Easing.bezier(0.83, 0, 0.17, 1),
    in: Easing.bezier(0.7, 0, 0.84, 0),
    soft: Easing.bezier(0.33, 1, 0.68, 1),
  },
  spring: {
    snappy: { damping: 14, stiffness: 160, mass: 0.6 },
    smooth: { damping: 20, stiffness: 90, mass: 1 },
    bouncy: { damping: 11, stiffness: 170, mass: 0.7 },
    gentle: { damping: 26, stiffness: 70, mass: 1 },
  },
  layout: {
    W: 1080,
    H: 1920,
    headlineTop: 190, // inside the top safe area
    phoneTop: 440,
    subtitleTop: 1455, // above the bottom platform UI
  },
} as const;
