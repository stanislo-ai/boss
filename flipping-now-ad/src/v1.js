// V1 — "Cennik": a printed price sheet on a table in window light (closest to the reference film).
import * as THREE from 'three';
import { FontLoader } from 'three/addons/loaders/FontLoader.js';
import { TextGeometry } from 'three/addons/geometries/TextGeometry.js';
import * as C from './common.js';
const { beatT, prog, smooth, easeOut, easeIn, easeInOut, easeOutBack, clamp, lerp, punch, beatPulse } = C;

const ACC = '#0f9d4a';
const renderer = C.makeRenderer();
const fin = C.makeFinisher(renderer);
const env = C.envMap(renderer);

await C.loadFonts(['900 100px Inter', '800 60px Inter', '600 30px Inter', '400 40px Inter', 'italic 200px "Instrument Serif"']);
const [logoImg, phA, phP, phC] = await Promise.all(['/assets/logo_mask.png', '/assets/ph_airpods.jpg', '/assets/ph_perfume.jpg', '/assets/ph_clothes.jpg'].map(C.loadImage));
// tinted logo (dark + silver)
function tintedLogo(color) {
  const c = document.createElement('canvas'); c.width = logoImg.width; c.height = logoImg.height;
  const x = c.getContext('2d'); x.drawImage(logoImg, 0, 0);
  const d = x.getImageData(0, 0, c.width, c.height);
  const col = new THREE.Color(color);
  for (let i = 0; i < d.data.length; i += 4) { const a = d.data[i]; d.data[i] = col.r * 255; d.data[i + 1] = col.g * 255; d.data[i + 2] = col.b * 255; d.data[i + 3] = a; }
  x.putImageData(d, 0, 0); return c;
}
const logoDark = tintedLogo('#141414'), logoLight = tintedLogo('#ffffff');

// ---------------------------------------------------------------- table scene
const scene = new THREE.Scene();
scene.background = new THREE.Color('#0d0d0f');
scene.environment = env; scene.environmentIntensity = 0.25;
const camera = new THREE.PerspectiveCamera(44, C.W / C.H, 0.05, 100);

// table: dark felt / concrete
const tableTex = (() => {
  const { c, ctx, tex } = C.canvasTex(1024, 1024);
  ctx.fillStyle = '#5a5a5e'; ctx.fillRect(0, 0, 1024, 1024);
  const img = ctx.getImageData(0, 0, 1024, 1024);
  for (let i = 0; i < img.data.length; i += 4) { const n = (Math.random() - 0.5) * 38; img.data[i] += n; img.data[i + 1] += n; img.data[i + 2] += n + 2; }
  ctx.putImageData(img, 0, 0);
  ctx.globalAlpha = 0.06; for (let i = 0; i < 2600; i++) { ctx.fillStyle = Math.random() < .5 ? '#000' : '#fff'; ctx.fillRect(Math.random() * 1024, Math.random() * 1024, 1 + Math.random() * 3, 1 + Math.random() * 3); }
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping; tex.repeat.set(5, 5); return tex;
})();
const table = new THREE.Mesh(new THREE.PlaneGeometry(40, 40), new THREE.MeshStandardMaterial({ map: tableTex, roughness: 0.95, metalness: 0, bumpMap: tableTex, bumpScale: 0.6 }));
table.rotation.x = -Math.PI / 2; table.receiveShadow = true; scene.add(table);

// window gobo
const gobo = (() => {
  const { c, ctx, tex } = C.canvasTex(512, 512);
  ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, 512, 512);
  ctx.filter = 'blur(7px)'; ctx.fillStyle = '#000';
  for (let i = 0; i < 5; i++) { ctx.fillRect(18 + i * 118, -20, 34, 560); ctx.fillRect(-20, 30 + i * 118, 560, 34); }
  ctx.filter = 'none'; tex.colorSpace = THREE.SRGBColorSpace; return tex;
})();
const sun = new THREE.SpotLight('#fff1dc', 6, 0, 0.62, 0.35, 0);
sun.map = gobo; sun.castShadow = true; sun.shadow.mapSize.set(2048, 2048); sun.shadow.bias = -0.0004; sun.shadow.radius = 6; sun.shadow.camera.near = 4; sun.shadow.camera.far = 40;
scene.add(sun, sun.target);
const fill = new THREE.HemisphereLight('#c9d4ff', '#2a2622', 0.25); scene.add(fill);

// ---------------------------------------------------------------- paper
const PW = 2.1, PH = 2.97, CW = 1240, CH = 1754;
const front = C.canvasTex(CW, CH);
const paperGeo = new THREE.PlaneGeometry(PW, PH, 24, 32);
{ const p = paperGeo.attributes.position; for (let i = 0; i < p.count; i++) { const x = p.getX(i), y = p.getY(i); p.setZ(i, 0.014 * Math.pow(Math.abs(x) / 1.05, 3) + 0.004 * (1 + Math.sin(y * 1.3 + 0.4))); } paperGeo.computeVertexNormals(); }
const paperMat = new THREE.MeshStandardMaterial({ map: front.tex, roughness: 0.82, metalness: 0 });
const paper = new THREE.Mesh(paperGeo, paperMat); paper.castShadow = true; paper.receiveShadow = true;
// back: black card with silver foil logo
const back = C.canvasTex(CW, CH), backMR = C.canvasTex(CW, CH);
{
  const draw = (ctx, bg, fg, mr) => {
    ctx.fillStyle = bg; ctx.fillRect(0, 0, CW, CH);
    const lh = 760, lw = lh * logoImg.width / logoImg.height;
    const src = mr ? tintedLogo(fg) : tintedLogo(fg);
    ctx.drawImage(src, (CW - lw) / 2, 330, lw, lh);
    ctx.fillStyle = fg; ctx.textAlign = 'center';
    ctx.font = '800 92px Inter'; ctx.letterSpacing = '22px'; ctx.fillText('FLIPPING NOW', CW / 2 + 11, 1290);
    ctx.font = '600 38px Inter'; ctx.letterSpacing = '10px'; ctx.fillText('NAJLEPSZY DOSTAWCA', CW / 2 + 5, 1372);
    ctx.letterSpacing = '0px';
  };
  draw(back.ctx, '#0c0c0d', '#d9d9de', false);
  draw(backMR.ctx, 'rgb(0,200,0)', 'rgb(0,60,255)', true);
  backMR.tex.colorSpace = THREE.NoColorSpace;
}
const backMesh = new THREE.Mesh(paperGeo, new THREE.MeshStandardMaterial({ map: back.tex, roughnessMap: backMR.tex, metalnessMap: backMR.tex, roughness: 1, metalness: 1, envMapIntensity: 2.2 }));
backMesh.rotation.y = Math.PI; backMesh.position.z = -0.003; backMesh.castShadow = true;

const paperInner = new THREE.Group(); paperInner.rotation.x = -Math.PI / 2; paperInner.add(paper, backMesh);
const flipper = new THREE.Group(); flipper.add(paperInner); scene.add(flipper);

// polaroids (children of the paper)
function polaroid(img, label) {
  const { ctx, tex } = C.canvasTex(560, 680);
  ctx.fillStyle = '#f7f5f0'; ctx.fillRect(0, 0, 560, 680);
  const s = Math.max(500 / img.width, 500 / img.height), w = img.width * s, h = img.height * s;
  ctx.save(); ctx.beginPath(); ctx.rect(30, 30, 500, 500); ctx.clip(); ctx.drawImage(img, 30 + (500 - w) / 2, 30 + (500 - h) / 2, w, h); ctx.restore();
  ctx.fillStyle = '#1b1b1b'; ctx.font = 'italic 78px "Instrument Serif"'; ctx.textAlign = 'center'; ctx.fillText(label, 280, 625);
  const m = new THREE.Mesh(new THREE.BoxGeometry(0.58, 0.704, 0.006), [0, 0, 0, 0, 0, 0].map((_, i) => i === 4 ? new THREE.MeshStandardMaterial({ map: tex, roughness: 0.55 }) : new THREE.MeshStandardMaterial({ color: '#eeeae2', roughness: 0.8 })));
  m.castShadow = true; m.receiveShadow = true; paperInner.add(m); return m;
}
const pols = [
  { m: polaroid(phA, 'AirPods'), x: -0.62, y: -1.12, r: 0.10, b: 9 },
  { m: polaroid(phP, 'Perfumy'), x: 0.03, y: -1.17, r: -0.06, b: 10 },
  { m: polaroid(phC, 'Ubrania'), x: 0.66, y: -1.1, r: 0.13, b: 11 },
];

// ---------------------------------------------------------------- paper content (redrawn every frame)
const rows = [
  { k: 'AIRPODS PRO 2', from: 120, to: 50, b: 4 },
  { k: 'AIRPODS PRO 3', from: 200, to: 45, b: 5 },
  { k: 'PERFUMY', from: 130, to: 70, b: 6 },
  { k: 'UBRANIA', txt: 'pełny cennik u dostawcy', b: 7 },
  { k: 'DOSTĘP', txt: 'prywatny dostawca · link w bio', b: 7.5 },
];
const checks = ['AirPods', 'Perfumy', 'Ubrania', 'Od 1 szt.'];
const TRAIL = ['#facc15', '#a3e635', '#22c55e', '#10b981', '#06b6d4', '#3b82f6'];

function drawFront(t, final) {
  const x = front.ctx; x.save();
  x.fillStyle = '#f6f4ef'; x.fillRect(0, 0, CW, CH);
  // subtle paper fibre
  x.globalAlpha = 0.035; x.fillStyle = '#000'; for (let i = 0; i < 90; i++) x.fillRect((i * 997) % CW, (i * 613) % CH, 2, 2); x.globalAlpha = 1;
  // header micro labels
  x.fillStyle = '#9b9890'; x.font = '600 22px Inter'; x.letterSpacing = '4px';
  x.fillText('CENNIK DOSTAWCY', 110, 112); x.textAlign = 'right'; x.fillText('NR 999', CW - 110, 112); x.textAlign = 'left'; x.letterSpacing = '0px';
  // brand
  const lh = 84, lw = lh * logoImg.width / logoImg.height; x.drawImage(logoDark, 110, 150, lw, lh);
  x.fillStyle = ACC; x.font = '800 62px Inter'; x.fillText('Flipping Now', 110 + lw + 26, 212);
  x.fillStyle = '#8f8c85'; x.font = '400 28px Inter'; x.fillText('Najlepszy dostawca · @flippingnow999', 110 + lw + 28, 258);
  // headline line 1 (typed during intro)
  const L1 = 'Kupuj taniej,';
  const n = final ? L1.length : Math.floor(clamp(t / 1.5) * L1.length);
  x.fillStyle = '#121212'; x.font = '900 112px Inter'; x.letterSpacing = '-3px';
  x.fillText(L1.slice(0, n), 104, 470);
  if (!final && t < C.DROP && Math.floor(t * 4) % 2 === 0) { const w = x.measureText(L1.slice(0, n)).width; x.fillRect(110 + w + 6, 380, 8, 100); }
  x.letterSpacing = '0px';
  // line 2 "flipuj."
  const pIn = final ? 1 : prog(t, C.DROP - 0.04, C.DROP + 0.18);
  const colorP = final ? 1 : prog(t, beatT(8) - 0.05, beatT(8) + 0.35);
  if (pIn > 0) {
    x.save(); const s = lerp(1.35, 1, easeOut(pIn)); x.globalAlpha = pIn;
    x.translate(100, 690); x.scale(s, s); x.font = 'italic 250px "Instrument Serif"';
    if (colorP > 0) {
      for (let i = TRAIL.length - 1; i >= 0; i--) { const o = (i + 1) * 11 * easeOut(colorP); x.fillStyle = TRAIL[i]; x.fillText('flipuj.', o * 0.35, o); }
      const g = x.createLinearGradient(0, -180, 640, 0); g.addColorStop(0, '#0f9d4a'); g.addColorStop(0.5, '#16c172'); g.addColorStop(1, '#0ea5e9');
      x.fillStyle = colorP < 1 ? '#121212' : g; x.globalAlpha = 1; x.fillText('flipuj.', 0, 0);
      if (colorP < 1) { x.globalAlpha = colorP; x.fillStyle = g; x.fillText('flipuj.', 0, 0); }
    } else { x.fillStyle = '#121212'; x.fillText('flipuj.', 0, 0); }
    x.restore();
  }
  // checkboxes 2x2
  checks.forEach((lab, i) => {
    const cx = 760 + (i % 2) * 215, cy = 575 + Math.floor(i / 2) * 70;
    x.strokeStyle = '#2a2a2a'; x.lineWidth = 3; C.rr(x, cx, cy - 30, 34, 34, 5); x.stroke();
    x.fillStyle = '#1b1b1b'; x.font = '600 34px Inter'; x.fillText(lab, cx + 48, cy);
    const p = final ? 1 : prog(t, beatT(1 + i) - 0.06, beatT(1 + i) + 0.12);
    if (p > 0) {
      x.strokeStyle = ACC; x.lineWidth = 7; x.lineCap = 'round'; x.lineJoin = 'round'; x.beginPath();
      const pts = [[cx + 5, cy - 14], [cx + 15, cy - 2], [cx + 40, cy - 42]];
      const L1 = 0.35; x.moveTo(...pts[0]);
      if (p < L1) x.lineTo(lerp(pts[0][0], pts[1][0], p / L1), lerp(pts[0][1], pts[1][1], p / L1));
      else { x.lineTo(...pts[1]); const q = (p - L1) / (1 - L1); x.lineTo(lerp(pts[1][0], pts[2][0], q), lerp(pts[1][1], pts[2][1], q)); }
      x.stroke();
    }
  });
  // rows
  x.fillStyle = '#d6d2c8'; x.fillRect(110, 800, CW - 220, 2);
  rows.forEach((r, i) => {
    const y = 870 + i * 86; const p = final ? 1 : prog(t, beatT(r.b) - 0.05, beatT(r.b) + 0.2);
    if (p <= 0) return;
    x.save(); x.globalAlpha = p; x.translate(lerp(-40, 0, easeOut(p)), 0);
    x.fillStyle = '#7d7a73'; x.font = '600 27px Inter'; x.letterSpacing = '3px'; x.fillText(r.k, 110, y); x.letterSpacing = '0px';
    if (r.txt) { x.fillStyle = '#1d1d1d'; x.font = '400 40px Inter'; x.fillText(r.txt, 440, y + 2); }
    else {
      const cp = final ? 1 : prog(t, beatT(r.b), beatT(r.b) + 0.42);
      const v = Math.round(lerp(r.from, r.to, easeOut(cp)));
      x.fillStyle = '#1d1d1d'; x.font = '400 42px Inter'; x.fillText('od ', 440, y + 2);
      const w0 = x.measureText('od ').width;
      x.fillStyle = cp >= 1 ? ACC : '#1d1d1d'; x.font = '800 58px Inter'; const vs = `${v} zł`; x.fillText(vs, 440 + w0, y + 4);
      const w1 = x.measureText(vs).width; x.fillStyle = '#8f8c85'; x.font = '400 34px Inter'; x.fillText(' / szt.', 440 + w0 + w1, y + 2);
      // tier hint
      x.fillStyle = '#8a857b'; x.font = '400 25px Inter'; x.textAlign = 'right'; x.fillText(`1 szt. = ${r.from} zł`, CW - 110, y); x.textAlign = 'left';
    }
    x.restore();
    x.fillStyle = '#e4e0d6'; x.fillRect(110, y + 34, CW - 220, 1.5);
  });
  // final CTA zone
  if (final) {
    const p = prog(t, beatT(20.5), beatT(21.5));
    x.fillStyle = '#121212'; x.font = 'italic 200px "Instrument Serif"'; x.textAlign = 'center';
    x.globalAlpha = smooth(prog(t, beatT(20) - 0.02, beatT(20) + 0.25));
    x.fillText('link w bio', CW / 2, 1430);
    x.font = '800 46px Inter'; x.letterSpacing = '6px'; x.fillStyle = ACC; x.fillText('@FLIPPINGNOW999', CW / 2 + 3, 1600); x.letterSpacing = '0px';
    x.globalAlpha = 1; x.textAlign = 'left';
    // hand-drawn ellipse
    if (p > 0) {
      x.strokeStyle = ACC; x.lineWidth = 9; x.lineCap = 'round'; x.beginPath();
      const N = 80; for (let i = 0; i <= N * p; i++) { const a = -2.6 + (i / N) * Math.PI * 2.15; const rx = 470 + 12 * Math.sin(i * .2), ry = 150 + 6 * Math.cos(i * .3);
        const px = CW / 2 + Math.cos(a) * rx, py = 1375 + Math.sin(a) * ry; i ? x.lineTo(px, py) : x.moveTo(px, py); }
      x.stroke();
    }
    // arrow pointing down
    const ap = prog(t, beatT(22), beatT(22.6));
    if (ap > 0) {
      x.save(); x.strokeStyle = '#121212'; x.lineWidth = 8; x.lineCap = 'round'; x.lineJoin = 'round';
      const bob = 10 * Math.sin((t - beatT(22)) * Math.PI * 2 / C.BEAT);
      x.globalAlpha = ap; x.beginPath(); x.moveTo(CW / 2, 1630 + bob); x.lineTo(CW / 2, 1712 + bob); x.moveTo(CW / 2 - 30, 1682 + bob); x.lineTo(CW / 2, 1714 + bob); x.lineTo(CW / 2 + 30, 1682 + bob); x.stroke(); x.restore();
    }
  }
  x.fillStyle = '#b3afa6'; x.font = '400 20px Inter'; x.fillText('Ceny orientacyjne, zależne od ilości.', 110, CH - 34);
  x.restore(); front.tex.needsUpdate = true;
}

// ---------------------------------------------------------------- sky scene
const sky = new THREE.Scene();
{
  const { ctx, tex } = C.canvasTex(16, 512);
  const g = ctx.createLinearGradient(0, 0, 0, 512); g.addColorStop(0, '#0b5fe0'); g.addColorStop(0.55, '#3d93f5'); g.addColorStop(1, '#a9d4ff');
  ctx.fillStyle = g; ctx.fillRect(0, 0, 16, 512); sky.background = tex;
}
sky.environment = env; sky.environmentIntensity = 0.6;
sky.fog = new THREE.Fog('#8cc3ff', 14, 40);
const skyCam = new THREE.PerspectiveCamera(50, C.W / C.H, 0.05, 200);
const sunS = new THREE.DirectionalLight('#ffffff', 4.2); sunS.position.set(-4, 8, 6); sky.add(sunS);
sky.add(new THREE.HemisphereLight('#dfefff', '#4a7fd0', 1.6));
const cloudMat = new THREE.MeshStandardMaterial({ color: '#ffffff', roughness: 1, flatShading: false });
const sph = new THREE.SphereGeometry(1, 24, 16);
function cloud(x, y, z, s) {
  const g = new THREE.Group(); const k = [[0, 0, 0, 1], [1.1, -0.2, 0, 0.75], [-1.1, -0.25, 0, 0.7], [0.5, 0.45, -0.1, 0.7], [-0.5, 0.35, 0.1, 0.65], [1.9, -0.45, 0, 0.45], [-1.9, -0.45, 0, 0.45]];
  k.forEach(([a, b, c, r]) => { const m = new THREE.Mesh(sph, cloudMat); m.position.set(a, b, c); m.scale.setScalar(r); g.add(m); });
  g.position.set(x, y, z); g.scale.set(s, s * 0.8, s * 0.7); sky.add(g); return g;
}
const clouds = [];
const rnd = (() => { let s = 7; return () => (s = (s * 16807) % 2147483647) / 2147483647; })();
for (let i = 0; i < 40 && clouds.length < 24; i++) { const x = (rnd() - 0.5) * 16, y = (rnd() - 0.5) * 20, z = -4 - rnd() * 26, s = 0.6 + rnd() * 1.2;
  if (Math.abs(x) < 3.2 && Math.abs(y - 0.6) < 2.0 && z > -11.5) continue; clouds.push(cloud(x, y, z, s)); }
[[-2.2, -1.6, -7.2, 1.1], [2.4, -1.4, -6.8, 1.0], [0.2, -2.6, -9, 1.4], [-1.5, 2.9, -10, 1.1], [2.0, 3.3, -11, 1.2]].forEach(a => clouds.push(cloud(...a)));
const font = await new Promise(r => new FontLoader().load('/node_modules/three/examples/fonts/droid/droid_sans_bold.typeface.json', r));
const letterMat = [new THREE.MeshStandardMaterial({ color: '#ffffff', roughness: 0.45 }), new THREE.MeshStandardMaterial({ color: '#b9d2ff', roughness: 0.6 })];
const word = 'TANIEJ'; const letters = []; let xo = 0;
for (const ch of word) {
  const g = new TextGeometry(ch, { font, size: 0.92, depth: 0.42, curveSegments: 8, bevelEnabled: true, bevelThickness: 0.04, bevelSize: 0.03, bevelSegments: 3 });
  g.computeBoundingBox(); const w = g.boundingBox.max.x - g.boundingBox.min.x; g.translate(-g.boundingBox.min.x - w / 2, -0.46, -0.21);
  const m = new THREE.Mesh(g, letterMat); m.userData.x = xo + w / 2; xo += w + 0.08; letters.push(m); sky.add(m);
}
letters.forEach(m => { m.userData.x -= xo / 2; });
const { shapes } = await C.logoShapes();
const chromeMat = new THREE.MeshStandardMaterial({ color: '#ffffff', metalness: 1, roughness: 0.1, envMapIntensity: 2.6 });
const chromeF = C.logoMesh(shapes, { depth: 0.16, bevel: 0.025, material: chromeMat });
sky.add(chromeF);

// ---------------------------------------------------------------- camera path (table)
const V = (x, y, z) => new THREE.Vector3(x, y, z);
const keys = [
  [0.0, V(-0.4, 2.75, 0.55), V(-0.38, 0, -1.0)],
  [C.DROP, V(0.0, 3.45, 0.95), V(0.0, 0, -0.86)],
  [beatT(4), V(0.06, 3.1, 2.25), V(0.03, 0, 0.3)],
  [beatT(7.6), V(-0.03, 3.25, 2.05), V(0, 0, 0.12)],
  [beatT(8.6), V(-0.02, 3.7, 2.2), V(0, 0, 0.2)],
  [beatT(11.4), V(0.05, 4.0, 3.0), V(0, 0, 0.62)],
  [beatT(12), V(0.0, 5.4, 2.7), V(0, 0.5, 0.1)],
  [beatT(14), V(0.0, 4.6, 1.7), V(0, 0.55, -0.05)],
  [beatT(15.75), V(0.0, 1.55, 0.25), V(0, 0.42, -0.25)],
];
const finalKeys = [[beatT(20), V(0.0, 2.7, 3.0), V(0, 0, 0.8)], [beatT(22), V(0.0, 3.5, 3.1), V(0, 0, 0.55)], [15.3, V(0.0, 4.6, 2.9), V(0, 0, 0.12)]];
function path(ks, t) {
  if (t <= ks[0][0]) return [ks[0][1].clone(), ks[0][2].clone()];
  for (let i = 0; i < ks.length - 1; i++) {
    const [ta, pa, la] = ks[i], [tb, pb, lb] = ks[i + 1];
    if (t <= tb) { const e = easeInOut((t - ta) / (tb - ta)); return [pa.clone().lerp(pb, e), la.clone().lerp(lb, e)]; }
  }
  const k = ks[ks.length - 1]; return [k[1].clone(), k[2].clone()];
}
const shake = (t, a) => V(Math.sin(t * 1.7) * a + Math.sin(t * 3.1) * a * 0.5, Math.sin(t * 1.3 + 1) * a * 0.6, Math.cos(t * 2.3) * a);

// ---------------------------------------------------------------- frame
const warm = new THREE.Color('#ffe2b8'), cool = new THREE.Color('#a9bde6'), neutral = new THREE.Color('#fff4e6'), gold = new THREE.Color('#ffc98a');
function frame(t) {
  const SKY0 = beatT(16) - 0.02, SKY1 = beatT(20) - 0.02;
  const final = t >= SKY1;
  fin.uniforms.flash.value = 0.85 * punch(t, SKY0, 0.12) + 0.6 * punch(t, SKY1, 0.1) + 0.18 * punch(t, C.DROP, 0.15);
  fin.uniforms.blur.value = 0;
  if (t >= SKY0 && t < SKY1) return frameSky(t, t - SKY0);
  drawFront(t, final);
  // light
  const lit = final ? 1 : smooth(prog(t, 0.0, C.DROP + 0.05)) * 0.7 + (t >= C.DROP ? 0.3 : 0);
  sun.intensity = lerp(1.0, 4.4, lit) * (1 + 0.06 * beatPulse(t, 0, 24, 0.15));
  const col = new THREE.Color().copy(cool).lerp(neutral, t >= C.DROP || final ? 1 : 0);
  if (!final) col.lerp(gold, 0.75 * smooth(prog(t, beatT(8), beatT(8.6))) * (1 - smooth(prog(t, beatT(11), beatT(12)))));
  sun.color.copy(col);
  fill.intensity = final ? 0.45 : lerp(0.18, 0.4, lit);
  const drift = t * 0.07;
  sun.position.set(-5.5 + drift * 2, 9, -2.5 - drift); sun.target.position.set(0.4 - drift * 0.6, 0, 0.2);
  // paper flip
  const fp = final ? 0 : easeInOut(prog(t, beatT(12), beatT(13.6)));
  flipper.rotation.z = fp * Math.PI;
  flipper.position.y = 0.006 + Math.sin(fp * Math.PI) * 0.9 + fp * 0.03;
  flipper.position.x = Math.sin(fp * Math.PI) * -0.15;
  // land: back face resting on the table once flipped (very small lift)
  // polaroids
  pols.forEach(p => {
    const lp = final ? 2 : prog(t, beatT(p.b) - 0.3, beatT(p.b));
    p.m.visible = (final ? false : lp > 0);
    const e = lp >= 1 ? 1 : easeIn(lp);
    const bounce = lp >= 1 ? 0.03 * Math.exp(-(t - beatT(p.b)) * 9) * Math.abs(Math.sin((t - beatT(p.b)) * 26)) : 0;
    p.m.position.set(p.x, p.y + (1 - e) * 0.25, 0.012 + (1 - e) * 0.7 + bounce);
    p.m.rotation.set((1 - e) * 0.25, -(1 - e) * 0.3, p.r + (1 - e) * 0.6);
  });
  // camera
  const [pos, look] = final ? path(finalKeys, t) : path(keys, t);
  pos.add(shake(t, 0.02)); camera.position.copy(pos); camera.lookAt(look);
  camera.fov = 44 - 1.0 * beatPulse(t, 0, 12, 0.14) - (final ? 0.6 * beatPulse(t, 20, 26, 0.14) : 0); camera.updateProjectionMatrix();
  scene.environmentIntensity = 0.25;
  fin.uniforms.vig.value = 0.42; fin.uniforms.grain.value = 0.05;
  fin.render(scene, camera, t);
}
function frameSky(t, ts) {
  const b = C.beatOf(t);
  // letters pop in one per half beat
  letters.forEach((m, i) => {
    const bt = beatT(16 + i * 0.5); const p = prog(t, bt - 0.08, bt + 0.22);
    const s = p <= 0 ? 0.0001 : easeOutBack(p, 2.2);
    m.scale.setScalar(s); m.position.set(m.userData.x, 0.75 + Math.sin(t * 2 + i) * 0.06 + 0.25 * punch(t, bt, 0.12), -9.5);
    m.rotation.set(0, Math.sin(t * 0.8 + i * 0.5) * 0.1, Math.sin(t * 1.2 + i) * 0.03);
  });
  // chrome F swoops through the frame
  const fpP = prog(ts, 0, beatT(20) - beatT(16));
  const fpE = easeInOut(fpP);
  chromeF.position.set(lerp(3.2, -0.2, fpE) + Math.sin(fpP * 6) * 0.15, lerp(-4.2, -1.85, easeOut(fpP * 1.5)) + Math.sin(fpP * 9) * 0.08, lerp(-3.2, -4.4, fpE));
  chromeF.rotation.set(-0.25 + Math.sin(t * 2) * 0.1, lerp(-1.2, 0.35, fpE), lerp(0.35, -0.08, fpE));
  chromeF.scale.setScalar(1.6);
  // camera dolly forward, rush at the end
  const rush = easeIn(prog(t, beatT(19.2), beatT(20)));
  skyCam.position.set(Math.sin(ts * 0.9) * 0.15, 0.2 - ts * 0.05, 2.8 - ts * 0.55 - rush * 6.5);
  skyCam.lookAt(0, 0.35, -8);
  skyCam.rotation.z = Math.sin(ts * 1.1) * 0.03 + rush * 0.25;
  skyCam.fov = 50 + 2.5 * beatPulse(t, 16, 20, 0.12) + rush * 30; skyCam.updateProjectionMatrix();
  clouds.forEach((c, i) => { c.position.x += 0; c.rotation.z = 0; });
  fin.uniforms.blur.value = rush * 0.006;
  fin.uniforms.vig.value = 0.25; fin.uniforms.grain.value = 0.035;
  fin.render(sky, skyCam, t);
}
window.__dbg = { sun, renderer, scene, table, paper, fill };
C.run(frame);
