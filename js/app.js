/* App: nawigacja, lobby, statystyki, portfel */
(() => {
'use strict';
const C = Casino, { $, $$, h, GEM, fmt, fmtM } = C;
const CATS = { originals: ['Originals', '🎯'], slots: ['Sloty', '🎰'], table: ['Stołowe', '🃏'] };
const S = C.S;

const tile = g => `<a class="tile" href="#/g/${g.id}" style="--c1:${g.c[0]};--c2:${g.c[1]}">
  <span class="prov">${g.provider}</span>${g.hot ? '<span class="hot">HOT</span>' : ''}
  <div class="art">${g.art[0]}<small>${g.art[1] || ''}</small><small>${g.art[2] || ''}</small></div><div class="tn">${g.name}</div></a>`;
const byCat = c => C.games.filter(g => g.cat === c);

/* nawigacja */
function buildNav() {
  const link = (href, ic, t, key) => `<a href="${href}" data-k="${key}"><span class="ic">${ic}</span>${t}</a>`;
  $('#nav').innerHTML = `<div class="sec">Menu</div>${link('#/', '🏠', 'Lobby', 'home')}${Object.entries(CATS).map(([k, [t, ic]]) => link('#/c/' + k, ic, t, 'c:' + k)).join('')}${link('#/stats', '📊', 'Statystyki', 'stats')}
    <div class="sec">Wszystkie gry</div>${C.games.map(g => link('#/g/' + g.id, g.art[0], g.name, 'g:' + g.id)).join('')}`;
}
const setNav = key => $$('#nav a').forEach(a => a.classList.toggle('on', a.dataset.k === key));

/* lobby */
function lobby(v) {
  const bonusReady = Date.now() - S().lastDaily > 864e5;
  v.innerHTML = `<div class="hero">
      <div class="promo a"><span class="big">💎</span><h2>10 000 gemów na start</h2><p>Graj w ${C.games.length} gier za wirtualne gemy. Zero prawdziwych pieniędzy, 100% zabawy.</p><div><a class="btn primary" href="#/g/mines">Zagraj w Mines</a></div></div>
      <div class="promo b"><span class="big">🎁</span><h2>Bonus dzienny</h2><p>+1 000 💎 co 24 godziny.</p><div><button class="btn" id="bn">${bonusReady ? 'Odbierz teraz' : 'Odebrany'}</button></div></div>
      <div class="promo c"><span class="big">🚀</span><h2>Crash &amp; Plinko</h2><p>Najgorętsze gry tygodnia.</p><div><a class="btn" href="#/g/crash">Zobacz</a></div></div></div>
    <div class="sec-h" style="margin-top:0"><h3>🔥 Popularne</h3></div><div class="row">${C.games.filter(g => g.hot).map(tile).join('')}</div>
    ${Object.entries(CATS).map(([k, [t, ic]]) => `<div class="sec-h"><h3>${ic} ${t}</h3><a href="#/c/${k}">Zobacz wszystkie →</a></div><div class="row">${byCat(k).map(tile).join('')}</div>`).join('')}
    <div class="sec-h"><h3>⚡ Zakłady na żywo</h3></div><div id="lf"></div>`;
  $('#lf', v).replaceWith(C.feedEl());
  $('#bn', v).onclick = e => { if (C.claimDaily()) e.target.textContent = 'Odebrany'; };
}

function catPage(v, k) {
  const [t, ic] = CATS[k]; v.innerHTML = `<h1 class="page-h">${ic} ${t}</h1><div class="grid">${byCat(k).map(tile).join('')}</div>`;
}
function searchPage(v, q) {
  const r = C.games.filter(g => g.name.toLowerCase().includes(q.toLowerCase()));
  v.innerHTML = `<h1 class="page-h">Wyniki: „${q.replace(/[<>&]/g, '')}”</h1>` + (r.length ? `<div class="grid">${r.map(tile).join('')}</div>` : '<div class="empty">Nic nie znaleziono 🤷</div>');
}

/* strona gry */
function gamePage(v, id) {
  const g = C.games.find(x => x.id === id); if (!g) { v.innerHTML = '<div class="empty">Nie ma takiej gry.</div>'; return; }
  document.title = `${g.name} — GEMBET`;
  v.innerHTML = `<div class="crumb"><a href="#/">Lobby</a> › <a href="#/c/${g.cat}">${CATS[g.cat][0]}</a> › <b style="color:#fff">${g.name}</b></div><div id="gm"></div>
    <div class="sec-h"><h3>Więcej gier</h3></div><div class="row">${C.games.filter(x => x.id !== id).sort(() => Math.random() - .5).slice(0, 10).map(tile).join('')}</div>`;
  g.mount($('#gm', v));
}

/* statystyki */
function stats(v) {
  const s = S(), hist = s.hist, mx = Math.max(...hist, 1), mn = Math.min(...hist, 0), W = 800, H = 180;
  const pts = hist.map((b, i) => `${(i / Math.max(1, hist.length - 1)) * W},${H - 10 - (b - mn) / ((mx - mn) || 1) * (H - 20)}`).join(' ');
  const rows = Object.entries(s.games).map(([id, g]) => [C.games.find(x => x.id === id), g]).filter(x => x[0]).sort((a, b) => b[1].wagered - a[1].wagered);
  v.innerHTML = `<h1 class="page-h">📊 Statystyki</h1><div class="stats">
    <div class="s"><small>Saldo</small><b>${fmt(s.balance)} ${GEM}</b></div><div class="s"><small>Obrót</small><b>${fmt(s.wagered, 0)} ${GEM}</b></div>
    <div class="s"><small>Zakłady</small><b>${s.bets}</b></div><div class="s"><small>Wygrane</small><b>${s.wins} <span class="dim" style="font-size:16px">(${s.bets ? fmt(s.wins / s.bets * 100, 0) : 0}%)</span></b></div>
    <div class="s"><small>Zysk / strata</small><b class="${s.profit >= 0 ? 'grn' : 'red'}">${s.profit >= 0 ? '+' : ''}${fmt(s.profit)}</b></div>
    <div class="s"><small>Najlepsza wygrana</small><b class="gold">${fmt(s.bestWin)}</b></div><div class="s"><small>Najwyższy mnożnik</small><b>${fmtM(s.bestMult)}×</b></div></div>
    <div class="panel"><h4>Historia salda</h4><svg class="svgchart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none"><polyline points="${pts}" fill="none" stroke="#9d6bff" stroke-width="2.5" vector-effect="non-scaling-stroke"/></svg></div>
    <div class="panel"><h4>Gry</h4>${rows.length ? `<table><tr><th>Gra</th><th>Zakłady</th><th>Obrót</th><th>Wynik</th></tr>${rows.map(([g, x]) => `<tr><td><a href="#/g/${g.id}">${g.art[0]} ${g.name}</a></td><td>${x.bets}</td><td>${fmt(x.wagered, 0)}</td><td class="${x.profit >= 0 ? 'grn' : 'red'}">${x.profit >= 0 ? '+' : ''}${fmt(x.profit)}</td></tr>`).join('')}</table>` : '<div class="empty">Zagraj w coś, aby zobaczyć statystyki.</div>'}</div>
    <button class="btn" id="rs">Zeruj statystyki</button>`;
  $('#rs', v).onclick = () => { if (confirm('Wyzerować statystyki i saldo do 10 000 gemów?')) { C.resetAll(); stats(v); } };
}

/* portfel */
function wallet() {
  const m = $('#modal'), left = 864e5 - (Date.now() - S().lastDaily);
  m.innerHTML = `<div class="dlg"><h3>Portfel gemów</h3><div class="dim">Tylko wirtualna waluta — brak wpłat i wypłat.</div>
    <div class="bigbal">${GEM}<span id="wb">${fmt(S().balance)}</span></div>
    <div class="opt"><div><b>🎁 Bonus dzienny</b><small>+1 000 💎 co 24 h${left > 0 ? ` (za ${Math.ceil(left / 36e5)} h)` : ''}</small></div><button class="btn primary sm" id="wd" ${left > 0 ? 'disabled' : ''}>Odbierz</button></div>
    <div class="opt"><div><b>🔄 Reset do 10 000 💎</b><small>Przywróć saldo startowe (stats zostają)</small></div><button class="btn sm" id="wr">Reset</button></div>
    <div style="text-align:right;margin-top:16px"><button class="btn ghost" id="wx">Zamknij</button></div></div>`;
  m.classList.add('on');
  const close = () => m.classList.remove('on');
  $('#wx', m).onclick = close; m.onclick = e => { if (e.target === m) close(); };
  $('#wd', m).onclick = () => { if (C.claimDaily()) wallet(); };
  $('#wr', m).onclick = () => { C.reset(); wallet(); C.toast('Saldo zresetowane do 10 000 💎', 'win'); };
}

/* router */
let q = '';
function route() {
  C.runLeave(); document.body.classList.remove('menu'); document.title = 'GEMBET — fake kasyno na gemy';
  const v = $('#view'); v.innerHTML = ''; window.scrollTo(0, 0);
  const [, a, b] = (location.hash.slice(1) || '/').split('/');
  if (a === 'g') { setNav('g:' + b); gamePage(v, b); }
  else if (a === 'c' && CATS[b]) { setNav('c:' + b); catPage(v, b); }
  else if (a === 'stats') { setNav('stats'); stats(v); }
  else if (a === 's') { setNav(''); searchPage(v, decodeURIComponent(b || '')); }
  else { setNav('home'); lobby(v); }
}

/* init */
buildNav(); C.renderBalance();
for (let i = 0; i < 12; i++) C.botBet();
$('#walletBtn').onclick = wallet; $('#balbox').onclick = wallet;
$('#burger').onclick = () => document.body.classList.toggle('menu'); $('#scrim').onclick = () => document.body.classList.remove('menu');
const mute = $('#muteBtn'); mute.textContent = S().mute ? '🔇' : '🔊';
mute.onclick = () => { S().mute = !S().mute; C.save(); mute.textContent = S().mute ? '🔇' : '🔊'; };
$('#q').oninput = e => { q = e.target.value.trim(); location.hash = q ? '#/s/' + encodeURIComponent(q) : '#/'; };
let warned = false;
C.onBalance(() => { if (S().balance < C.MIN_BET && !warned) { warned = true; setTimeout(() => { C.toast('Skończyły Ci się gemy! Odbierz bonus lub zresetuj saldo.', 'err'); wallet(); }, 900); } if (S().balance >= C.MIN_BET) warned = false; });
window.addEventListener('hashchange', route); route();
})();
