// V2 — "Tablica": a split-flap (departures) board. Every change is a literal flip, cut to the beat.
import * as THREE from 'three';
import * as C from './common.js';
const { beatT, prog, smooth, easeOut, easeIn, easeInOut, clamp, lerp, punch, beatPulse } = C;

const renderer = C.makeRenderer();
const fin = C.makeFinisher(renderer);
const env = C.envMap(renderer);
await C.loadFonts(['700 100px Oswald', '500 100px Oswald', '800 40px Inter']);
const logoImg = await C.loadImage('/assets/logo_mask.png');

const COLS = 16, ROWS = 12;
const CWID = 0.125, CHGT = 0.21, GX = 0.01, GY = 0.018;
const BW = COLS * (CWID + GX), BH = ROWS * (CHGT + GY);

// ---------------------------------------------------------------- atlas
const TW = 84, TH = 144, AC = 48, AR = 14; // 4032 x 2016
const atlas = C.canvasTex(TW * AC, TH * AR);
atlas.tex.anisotropy = 8; atlas.tex.generateMipmaps = true;
const entries = new Map(); let nEnt = 0;
const COLORS = { w: '#f1efe9', g: '#3ee07f', a: '#ffb238', d: '#8a8a8a' };
function cardBg(x, y) {
  const a = atlas.ctx; const g = a.createLinearGradient(0, y, 0, y + TH);
  g.addColorStop(0, '#19191b'); g.addColorStop(0.5, '#131315'); g.addColorStop(0.5, '#0c0c0d'); g.addColorStop(1, '#121214');
  a.fillStyle = g; a.fillRect(x, y, TW, TH);
}
function entry(key, draw) {
  if (entries.has(key)) return entries.get(key);
  const i = nEnt++; const x = (i % AC) * TW, y = Math.floor(i / AC) * TH;
  cardBg(x, y); const a = atlas.ctx; a.save(); a.beginPath(); a.rect(x, y, TW, TH); a.clip(); draw(a, x, y); a.restore();
  entries.set(key, i); return i;
}
function glyph(ch, col = 'w') {
  return entry(col + ':' + ch, (a, x, y) => {
    if (ch === ' ') return;
    a.fillStyle = COLORS[col]; a.textAlign = 'center'; a.textBaseline = 'alphabetic';
    if (ch === '↓') { a.strokeStyle = COLORS[col]; a.lineWidth = 11; a.lineCap = 'round'; a.lineJoin = 'round'; a.beginPath(); a.moveTo(x + 42, y + 28); a.lineTo(x + 42, y + 112); a.moveTo(x + 18, y + 86); a.lineTo(x + 42, y + 114); a.lineTo(x + 66, y + 86); a.stroke(); return; }
    a.font = '500 104px Oswald'; a.fillText(ch, x + TW / 2, y + 110);
  });
}
// logo mosaic tiles
const LOGO_R = [1, 11], LOGO_C = [2, 14]; // rows 1..10, cols 2..13 hold the logo
function logoTile(r, c) {
  return entry(`L${r},${c}`, (a, x, y) => {
    const nr = LOGO_R[1] - LOGO_R[0], nc = LOGO_C[1] - LOGO_C[0];
    // fit logo into the region keeping aspect
    const regW = nc * TW, regH = nr * TH; const s = Math.min(regW / logoImg.width, regH / logoImg.height) * 0.92;
    const lw = logoImg.width * s, lh = logoImg.height * s; const ox = (regW - lw) / 2, oy = (regH - lh) / 2;
    const tmp = logoTileCanvas || (logoTileCanvas = (() => { const c = document.createElement('canvas'); c.width = regW; c.height = regH; const k = c.getContext('2d');
      k.drawImage(logoImg, ox, oy, lw, lh); const d = k.getImageData(0, 0, regW, regH);
      for (let i = 0; i < d.data.length; i += 4) { const v = d.data[i]; const yy = Math.floor(i / 4 / regW) / regH; const sh = 200 + 55 * Math.sin(yy * 9 + 1.2); d.data[i] = sh; d.data[i + 1] = sh; d.data[i + 2] = sh + 8; d.data[i + 3] = v; }
      k.putImageData(d, 0, 0); return c; })());
    a.drawImage(tmp, (c - LOGO_C[0]) * TW, (r - LOGO_R[0]) * TH, TW, TH, x, y, TW, TH);
  });
}
let logoTileCanvas = null;
const CHARSET = 'ABCDEFGHIJKLMNOPRSTUWYZ0123456789ŁŻŚĆĘŃÓ';
for (const ch of ' ' + CHARSET + '?@.,/↓') glyph(ch, 'w');
for (let r = LOGO_R[0]; r < LOGO_R[1]; r++) for (let c = LOGO_C[0]; c < LOGO_C[1]; c++) logoTile(r, c);
// thin hinge line across every card
atlas.tex.needsUpdate = true;
const uvOf = (i, half) => { // half: 0 top, 1 bottom -> [u0,v0,u1,v1]
  const x = (i % AC) * TW, y = Math.floor(i / AC) * TH; const W = TW * AC, H = TH * AR;
  const y0 = y + (half ? TH / 2 : 0), y1 = y0 + TH / 2;
  return [x / W + 0.5 / W, 1 - y1 / H, (x + TW) / W - 0.5 / W, 1 - y0 / H];
};

// ---------------------------------------------------------------- scene
const scene = new THREE.Scene(); scene.background = new THREE.Color('#050506');
scene.environment = env; scene.environmentIntensity = 0.035;
scene.fog = new THREE.Fog('#050506', 9, 22);
const camera = new THREE.PerspectiveCamera(36, C.W / C.H, 0.05, 60);
// wall
const wallTex = (() => { const { ctx, tex } = C.canvasTex(512, 512); ctx.fillStyle = '#2b2b2e'; ctx.fillRect(0, 0, 512, 512);
  const d = ctx.getImageData(0, 0, 512, 512); for (let i = 0; i < d.data.length; i += 4) { const n = (Math.random() - .5) * 30; d.data[i] += n; d.data[i + 1] += n; d.data[i + 2] += n; } ctx.putImageData(d, 0, 0);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping; tex.repeat.set(6, 6); return tex; })();
const wall = new THREE.Mesh(new THREE.PlaneGeometry(30, 20), new THREE.MeshStandardMaterial({ map: wallTex, roughness: 0.95, bumpMap: wallTex, bumpScale: 1 }));
wall.position.z = -0.12; wall.receiveShadow = true; scene.add(wall);
// board housing
const housing = new THREE.Mesh(new THREE.BoxGeometry(BW + 0.34, BH + 0.5, 0.16), new THREE.MeshStandardMaterial({ color: '#0e0e10', roughness: 0.35, metalness: 0.6 }));
housing.position.z = -0.06; housing.castShadow = true; housing.receiveShadow = true; scene.add(housing);
const plate = new THREE.Mesh(new THREE.BoxGeometry(BW + 0.1, BH + 0.1, 0.02), new THREE.MeshStandardMaterial({ color: '#050505', roughness: 0.9 }));
plate.position.z = 0.025; scene.add(plate);
// header label plate under board
const lab = C.canvasTex(1024, 96); lab.ctx.fillStyle = '#121214'; lab.ctx.fillRect(0, 0, 1024, 96);
lab.ctx.fillStyle = '#c9c6bd'; lab.ctx.font = '800 40px Inter'; lab.ctx.letterSpacing = '14px'; lab.ctx.textAlign = 'center'; lab.ctx.fillText('ODLOTY CEN  ·  FLIPPING NOW', 512, 62);
const labelPlate = new THREE.Mesh(new THREE.PlaneGeometry(BW * 0.8, BW * 0.8 * 96 / 1024), new THREE.MeshStandardMaterial({ map: lab.tex, roughness: 0.4, metalness: 0.3 }));
labelPlate.position.set(0, BH / 2 + 0.13, 0.025); scene.add(labelPlate);

const flapMat = new THREE.MeshStandardMaterial({ map: atlas.tex, roughness: 0.42, metalness: 0.05, side: THREE.FrontSide });
function halfGeo() { const g = new THREE.PlaneGeometry(CWID, CHGT / 2 - 0.003); return g; }
function setUV(mesh, idx, half, flipV = false) {
  const [u0, v0, u1, v1] = uvOf(idx, half); const uv = mesh.geometry.attributes.uv;
  // PlaneGeometry uv order: (0,1) (1,1) (0,0) (1,0)
  if (!flipV) { uv.setXY(0, u0, v1); uv.setXY(1, u1, v1); uv.setXY(2, u0, v0); uv.setXY(3, u1, v0); }
  else { uv.setXY(0, u0, v0); uv.setXY(1, u1, v0); uv.setXY(2, u0, v1); uv.setXY(3, u1, v1); }
  uv.needsUpdate = true;
}
const cells = [];
for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) {
  const g = new THREE.Group();
  g.position.set(-BW / 2 + (c + 0.5) * (CWID + GX), BH / 2 - (r + 0.5) * (CHGT + GY), 0.05);
  const top = new THREE.Mesh(halfGeo(), flapMat); top.position.y = CHGT / 4 + 0.0015;
  const bot = new THREE.Mesh(halfGeo(), flapMat); bot.position.y = -CHGT / 4 - 0.0015;
  const pivot = new THREE.Group(); pivot.position.z = 0.004;
  const ff = new THREE.Mesh(halfGeo(), flapMat); ff.position.y = CHGT / 4 + 0.0015;
  const fb = new THREE.Mesh(halfGeo(), flapMat); fb.position.y = CHGT / 4 + 0.0015; fb.rotation.y = Math.PI; fb.rotation.z = Math.PI; fb.position.z = -0.0005;
  // after rotating pivot by -PI around X, fb faces the viewer upside-down-corrected
  fb.rotation.set(Math.PI, 0, 0);
  pivot.add(ff, fb);
  [top, bot, ff, fb].forEach(m => { m.castShadow = true; m.receiveShadow = true; });
  g.add(top, bot, pivot); scene.add(g);
  cells.push({ r, c, top, bot, pivot, ff, fb, events: [], cur: glyph(' ') });
}
const cell = (r, c) => cells[r * COLS + c];

// ---------------------------------------------------------------- choreography
let seed = 11; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
const FD = 0.075; // one flip duration
// schedule: cell lands on `target` at time `land`, cycling `k` random cards before
function setCell(r, c, target, land, k = 4) {
  const ce = cell(r, c); const seq = [];
  for (let i = 0; i < k; i++) seq.push(glyph(CHARSET[Math.floor(rnd() * CHARSET.length)], 'w'));
  seq.push(target);
  const t0 = land - seq.length * FD;
  seq.forEach((to, i) => ce.events.push({ t: t0 + i * FD, to }));
}
function setRow(r, text, land, { col = 'w', align = 'left', stagger = 0.018, k = 4, keepBlank = false } = {}) {
  let s = text; if (align === 'center') { const p = Math.floor((COLS - s.length) / 2); s = ' '.repeat(p) + s; } else if (align === 'right') s = s.padStart(COLS);
  s = s.padEnd(COLS);
  for (let c = 0; c < COLS; c++) {
    const ch = s[c]; if (keepBlank && ch === ' ') continue;
    setCell(r, c, glyph(ch, col), land + c * stagger, ch === ' ' ? 2 : k + Math.floor(rnd() * 3));
  }
}
function clearRows(rows, land) { rows.forEach((r, i) => setRow(r, '', land + i * 0.01, { k: 2, stagger: 0.01 })); }

// intro hook
setRow(5, 'PŁACISZ', 0.55, { align: 'center', stagger: 0.03 });
setRow(6, 'ZA DUŻO?', 1.04, { align: 'center', stagger: 0.03 });
// drop: price board
clearRows([5, 6], C.DROP - 0.02);
setRow(0, 'FLIPPING NOW', C.DROP + 0.02, { col: 'a', align: 'center', stagger: 0.012 });
setRow(1, 'DOSTAWCA CENNIK', beatT(0.5), { col: 'w', align: 'center', stagger: 0.012 });
const prods = [['AIRPODS PRO 2', '1 SZT 120 ZŁ', 'OD 50 ZŁ/SZT'], ['AIRPODS PRO 3', '1 SZT 200 ZŁ', 'OD 45 ZŁ/SZT'], ['PERFUMY', '1 SZT 130 ZŁ', 'OD 70 ZŁ/SZT'], ['UBRANIA', 'ZAPYTAJ O CENĘ', null]];
prods.forEach(([n, p1, p2], i) => {
  setRow(3 + i * 2, n, beatT(1 + i), { stagger: 0.014 });
  setRow(4 + i * 2, p1, beatT(1.5 + i), { align: 'right', stagger: 0.014, col: 'd' });
  if (p2) setRow(4 + i * 2, p2, beatT(6 + i * 0.5), { align: 'right', col: 'g', stagger: 0.012, k: 3 });
});
setRow(11, 'PRYWATNY KONTAKT', beatT(9), { col: 'a', align: 'center', stagger: 0.02, k: 5 });
setRow(2, 'HURT I DETAL', beatT(10), { align: 'center', stagger: 0.02 });
// logo mosaic wave at bar 3
const LOGO_LAND = beatT(13);
for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) {
  const inLogo = r >= LOGO_R[0] && r < LOGO_R[1] && c >= LOGO_C[0] && c < LOGO_C[1];
  const d = Math.hypot(c - COLS / 2 + 0.5, (r - ROWS / 2 + 0.5) * 1.4) * 0.03;
  const target = inLogo ? logoTile(r, c) : glyph(' ');
  setCell(r, c, target, LOGO_LAND - 0.22 + d, 4 + Math.floor(rnd() * 4));
}
// CTA
const CTA = beatT(17);
for (let r = 0; r < ROWS; r++) setRow(r, '', CTA - 0.2 + r * 0.008, { k: 2, stagger: 0.006 });
setRow(1, 'FLIPPING NOW', beatT(17), { col: 'a', align: 'center' });
setRow(3, 'NAJLEPSZY', beatT(17.5), { align: 'center' });
setRow(4, 'DOSTAWCA', beatT(18), { align: 'center' });
setRow(6, 'LINK W BIO', beatT(19), { col: 'g', align: 'center', stagger: 0.03, k: 6 });
setRow(8, '↓', beatT(20), { col: 'g', align: 'center' });
setRow(10, '@FLIPPINGNOW999', beatT(21), { align: 'center', stagger: 0.02 });
// keep the end card alive: arrow blinks via flaps, CTA row re-flips in a wave
{ const ac = Math.floor((COLS - 1) / 2);
  for (let b = 21; b <= 27; b += 1) { const ce = cell(8, ac); ce.events.push({ t: beatT(b) - 2 * FD, to: glyph(' ') }, { t: beatT(b) - FD, to: glyph('↓', 'g') }); }
  const txt = 'LINK W BIO', p0 = Math.floor((COLS - txt.length) / 2);
  [23, 25, 27].forEach(b => { for (let i = 0; i < txt.length; i++) if (txt[i] !== ' ') cell(6, p0 + i).events.push({ t: beatT(b) - FD + i * 0.03, to: glyph(txt[i], 'g') }); }); }
cells.forEach(ce => ce.events.sort((a, b) => a.t - b.t));

function applyCell(ce, t) {
  // find state
  let cur = glyph(' '), prev = glyph(' '), active = null;
  for (const e of ce.events) {
    if (e.t > t) break;
    if (t < e.t + FD) { active = { e, from: cur }; }
    prev = cur; cur = e.to;
  }
  if (!active) {
    setUV(ce.top, cur, 0); setUV(ce.bot, cur, 1); ce.pivot.visible = false; return;
  }
  const from = active.from, to = active.e.to; const p = (t - active.e.t) / FD;
  // static halves: top shows NEW top, bottom shows OLD bottom; flap falls carrying old top / new bottom
  setUV(ce.top, to, 0); setUV(ce.bot, from, 1);
  setUV(ce.ff, from, 0); setUV(ce.fb, to, 1, false);
  ce.pivot.visible = true; ce.pivot.rotation.x = -Math.PI * easeIn(clamp(p)) * 0.999;
}
// flap back mesh geometry: when pivot rotated -PI, a plane at +y/4 rotated by PI about X ends below the hinge facing +z.
cells.forEach(ce => { ce.fb.rotation.set(Math.PI, 0, 0); ce.fb.position.set(0, CHGT / 4 + 0.0015, -0.0005); });

// ---------------------------------------------------------------- lights
const key = new THREE.SpotLight('#ffe3bd', 0, 0, 0.55, 0.6, 0); key.position.set(-1.2, 4.2, 4.4); key.target.position.set(0, -0.1, 0);
key.castShadow = true; key.shadow.mapSize.set(2048, 2048); key.shadow.bias = -0.0003; key.shadow.camera.near = 2; key.shadow.camera.far = 14; scene.add(key, key.target);
const rim = new THREE.SpotLight('#9fc2ff', 0, 0, 0.5, 0.8, 0); rim.position.set(3.2, -2.5, 3); rim.target.position.set(0, 0, 0); scene.add(rim, rim.target);
const amb = new THREE.HemisphereLight('#bcc6d8', '#1a1612', 0.15); scene.add(amb);
// practical lamps above the board
const lampMat = new THREE.MeshStandardMaterial({ color: '#000', emissive: '#ffcf8a', emissiveIntensity: 0 });
const lamps = [];
for (let i = 0; i < 4; i++) {
  const g = new THREE.Group(); const arm = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 0.5), new THREE.MeshStandardMaterial({ color: '#111', metalness: 0.8, roughness: 0.3 }));
  arm.rotation.x = Math.PI / 2; arm.position.z = 0.25; const shade = new THREE.Mesh(new THREE.ConeGeometry(0.12, 0.14, 24, 1, true), new THREE.MeshStandardMaterial({ color: '#141414', metalness: 0.7, roughness: 0.35, side: THREE.DoubleSide }));
  shade.position.set(0, -0.02, 0.5); const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.05, 16, 12), lampMat); bulb.position.set(0, -0.08, 0.5);
  g.add(arm, shade, bulb); g.position.set(-BW / 2 + (i + 0.5) * BW / 4, BH / 2 + 0.38, 0); scene.add(g); lamps.push(g);
  const pl = new THREE.SpotLight('#ffcf8a', 0, 0, 0.7, 0.9, 0); pl.position.set(g.position.x, g.position.y - 0.1, 0.55); pl.target.position.set(g.position.x, g.position.y - 1.8, 0.0); scene.add(pl, pl.target); g.userData.light = pl;
}

// ---------------------------------------------------------------- camera
const V = (x, y, z) => new THREE.Vector3(x, y, z);
const rowY = (r) => BH / 2 - (r + 0.5) * (CHGT + GY);
const keys = [
  [0.0, V(0.15, rowY(5.5) - 0.25, 3.9), V(0, rowY(5.5), 0)],
  [C.DROP - 0.05, V(0.03, rowY(5.5) - 0.1, 3.45), V(0, rowY(5.5), 0)],
  [C.DROP + 0.05, V(-0.35, rowY(1) - 0.35, 4.4), V(0, rowY(1.5), 0)],
  [beatT(4), V(0.3, rowY(5) - 0.5, 6.9), V(0, rowY(5.3), 0)],
  [beatT(8), V(-0.1, rowY(6), 6.6), V(0.0, rowY(6), 0)],
  [beatT(11.5), V(0.3, rowY(8) - 0.3, 6.5), V(0.0, rowY(7.5), 0)],
  [beatT(12.3), V(0.0, 0.2, 7.4), V(0, 0.2, 0)],
  [beatT(16), V(-0.3, 0.0, 6.6), V(0, 0.1, 0)],
  [beatT(17), V(0.3, rowY(3) - 0.2, 5.6), V(0, rowY(4), 0)],
  [beatT(19.2), V(-0.08, rowY(6) - 0.3, 6.9), V(-0.08, rowY(6.5), 0)],
  [15.3, V(-0.12, rowY(6) - 0.45, 8.3), V(-0.12, rowY(5.8), 0)],
];
// hard cuts happen where two keys are 0.1s apart (drop) — we just interpolate fast there
function path(t) {
  for (let i = 0; i < keys.length - 1; i++) {
    const [ta, pa, la] = keys[i], [tb, pb, lb] = keys[i + 1];
    if (t <= tb) { const e = easeInOut((t - ta) / (tb - ta)); return [pa.clone().lerp(pb, e), la.clone().lerp(lb, e)]; }
  }
  const k = keys[keys.length - 1]; return [k[1].clone(), k[2].clone()];
}

function frame(t) {
  cells.forEach(ce => applyCell(ce, t));
  const on = t >= C.DROP - 0.03 ? 1 : 0;
  const intro = smooth(prog(t, 0, 0.5));
  const bp = beatPulse(t, 0, 28, 0.16);
  key.intensity = (on ? 9 : 3.4 * intro) * (1 + 0.12 * bp);
  rim.intensity = on ? 3 : 0.6;
  amb.intensity = on ? 0.18 : 0.06;
  lamps.forEach((g, i) => { const flick = on ? 1 : (t > 0.3 + i * 0.12 ? 0.35 + 0.25 * Math.sin(t * 40 + i * 3) : 0); g.userData.light.intensity = 5 * flick * (1 + 0.25 * bp); });
  lampMat.emissiveIntensity = on ? 5 * (1 + 0.4 * bp) : 1.5 * intro;
  // light sweep across board during logo hold
  key.position.x = -1.2 + 2.4 * smooth(prog(t, beatT(12), beatT(16.5)));
  const [pos, look] = path(t);
  pos.x += Math.sin(t * 0.9) * 0.03; pos.y += Math.sin(t * 1.3) * 0.02;
  camera.position.copy(pos); camera.lookAt(look);
  camera.fov = 36 - 1.2 * bp; camera.updateProjectionMatrix();
  fin.uniforms.flash.value = 0.35 * punch(t, C.DROP, 0.12) + 0.25 * punch(t, LOGO_LAND, 0.1) + 0.2 * punch(t, CTA, 0.1);
  fin.uniforms.vig.value = 0.55; fin.uniforms.grain.value = 0.055; fin.uniforms.ca.value = 0.002;
  fin.render(scene, camera, t);
}
window.__dbg = { scene, key, rim, cells };
window.__flapTimes = cells.flatMap(ce => ce.events.map(e => +e.t.toFixed(4)));
C.run(frame);
