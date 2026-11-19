/* GEMBET core — portfel, RNG, UI helpery, rejestr gier. Tylko wirtualne gemy. */
(() => {
'use strict';
const KEY = 'gembet.v1', START = 10000, MIN_BET = 0.1;
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const h = html => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; };
const sleep = ms => new Promise(r => setTimeout(r, ms));
function rnd() { const a = new Uint32Array(2); crypto.getRandomValues(a); return (a[0] * 2097152 + (a[1] >>> 11)) / 9007199254740992; }
const rint = n => Math.floor(rnd() * n);
const pick = a => a[rint(a.length)];
const shuffle = a => { for (let i = a.length - 1; i > 0; i--) { const j = rint(i + 1); [a[i], a[j]] = [a[j], a[i]]; } return a; };
const r2 = x => Math.round(x * 100) / 100;
const fl2 = x => Math.floor(x * 100 + 1e-7) / 100;
const fmt = (n, d = 2) => Number(n).toLocaleString('pl-PL', { minimumFractionDigits: d, maximumFractionDigits: d });
const fmtM = m => m >= 1000 ? fmt(m, 0) : m >= 100 ? fmt(m, 1) : fmt(m, 2);
const GEM = '<i class="gem"></i>';
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

/* ---------- stan ---------- */
const NAMES = ['Gracz', 'Rekin', 'Wieloryb', 'Lucky', 'Diamond', 'Hazard', 'Krupier', 'Joker', 'Szczęściarz', 'Ninja', 'Tygrys', 'Smok'];
let S = {
  balance: START, wagered: 0, bets: 0, wins: 0, profit: 0, bestWin: 0, bestMult: 0, lastDaily: 0, mute: false,
  name: pick(NAMES) + rint(9000 + 1000), hist: [START], games: {}
};
try { const raw = localStorage.getItem(KEY); if (raw) S = Object.assign(S, JSON.parse(raw)); } catch (e) {}
const save = () => { try { localStorage.setItem(KEY, JSON.stringify(S)); } catch (e) {} };

const LEVELS = [
  ['Brąz', 0, '🥉'], ['Srebro', 5e3, '🥈'], ['Złoto', 25e3, '🥇'], ['Platyna', 1e5, '💠'],
  ['Diament', 5e5, '💎'], ['Obsydian', 2e6, '🔮'], ['Legenda', 1e7, '👑']
];
const levelOf = w => { let i = 0; LEVELS.forEach((l, k) => { if (w >= l[1]) i = k; }); return i; };

/* ---------- dźwięk ---------- */
let AC;
function tone(f, d = .08, type = 'sine', v = .04, delay = 0) {
  if (S.mute) return;
  try {
    AC = AC || new (window.AudioContext || window.webkitAudioContext)();
    const o = AC.createOscillator(), g = AC.createGain(), t = AC.currentTime + delay;
    o.type = type; o.frequency.value = f; g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(.0001, t + d);
    o.connect(g).connect(AC.destination); o.start(t); o.stop(t + d + .02);
  } catch (e) {}
}
const sfx = {
  click: () => tone(420, .05, 'square', .02),
  tick: () => tone(700 + Math.random() * 200, .04, 'triangle', .03),
  gem: n => tone(520 + (n || 0) * 70, .12, 'sine', .05),
  win: () => { tone(660, .12); tone(880, .14, 'sine', .04, .09); tone(1170, .25, 'sine', .04, .18); },
  big: () => { [523, 659, 784, 1046, 1318].forEach((f, i) => tone(f, .2, 'triangle', .05, i * .08)); },
  lose: () => { tone(200, .25, 'sawtooth', .03); tone(140, .3, 'sawtooth', .03, .12); },
  boom: () => { tone(90, .4, 'sawtooth', .07); tone(60, .5, 'square', .05, .05); }
};

/* ---------- toasty ---------- */
function toast(msg, type = '') {
  const t = h(`<div class="toast ${type}">${msg}</div>`);
  $('#toasts').appendChild(t); setTimeout(() => t.remove(), 3200);
}

/* ---------- portfel ---------- */
const balListeners = [];
function renderBalance(dir) {
  const b = $('#bal'); if (!b) return;
  b.textContent = fmt(S.balance);
  const box = $('#balbox');
  if (dir) { box.classList.remove('up', 'down'); void box.offsetWidth; box.classList.add(dir); }
  renderVip();
  balListeners.forEach(f => f());
}
function renderVip() {
  const v = $('#vip'); if (!v) return;
  const i = levelOf(S.wagered), cur = LEVELS[i], nxt = LEVELS[i + 1];
  const pct = nxt ? clamp((S.wagered - cur[1]) / (nxt[1] - cur[1]) * 100, 0, 100) : 100;
  v.innerHTML = `<div class="lv"><span class="badge">${cur[2]}</span><div>${S.name}<br><small>Ranga: ${cur[0]}</small></div></div>
    <div class="bar"><i style="width:${pct}%"></i></div>
    <small>${nxt ? `${fmt(S.wagered, 0)} / ${fmt(nxt[1], 0)} obrotu do „${nxt[0]}”` : 'Maksymalna ranga!'}</small>`;
}
function take(bet) {
  bet = r2(bet);
  if (!(bet >= MIN_BET)) { toast(`Minimalny zakład to ${MIN_BET} 💎`, 'err'); return false; }
  if (bet > S.balance + 1e-9) { toast('Za mało gemów! Otwórz Portfel po darmowe 💎', 'err'); return false; }
  S.balance = r2(S.balance - bet); save(); renderBalance('down'); return true;
}
function credit(amount) { if (amount > 0) { S.balance = r2(S.balance + amount); save(); renderBalance('up'); } }

/* ---------- feed (żywe zakłady) ---------- */
const BOT_NAMES = ['Ukryty', 'moon_walker', 'KrolHazardu', 'gemhunter', 'Ola_X', 'DiamondHands', 'zbyszek77', 'rekin', 'RNGesus', 'Wojtek', 'lucky_luke', 'NocnyGracz', 'Mati', 'cashout_kid', 'highroller', 'Kasia_PL', 'vegas', 'blokus', 'degen', 'hodl'];
const feed = { rows: [], subs: [] };
function pushFeed(row) {
  feed.rows.unshift(row); if (feed.rows.length > 40) feed.rows.pop();
  feed.subs.forEach(f => f(row));
}
function botBet() {
  const gs = Casino.games; if (!gs.length) return;
  const g = pick(gs);
  const bet = r2(Math.exp(Math.log(1) + rnd() * Math.log(8000)));
  const roll = rnd(); let mult = 0;
  if (roll > .56) mult = r2(1.1 + rnd() * 2.4);
  if (roll > .9) mult = r2(3 + rnd() * 12);
  if (roll > .985) mult = r2(20 + rnd() * 400);
  pushFeed({ user: rnd() < .12 ? 'Ukryty' : pick(BOT_NAMES), game: g.name, bet, mult, payout: r2(bet * mult), me: false });
}
setInterval(botBet, 1700);

function feedEl(gameName) {
  const el = h(`<div class="feed"><div class="tabs"><span class="live">Na żywo — zakłady</span></div>
    <table><thead><tr><th>Gra</th><th>Gracz</th><th>Zakład</th><th>Mnożnik</th><th>Wypłata</th></tr></thead><tbody></tbody></table></div>`);
  const tb = $('tbody', el);
  const rowHtml = (r, nw) => `<tr class="${r.me ? 'me' : ''} ${nw ? 'new' : ''}"><td>${r.game}</td><td>${r.me ? '<b>' + r.user + '</b>' : r.user}</td>
    <td>${fmt(r.bet)} ${GEM}</td><td>${fmtM(r.mult)}×</td><td class="${r.payout > r.bet ? 'grn' : 'dim'}">${fmt(r.payout)} ${GEM}</td></tr>`;
  const draw = () => { tb.innerHTML = feed.rows.slice(0, 10).map(r => rowHtml(r)).join(''); };
  draw();
  const sub = row => {
    if (!el.isConnected) return;
    tb.insertAdjacentHTML('afterbegin', rowHtml(row, true));
    while (tb.children.length > 10) tb.lastElementChild.remove();
  };
  feed.subs.push(sub);
  Casino.onLeave(() => { feed.subs = feed.subs.filter(f => f !== sub); });
  return el;
}

/* ---------- rozliczenie ---------- */
function settle(game, bet, payout) {
  payout = fl2(payout); bet = r2(bet);
  const mult = bet > 0 ? payout / bet : 0;
  S.wagered = r2(S.wagered + bet); S.bets++; S.profit = r2(S.profit + payout - bet);
  if (payout > bet) { S.wins++; S.bestWin = Math.max(S.bestWin, r2(payout - bet)); }
  S.bestMult = Math.max(S.bestMult, mult);
  const g = S.games[game.id] || (S.games[game.id] = { bets: 0, wagered: 0, profit: 0 });
  g.bets++; g.wagered = r2(g.wagered + bet); g.profit = r2(g.profit + payout - bet);
  const prevLv = levelOf(S.wagered - bet);
  if (payout > 0) { S.balance = r2(S.balance + payout); }
  S.hist.push(S.balance); if (S.hist.length > 300) S.hist.shift();
  save(); renderBalance(payout > bet ? 'up' : payout < bet ? 'down' : '');
  pushFeed({ user: S.name, game: game.name, bet, mult, payout, me: true });
  const lv = levelOf(S.wagered);
  if (lv > prevLv) toast(`🎉 Awans! Nowa ranga: <b>${LEVELS[lv][0]}</b> ${LEVELS[lv][2]}`, 'win');
  if (payout > bet) { mult >= 10 ? sfx.big() : sfx.win(); } else if (payout < bet) sfx.lose();
}

function winPop(stage, mult, payout) {
  $$('.winpop', stage).forEach(e => e.remove());
  const p = h(`<div class="winpop"><b>${fmtM(mult)}×</b><span>${fmt(payout)} ${GEM}</span></div>`);
  stage.appendChild(p); setTimeout(() => p.remove(), 2300);
}

/* ---------- komponenty UI ---------- */
function betField(label = 'Kwota zakładu') {
  const el = h(`<div class="field"><label><span>${label}</span><span class="hint" data-usd></span></label>
    <div class="amt">${GEM}<input type="text" inputmode="decimal" value="10.00" aria-label="Kwota zakładu">
    <button data-k="half">½</button><button data-k="dbl">2×</button><button data-k="max">Max</button></div></div>`);
  const inp = $('input', el);
  const set = v => { inp.value = fmtInput(clamp(r2(v), 0, 1e9)); };
  const fmtInput = v => v.toFixed(2);
  const api = {
    el, input: inp,
    get() { const v = parseFloat(String(inp.value).replace(',', '.')); return isFinite(v) && v > 0 ? r2(v) : 0; },
    set,
    lock(b) { $$('input,button', el).forEach(x => x.disabled = b); }
  };
  el.addEventListener('click', e => {
    const k = e.target.dataset.k; if (!k) return; sfx.click();
    if (k === 'half') set(api.get() / 2);
    if (k === 'dbl') set(api.get() * 2);
    if (k === 'max') set(S.balance);
  });
  inp.addEventListener('blur', () => set(api.get()));
  return api;
}

/* szkielet strony gry */
function shell(g, root, { controls = '', stage = '', cls = '', betLabel } = {}) {
  root.innerHTML = `<div class="gp"><div class="game ${cls}"><aside class="ctrl"><div class="slot-bet"></div>${controls}</aside>
    <section class="stage">${stage}</section></div>
    <div class="gbar"><div><b>${g.name}</b><span class="tag">${g.provider}</span></div><div class="dim">RTP ${g.rtp || '99%'} · tylko wirtualne gemy</div></div>
    <div class="feedwrap"></div></div>`;
  const bet = betField(betLabel);
  $('.slot-bet', root).replaceWith(bet.el);
  $('.feedwrap', root).replaceWith(feedEl(g.name));
  return { root, ctrl: $('.ctrl', root), stage: $('.stage', root), bet };
}

/* przycisk autobet; run() => Promise<boolean|void>, false przerywa */
function auto(ctrl, run, opts = {}) {
  const box = h(`<div class="auto"><div class="lbl"><span>Autobet</span><span class="hint">0 = bez końca</span></div>
    <div class="r"><div class="amt plain"><input type="number" min="0" value="${opts.n ?? 10}" aria-label="Liczba rund"><span class="suf">rund</span></div>
    <button class="btn" style="width:120px">Start</button></div></div>`);
  ctrl.appendChild(box);
  const btn = $('button', box), inp = $('input', box);
  let on = false, alive = true;
  Casino.onLeave(() => { alive = false; on = false; });
  btn.onclick = async () => {
    if (on) { on = false; btn.textContent = 'Start'; return; }
    on = true; btn.textContent = 'Stop';
    let n = parseInt(inp.value) || 0, i = 0;
    while (on && alive && (n === 0 || i < n)) {
      const ok = await run(); i++;
      if (ok === false) break;
      await sleep(opts.gap ?? 120);
    }
    on = false; if (alive) btn.textContent = 'Start';
  };
  return { stop() { on = false; }, get running() { return on; } };
}

function pills(stage) {
  const p = h('<div class="pills"></div>'); stage.appendChild(p);
  return (m, win) => { p.prepend(h(`<span class="${win ? 'w' : ''}">${fmtM(m)}×</span>`)); while (p.children.length > 8) p.lastChild.remove(); };
}

function seg(options, val, onChange) {
  const el = h(`<div class="seg">${options.map(([k, t]) => `<button data-v="${k}" class="${k == val ? 'on' : ''}">${t}</button>`).join('')}</div>`);
  el.addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b || b.disabled) return;
    $$('button', el).forEach(x => x.classList.toggle('on', x === b)); sfx.click(); onChange(b.dataset.v);
  });
  return el;
}

function cardHTML(c, cls = '') {
  if (!c) return `<div class="card back ${cls}"></div>`;
  const red = c.s === '♥' || c.s === '♦';
  return `<div class="card ${red ? 'r' : ''} ${cls}"><div class="tl">${c.r}<i>${c.s}</i></div><div class="ct">${c.s}</div></div>`;
}

/* ---------- rejestr gier i router ---------- */
let leaveFns = [];
const Casino = {
  games: [], S: () => S, $, $$, h, sleep, rnd, rint, pick, shuffle, r2, fl2, fmt, fmtM, GEM, clamp, sfx, toast,
  take, credit, settle, winPop, betField, shell, auto, pills, seg, cardHTML, tone, MIN_BET, START, LEVELS, levelOf,
  register(g) { this.games.push(g); },
  onLeave(fn) { leaveFns.push(fn); },
  runLeave() { const f = leaveFns; leaveFns = []; f.forEach(fn => { try { fn(); } catch (e) { console.error(e); } }); },
  save, renderBalance, onBalance(fn) { balListeners.push(fn); },
  reset() { S.balance = START; S.hist = [START]; save(); renderBalance(); },
  resetAll() { const n = S.name; S = Object.assign({}, S, { balance: START, wagered: 0, bets: 0, wins: 0, profit: 0, bestWin: 0, bestMult: 0, hist: [START], games: {} }); S.name = n; save(); renderBalance(); },
  claimDaily() {
    const left = 864e5 - (Date.now() - S.lastDaily);
    if (left > 0) { toast(`Bonus dostępny za ${Math.ceil(left / 36e5)} h`, 'err'); return false; }
    S.lastDaily = Date.now(); credit(1000); toast('🎁 +1 000 💎 bonus dzienny!', 'win'); return true;
  },
  feedRows: () => feed.rows, botBet, feedEl
};
window.Casino = Casino;
})();
