/* Silnik slotów 5x3, 10 linii, wild + scatter + free spins. Czysta logika (testowalna w node). */
(function (root) {
  const LINES = [[1,1,1,1,1],[0,0,0,0,0],[2,2,2,2,2],[0,1,2,1,0],[2,1,0,1,2],[0,0,1,2,2],[2,2,1,0,0],[1,0,0,0,1],[1,2,2,2,1],[0,1,1,1,0]];
  // symbole 0..5 (od najniższego), 6 = WILD, 7 = SCATTER
  const WEIGHTS = [26, 23, 20, 14, 9, 5];
  const WILD = 6, SCAT = 7, WILD_W = 3.2, SCAT_W = 2.3;
  const PAY = [[6, 19, 55], [7, 21, 65], [12, 36, 105], [18, 60, 210], [36, 130, 520], [72, 300, 1800]];
  const SCAT_PAY = { 3: 2, 4: 10, 5: 50 }, FS_AWARD = { 3: 8, 4: 12, 5: 20 };
  const FS_MULT = 2;

  function cell(rnd, reel) {
    const wild = (reel >= 1 && reel <= 3) ? WILD_W : 0;
    const tot = WEIGHTS.reduce((a, b) => a + b, 0) + wild + SCAT_W;
    let r = rnd() * tot;
    for (let i = 0; i < 6; i++) { if ((r -= WEIGHTS[i]) < 0) return i; }
    if (wild && (r -= wild) < 0) return WILD;
    return SCAT;
  }
  function spin(rnd) { return Array.from({ length: 5 }, (_, r) => Array.from({ length: 3 }, () => cell(rnd, r))); }

  function evaluate(grid, bet, mult) {
    mult = mult || 1;
    const lineBet = bet / LINES.length, wins = []; let total = 0;
    LINES.forEach((ln, li) => {
      const syms = ln.map((row, reel) => grid[reel][row]);
      let base = syms.find(s => s !== WILD);
      if (syms[0] === SCAT) return;
      if (base === undefined) base = 5;
      if (base === SCAT) { base = undefined; }
      let n = 0;
      if (base !== undefined) {
        for (const s of syms) { if (s === base || s === WILD) n++; else break; }
      }
      // linia zaczynająca od wilda z lewej: base = pierwszy nie-wild, ale scatter przerywa
      if (base !== undefined && n >= 3) {
        const win = PAY[base][n - 3] * lineBet * mult;
        total += win; wins.push({ line: li, sym: base, count: n, win, cells: ln.slice(0, n).map((row, reel) => [reel, row]) });
      }
    });
    let sc = 0; const scCells = [];
    grid.forEach((col, r) => col.forEach((s, row) => { if (s === SCAT) { sc++; scCells.push([r, row]); } }));
    let scWin = 0, fs = 0;
    if (sc >= 3) { scWin = SCAT_PAY[Math.min(sc, 5)] * bet; fs = FS_AWARD[Math.min(sc, 5)]; total += scWin; }
    return { total, wins, scatters: sc, scCells, scWin, fs };
  }

  function playRound(rnd, bet) { // pełna runda z free spinami; zwraca wypłatę
    let g = spin(rnd), e = evaluate(g, bet, 1), total = e.total, fs = e.fs, guard = 0;
    while (fs > 0 && guard++ < 300) {
      fs--; g = spin(rnd); const f = evaluate(g, bet, FS_MULT); total += f.total; fs += f.fs;
    }
    return total;
  }

  const api = { LINES, PAY, SCAT_PAY, FS_AWARD, FS_MULT, spin, evaluate, playRound, WILD, SCAT };
  if (typeof module !== 'undefined') module.exports = api; else root.SlotEngine = api;
})(typeof window !== 'undefined' ? window : globalThis);
