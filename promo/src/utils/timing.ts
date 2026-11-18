// All timing comes from the real voice-over (docs/audio, 75.76 s). Seconds → frames at 30 fps.
// Segment text is the narration as spoken (checked against the audio); times from pauses + Whisper.
export const FPS = 30;
export const s = (sec: number) => Math.round(sec * FPS);

export type Seg = { from: number; to: number; text: string };
export const SEGMENTS: Seg[] = [
  { from: 0.0, to: 5.0, text: "তুমি কি কখনো ফেসবুকে রিলস দেখতে গিয়ে নিজের অজান্তেই এক ঘণ্টা নষ্ট করে ফেলেছ?" },
  { from: 5.4, to: 7.1, text: "ক্লাসে বোর্ডের ছবি তুলেছ," },
  { from: 7.3, to: 10.9, text: "কিন্তু পরীক্ষার আগের রাতে গ্যালারিতে এসে সেই ছবিটাই খুঁজে পাচ্ছ না?" },
  { from: 11.3, to: 14.1, text: "মাস শেষে কোথায় কত টাকা খরচ করলে," },
  { from: 14.3, to: 15.5, text: "সেটা কি মনে থাকে না?" },
  { from: 16.0, to: 20.4, text: "এই ছোট ছোট সমস্যাগুলোর সমাধান এক জায়গায় আনতেই তৈরি করেছি দিনরেখা।" },
  { from: 20.8, to: 23.3, text: "এখানে আছে ডেইলি রুটিন, হ্যাবিট ট্র্যাকার," },
  { from: 23.5, to: 26.4, text: "প্রেয়ার রিমাইন্ডার, ক্লাস শিডিউল এবং ক্যালেন্ডার।" },
  { from: 26.8, to: 28.9, text: "পড়াশোনার জন্য রয়েছে ফোকাস টাইমার।" },
  { from: 29.2, to: 31.4, text: "আর ক্লাসের বোর্ড বা স্লাইডের ছবি" },
  { from: 31.6, to: 34.4, text: "সরাসরি কোর্স অনুযায়ী সেভ করে রাখার সুবিধা।" },
  { from: 34.9, to: 38.4, text: "পরীক্ষার আগে নির্দিষ্ট কোর্সের সব নোট এক জায়গায় পেয়ে যাবে।" },
  { from: 38.9, to: 41.4, text: "ওয়ালেট দিয়ে ট্র্যাক করতে পারবে আয়, খরচ, বাজেট" },
  { from: 41.4, to: 44.1, text: "এবং আলাদা অ্যাকাউন্টের হিসাব।" },
  { from: 44.5, to: 47.0, text: "ফেসবুক রিলস, ইনস্টাগ্রাম রিলস কিংবা ইউটিউব শর্টসের জন্য" },
  { from: 47.0, to: 50.0, text: "সেট করতে পারবে দৈনিক লিমিট।" },
  { from: 50.2, to: 52.6, text: "ধরো, দিনে মাত্র ৫০টি রিলস।" },
  { from: 52.9, to: 54.4, text: "লিমিট শেষ হলে অ্যাপ ব্লক।" },
  { from: 54.6, to: 57.1, text: "সঙ্গে হার্ড মোড এবং ইমার্জেন্সি আনলক।" },
  { from: 57.5, to: 60.6, text: "এছাড়াও রয়েছে ৩০ দিনের চ্যালেঞ্জ, প্রোগ্রেস ট্র্যাকিং," },
  { from: 60.6, to: 63.7, text: "স্লিপ-মুড জার্নাল এবং স্ক্রিন টাইম অ্যানালিটিক্স।" },
  { from: 64.1, to: 65.2, text: "আর সবচেয়ে ভালো ব্যাপার," },
  { from: 65.6, to: 67.6, text: "সব ডেটা থাকবে তোমার নিজের ফোনে।" },
  { from: 68.0, to: 70.7, text: "কোনো অ্যাকাউন্ট নেই, অ্যাডস নেই, ট্র্যাকিং নেই।" },
  { from: 71.0, to: 71.8, text: "দিনরেখা।" },
  { from: 72.1, to: 73.5, text: "শুধু সময়ের হিসাব নয়," },
  { from: 73.7, to: 75.7, text: "নিজের জীবনটা গুছিয়ে নেওয়ার শুরু।" },
];

// Words to highlight in subtitles (kinetic emphasis).
export const HIGHLIGHT = ["৫০টি", "রিলস", "ফোকাস", "টাইমার", "কোর্স", "ওয়ালেট", "৩০", "দিনের", "ফোনে", "দিনরেখা"];

// Scene map (seconds). Each scene starts a little before its first word so the picture leads the voice.
export const SCENES = {
  hookReels: [0, 5.3],
  hookClass: [5.3, 11.2],
  hookMoney: [11.2, 15.9],
  logo: [15.9, 20.2],
  today: [20.2, 26.6],
  study: [26.6, 38.7],
  wallet: [38.7, 44.4],
  blocker: [44.4, 57.3],
  progress: [57.3, 63.9],
  privacy: [63.9, 70.9],
  outro: [70.9, 78.6],
} as const;

export const TOTAL_SEC = 78.6;
export const OVERLAP = 8; // frames of cross-fade between scenes
