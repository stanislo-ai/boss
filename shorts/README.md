# Shorts – montaż klipów

`make_short.py` robi z fragmentu dłuższego filmu gotowego shorta 9:16 (1080x1920)
z dużymi automatycznymi napisami i opcjonalnym tekstem-hookiem na górze.

```bash
pip install -r shorts/requirements.txt   # potrzebny też ffmpeg
python3 shorts/make_short.py film.mp4 --transcribe-only            # znajdź najlepsze momenty
python3 shorts/make_short.py film.mp4 --start 12:30 --end 13:05 --hook "TEGO SIĘ NIE SPODZIEWAŁ" -o out/klip1.mp4
```

- `--mode blur` (domyślnie): cały kadr na rozmytym tle, dobre do gier i filmów.
- `--mode crop`: środek kadru na cały ekran, dobre do podcastów i gadających głów.

Montuj tylko materiały, do których masz prawo: kampanie clippingowe, zgodę twórcy albo własne nagrania.
