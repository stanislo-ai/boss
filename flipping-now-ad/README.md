# Flipping Now — reklamy TikTok (15 s, 9:16)

Trzy wersje reklamy dostawcy **Flipping Now** (@flippingnow999), renderowane w three.js (WebGL) klatka po klatce i składane w MP4 z muzyką.

| Plik | Koncept |
|---|---|
| `out/flipping_now_v1_cennik.mp4` | Kartka-cennik w świetle z okna → „flip” kartki → lot przez chmury („TANIEJ”) → link w bio |
| `out/flipping_now_v2_tablica.mp4` | Tablica odlotów z klapkami (split-flap) — ceny przeskakują na bicie, logo z klapek, link w bio |
| `out/flipping_now_v3_studio.mp4` | Studio: chromowane logo F, produkty 3D na obrotowych podestach, duże napisy TikTok |

Format: 1080×1920, 30 fps, H.264 + AAC, 15,2 s. Muzyka jest przycięta tak, że „drop” wypada w 1,96 s; wszystkie animacje są na siatce bitu (123 BPM).

## Render
```
npm install
node tools/render.cjs v1.html <katalog_klatek> 30 15.2
./tools/encode.sh <katalog_klatek> out/plik.mp4 30 2.08 15.2 [sfx.wav] [gain_dB]
```
Podgląd na żywo: serwer statyczny w tym folderze i `v1.html?t=5` (stała klatka) lub `v1.html` (pętla).

Kolor: V1 i V2 dostają lekką korektę kontrastu przy kodowaniu (zmienna `VF` w `encode.sh`, np.
`VF="curves=master='0/0 0.25/0.2 0.6/0.66 1/1',eq=saturation=1.08" ./tools/encode.sh ...`).
Efekty dźwiękowe: `tools/flap_sfx.py` (klapki V2, czasy eksportowane z `tools/export_flaps.cjs`) i `tools/sfx.py` (V1/V3).

Bezpieczne strefy TikToka (górne ~140 px, dolne ~420 px, prawa kolumna przycisków) zostały sprawdzone na końcowych planszach.

## Zdjęcia
Zdjęcia produktów w V1 pochodzą z Unsplash (licencja Unsplash) — patrz `assets/CREDITS.md`.
Muzyka (plik użytkownika) nie jest dołączona do repozytorium.
