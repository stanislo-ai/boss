import json, sys, numpy as np, soundfile as sf
ts = json.load(open(sys.argv[1])); out = sys.argv[2]
SR = 44100; DUR = 15.2; FD = 0.075
y = np.zeros((int(SR*DUR)+SR, 2))
rng = np.random.default_rng(3)
n = int(0.012*SR); tt = np.arange(n)/SR
for t in ts:
    for k, (off, amp) in enumerate([(FD*0.92, 1.0), (FD*0.1, 0.25)]):  # landing clack + release tick
        s = int((t+off)*SR)
        if s < 0 or s+n >= len(y): continue
        f = rng.uniform(2200, 4200)
        noise = rng.standard_normal(n)
        click = (0.6*noise + np.sin(2*np.pi*f*tt) + 0.5*np.sin(2*np.pi*f*0.47*tt)) * np.exp(-tt/rng.uniform(0.0015, 0.0035))
        a = amp*rng.uniform(0.5, 1.0); pan = rng.uniform(0.25, 0.75)
        y[s:s+n, 0] += click*a*(1-pan); y[s:s+n, 1] += click*a*pan
# simple one-pole highpass to remove rumble
y = y[:int(SR*DUR)]
y -= np.convolve(y[:,0], np.ones(64)/64, 'same')[:,None]*0  # keep simple
peak = np.max(np.abs(y)); y = y/peak*0.9
sf.write(out, y.astype(np.float32), SR)
print('ok', peak)
