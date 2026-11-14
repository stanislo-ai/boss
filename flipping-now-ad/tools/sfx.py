# usage: sfx.py events.json out.wav   events: [[t, type, gain], ...]
import json, sys, numpy as np, soundfile as sf
SR = 44100; DUR = 15.2
ev = json.load(open(sys.argv[1])); out = sys.argv[2]
y = np.zeros((int(SR*DUR), 2)); rng = np.random.default_rng(5)
def onepole(x, a):  # a can be array (time-varying)
    o = np.zeros_like(x); s = 0.0; a = np.broadcast_to(a, x.shape)
    for i in range(len(x)): s += a[i]*(x[i]-s); o[i] = s
    return o
def add(sig, t, pan=0.5):
    s = int(t*SR); e = min(len(y), s+len(sig))
    if s < 0: sig = sig[-s:]; s = 0
    if e <= s: return
    y[s:e,0] += sig[:e-s]*(1-pan)*2*0.5; y[s:e,1] += sig[:e-s]*pan*2*0.5
def whoosh(dur=0.55, peak=0.6):
    n = int(dur*SR); tt = np.linspace(0,1,n); x = rng.standard_normal(n)
    a = 0.02 + 0.35*np.sin(np.pi*np.clip(tt/peak*0.5,0,1))**2
    lp = onepole(x, a); hp = lp - onepole(lp, 0.01)
    env = np.where(tt<peak, (tt/peak)**2, np.exp(-(tt-peak)/(1-peak)*4))
    return hp*env
def tap():
    n = int(0.09*SR); tt = np.arange(n)/SR
    return (np.sin(2*np.pi*90*tt)*np.exp(-tt/0.025)*1.2 + onepole(rng.standard_normal(n), 0.4)*np.exp(-tt/0.008))
def scratch(dur=0.13):
    n = int(dur*SR); tt = np.arange(n)/SR; x = rng.standard_normal(n)
    hp = x - onepole(x, 0.25); am = 0.6 + 0.4*np.sin(2*np.pi*38*tt)
    return hp*am*np.sin(np.pi*tt/dur)**0.7*0.5
def impact():
    n = int(0.6*SR); tt = np.arange(n)/SR
    return np.sin(2*np.pi*(55+40*np.exp(-tt/0.05))*tt)*np.exp(-tt/0.18)*1.4 + onepole(rng.standard_normal(n),0.2)*np.exp(-tt/0.05)*0.5
for t, typ, g in ev:
    if typ == 'whoosh': s = whoosh(); add(s*g, t-0.55*0.6, rng.uniform(0.35,0.65))
    elif typ == 'whooshL': s = whoosh(1.0, 0.75); add(s*g, t-0.75, 0.5)
    elif typ == 'tap': add(tap()*g, t, rng.uniform(0.4,0.6))
    elif typ == 'scratch': add(scratch()*g, t-0.04, rng.uniform(0.4,0.6))
    elif typ == 'impact': add(impact()*g, t, 0.5)
y /= max(1e-9, np.max(np.abs(y))) / 0.9
sf.write(out, y.astype(np.float32), SR); print('ok')
