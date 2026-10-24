#!/usr/bin/env python3
"""Robi gotowego shorta 9:16 z fragmentu dłuższego filmu.

Kroki: pobranie (link lub plik) -> wycięcie fragmentu -> pion 1080x1920
-> automatyczne napisy (Whisper) wypalone w obrazie -> gotowy MP4.

Używaj tylko materiałów, do których masz prawo (kampanie clippingowe,
zgoda twórcy, własne nagrania).

Przykłady:
  python3 shorts/make_short.py "https://youtu.be/XXXX" --start 12:30 --end 13:05 -o out/klip1.mp4
  python3 shorts/make_short.py film.mp4 --start 95 --end 130 --mode crop --hook "ON NAPRAWDĘ TO POWIEDZIAŁ?"
  python3 shorts/make_short.py film.mp4 --transcribe-only   # transkrypcja z czasami do wyboru momentów
"""
import argparse
import os
import subprocess
import sys
import tempfile

W, H = 1080, 1920
FONT = "Inter Display"


def ts(value):
    """'1:02:03.5', '12:30' albo '95' -> sekundy."""
    sec = 0.0
    for part in str(value).split(":"):
        sec = sec * 60 + float(part)
    return sec


def run(cmd):
    subprocess.run(cmd, check=True)


def fetch(source, workdir):
    if os.path.exists(source):
        return source
    out = os.path.join(workdir, "source.%(ext)s")
    run(["yt-dlp", "-q", "--no-playlist", "-f", "bv*[height<=1080]+ba/b[height<=1080]/b",
         "--merge-output-format", "mp4", "-o", out, source])
    return os.path.join(workdir, "source.mp4")


def transcribe(path, language):
    import numpy as np
    from faster_whisper import WhisperModel
    # dekodujemy audio ffmpegiem, bo wbudowany dekoder faster-whisper bywa niezgodny z wersją PyAV
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", "16000", "-"],
                         check=True, capture_output=True).stdout
    audio = np.frombuffer(raw, dtype=np.float32)
    model = WhisperModel("small", device="cpu", compute_type="int8")
    segments, _ = model.transcribe(audio, language=language, word_timestamps=True, vad_filter=True)
    return list(segments)


def ass_time(t):
    t = max(t, 0)
    return f"{int(t // 3600)}:{int(t % 3600 // 60):02d}:{t % 60:05.2f}"


def build_ass(segments, hook, duration, words_per_line=3):
    lines = [
        "[Script Info]", "ScriptType: v4.00+", f"PlayResX: {W}", f"PlayResY: {H}", "",
        "[V4+ Styles]",
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, "
        "Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
        "Alignment, MarginL, MarginR, MarginV, Encoding",
        f"Style: Sub,{FONT},88,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,7,3,2,60,60,560,1",
        f"Style: Hook,{FONT},74,&H00000000,&H00000000,&H00FFFFFF,&H00FFFFFF,-1,0,0,0,100,100,0,0,3,18,0,8,70,70,230,1",
        "", "[Events]",
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
    ]
    if hook:
        lines.append(f"Dialogue: 1,{ass_time(0)},{ass_time(min(3.5, duration))},Hook,,0,0,0,,{hook.upper()}")
    words = [w for s in segments for w in (s.words or [])]
    for i in range(0, len(words), words_per_line):
        chunk = words[i:i + words_per_line]
        start = chunk[0].start
        end = words[i + words_per_line].start if i + words_per_line < len(words) else chunk[-1].end + 0.3
        end = min(end, chunk[-1].end + 0.6)
        text = " ".join(w.word.strip() for w in chunk).upper().replace("{", "").replace("}", "")
        lines.append(f"Dialogue: 0,{ass_time(start)},{ass_time(end)},Sub,,0,0,0,,{text}")
    return "\n".join(lines) + "\n"


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("source", help="link do filmu albo ścieżka do pliku")
    p.add_argument("--start", default="0", help="początek fragmentu, np. 12:30")
    p.add_argument("--end", help="koniec fragmentu, np. 13:05 (max ~60 s)")
    p.add_argument("--mode", choices=["blur", "crop"], default="blur",
                   help="blur: cały kadr na rozmytym tle; crop: wycięty środek na pełny ekran (podcasty)")
    p.add_argument("--hook", default="", help="tekst na górze przez pierwsze 3,5 s")
    p.add_argument("--lang", default=None, help="język napisów, np. pl, en (domyślnie wykrywa sam)")
    p.add_argument("--no-subs", action="store_true")
    p.add_argument("--transcribe-only", action="store_true",
                   help="wypisz transkrypcję całego filmu z czasami i zakończ")
    p.add_argument("-o", "--output", default="out/short.mp4")
    a = p.parse_args()

    with tempfile.TemporaryDirectory() as wd:
        src = fetch(a.source, wd)
        if a.transcribe_only:
            for s in transcribe(src, a.lang):
                print(f"[{int(s.start // 60)}:{s.start % 60:04.1f} - {int(s.end // 60)}:{s.end % 60:04.1f}] {s.text.strip()}")
            return

        start = ts(a.start)
        if a.end is None:
            sys.exit("Podaj --end")
        duration = ts(a.end) - start

        clip = os.path.join(wd, "clip.mp4")
        run(["ffmpeg", "-loglevel", "error", "-y", "-ss", str(start), "-i", src, "-t", str(duration),
             "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-c:a", "aac", clip])

        if a.mode == "blur":
            vf = (f"[0:v]split[a][b];"
                  f"[a]scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H},boxblur=25:2,eq=brightness=-0.15[bg];"
                  f"[b]scale={W}:-2[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2")
        else:
            vf = f"[0:v]scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H}"

        if not a.no_subs or a.hook:
            segments = [] if a.no_subs else transcribe(clip, a.lang)
            ass = os.path.join(wd, "subs.ass")
            with open(ass, "w", encoding="utf-8") as f:
                f.write(build_ass(segments, a.hook, duration))
            vf += f",ass={ass}"

        os.makedirs(os.path.dirname(os.path.abspath(a.output)), exist_ok=True)
        run(["ffmpeg", "-loglevel", "error", "-y", "-i", clip, "-filter_complex", vf + "[v]",
             "-map", "[v]", "-map", "0:a?", "-c:v", "libx264", "-preset", "medium", "-crf", "20",
             "-r", "30", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", a.output])
        print(f"Gotowe: {a.output}")


if __name__ == "__main__":
    main()
