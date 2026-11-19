/* Gry Originals #2: Plinko, Crash, Keno, Wheel, Towers, Chicken */
(() => {
'use strict';
const C = Casino, { $, $$, h, GEM, fmt, fmtM, r2, fl2, sfx, sleep, rint, rnd, shuffle } = C;

/* ================= PLINKO ================= */
const PT = {
  8: { low: [5.6, 2.1, 1.1, 1, .5, 1, 1.1, 2.1, 5.6], med: [13, 3, 1.3, .7, .4, .7, 1.3, 3, 13], high: [29, 4, 1.5, .3, .2, .3, 1.5, 4, 29] },
  12: { low: [10, 3, 1.6, 1.4, 1.1, 1, .5, 1, 1.1, 1.4, 1.6, 3, 10], med: [33, 11, 4, 2, 1.1, .6, .3, .6, 1.1, 2, 4, 11, 33], high: [170, 24, 8.1, 2, .7, .2, .2, .2, .7, 2, 8.1, 24, 170] },
  16: { low: [16, 9, 2, 1.4, 1.4, 1.2, 1.1, 1, .5, 1, 1.1, 1.2, 1.4, 1.4, 2, 9, 16], med: [110, 41, 10, 5, 3, 1.5, 1, .5, .3, .5, 1, 1.5, 3, 5, 10, 41, 110], high: [1000, 130, 26, 9, 4, 2, .2, .2, .2, .2, .2, 2, 4, 9, 26, 130, 1000] }
};
const binColor = m => m >= 10 ? '#ff4d6d' : m >= 3 ? '#ff8a3d' : m >= 1.2 ? '#ffc83d' : m >= 1 ? '#c9b458' : '#7a6bb8';
const Plinko = {
  id: 'plinko', name: 'Plinko', cat: 'originals', provider: 'GEMBET', art: ['🔮', '⚪', '✨'], c: ['#a24bff', '#2a0f5c'], hot: true, rtp: '97–99%',
  mount(root) {
    const ui = C.shell(Plinko, root, {
      controls: `<div class="field"><label><span>Ryzyko</span></label><div id="rk"></div></div>
        <div class="field"><label><span>Rzędy</span></label><div id="rw"></div></div>
        <button class="btn go" id="go">Wrzuć kulkę</button>`,
      stage: `<div class="plwrap"><canvas id="cv" width="640" height="560"></canvas></div>`
    });
    let risk = 'med', rows = 16, balls = [], raf = 0, flash = {}, alive = true;
    $('#rk', root).replaceWith(C.seg([['low', 'Niskie'], ['med', 'Średnie'], ['high', 'Wysokie']], risk, v => { risk = v; draw(); }));
    $('#rw', root).replaceWith(C.seg([['8', '8'], ['12', '12'], ['16', '16']], rows, v => {
      if (balls.length) { C.toast('Poczekaj aż kulki spadną', 'err'); $$('#rw button', root).forEach(b => b.classList.toggle('on', b.dataset.v == rows)); return; }
      rows = +v; draw();
    }));
    const cv = $('#cv', root), ctx = cv.getContext('2d'), W = 640, H = 560, dpr = Math.min(2, window.devicePixelRatio || 1);
    cv.width = W * dpr; cv.height = H * dpr; ctx.scale(dpr, dpr);
    const geo = R => { const sx = (W - 40) / (R + 2), top = 40, sy = (H - 110) / (R + 1); return { sx, top, sy, cx: W / 2 }; };
    function draw() {
      const R = rows, g = geo(R), tbl = PT[R][risk];
      ctx.clearRect(0, 0, W, H);
      ctx.fillStyle = '#d9d3f5';
      for (let k = 0; k < R; k++) for (let j = 0; j < k + 3; j++) {
        ctx.beginPath(); ctx.arc(g.cx + (j - (k + 2) / 2) * g.sx, g.top + k * g.sy, 3.6, 0, 7); ctx.fill();
      }
      const by = g.top + R * g.sy + 4;
      tbl.forEach((m, j) => {
        const f = flash[j] ? Math.max(0, 1 - (performance.now() - flash[j]) / 350) : 0, w = g.sx * .86 + f * 4, x = g.cx + (j - R / 2) * g.sx - w / 2, y = by + f * 8;
        ctx.fillStyle = binColor(m); ctx.globalAlpha = .92 - (f ? 0 : .1);
        ctx.beginPath(); ctx.roundRect(x, y, w, 30, 6); ctx.fill(); ctx.globalAlpha = 1;
        ctx.fillStyle = m >= 3 ? '#fff' : '#201533'; ctx.font = `800 ${R > 12 ? 10 : 12}px Inter,sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText((m >= 100 ? Math.round(m) : m) + '×', x + w / 2, y + 16);
      });
      balls.forEach(b => {
        if (!b.pos) return;
        ctx.beginPath(); ctx.arc(b.pos.x, b.pos.y, 7.5, 0, 7);
        const gr = ctx.createRadialGradient(b.pos.x - 2, b.pos.y - 2, 1, b.pos.x, b.pos.y, 8); gr.addColorStop(0, '#fff'); gr.addColorStop(1, '#ff4d9d');
        ctx.fillStyle = gr; ctx.shadowColor = '#ff4d9d'; ctx.shadowBlur = 12; ctx.fill(); ctx.shadowBlur = 0;
      });
    }
    function frame(now) {
      raf = 0; const g = geo(rows);
      balls = balls.filter(b => {
        const t = (now - b.t0) / b.seg, i = Math.floor(t);
        if (t < 0) return true;
        if (i >= b.pts.length - 1) { land(b); return false; }
        const f = t - i, p0 = b.pts[i], p1 = b.pts[i + 1];
        const hop = Math.sin(f * Math.PI) * 5;
        b.pos = { x: p0.x + (p1.x - p0.x) * f, y: p0.y + (p1.y - p0.y) * (f * f) - hop };
        return true;
      });
      draw();
      if (balls.length || Object.values(flash).some(t => now - t < 350)) raf = requestAnimationFrame(frame);
    }
    function land(b) {
      flash[b.bin] = performance.now(); const pay = fl2(b.bet * b.mult);
      sfx.tick(); C.settle(Plinko, b.bet, pay); if (b.mult >= 2) C.winPop(ui.stage, b.mult, pay);
    }
    function drop() {
      const bet = ui.bet.get(); if (!C.take(bet)) return false;
      const R = rows, g = geo(R); let off = 0, rights = 0; const pts = [{ x: g.cx, y: g.top - g.sy * .9 }];
      for (let k = 0; k <= R; k++) {
        pts.push({ x: g.cx + off * g.sx / 2, y: g.top + k * g.sy + (k === R ? 8 : 0) });
        if (k < R) { const d = rnd() < .5 ? 1 : -1; off += d; if (d === 1) rights++; }
      }
      balls.push({ pts, bin: rights, mult: PT[R][risk][rights], bet, t0: performance.now(), seg: 115, pos: null });
      if (!raf) raf = requestAnimationFrame(frame);
      return true;
    }
    $('#go', root).onclick = drop;
    C.auto(ui.ctrl, async () => drop(), { gap: 260, n: 20 });
    C.onLeave(() => { alive = false; cancelAnimationFrame(raf); balls.forEach(land); balls = []; });
    draw();
  }
};

/* ================= CRASH ================= */
const Crash = {
  id: 'crash', name: 'Crash', cat: 'originals', provider: 'GEMBET', art: ['📈', '🚀', '💥'], c: ['#ff3d6e', '#4a0a24'], hot: true,
  mount(root) {
    const ui = C.shell(Crash, root, {
      controls: `<div class="field"><label><span>Auto-wypłata przy</span></label><div class="amt plain"><input type="number" id="ac" value="2.00" min="1.01" step="0.01"><span class="suf">×</span></div></div>
        <button class="btn go" id="go">Zakład</button><div class="msg dim" id="info" style="text-align:left;font-size:12px">Wypłać zanim rakieta eksploduje!</div>`,
      stage: `<canvas id="cv" width="760" height="440" style="width:100%;border-radius:12px;background:#0d0a18"></canvas>`
    });
    const cv = $('#cv', root), ctx = cv.getContext('2d'), W = 760, H = 440, dpr = Math.min(2, devicePixelRatio || 1);
    cv.width = W * dpr; cv.height = H * dpr; ctx.scale(dpr, dpr);
    const go = $('#go', root), ac = $('#ac', root), addPill = C.pills(ui.stage);
    let state = 'idle', t0 = 0, crashAt = 1, bet = 0, raf = 0, cur = 1, cashedAt = 0, autoAt = 0, lastCrash = null, alive = true;
    const K = .085;
    function render(m, t, crashed) {
      ctx.clearRect(0, 0, W, H);
      const tmax = Math.max(6, t * 1.15), mmax = Math.max(2, m * 1.2), px = tt => 50 + tt / tmax * (W - 80), py = mm => H - 40 - (mm - 1) / (mmax - 1) * (H - 90);
      ctx.strokeStyle = 'rgba(255,255,255,.07)'; ctx.fillStyle = '#6f6a8c'; ctx.font = '600 11px Inter'; ctx.lineWidth = 1;
      for (let i = 0; i <= 4; i++) { const mm = 1 + (mmax - 1) * i / 4, y = py(mm); ctx.beginPath(); ctx.moveTo(50, y); ctx.lineTo(W - 20, y); ctx.stroke(); ctx.textAlign = 'right'; ctx.fillText(mm.toFixed(2) + '×', 44, y + 4); }
      for (let i = 0; i <= 5; i++) { const tt = tmax * i / 5; ctx.textAlign = 'center'; ctx.fillText(tt.toFixed(0) + 's', px(tt), H - 18); }
      const col = crashed ? '#ff4d6d' : '#25e07f';
      ctx.beginPath(); ctx.moveTo(px(0), py(1));
      for (let i = 1; i <= 60; i++) { const tt = t * i / 60; ctx.lineTo(px(tt), py(Math.exp(K * tt))); }
      ctx.strokeStyle = col; ctx.lineWidth = 5; ctx.lineCap = 'round'; ctx.shadowColor = col; ctx.shadowBlur = 16; ctx.stroke(); ctx.shadowBlur = 0;
      ctx.lineTo(px(t), H - 40); ctx.lineTo(px(0), H - 40); ctx.closePath();
      const g = ctx.createLinearGradient(0, 0, 0, H); g.addColorStop(0, crashed ? 'rgba(255,77,109,.28)' : 'rgba(37,224,127,.25)'); g.addColorStop(1, 'rgba(0,0,0,0)'); ctx.fillStyle = g; ctx.fill();
      ctx.font = crashed ? '36px serif' : '30px serif'; ctx.textAlign = 'center'; ctx.fillText(crashed ? '💥' : '🚀', px(t), py(m) - 6);
      ctx.font = '800 76px "Barlow Condensed",Inter,sans-serif'; ctx.fillStyle = crashed ? '#ff4d6d' : cashedAt ? '#ffc83d' : '#fff'; ctx.textAlign = 'center';
      ctx.fillText(m.toFixed(2) + '×', W / 2, H / 2 - 10);
      ctx.font = '700 16px Inter'; ctx.fillStyle = crashed ? '#ff8aa0' : '#9d97b8';
      ctx.fillText(crashed ? 'ROZBITA' : state === 'run' ? (cashedAt ? `Wypłacono @ ${cashedAt.toFixed(2)}×` : 'Lecimy…') : 'Gotowy do startu', W / 2, H / 2 + 22);
    }
    function ui2() {
      go.className = state === 'run' && !cashedAt ? 'btn cash' : 'btn go';
      go.innerHTML = state === 'run' ? (cashedAt ? 'Czekaj…' : `Wypłać ${fmt(fl2(bet * cur))} ${GEM}`) : 'Zakład';
      go.disabled = state === 'run' && !!cashedAt;
    }
    function cash(m) {
      if (cashedAt) return; cashedAt = m; const pay = fl2(bet * m);
      C.settle(Crash, bet, pay); C.winPop(ui.stage, m, pay); ui2();
    }
    function loop(now) {
      raf = 0; if (!alive) return;
      const t = (now - t0) / 1000, m = Math.exp(K * t); cur = Math.min(m, crashAt);
      if (autoAt >= 1.01 && !cashedAt && autoAt <= crashAt && m >= autoAt) cash(autoAt);
      if (m >= crashAt) {
        cur = crashAt; render(crashAt, Math.log(crashAt) / K, true);
        if (!cashedAt) { C.settle(Crash, bet, 0); sfx.boom(); }
        addPill(crashAt, crashAt >= 2); state = 'idle'; ui.bet.lock(false); ac.disabled = false; ui2(); return;
      }
      render(cur, t, false); ui2(); raf = requestAnimationFrame(loop);
    }
    go.onclick = () => {
      if (state === 'run') { if (!cashedAt) cash(fl2(Math.min(cur, crashAt))); return; }
      bet = ui.bet.get(); if (!C.take(bet)) return;
      const U = 1 - rnd(); crashAt = Math.min(1e6, Math.max(1, Math.floor(.99 / U * 100) / 100));
      autoAt = +ac.value || 0; cashedAt = 0; cur = 1; state = 'run'; ui.bet.lock(true); ac.disabled = true;
      t0 = performance.now(); raf = requestAnimationFrame(loop);
    };
    C.onLeave(() => { alive = false; cancelAnimationFrame(raf); if (state === 'run' && !cashedAt) { cash(fl2(Math.min(cur, crashAt))); } });
    render(1, 0, false);
  }
};

/* ================= KENO ================= */
const KPAY = {
  1: [0, 3.96], 2: [0, 1.9, 4.5], 3: [0, 1.1, 1.38, 26], 4: [0, 0, 2.2, 7.9, 90], 5: [0, 0, 1.5, 4.2, 13, 300],
  6: [0, 0, 1.1, 2, 6.2, 100, 700], 7: [0, 0, 1.1, 1.6, 3.5, 15, 225, 700], 8: [0, 0, 1.1, 1.5, 2, 5.5, 39, 100, 800],
  9: [0, 0, 1.1, 1.3, 1.7, 2.5, 7.5, 50, 250, 1000], 10: [0, 0, 1.1, 1.2, 1.3, 1.8, 3.5, 13, 50, 250, 1000]
};
const Keno = {
  id: 'keno', name: 'Keno', cat: 'originals', provider: 'GEMBET', art: ['🎱', '💎', '🔢'], c: ['#00b8d9', '#04384a'], rtp: '95–97%',
  mount(root) {
    const ui = C.shell(Keno, root, {
      controls: `<div class="stat2"><div class="s"><small>Wybrane</small><b id="sc">0 / 10</b></div><div class="s"><small>Trafienia</small><b id="hc">—</b></div></div>
        <div class="actions"><button class="btn" id="auto">🎲 Losowe 10</button><button class="btn" id="clr">Wyczyść</button></div>
        <button class="btn go" id="go">Zakład</button>`,
      stage: `<div class="kgrid" id="kg"></div><div class="paytable" id="pt"></div>`
    });
    const kg = $('#kg', root), go = $('#go', root);
    kg.innerHTML = Array.from({ length: 40 }, (_, i) => `<button class="kcell" data-n="${i + 1}">${i + 1}</button>`).join('');
    const cells = $$('.kcell', kg); let sel = new Set(), busy = false;
    function pt(hits = -1) {
      const n = sel.size; $('#sc', root).textContent = n + ' / 10';
      $('#pt', root).innerHTML = n ? KPAY[n].map((m, i) => `<div class="${i === hits ? 'on' : ''}"><small>${i}×💎</small>${m}×</div>`).join('') : '<span class="dim">Wybierz od 1 do 10 liczb</span>';
    }
    kg.onclick = e => {
      const b = e.target.closest('.kcell'); if (!b || busy) return; const n = +b.dataset.n;
      if (sel.has(n)) sel.delete(n); else if (sel.size < 10) sel.add(n); else return C.toast('Maksymalnie 10 liczb', 'err');
      b.classList.toggle('sel'); sfx.click(); resetDraw(); pt();
    };
    function resetDraw() { cells.forEach(c => c.classList.remove('drawn', 'hit')); $('#hc', root).textContent = '—'; }
    $('#auto', root).onclick = () => { if (busy) return; sel = new Set(shuffle([...Array(40).keys()]).slice(0, 10).map(x => x + 1)); cells.forEach(c => c.classList.toggle('sel', sel.has(+c.dataset.n))); resetDraw(); pt(); };
    $('#clr', root).onclick = () => { if (busy) return; sel.clear(); cells.forEach(c => c.classList.remove('sel')); resetDraw(); pt(); };
    async function play() {
      if (busy) return false;
      if (!sel.size) { C.toast('Wybierz od 1 do 10 liczb', 'err'); return false; }
      const bet = ui.bet.get(); if (!C.take(bet)) return false;
      busy = true; go.disabled = true; resetDraw(); let hits = 0;
      const draw = shuffle([...Array(40).keys()]).slice(0, 10).map(x => x + 1);
      for (const n of draw) {
        const c = cells[n - 1]; if (!document.body.contains(c)) { busy = false; return false; }
        if (sel.has(n)) { c.classList.add('hit'); hits++; sfx.gem(hits); } else { c.classList.add('drawn'); sfx.tick(); }
        $('#hc', root).textContent = hits; pt(hits); await sleep(130);
      }
      const m = KPAY[sel.size][hits], pay = fl2(bet * m);
      C.settle(Keno, bet, pay); if (m > 0) C.winPop(ui.stage, m, pay);
      busy = false; go.disabled = false; return true;
    }
    go.onclick = play; C.auto(ui.ctrl, play, { n: 5, gap: 500 }); pt();
  }
};

/* ================= WHEEL ================= */
const WPAT = {
  low: [1.5, 1.2, 1.2, 0, 1.2, 1.2, 1.2, 0, 1.2, 1.2],
  med: [0, 1.9, 0, 1.5, 0, 2, 0, 1.5, 0, 3]
};
const wcol = m => m === 0 ? '#34304f' : m <= 1.3 ? '#e8a317' : m <= 2 ? '#1fb59a' : m <= 3 ? '#3d8fe8' : m <= 12 ? '#a35cf0' : '#ff4d6d';
const Wheel = {
  id: 'wheel', name: 'Wheel', cat: 'originals', provider: 'GEMBET', art: ['🎡', '⭐', '🎯'], c: ['#ffb020', '#5c3300'],
  mount(root) {
    const ui = C.shell(Wheel, root, {
      controls: `<div class="field"><label><span>Ryzyko</span></label><div id="rk"></div></div>
        <div class="field"><label><span>Segmenty</span></label><div id="sg"></div></div>
        <button class="btn go" id="go">Zakręć</button>`,
      stage: `<div class="wheelw"><div class="ptr">▼</div><svg viewBox="0 0 300 300"><g class="spinner" id="sp"></g><circle cx="150" cy="150" r="26" fill="#120e22" stroke="#3a3260" stroke-width="4"/></svg></div><div class="msg" id="msg" style="margin-top:8px">Zakręć kołem fortuny</div>`
    });
    let risk = 'med', N = 30, rot = 0, busy = false, segs = [];
    $('#rk', root).replaceWith(C.seg([['low', 'Niskie'], ['med', 'Średnie'], ['high', 'Wysokie']], risk, v => { if (!busy) { risk = v; build(); } }));
    $('#sg', root).replaceWith(C.seg([['10', '10'], ['20', '20'], ['30', '30'], ['40', '40'], ['50', '50']], N, v => { if (!busy) { N = +v; build(); } }));
    const sp = $('#sp', root), go = $('#go', root), msg = $('#msg', root), addPill = C.pills(ui.stage);
    function build() {
      segs = risk === 'high' ? Array.from({ length: N }, (_, i) => i === 0 ? r2(.99 * N) : 0) : Array.from({ length: N }, (_, i) => WPAT[risk][i % 10]);
      if (risk === 'high') { segs = shuffle(Array(N).fill(0)); segs[rint(N)] = 0; segs[0] = r2(.99 * N); }
      const a = 360 / N, P = (ang, r) => [150 + r * Math.sin(ang * Math.PI / 180), 150 - r * Math.cos(ang * Math.PI / 180)];
      sp.style.transition = 'none'; sp.style.transform = `rotate(${rot = 0}deg)`; sp.style.transformOrigin = '150px 150px';
      sp.innerHTML = segs.map((m, i) => {
        const [x1, y1] = P(a * i, 146), [x2, y2] = P(a * (i + 1), 146), [tx, ty] = P(a * (i + .5), 118);
        return `<path d="M150 150L${x1} ${y1}A146 146 0 0 1 ${x2} ${y2}Z" fill="${wcol(m)}" stroke="#120e22" stroke-width="1.5"/>` +
          (N <= 30 ? `<text x="${tx}" y="${ty}" fill="#fff" font-size="${N <= 20 ? 11 : 8}" font-weight="800" text-anchor="middle" dominant-baseline="middle" transform="rotate(${a * (i + .5)} ${tx} ${ty})">${m || ''}${m ? '×' : ''}</text>` : '');
      }).join('') + '<circle cx="150" cy="150" r="148" fill="none" stroke="#3a3260" stroke-width="4"/>';
      void sp.getBoundingClientRect();
    }
    async function play() {
      if (busy) return false;
      const bet = ui.bet.get(); if (!C.take(bet)) return false;
      busy = true; go.disabled = true; msg.textContent = 'Kręci się…';
      const i = rint(N), a = 360 / N, goal = -(i + .5) * a;
      rot += 360 * 5 + ((((goal - rot) % 360) + 360) % 360);
      sp.style.transition = ''; sp.style.transform = `rotate(${rot}deg)`;
      for (let k = 0; k < 14; k++) { sfx.tick(); await sleep(k < 8 ? 250 : 190 + k * 20); }
      await sleep(500);
      const m = segs[i], pay = fl2(bet * m), win = m > 0;
      msg.innerHTML = win ? `<span class="grn">${fmt(m)}× — wygrana!</span>` : '<span class="red">0× — pech</span>'; addPill(m, win);
      C.settle(Wheel, bet, pay); if (win) C.winPop(ui.stage, m, pay);
      busy = false; go.disabled = false; return true;
    }
    go.onclick = play; C.auto(ui.ctrl, play, { n: 5, gap: 300 }); build();
  }
};

/* ================= TOWERS ================= */
const TCFG = { easy: [4, 1, 'Łatwy'], med: [3, 1, 'Średni'], hard: [2, 1, 'Trudny'], ext: [3, 2, 'Ekstremalny'], mas: [4, 3, 'Mistrz'] };
const Towers = {
  id: 'towers', name: 'Towers', cat: 'originals', provider: 'GEMBET', art: ['🏰', '💎', '☠️'], c: ['#8b5cf6', '#1e0b4a'],
  mount(root) {
    const ui = C.shell(Towers, root, {
      controls: `<div class="field"><label><span>Trudność</span></label><select class="sel" id="df">${Object.entries(TCFG).map(([k, v]) => `<option value="${k}" ${k === 'med' ? 'selected' : ''}>${v[2]} (${v[0]} pola, ${v[1]} 💀)</option>`).join('')}</select></div>
        <div class="stat2"><div class="s"><small>Mnożnik</small><b id="cm">1,00×</b></div><div class="s"><small>Następny</small><b id="nm">—</b></div></div>
        <button class="btn go" id="go">Zakład</button><button class="btn" id="rb" disabled>Losowe pole</button>`,
      stage: `<div class="towers" id="tw"></div><div class="msg" id="msg" style="margin-top:12px">Wspinaj się wyżej — unikaj czaszek</div>`
    });
    const ROWS = 9, df = $('#df', root), go = $('#go', root), tw = $('#tw', root), msg = $('#msg', root);
    let active = false, row = 0, bombs = [], bet = 0, cols = 3, nb = 1;
    const mult = n => .99 / Math.pow((cols - nb) / cols, n);
    function build() {
      [cols, nb] = TCFG[df.value];
      tw.innerHTML = Array.from({ length: ROWS }, (_, r) => `<div class="trow" data-r="${r}"><span class="m">${fmtM(mult(r + 1))}×</span>${Array.from({ length: cols }, (_, c) => `<button class="mtile" data-r="${r}" data-c="${c}" disabled></button>`).join('')}</div>`).join('');
    }
    function upd() {
      $('#cm', root).textContent = fmt(row ? mult(row) : 1) + '×'; $('#nm', root).textContent = active && row < ROWS ? fmt(mult(row + 1)) + '×' : '—';
      go.className = active ? 'btn cash' : 'btn go'; go.innerHTML = active ? `Wypłać ${fmt(fl2(bet * mult(row)))} ${GEM}` : 'Zakład'; go.disabled = active && !row;
      $('#rb', root).disabled = !active; df.disabled = active;
      $$('.trow', tw).forEach((r, i) => { r.classList.toggle('cur', active && i === row); $$('.mtile', r).forEach(t => t.disabled = !(active && i === row)); });
    }
    function reveal() {
      $$('.trow', tw).forEach((r, ri) => $$('.mtile', r).forEach((t, ci) => {
        if (t.classList.contains('open')) return; t.classList.add('open', 'ghost');
        const b = bombs[ri].has(ci); t.classList.add(b ? 'bomb' : 'good'); t.innerHTML = b ? '💀' : '<i class="gem"></i>';
      }));
    }
    function end() { active = false; ui.bet.lock(false); reveal(); upd(); }
    function cash(silent) {
      const m = mult(row), pay = fl2(bet * m); C.settle(Towers, bet, pay);
      if (!silent) { C.winPop(ui.stage, m, pay); msg.innerHTML = `<span class="grn">Wypłacono ${fmt(pay)} 💎</span>`; } end();
    }
    function pickTile(c) {
      if (!active) return; const t = $(`.mtile[data-r="${row}"][data-c="${c}"]`, tw); t.classList.add('open');
      if (bombs[row].has(c)) { t.classList.add('bomb'); t.innerHTML = '💀'; sfx.boom(); C.settle(Towers, bet, 0); msg.innerHTML = '<span class="red">Pułapka!</span>'; end(); }
      else { t.classList.add('good'); t.innerHTML = '<i class="gem"></i>'; sfx.gem(row); row++; if (row >= ROWS) cash(); else upd(); }
    }
    tw.onclick = e => { const t = e.target.closest('.mtile'); if (t && !t.disabled) pickTile(+t.dataset.c); };
    $('#rb', root).onclick = () => pickTile(rint(cols));
    df.onchange = () => { build(); upd(); };
    go.onclick = () => {
      if (active) return cash();
      bet = ui.bet.get(); if (!C.take(bet)) return; build();
      bombs = Array.from({ length: ROWS }, () => new Set(shuffle([...Array(cols).keys()]).slice(0, nb)));
      active = true; row = 0; ui.bet.lock(true); msg.textContent = 'Wybierz pole w podświetlonym rzędzie'; upd();
    };
    C.onLeave(() => { if (active) { row ? cash(true) : C.credit(bet); } });
    build(); upd();
  }
};

/* ================= CHICKEN ================= */
const CH = { easy: [.08, 25, 'Łatwy'], med: [.15, 20, 'Średni'], hard: [.25, 15, 'Trudny'], ext: [.4, 10, 'Ekstremalny'] };
const Chicken = {
  id: 'chicken', name: 'Chicken', cat: 'originals', provider: 'GEMBET', art: ['🐔', '🚗', '🌽'], c: ['#ff9f1c', '#6b2d00'], hot: true,
  mount(root) {
    const ui = C.shell(Chicken, root, {
      controls: `<div class="field"><label><span>Trudność</span></label><div id="dseg"></div></div>
        <div class="stat2"><div class="s"><small>Mnożnik</small><b id="cm">1,00×</b></div><div class="s"><small>Szansa przejścia</small><b id="sp">—</b></div></div>
        <button class="btn go" id="go">Zakład</button>
        <div id="act" style="display:none;flex-direction:column;gap:8px"><button class="btn primary" id="step" style="height:48px">🐔 Przejdź dalej</button><button class="btn cash" id="cash">Wypłać</button></div>`,
      stage: `<div class="road" id="road"></div><div class="msg" id="msg" style="margin-top:14px">Przeprowadź kurczaka przez drogę — każdy pas to wyższy mnożnik</div>`
    });
    let diff = 'med', active = false, pos = 0, bet = 0, busy = false;
    $('#dseg', root).replaceWith(C.seg(Object.entries(CH).map(([k, v]) => [k, v[2]]), diff, v => { if (!active) { diff = v; build(); upd(); } }));
    const road = $('#road', root), go = $('#go', root), act = $('#act', root), msg = $('#msg', root);
    const D = () => CH[diff][0], L = () => CH[diff][1], mult = n => n ? .98 / Math.pow(1 - D(), n) : 1;
    function build() {
      pos = 0;
      road.innerHTML = `<div class="lane" data-l="0"><span class="lm">START</span><div class="ent">🐔</div><div class="ground">🏁</div></div>` +
        Array.from({ length: L() }, (_, i) => `<div class="lane" data-l="${i + 1}"><span class="lm">${fmtM(mult(i + 1))}×</span><span class="car">🚗</span><div class="ent"></div><div class="ground"></div></div>`).join('');
      road.scrollLeft = 0;
    }
    function upd() {
      $('#cm', root).textContent = fmt(mult(pos)) + '×'; $('#sp', root).textContent = fmt((1 - D()) * 100) + '%';
      go.style.display = active ? 'none' : ''; act.style.display = active ? 'flex' : 'none';
      $('#cash', root).innerHTML = `Wypłać ${fmt(fl2(bet * mult(pos)))} ${GEM}`; $('#cash', root).disabled = !pos || busy; $('#step', root).disabled = busy;
    }
    const lane = i => $(`.lane[data-l="${i}"]`, road);
    const scrollTo = i => road.scrollTo({ left: Math.max(0, lane(i).offsetLeft - road.clientWidth / 2 + 39), behavior: 'smooth' });
    function finish() { active = false; ui.bet.lock(false); upd(); }
    function cash(silent) {
      const m = mult(pos), pay = fl2(bet * m); C.settle(Chicken, bet, pay);
      if (!silent) { C.winPop(ui.stage, m, pay); msg.innerHTML = `<span class="grn">Kurczak bezpieczny! +${fmt(pay)} 💎</span>`; } finish();
    }
    $('#step', root).onclick = async () => {
      if (!active || busy) return; busy = true; upd();
      const next = pos + 1, dead = rnd() < D();
      lane(pos).querySelector('.ent').textContent = ''; lane(pos).classList.add('done');
      const nl = lane(next); nl.querySelector('.ent').textContent = '🐔'; scrollTo(next); sfx.tick();
      if (dead) {
        await sleep(350); nl.classList.add('hit'); await sleep(450); nl.querySelector('.ent').textContent = '💥'; sfx.boom();
        C.settle(Chicken, bet, 0); msg.innerHTML = '<span class="red">Auć! Potrącony…</span>'; busy = false; finish();
      } else {
        pos = next; nl.classList.add('done'); sfx.gem(pos); await sleep(250); busy = false;
        if (pos >= L()) cash(); else upd();
      }
    };
    $('#cash', root).onclick = () => !busy && cash();
    go.onclick = () => {
      bet = ui.bet.get(); if (!C.take(bet)) return; build(); active = true; ui.bet.lock(true); msg.textContent = 'Naciskaj „Przejdź dalej”!'; upd();
    };
    C.onLeave(() => { if (active) { pos ? cash(true) : C.credit(bet); } });
    build(); upd();
  }
};

[Plinko, Crash, Keno, Wheel, Towers, Chicken].forEach(g => C.register(g));
})();
