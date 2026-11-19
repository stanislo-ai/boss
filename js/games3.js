/* Gry stołowe i sloty: Roulette, Blackjack, 4x slot */
(() => {
'use strict';
const C = Casino, { $, $$, h, GEM, fmt, fmtM, r2, fl2, sfx, sleep, rint, rnd, shuffle } = C;

/* ================= ROULETTE ================= */
const RED = new Set([1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36]);
const ORDER = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26];
const ncol = n => n === 0 ? '#12915a' : RED.has(n) ? '#c92a4a' : '#1b1730';
const RTEST = {
  red: n => RED.has(n), black: n => n && !RED.has(n), odd: n => n && n % 2 === 1, even: n => n && n % 2 === 0, low: n => n >= 1 && n <= 18, high: n => n >= 19,
  d1: n => n >= 1 && n <= 12, d2: n => n >= 13 && n <= 24, d3: n => n >= 25, c1: n => n && n % 3 === 1, c2: n => n && n % 3 === 2, c3: n => n && n % 3 === 0
};
const RMULT = k => k[0] === 'n' ? 36 : /^[dc]\d/.test(k) ? 3 : 2;
const Roulette = {
  id: 'roulette', name: 'Roulette', cat: 'table', provider: 'GEMBET', art: ['🎰', '🔴', '⚫'], c: ['#d12d4f', '#3d0816'], rtp: '97,3%',
  mount(root) {
    let cells = `<div class="rc zero" data-b="n0">0</div>`;
    for (let n = 1; n <= 36; n++) cells += `<div class="rc ${RED.has(n) ? 'red' : 'blk'}" data-b="n${n}" style="grid-column:${2 + Math.floor((n - 1) / 3)};grid-row:${3 - ((n - 1) % 3)}">${n}</div>`;
    cells += [['c3', 1], ['c2', 2], ['c1', 3]].map(([k, r]) => `<div class="rc sp" data-b="${k}" style="grid-column:14;grid-row:${r}">2:1</div>`).join('');
    cells += [['d1', '1–12'], ['d2', '13–24'], ['d3', '25–36']].map(([k, t], i) => `<div class="rc sp" data-b="${k}" style="grid-column:${2 + i * 4}/span 4;grid-row:4">${t}</div>`).join('');
    cells += [['low', '1–18', ''], ['even', 'Parzyste', ''], ['red', '◆ Czerwone', 'red'], ['black', '◆ Czarne', 'blk'], ['odd', 'Nieparzyste', ''], ['high', '19–36', '']]
      .map(([k, t, c], i) => `<div class="rc sp ${c}" data-b="${k}" style="grid-column:${2 + i * 2}/span 2;grid-row:5">${t}</div>`).join('');
    const ui = C.shell(Roulette, root, {
      betLabel: 'Wartość żetonu',
      controls: `<div class="stat2"><div class="s"><small>Suma zakładów</small><b id="tot">0,00</b></div><div class="s"><small>Żetonów</small><b id="cnt">0</b></div></div>
        <button class="btn go" id="go">Zakręć</button>
        <div class="actions"><button class="btn" id="undo">↶ Cofnij</button><button class="btn" id="clr">Wyczyść</button></div>
        <button class="btn" id="dbl">Podwój wszystkie</button><div class="hint" style="font-size:12px">Kliknij pole, aby postawić żeton. Zakłady zostają na stole po spinie.</div>`,
      stage: `<div class="wheelw" style="width:min(300px,70%)"><div class="ptr">▼</div><svg viewBox="0 0 300 300"><g class="spinner" id="sp" style="transform-origin:150px 150px"></g><circle cx="150" cy="150" r="30" fill="#120e22" stroke="#3a3260" stroke-width="4"/><text id="rn" x="150" y="152" fill="#fff" font-size="26" font-weight="800" text-anchor="middle" dominant-baseline="middle"></text></svg></div>
        <div class="rlast" id="last"></div><div class="rboard" id="bd" style="margin-top:12px">${cells}</div><div class="msg" id="msg" style="margin-top:8px"></div>`
    });
    const sp = $('#sp', root), bd = $('#bd', root), go = $('#go', root), msg = $('#msg', root);
    const a = 360 / 37, P = (ang, r) => [150 + r * Math.sin(ang * Math.PI / 180), 150 - r * Math.cos(ang * Math.PI / 180)];
    sp.innerHTML = ORDER.map((n, i) => {
      const [x1, y1] = P(a * i, 146), [x2, y2] = P(a * (i + 1), 146), [tx, ty] = P(a * (i + .5), 124);
      return `<path d="M150 150L${x1} ${y1}A146 146 0 0 1 ${x2} ${y2}Z" fill="${ncol(n)}" stroke="#120e22" stroke-width="1"/><text x="${tx}" y="${ty}" fill="#fff" font-size="10" font-weight="800" text-anchor="middle" dominant-baseline="middle" transform="rotate(${a * (i + .5)} ${tx} ${ty})">${n}</text>`;
    }).join('') + '<circle cx="150" cy="150" r="148" fill="none" stroke="#c9a14a" stroke-width="4"/>';
    let bets = new Map(), stack = [], busy = false, rot = 0;
    const total = () => r2([...bets.values()].reduce((s, v) => s + v, 0));
    const short = v => v >= 1e6 ? (v / 1e6).toFixed(1) + 'M' : v >= 1e3 ? (v / 1e3).toFixed(v >= 1e4 ? 0 : 1) + 'k' : String(+v.toFixed(2));
    function draw() {
      $$('.rc', bd).forEach(c => { const v = bets.get(c.dataset.b); let ch = $('.ch', c); if (v) { if (!ch) { ch = h('<span class="ch"></span>'); c.appendChild(ch); } ch.textContent = short(v); } else if (ch) ch.remove(); });
      $('#tot', root).textContent = fmt(total()); $('#cnt', root).textContent = stack.length;
    }
    bd.onclick = e => {
      const c = e.target.closest('.rc'); if (!c || busy) return; const v = ui.bet.get();
      if (!v) return; if (total() + v > C.S().balance + 1e-9) return C.toast('Za mało gemów na kolejny żeton', 'err');
      bets.set(c.dataset.b, r2((bets.get(c.dataset.b) || 0) + v)); stack.push([c.dataset.b, v]); sfx.click(); draw();
    };
    $('#undo', root).onclick = () => { if (busy || !stack.length) return; const [k, v] = stack.pop(); const nv = r2(bets.get(k) - v); nv > 0 ? bets.set(k, nv) : bets.delete(k); draw(); };
    $('#clr', root).onclick = () => { if (busy) return; bets.clear(); stack = []; draw(); };
    $('#dbl', root).onclick = () => { if (busy || !bets.size) return; if (total() * 2 > C.S().balance + 1e-9) return C.toast('Za mało gemów', 'err'); [...bets].forEach(([k, v]) => bets.set(k, r2(v * 2))); stack = [...stack, ...stack]; draw(); };
    go.onclick = async () => {
      if (busy) return; const t = total(); if (!t) return C.toast('Postaw żetony na stole', 'err');
      if (!C.take(t)) return; busy = true; go.disabled = true; ui.bet.lock(true); $$('.rc.hl', bd).forEach(c => c.classList.remove('hl')); msg.textContent = 'Kula się toczy…';
      const n = rint(37), i = ORDER.indexOf(n), goal = -(i + .5) * a;
      rot += 360 * 6 + ((((goal - rot) % 360) + 360) % 360); sp.style.transform = `rotate(${rot}deg)`; $('#rn', root).textContent = '';
      for (let k = 0; k < 16; k++) { sfx.tick(); await sleep(k < 9 ? 230 : 160 + k * 18); }
      await sleep(400);
      let pay = 0; bets.forEach((v, k) => { if (k[0] === 'n' ? +k.slice(1) === n : RTEST[k](n)) pay += v * RMULT(k); }); pay = fl2(pay);
      $('#rn', root).textContent = n; const pill = h(`<span style="background:${ncol(n)}">${n}</span>`); $('#last', root).prepend(pill); while ($('#last', root).children.length > 12) $('#last', root).lastChild.remove();
      const hl = [...bets.keys()].filter(k => k[0] === 'n' ? +k.slice(1) === n : RTEST[k](n)); hl.forEach(k => $(`[data-b="${k}"]`, bd).classList.add('hl'));
      msg.innerHTML = `Wypadło <b>${n}</b> ${n === 0 ? '🟢' : RED.has(n) ? '🔴' : '⚫'} — ` + (pay > 0 ? `<span class="grn">wygrywasz ${fmt(pay)} 💎</span>` : '<span class="red">tym razem bez wygranej</span>');
      C.settle(Roulette, t, pay); if (pay > t) C.winPop(ui.stage, pay / t, pay);
      busy = false; go.disabled = false; ui.bet.lock(false);
    };
    C.onLeave(() => { busy = false; });
    draw();
  }
};

/* ================= BLACKJACK ================= */
const BR = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'], BS = ['♠', '♥', '♦', '♣'];
const newShoe = () => { const s = []; for (let d = 0; d < 6; d++) BR.forEach(r => BS.forEach(x => s.push({ r, s: x }))); return shuffle(s); };
const tot = hand => { let t = 0, a = 0; hand.forEach(c => { if (c.r === 'A') { a++; t += 11; } else t += 'JQK'.includes(c.r) || c.r === '10' ? 10 : +c.r; }); while (t > 21 && a) { t -= 10; a--; } return t; };
const Blackjack = {
  id: 'blackjack', name: 'Blackjack', cat: 'table', provider: 'GEMBET', art: ['🃏', '♠️', '♥️'], c: ['#0d9b6a', '#06281e'], hot: true, rtp: '99,4%',
  mount(root) {
    const ui = C.shell(Blackjack, root, {
      controls: `<button class="btn go" id="go">Rozdaj</button>
        <div class="actions"><button class="btn primary" id="hit" disabled>Dobierz</button><button class="btn" id="stand" disabled>Pas</button></div>
        <button class="btn" id="dbl" disabled>Podwój (2×)</button>
        <div class="hint" style="font-size:12px">Blackjack płaci 3:2 · krupier staje na 17 · zakład zwracany przy remisie</div>`,
      stage: `<div class="bj"><div><div class="lab">Krupier</div><div class="hand" id="dh"></div></div><div class="msg" id="msg" style="font-size:18px">Rozdaj karty, aby zacząć</div><div><div class="lab">Ty</div><div class="hand" id="ph"></div></div></div>`
    });
    const go = $('#go', root), hit = $('#hit', root), stand = $('#stand', root), dbl = $('#dbl', root), msg = $('#msg', root);
    let shoe = newShoe(), st = null, alive = true;
    const draw = () => { if (shoe.length < 40) shoe = newShoe(); return shoe.pop(); };
    function render(hide) {
      $('#dh', root).innerHTML = st ? st.d.map((c, i) => C.cardHTML(hide && i === 1 ? null : c)).join('') + `<span class="total">${hide ? tot([st.d[0]]) : tot(st.d)}</span>` : '';
      $('#ph', root).innerHTML = st ? st.p.map(c => C.cardHTML(c)).join('') + `<span class="total">${tot(st.p)}</span>` : '';
    }
    function setBtns(on) { hit.disabled = stand.disabled = !on; dbl.disabled = !(on && st && st.p.length === 2 && !st.dbl && C.S().balance >= st.bet); go.disabled = on; ui.bet.lock(on); }
    function finish(pay, text) {
      st.over = true; C.settle(Blackjack, st.stake, pay); setBtns(false); render(false);
      msg.innerHTML = text; if (pay > st.stake) C.winPop(ui.stage, pay / st.stake, pay);
    }
    async function dealerPlay() {
      setBtns(false); render(false); await sleep(450);
      while (alive && tot(st.d) < 17) { st.d.push(draw()); sfx.tick(); render(false); await sleep(550); }
      if (!alive) return;
      const p = tot(st.p), d = tot(st.d), s = st.stake;
      if (d > 21) finish(s * 2, `<span class="grn">Krupier przekroczył (${d}) — wygrywasz!</span>`);
      else if (p > d) finish(s * 2, `<span class="grn">${p} vs ${d} — wygrywasz!</span>`);
      else if (p === d) finish(s, `Remis ${p}:${d} — zwrot zakładu`);
      else finish(0, `<span class="red">${p} vs ${d} — wygrywa krupier</span>`);
    }
    go.onclick = async () => {
      const bet = ui.bet.get(); if (!C.take(bet)) return;
      st = { p: [draw(), draw()], d: [draw(), draw()], bet, stake: bet, dbl: false, over: false };
      go.disabled = true; ui.bet.lock(true); msg.textContent = ''; render(true); sfx.tick();
      const pb = tot(st.p) === 21, db = tot(st.d) === 21;
      if (pb || db) { await sleep(500); if (pb && db) finish(bet, 'Obaj mają blackjacka — remis'); else if (pb) finish(bet * 2.5, '<span class="grn">BLACKJACK! Płatność 3:2</span>'); else finish(0, '<span class="red">Krupier ma blackjacka</span>'); return; }
      msg.textContent = 'Twój ruch'; setBtns(true);
    };
    hit.onclick = () => {
      st.p.push(draw()); sfx.tick(); render(true); const t = tot(st.p);
      if (t > 21) finish(0, `<span class="red">Przekroczyłeś (${t})</span>`); else if (t === 21) dealerPlay(); else setBtns(true);
    };
    stand.onclick = () => dealerPlay();
    dbl.onclick = () => {
      if (!C.take(st.bet)) return; st.stake += st.bet; st.dbl = true; st.p.push(draw()); render(true); const t = tot(st.p);
      if (t > 21) finish(0, `<span class="red">Przekroczyłeś (${t})</span>`); else dealerPlay();
    };
    C.onLeave(() => { alive = false; if (st && !st.over) { st.over = true; C.credit(st.stake); } });
    setBtns(false);
  }
};

/* ================= SLOTY ================= */
const SE = window.SlotEngine;
const THEMES = [
  { id: 'gemrush', name: 'Gem Rush', syms: ['💠', '🔶', '🟢', '🔴', '👑', '💎'], wild: '🌈', scat: '⭐', c: ['#18a9ff', '#0b1f5c'], art: ['💎', '🌈', '⭐'], glow: '#38d6ff', hot: true },
  { id: 'fruit', name: 'Fruit Fiesta', syms: ['🍒', '🍋', '🍊', '🍇', '🍉', '🔔'], wild: '🃏', scat: '🍀', c: ['#ff4f9a', '#5c0f3a'], art: ['🍓', '🍒', '🍉'], glow: '#ff4f9a' },
  { id: 'dragon', name: 'Smocze Skarby', syms: ['🪙', '🍺', '🗡️', '🛡️', '👑', '🐉'], wild: '🔥', scat: '🥚', c: ['#e8502a', '#4a1008'], art: ['🐉', '🔥', '👑'], glow: '#ff6a3d' },
  { id: 'pharaoh', name: 'Księga Faraona', syms: ['🪲', '🐫', '🏺', '🐍', '👁️', '🔱'], wild: '📜', scat: '☀️', c: ['#e0b13a', '#4a3205'], art: ['🏺', '☀️', '👁️'], glow: '#ffc83d' }
];
const BUY = 20;
function makeSlot(T) {
  const G = {
    id: 'slot-' + T.id, name: T.name, cat: 'slots', provider: 'GEMBET SLOTS', art: T.art, c: T.c, hot: T.hot, rtp: '96%',
    mount(root) {
      const sym = i => i === SE.WILD ? T.wild : i === SE.SCAT ? T.scat : T.syms[i];
      const ui = C.shell(G, root, {
        controls: `<button class="btn go" id="go">Zakręć</button>
          <div class="actions"><button class="btn" id="turbo">⚡ Turbo: wył.</button><button class="btn" id="buy">Kup FS (${BUY}×)</button></div>
          <div class="stat2"><div class="s"><small>Ostatnia wygrana</small><b id="lw">0,00</b></div><div class="s"><small>Linie</small><b>10</b></div></div>
          <div class="hint" style="font-size:12px">${T.wild} wild zastępuje symbole · 3+ ${T.scat} = Free Spins z mnożnikiem ${SE.FS_MULT}×</div>`,
        stage: `<div class="slot" id="slot" style="--sc:${T.glow}"><div class="reels" id="reels">${'<div class="reel"><div class="strip"></div></div>'.repeat(5)}</div>
          <div class="slotinfo"><span id="sm">Powodzenia!</span><span id="fsb"></span></div></div>
          <div class="ptab">${T.syms.map((s, i) => [s, SE.PAY[i]]).reverse().map(([s, p]) => `<div><span>${s}</span><span class="dim">${p.map(x => fmtM(x / 10)).join(' · ')}×</span></div>`).join('')}<div><span>${T.wild}</span><span class="dim">Wild</span></div><div><span>${T.scat}</span><span class="dim">Scatter</span></div></div>`
      });
      const reels = $$('.reel', root), strips = $$('.strip', root), slot = $('#slot', root), go = $('#go', root), sm = $('#sm', root);
      let cell = 92, grid = SE.spin(rnd), busy = false, turbo = false, alive = true;
      const size = () => { cell = Math.max(54, Math.min(100, (slot.clientWidth - 24 - 24) / 5)); slot.style.setProperty('--ch', cell + 'px'); };
      const colHTML = col => col.map(s => `<div class="sc">${sym(s)}</div>`).join('');
      const fill = () => strips.forEach((s, i) => { s.style.transition = 'none'; s.style.transform = 'none'; s.innerHTML = colHTML(grid[i]); });
      const onResize = () => size(); window.addEventListener('resize', onResize); C.onLeave(() => { alive = false; window.removeEventListener('resize', onResize); });
      size(); fill();
      async function animate(g) {
        if (!alive) { grid = g; return; }
        const fast = turbo;
        strips.forEach((s, i) => {
          const n = (fast ? 6 : 12) + i * (fast ? 1 : 5), mid = Array.from({ length: n }, () => `<div class="sc">${sym(rint(8))}</div>`).join('');
          s.style.transition = 'none'; s.style.transform = 'none'; s.innerHTML = colHTML(grid[i]) + mid + colHTML(g[i]); void s.offsetHeight;
          s.style.transition = `transform ${(fast ? .35 : .75) + i * (fast ? .06 : .22)}s cubic-bezier(.2,.75,.25,1)`; s.style.transform = `translateY(${-(3 + n) * cell}px)`;
        });
        for (let i = 0; i < 5; i++) { await sleep(fast ? 90 : 240); sfx.tick(); }
        await sleep(fast ? 250 : 550);
        grid = g; fill();
      }
      function show(ev) {
        $$('.sc', root).forEach(c => c.classList.remove('win', 'dim'));
        const hit = new Set(); ev.wins.forEach(w => w.cells.forEach(([r, y]) => hit.add(r * 3 + y))); if (ev.scatters >= 3) ev.scCells.forEach(([r, y]) => hit.add(r * 3 + y));
        if (!hit.size) return;
        reels.forEach((r, i) => $$('.sc', r).forEach((c, j) => c.classList.add(hit.has(i * 3 + j) ? 'win' : 'dim')));
      }
      async function round(buy) {
        if (busy) return false;
        const bet = ui.bet.get(), stake = buy ? r2(bet * BUY) : bet; if (!C.take(stake)) return false;
        busy = true; go.disabled = true; ui.bet.lock(true); $('#buy', root).disabled = true; $$('.sc', root).forEach(c => c.classList.remove('win', 'dim'));
        let total = 0, fs = 0, spun = 0;
        sm.textContent = 'Kręcimy…'; $('#fsb', root).innerHTML = '';
        if (buy) fs = 10; else {
          const g = SE.spin(rnd); await animate(g);
          const ev = SE.evaluate(g, bet, 1); total += ev.total; fs = ev.fs; show(ev);
          sm.innerHTML = ev.total ? `<span class="grn">Wygrana: ${fmt(ev.total)} 💎</span>` : 'Brak wygranej';
          if (ev.total) sfx.win();
        }
        if (fs > 0) {
          sm.innerHTML = '🎉 <b class="gold">FREE SPINS!</b>'; if (alive) { C.toast(`🎰 ${fs} Free Spinów, wygrane ×${SE.FS_MULT}!`, 'win'); await sleep(1200); }
          while (fs > 0) {
            fs--; spun++; $('#fsb', root).innerHTML = `<span class="fsbadge">FS: pozostało ${fs} · ×${SE.FS_MULT}</span>`;
            const g = SE.spin(rnd); await animate(g);
            const ev = SE.evaluate(g, bet, SE.FS_MULT); total += ev.total; fs += ev.fs; show(ev);
            sm.innerHTML = `Suma FS: <b class="grn">${fmt(total)} 💎</b>`; if (alive) await sleep(turbo ? 300 : 850);
          }
        }
        total = fl2(total);
        C.settle(G, stake, total); if (!alive) return false;
        $('#lw', root).textContent = fmt(total); $('#fsb', root).innerHTML = '';
        sm.innerHTML = total > 0 ? `<span class="grn">Łącznie: ${fmt(total)} 💎 (${fmt(total / stake)}×)</span>` : 'Brak wygranej';
        if (total > stake) C.winPop(ui.stage, total / stake, total);
        busy = false; go.disabled = false; ui.bet.lock(false); $('#buy', root).disabled = false; return true;
      }
      go.onclick = () => round(false);
      $('#buy', root).onclick = () => { if (confirm(`Kupić Free Spins za ${fmt(ui.bet.get() * BUY)} gemów?`)) round(true); };
      $('#turbo', root).onclick = e => { turbo = !turbo; e.target.textContent = '⚡ Turbo: ' + (turbo ? 'wł.' : 'wył.'); };
      C.auto(ui.ctrl, () => round(false), { n: 10, gap: 400 });
      const keyH = e => { if (e.code === 'Space' && !/INPUT|SELECT|BUTTON/.test(document.activeElement.tagName)) { e.preventDefault(); round(false); } };
      document.addEventListener('keydown', keyH); C.onLeave(() => document.removeEventListener('keydown', keyH));
    }
  };
  return G;
}

[Roulette, Blackjack, ...THEMES.map(makeSlot)].forEach(g => C.register(g));
})();
