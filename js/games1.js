/* Gry Originals #1: Mines, Dice, Limbo, Coinflip, Hi-Lo, Pump */
(() => {
'use strict';
const C = Casino, { $, $$, h, GEM, fmt, fmtM, r2, fl2, sfx, sleep, rint, rnd, shuffle } = C;

/* ================= MINES ================= */
const Mines = {
  id: 'mines', name: 'Mines', cat: 'originals', provider: 'GEMBET', art: ['💣', '💎', '💥'], c: ['#2a7bff', '#0b2457'], hot: true,
  mount(root) {
    const ui = C.shell(Mines, root, {
      controls: `<div class="field"><label><span>Miny</span><span class="hint" id="mc">3</span></label><input type="range" min="1" max="24" value="3" id="mr"></div>
        <div class="stat2"><div class="s"><small>Mnożnik</small><b id="cm">1,00×</b></div><div class="s"><small>Następne pole</small><b id="nm">—</b></div></div>
        <button class="btn go" id="go">Zakład</button>
        <button class="btn" id="rnd" disabled>Losowe pole</button>`,
      stage: `<div class="minegrid" id="mg"></div><div class="msg" id="msg" style="margin-top:16px">Wybierz liczbę min i zacznij grę</div>`
    });
    const mg = $('#mg', root), go = $('#go', root), rb = $('#rnd', root), mr = $('#mr', root), msg = $('#msg', root);
    let active = false, mines = new Set(), opened = new Set(), bet = 0, nm = 3, mult = 1;
    mg.innerHTML = Array.from({ length: 25 }, (_, i) => `<button class="mtile" data-i="${i}" disabled></button>`).join('');
    const tiles = $$('.mtile', mg);
    const multAt = k => { let m = .99; for (let i = 0; i < k; i++) m *= (25 - i) / (25 - nm - i); return m; };
    const upd = () => {
      $('#cm', root).textContent = fmt(mult) + '×';
      $('#nm', root).textContent = opened.size < 25 - nm ? fmt(multAt(opened.size + 1)) + '×' : '—';
      go.className = active ? 'btn cash' : 'btn go';
      go.innerHTML = active ? (opened.size ? `Wypłać ${fmt(fl2(bet * mult))} ${GEM}` : 'Wybierz pole') : 'Zakład';
      go.disabled = active && !opened.size;
      rb.disabled = !active;
      tiles.forEach((t, i) => t.disabled = !active || opened.has(i));
    };
    mr.oninput = () => { if (active) return; nm = +mr.value; $('#mc', root).textContent = nm; $('#nm', root).textContent = '—'; };
    function endRound() {
      active = false; ui.bet.lock(false); mr.disabled = false;
      tiles.forEach((t, i) => {
        if (!opened.has(i)) { t.classList.add('open', 'ghost'); t.classList.add(mines.has(i) ? 'bomb' : 'good'); t.innerHTML = mines.has(i) ? '💣' : '<i class="gem"></i>'; }
      });
      upd();
    }
    function cash(silent) {
      const pay = fl2(bet * mult); C.settle(Mines, bet, pay);
      if (!silent) { C.winPop(ui.stage, mult, pay); msg.textContent = `Wypłacono ${fmt(pay)} 💎 (${fmt(mult)}×)`; }
      endRound();
    }
    function reveal(i) {
      if (!active || opened.has(i)) return;
      const t = tiles[i];
      t.classList.add('open');
      if (mines.has(i)) {
        t.classList.add('bomb'); t.innerHTML = '💥'; sfx.boom();
        C.settle(Mines, bet, 0); msg.innerHTML = '<span class="red">Bum! Trafiłeś na minę.</span>'; endRound();
      } else {
        t.classList.add('good'); t.innerHTML = '<i class="gem"></i>'; opened.add(i); sfx.gem(opened.size);
        mult = multAt(opened.size);
        if (opened.size === 25 - nm) cash(); else upd();
      }
    }
    mg.onclick = e => { const t = e.target.closest('.mtile'); if (t) reveal(+t.dataset.i); };
    rb.onclick = () => { const free = [...Array(25).keys()].filter(i => !opened.has(i)); reveal(free[rint(free.length)]); };
    go.onclick = () => {
      if (active) return cash();
      bet = ui.bet.get(); if (!C.take(bet)) return;
      active = true; opened = new Set(); mult = 1; nm = +mr.value;
      mines = new Set(shuffle([...Array(25).keys()]).slice(0, nm));
      tiles.forEach(t => { t.className = 'mtile'; t.innerHTML = ''; });
      ui.bet.lock(true); mr.disabled = true; msg.textContent = 'Znajdź gemy, unikaj min!'; upd();
    };
    C.onLeave(() => { if (active) { opened.size ? cash(true) : C.credit(bet); } });
    upd();
  }
};

/* ================= DICE ================= */
const Dice = {
  id: 'dice', name: 'Dice', cat: 'originals', provider: 'GEMBET', art: ['🎲', '✨', '🎯'], c: ['#1fbf75', '#0a3d2a'],
  mount(root) {
    const ui = C.shell(Dice, root, {
      controls: `<div class="field"><label><span>Tryb</span></label><div id="modeseg"></div></div>
        <div class="stat2"><div class="s"><small>Mnożnik</small><b id="dm">2,00×</b></div><div class="s"><small>Szansa</small><b id="dc">50,00%</b></div></div>
        <button class="btn go" id="go">Rzuć kością</button>`,
      stage: `<div style="width:100%;max-width:680px;display:flex;flex-direction:column;gap:34px;align-items:center">
        <div class="big-mult" id="res">50,00</div>
        <div style="width:100%;position:relative;padding-top:42px">
          <div class="dmark" id="mark" style="position:absolute;left:50%;top:0;transform:translateX(-50%);transition:left .35s;opacity:0;font-size:36px">🎲</div>
          <input type="range" id="rng" min="2" max="98" step="0.01" value="50" style="height:16px">
          <div style="display:flex;justify-content:space-between;color:var(--dim);font-weight:700;margin-top:8px"><span>0</span><span>25</span><span>50</span><span>75</span><span>100</span></div>
        </div></div>`
    });
    const rng = $('#rng', root), res = $('#res', root), go = $('#go', root), mark = $('#mark', root);
    const addPill = C.pills(ui.stage);
    let mode = 'under', busy = false;
    $('#modeseg', root).replaceWith(C.seg([['under', 'Poniżej'], ['over', 'Powyżej']], mode, v => { mode = v; upd(); }));
    const target = () => +rng.value;
    const chance = () => mode === 'under' ? target() : 100 - target();
    const mult = () => 99 / chance();
    function upd() {
      $('#dm', root).textContent = fmt(mult(), 2) + '×'; $('#dc', root).textContent = fmt(chance()) + '%';
      const t = target(), g = '#25e07f', r = '#ff4d6d';
      rng.style.background = mode === 'under' ? `linear-gradient(90deg,${g} ${t}%,${r} ${t}%)` : `linear-gradient(90deg,${r} ${t}%,${g} ${t}%)`;
    }
    rng.oninput = () => { sfx.tick(); upd(); };
    async function play() {
      if (busy) return false;
      const bet = ui.bet.get(); if (!C.take(bet)) return false;
      busy = true; go.disabled = true; res.className = 'big-mult';
      const v = rint(10000) / 100, t = target();
      const win = mode === 'under' ? v < t : v >= t;
      for (let i = 0; i < 6; i++) { res.textContent = fmt(rnd() * 100); sfx.tick(); await sleep(45); }
      res.textContent = fmt(v); mark.style.opacity = 1; mark.style.left = v + '%';
      res.classList.add(win ? 'win' : 'lose');
      const m = mult(), pay = win ? fl2(bet * m) : 0;
      addPill(win ? m : 0, win);
      C.settle(Dice, bet, pay); if (win) C.winPop(ui.stage, m, pay);
      busy = false; go.disabled = false; return true;
    }
    go.onclick = play; C.auto(ui.ctrl, play); upd();
  }
};

/* ================= LIMBO ================= */
const Limbo = {
  id: 'limbo', name: 'Limbo', cat: 'originals', provider: 'GEMBET', art: ['🚀', '☄️', '⭐'], c: ['#ff7a3d', '#5a1a0a'],
  mount(root) {
    const ui = C.shell(Limbo, root, {
      controls: `<div class="field"><label><span>Docelowy mnożnik</span></label><div class="amt plain"><input type="number" id="tg" value="2.00" min="1.01" max="1000000" step="0.01"><span class="suf">×</span></div></div>
        <div class="stat2"><div class="s"><small>Szansa wygranej</small><b id="lc">49,50%</b></div><div class="s"><small>Zysk</small><b id="lp">—</b></div></div>
        <button class="btn go" id="go">Zakład</button>`,
      stage: `<div class="big-mult" id="res">1,00×</div><div class="msg" id="msg">Trafisz powyżej celu — wygrywasz</div>`
    });
    const tg = $('#tg', root), res = $('#res', root), go = $('#go', root);
    const addPill = C.pills(ui.stage);
    let busy = false;
    const target = () => C.clamp(+tg.value || 2, 1.01, 1e6);
    const upd = () => { $('#lc', root).textContent = fmt(Math.min(99, 99 / target())) + '%'; $('#lp', root).textContent = fmt(ui.bet.get() * (target() - 1)) + ' 💎'; };
    tg.oninput = upd; tg.onblur = () => { tg.value = target().toFixed(2); upd(); };
    ui.bet.input.addEventListener('input', upd);
    async function play() {
      if (busy) return false;
      const bet = ui.bet.get(), t = target(); if (!C.take(bet)) return false;
      busy = true; go.disabled = true; res.className = 'big-mult';
      const U = 1 - rnd(), out = Math.min(1e6, Math.max(1, Math.floor(.99 / U * 100) / 100));
      const dur = Math.min(1300, 350 + Math.log10(out + 1) * 380), t0 = performance.now(), lo = Math.log(1);
      await new Promise(done => {
        const step = now => {
          const p = Math.min(1, (now - t0) / dur), e = 1 - Math.pow(1 - p, 3);
          res.textContent = fmt(Math.exp(lo + (Math.log(out) - lo) * e)) + '×';
          if (!document.body.contains(res)) return done();
          p < 1 ? requestAnimationFrame(step) : done();
        }; requestAnimationFrame(step);
      });
      const win = out >= t; res.textContent = fmt(out) + '×'; res.classList.add(win ? 'win' : 'lose');
      const pay = win ? fl2(bet * t) : 0; addPill(out, win);
      C.settle(Limbo, bet, pay); if (win) C.winPop(ui.stage, t, pay);
      busy = false; go.disabled = false; return true;
    }
    go.onclick = play; C.auto(ui.ctrl, play); upd();
  }
};

/* ================= COINFLIP ================= */
const Coin = {
  id: 'coinflip', name: 'Coinflip', cat: 'originals', provider: 'GEMBET', art: ['🪙', '✨', '🪙'], c: ['#f2b42a', '#6b3d05'], rtp: '98%',
  mount(root) {
    const ui = C.shell(Coin, root, {
      controls: `<div class="field"><label><span>Twój wybór</span></label><div id="side"></div></div>
        <div class="stat2"><div class="s"><small>Mnożnik</small><b>1,96×</b></div><div class="s"><small>Szansa</small><b>50%</b></div></div>
        <button class="btn go" id="go">Rzuć monetą</button>`,
      stage: `<div class="coinw"><div class="coin" id="coin"><div class="h">Orzeł</div><div class="t">Reszka</div></div></div><div class="msg" id="msg">Orzeł czy reszka?</div>`
    });
    let side = 'h', rot = 0, busy = false;
    $('#side', root).replaceWith(C.seg([['h', '🦅 Orzeł'], ['t', '🌊 Reszka']], side, v => side = v));
    const coin = $('#coin', root), go = $('#go', root), msg = $('#msg', root);
    const addPill = C.pills(ui.stage);
    async function play() {
      if (busy) return false;
      const bet = ui.bet.get(); if (!C.take(bet)) return false;
      busy = true; go.disabled = true; msg.textContent = '…';
      const r = rnd() < .5 ? 'h' : 't', targetAng = r === 'h' ? 0 : 180;
      rot += 1440 + ((targetAng - (rot % 360)) + 360) % 360;
      coin.style.transform = `rotateY(${rot}deg)`;
      for (let i = 0; i < 6; i++) { sfx.tick(); await sleep(230); }
      const win = r === side, pay = win ? fl2(bet * 1.96) : 0;
      msg.innerHTML = win ? '<span class="grn">Wygrana!</span>' : '<span class="red">Pudło</span>';
      addPill(win ? 1.96 : 0, win); C.settle(Coin, bet, pay); if (win) C.winPop(ui.stage, 1.96, pay);
      busy = false; go.disabled = false; return true;
    }
    go.onclick = play; C.auto(ui.ctrl, play);
  }
};

/* ================= HI-LO ================= */
const RANKS = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'], SUITS = ['♠', '♥', '♦', '♣'];
const HiLo = {
  id: 'hilo', name: 'Hi-Lo', cat: 'originals', provider: 'GEMBET', art: ['🃏', '⬆️', '⬇️'], c: ['#d6336c', '#4a0f2b'],
  mount(root) {
    const ui = C.shell(HiLo, root, {
      controls: `<button class="btn go" id="go">Zakład</button>
        <div class="stat2"><div class="s"><small>Mnożnik</small><b id="cm">1,00×</b></div><div class="s"><small>Wypłata</small><b id="cp">—</b></div></div>
        <div class="actions"><button class="btn" id="hi" disabled>⬆ Wyżej</button><button class="btn" id="lo" disabled>⬇ Niżej</button></div>
        <div class="stat2"><div class="s"><small>Szansa ⬆</small><b id="ph">—</b></div><div class="s"><small>Szansa ⬇</small><b id="pl">—</b></div></div>
        <button class="btn" id="skip" disabled>Pomiń kartę</button>`,
      stage: `<div class="hilo"><div id="cur"></div></div><div class="msg" id="msg" style="margin-top:16px">Zagraj: czy następna karta będzie wyższa czy niższa (remis = wygrana)</div><div class="hlhist" id="hist"></div>`
    });
    const go = $('#go', root), hi = $('#hi', root), lo = $('#lo', root), skip = $('#skip', root), cur = $('#cur', root), hist = $('#hist', root), msg = $('#msg', root);
    let active = false, card = null, mult = 1, bet = 0;
    const draw = () => ({ n: rint(13) + 1, r: RANKS[0], s: SUITS[rint(4)] });
    const mk = () => { const c = draw(); c.r = RANKS[c.n - 1]; return c; };
    const show = () => { cur.innerHTML = C.cardHTML(card, 'lg'); };
    const probs = () => ({ h: (14 - card.n) / 13, l: card.n / 13 });
    function upd() {
      go.className = active ? 'btn cash' : 'btn go';
      go.innerHTML = active ? `Wypłać ${fmt(fl2(bet * mult))} ${GEM}` : 'Zakład'; go.disabled = active && mult <= 1;
      $('#cm', root).textContent = fmt(mult) + '×'; $('#cp', root).textContent = active ? fmt(fl2(bet * mult)) + ' 💎' : '—';
      hi.disabled = lo.disabled = skip.disabled = !active;
      if (active) { const p = probs(); $('#ph', root).textContent = fmt(p.h * 100) + '%'; $('#pl', root).textContent = fmt(p.l * 100) + '%'; }
    }
    function end() { active = false; ui.bet.lock(false); upd(); }
    function cash(silent) {
      const pay = fl2(bet * mult); C.settle(HiLo, bet, pay);
      if (!silent) { C.winPop(ui.stage, mult, pay); msg.innerHTML = `<span class="grn">Wypłacono ${fmt(pay)} 💎</span>`; }
      end();
    }
    function guess(up) {
      if (!active) return;
      const p = probs(), pr = up ? p.h : p.l, nx = mk(), win = up ? nx.n >= card.n : nx.n <= card.n;
      hist.prepend(h(C.cardHTML(card))); while (hist.children.length > 14) hist.lastChild.remove();
      card = nx; show();
      if (win) { mult *= .99 / pr; sfx.gem(3); msg.textContent = 'Dobrze! Gramy dalej.'; upd(); }
      else { C.settle(HiLo, bet, 0); msg.innerHTML = '<span class="red">Pudło — karta poszła w złą stronę.</span>'; end(); }
    }
    hi.onclick = () => guess(true); lo.onclick = () => guess(false);
    skip.onclick = () => { hist.prepend(h(C.cardHTML(card))); card = mk(); show(); upd(); sfx.click(); };
    go.onclick = () => {
      if (active) return cash();
      bet = ui.bet.get(); if (!C.take(bet)) return;
      active = true; mult = 1; card = mk(); show(); hist.innerHTML = ''; ui.bet.lock(true); msg.textContent = 'Wyżej czy niżej?'; upd();
    };
    card = mk(); show();
    C.onLeave(() => { if (active) { mult > 1 ? cash(true) : C.credit(bet); } });
  }
};

/* ================= PUMP ================= */
const Pump = {
  id: 'pump', name: 'Pump', cat: 'originals', provider: 'GEMBET', art: ['🎈', '💨', '🎈'], c: ['#ff4d6d', '#5a0f2a'],
  mount(root) {
    const ui = C.shell(Pump, root, {
      controls: `<div class="field"><label><span>Trudność</span></label><div id="dseg"></div></div>
        <div class="stat2"><div class="s"><small>Mnożnik</small><b id="cm">1,00×</b></div><div class="s"><small>Następny</small><b id="nm">—</b></div></div>
        <button class="btn go" id="go">Zakład</button>
        <div id="act" hidden style="display:none;flex-direction:column;gap:8px"><button class="btn primary" id="pump" style="height:48px">💨 Pompuj</button><button class="btn cash" id="cash">Wypłać</button></div>`,
      stage: `<svg class="balloon" id="bal" viewBox="0 0 200 260"><defs><radialGradient id="bg" cx=".35" cy=".3"><stop offset="0" stop-color="#ff9db1"/><stop offset=".6" stop-color="#ff4d6d"/><stop offset="1" stop-color="#a3112f"/></radialGradient></defs>
        <path d="M100 8C45 8 14 52 14 100c0 52 40 90 86 98 46-8 86-46 86-98C186 52 155 8 100 8z" fill="url(#bg)" id="bpath"/><path d="M92 198h16l8 14H84z" fill="#c4163a"/><path d="M100 212c-6 20 10 30 0 46" stroke="#cfc8ee" stroke-width="3" fill="none"/></svg>
        <div class="msg" id="msg" style="margin-top:10px">Pompuj balon, ale uważaj — może pęknąć!</div>`
    });
    const DIFF = { easy: 25, med: 15, hard: 8, ext: 4 };
    let diff = 'med', active = false, pumps = 0, popAt = 0, bet = 0;
    $('#dseg', root).replaceWith(C.seg([['easy', 'Łatwy'], ['med', 'Średni'], ['hard', 'Trudny'], ['ext', 'Ekstremalny']], diff, v => { if (!active) { diff = v; upd(); } }));
    const go = $('#go', root), act = $('#act', root), bal = $('#bal', root), msg = $('#msg', root);
    const N = () => DIFF[diff], multAt = n => .99 * N() / (N() - n);
    function upd() {
      const m = pumps ? multAt(pumps) : 1;
      $('#cm', root).textContent = fmt(m) + '×'; $('#nm', root).textContent = pumps + 1 < N() ? fmt(multAt(pumps + 1)) + '×' : '—';
      $('#cash', root).innerHTML = `Wypłać ${fmt(fl2(bet * m))} ${GEM}`; $('#cash', root).disabled = !pumps;
      go.style.display = active ? 'none' : ''; act.style.display = active ? 'flex' : 'none';
      bal.style.transform = `scale(${.45 + Math.min(pumps, 30) / 30 * .85})`;
      const hue = Math.max(0, 1 - pumps / (N() * .8)); $('#bpath', root).style.filter = `hue-rotate(${-hue * 20 + 20}deg) saturate(${1 + (1 - hue) * .6})`;
    }
    function finish() { active = false; ui.bet.lock(false); upd(); }
    function cash(silent) {
      const m = multAt(pumps), pay = fl2(bet * m); C.settle(Pump, bet, pay);
      if (!silent) { C.winPop(ui.stage, m, pay); msg.innerHTML = `<span class="grn">Wypłacono ${fmt(pay)} 💎</span>`; }
      finish();
    }
    $('#pump', root).onclick = async () => {
      if (!active) return; pumps++; sfx.tick();
      if (pumps === popAt) {
        bal.classList.add('pop'); sfx.boom(); C.settle(Pump, bet, 0); msg.innerHTML = '<span class="red">PUF! Balon pękł.</span>';
        active = false; ui.bet.lock(false); await sleep(450); bal.classList.remove('pop'); pumps = 0; finish(); bal.style.transform = 'scale(.45)';
      } else if (pumps >= N() - 1) cash(); else upd();
    };
    $('#cash', root).onclick = () => cash();
    go.onclick = () => {
      bet = ui.bet.get(); if (!C.take(bet)) return;
      active = true; pumps = 0; popAt = rint(N()) + 1; ui.bet.lock(true); msg.textContent = 'Pompuj!'; upd();
    };
    C.onLeave(() => { if (active) { pumps ? cash(true) : C.credit(bet); } });
    upd();
  }
};

[Mines, Dice, Limbo, Coin, HiLo, Pump].forEach(g => C.register(g));
})();
