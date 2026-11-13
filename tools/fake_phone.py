#!/usr/bin/env python3
"""Test double for the phone app: speaks the Boss Drive wire protocol to a head unit.
Usage: fake_phone.py HOST [PORT] [scenario...]   scenarios: music contacts msg call ring transfer"""
import json, socket, struct, sys, time, threading, io, os

def frame(t, body=b"", **kw):
    kw["t"] = t
    js = json.dumps(kw).encode()
    return struct.pack(">ii", len(js), len(body)) + js + body

def read_frames(sock, log):
    buf = b""
    while True:
        try:
            d = sock.recv(65536)
        except OSError:
            return
        if not d:
            return
        buf += d
        while len(buf) >= 8:
            jl, bl = struct.unpack(">ii", buf[:8])
            if len(buf) < 8 + jl + bl: break
            js = json.loads(buf[8:8 + jl]); buf = buf[8 + jl + bl:]
            if js.get("t") != "ping": log.append(js); print("<-", json.dumps(js, ensure_ascii=False)[:160], flush=True)

def art_jpeg(c1, c2):
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        return b""
    im = Image.new("RGB", (400, 400), c1); d = ImageDraw.Draw(im)
    for i in range(0, 400, 4):
        d.ellipse((200 - i // 2, 200 - i // 2, 200 + i // 2, 200 + i // 2), outline=tuple(int(c1[k] + (c2[k] - c1[k]) * i / 400) for k in range(3)), width=4)
    o = io.BytesIO(); im.save(o, "JPEG", quality=85); return o.getvalue()

def main():
    host = sys.argv[1]; port = int(sys.argv[2]) if len(sys.argv) > 2 and sys.argv[2].isdigit() else 47872
    sc = [a for a in sys.argv[2:] if not a.isdigit()] or ["music", "contacts", "msg"]
    s = socket.create_connection((host, port), timeout=10); s.settimeout(None)
    log = []
    threading.Thread(target=read_frames, args=(s, log), daemon=True).start()
    s.sendall(frame("hello", id="fakephone01", name="Pixel 9 Pro", ver=1)); time.sleep(1.0)
    s.sendall(frame("status", bat=78, chg=False, sig=3, net="5G", carrier="Play"))
    s.sendall(frame("weather", temp=7, code=2, hi=9, lo=3, place="Warszawa", day=True, lat=52.23, lon=21.01))
    if "music" in sc:
        a = art_jpeg((255, 94, 98), (60, 20, 120))
        s.sendall(frame("np", title="Blinding Lights", artist="The Weeknd", album="After Hours", dur=200040, pos=63000, playing=True,
                        speed=1.0, pkg="com.spotify.music", app="Spotify", art="k1" if a else "", canSeek=True, canSkip=True))
        if a: s.sendall(frame("art", a, key="k1"))
        s.sendall(frame("srcs", items=[{"pkg": "com.spotify.music", "name": "Spotify", "active": True, "playing": True},
                                       {"pkg": "com.google.android.apps.youtube.music", "name": "YouTube Music", "active": False, "playing": False}]))
        s.sendall(frame("queue", items=[{"id": i, "title": t, "sub": "The Weeknd"} for i, t in enumerate(["Blinding Lights", "Save Your Tears", "In Your Eyes", "Starboy", "Die For You"])], active=0))
    if "contacts" in sc:
        fav = [{"n": n, "num": "+48 600 000 00%d" % i, "lbl": "komórka"} for i, n in enumerate(["Mama", "Kasia Nowak", "Tomek", "Szef", "Ania"])]
        allc = fav + [{"n": n, "num": "+48 500 100 1%02d" % i, "lbl": "komórka"} for i, n in enumerate(["Adam Kowalski", "Bartek", "Celina", "Darek Mechanik", "Ewa", "Filip", "Grzegorz"])]
        s.sendall(frame("contacts", fav=fav, all=allc))
        now = int(time.time() * 1000)
        s.sendall(frame("recents", items=[{"n": "Mama", "num": "+48600000000", "type": 3, "time": now - 600000},
                                          {"n": "Szef", "num": "+48600000003", "type": 2, "time": now - 7200000},
                                          {"n": "", "num": "+48 22 123 45 67", "type": 1, "time": now - 90000000}]))
    if "msg" in sc:
        time.sleep(1)
        s.sendall(frame("msg", key="m1", pkg="com.whatsapp", app="WhatsApp", title="Kasia Nowak", text="Kupisz po drodze chleb i mleko? 🙂",
                        time=int(time.time() * 1000), canReply=True))
    if "call" in sc:
        time.sleep(1); s.sendall(frame("call", state="active", number="+48 600 000 001", name="Kasia Nowak", since=83000))
    if "ring" in sc:
        time.sleep(1); s.sendall(frame("call", state="ringing", number="+48 600 000 001", name="Kasia Nowak", since=0))
    if "transfer" in sc:
        data = os.urandom(3 * 1024 * 1024)
        s.sendall(frame("xOffer", art_jpeg((30, 120, 255), (10, 10, 40)), id="test_song_1", size=len(data), title="Testowa piosenka", artist="Boss Band", album="Demo", dur=180000, ext="mp3", queued=2))
        while not any(m.get("t") == "xAck" for m in log): time.sleep(0.1)
        off = [m for m in log if m.get("t") == "xAck"][-1]["off"]
        t0 = time.time()
        while off < len(data):
            chunk = data[off:off + 65536]; s.sendall(frame("xChunk", chunk, id="test_song_1", off=off)); off += len(chunk); time.sleep(0.05)
        s.sendall(frame("xEnd", id="test_song_1"))
        while not any(m.get("t") in ("xDone", "xFail") for m in log): time.sleep(0.1)
        print("transfer: %.1f MB/s" % (len(data) / 1e6 / (time.time() - t0)))
    hold = float(os.environ.get("HOLD", "600"))
    end = time.time() + hold
    while time.time() < end:
        s.sendall(frame("ping")); time.sleep(3)

main()
