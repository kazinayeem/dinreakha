#!/bin/sh
# Rebuilds public/ from the project's real assets (docs/) and the synthesised audio kit.
set -e
cd "$(dirname "$0")/.."
mkdir -p public/shots public/brand public/audio
cp ../docs/screenshots/*.png public/shots/
cp ../docs/dinrekha-logo.svg public/brand/logo.svg
ffmpeg -hide_banner -loglevel error -y -i ../docs/audio/cartesia_2026-11-18T16_25_18Z_Arindam_sonic-3.6_bn-BD_volume-1.2_sr-24000.wav \
  -af "highpass=f=70,loudnorm=I=-16:TP=-1.5:LRA=7" -ar 48000 -ac 1 public/audio/voiceover.wav
python3 scripts/synth_audio.py
