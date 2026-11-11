# Flipping_now – TikTok promo (Remotion)

14-second, 1080×1920 / 30 fps Apple-style motion-design promo. Cuts are locked to the beat of "Cold" (~143.5 BPM).

- `render/flipping-now-tiktok-music.mp4` – with music + SFX
- `render/flipping-now-tiktok-nomusic.mp4` – SFX only (add "Cold" from TikTok's sound library)

## Edit / re-render

```bash
npm i
npm run dev                                   # Remotion Studio
npx remotion render FlippingPromo out/promo.mp4 --crf=14 --audio-bitrate=320k
npx remotion render FlippingPromoNoMusic out/promo-nomusic.mp4 --crf=14 --audio-bitrate=320k
```

Then limit the audio to -1 dBTP (the song is mastered very loud):

```bash
ffmpeg -i out/promo.mp4 -c:v copy -af "alimiter=limit=0.85:attack=3:release=60:level=false" -c:a aac -b:a 320k final.mp4
```

## Structure

- `src/timing.ts` – beat grid and scene boundaries
- `src/scenes/*` – Hook, Perfumy, AirPods, Montage (cut on every beat), Bento (live counters), Outro (logo + CTA)
- `src/components/Glass.tsx` – liquid-glass surface (backdrop blur, rim light, sheen, SVG refraction)
- `src/Soundtrack.tsx` – music + frame-accurate SFX
- `public/sfx` – SFX synthesized for this project (no third-party samples)
- `public/clips` – the source footage, graded, denoised and upscaled to 1080p
- `scripts/stills.mjs` – renders preview frames
