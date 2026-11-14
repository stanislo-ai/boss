// V3 — "Studio": chrome F + 3D products on a turntable, bold TikTok-style kinetic captions.
import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { Reflector } from 'three/addons/objects/Reflector.js';
import * as C from './common.js';
const { beatT, prog, smooth, easeOut, easeIn, easeInOut, easeOutBack, easeOutExpo, clamp, lerp, punch, beatPulse } = C;

const LIME = '#c8ff2e';
const renderer = C.makeRenderer();
const fin = C.makeFinisher(renderer);
const env = C.envMap(renderer);
await C.loadFonts(['900 100px Inter', '800 60px Inter', '600 30px Inter', 'italic 200px "Instrument Serif"']);

// ---------------------------------------------------------------- scene
const scene = new THREE.Scene(); scene.background = new THREE.Color('#040405');
scene.environment = env; scene.environmentIntensity = 0.5;
scene.fog = new THREE.Fog('#040405', 7, 16);
const camera = new THREE.PerspectiveCamera(38, C.W / C.H, 0.05, 60);

const mirror = new Reflector(new THREE.PlaneGeometry(30, 30), { textureWidth: 540, textureHeight: 960, color: '#3a3a40', clipBias: 0.003 });
mirror.rotation.x = -Math.PI / 2; scene.add(mirror);
const floorTint = new THREE.Mesh(new THREE.PlaneGeometry(30, 30), new THREE.MeshStandardMaterial({ color: '#060607', roughness: 0.5, metalness: 0.2, transparent: true, opacity: 0.72 }));
floorTint.rotation.x = -Math.PI / 2; floorTint.position.y = 0.002; floorTint.receiveShadow = true; scene.add(floorTint);

// lights
const key = new THREE.SpotLight('#ffffff', 0, 0, 0.42, 0.7, 0); key.position.set(0.8, 6, 3.2); key.castShadow = true; key.shadow.mapSize.set(1024, 1024); key.shadow.bias = -0.0005; scene.add(key, key.target);
const rimL = new THREE.SpotLight('#7fb2ff', 0, 0, 0.5, 0.8, 0); rimL.position.set(-4, 2.5, -3); scene.add(rimL, rimL.target);
const rimR = new THREE.SpotLight(LIME, 0, 0, 0.5, 0.8, 0); rimR.position.set(4, 2, -2.5); scene.add(rimR, rimR.target);
const amb = new THREE.HemisphereLight('#c8d0e0', '#0a0a0a', 0.1); scene.add(amb);

// turntable with 3 pedestals
const table = new THREE.Group(); scene.add(table);
const R = 2.1;
const pedMat = new THREE.MeshStandardMaterial({ color: '#111113', roughness: 0.35, metalness: 0.5 });
const ringMat = new THREE.MeshStandardMaterial({ color: '#000', emissive: LIME, emissiveIntensity: 2.5 });
const slots = [0, 1, 2].map(i => {
  const g = new THREE.Group(); const a = i * Math.PI * 2 / 3;
  g.position.set(Math.sin(a) * R, 0, Math.cos(a) * R);
  const ped = new THREE.Mesh(new THREE.CylinderGeometry(0.62, 0.66, 0.36, 64), pedMat); ped.position.y = 0.18; ped.castShadow = ped.receiveShadow = true;
  const ring = new THREE.Mesh(new THREE.TorusGeometry(0.63, 0.012, 8, 96), ringMat); ring.rotation.x = Math.PI / 2; ring.position.y = 0.362;
  const spin = new THREE.Group(); spin.position.y = 0.36; g.add(ped, ring, spin); table.add(g); g.userData.spin = spin; return g;
});
const S = (g) => g.userData.spin;

// --- product 1: earbuds case
{
  const white = new THREE.MeshPhysicalMaterial({ color: '#f4f4f2', roughness: 0.28, clearcoat: 1, clearcoatRoughness: 0.15 });
  const g = new THREE.Group();
  const body = new THREE.Mesh(new RoundedBoxGeometry(0.62, 0.36, 0.27, 6, 0.12), white); body.position.y = 0.18;
  const lid = new THREE.Mesh(new RoundedBoxGeometry(0.62, 0.16, 0.27, 6, 0.075), white); lid.position.y = 0.446;
  const seam = new THREE.Mesh(new THREE.BoxGeometry(0.6, 0.006, 0.272), new THREE.MeshStandardMaterial({ color: '#9a9a98', roughness: 0.6 })); seam.position.y = 0.362;
  const led = new THREE.Mesh(new THREE.SphereGeometry(0.012, 12, 8), new THREE.MeshStandardMaterial({ color: '#000', emissive: '#3dff6a', emissiveIntensity: 4 })); led.position.set(0, 0.25, 0.136);
  g.add(body, lid, led); [body, lid].forEach(m => m.castShadow = true);
  const bud = () => { const b = new THREE.Group();
    const head = new THREE.Mesh(new THREE.SphereGeometry(0.085, 32, 24), white); head.scale.set(1, 0.92, 1.05);
    const stem = new THREE.Mesh(new THREE.CapsuleGeometry(0.03, 0.22, 8, 16), white); stem.position.set(0, -0.15, 0.01);
    const tip = new THREE.Mesh(new THREE.SphereGeometry(0.05, 24, 16), new THREE.MeshStandardMaterial({ color: '#d8d8d6', roughness: 0.9 })); tip.position.set(0.0, 0.0, -0.08); tip.scale.set(1, 1, 0.6);
    const grill = new THREE.Mesh(new THREE.CircleGeometry(0.02, 16), new THREE.MeshStandardMaterial({ color: '#222' })); grill.position.set(0, 0.02, 0.087);
    b.add(head, stem, tip, grill); b.traverse(m => m.castShadow = true); return b; };
  const b1 = bud(), b2 = bud(); b1.position.set(-0.55, 0.72, 0.2); b2.position.set(0.55, 0.76, 0.12); b2.scale.x = -1;
  g.add(b1, b2); g.userData.buds = [b1, b2];
  S(slots[0]).add(g); slots[0].userData.prod = g;
}
// --- product 2: perfume
{
  const g = new THREE.Group();
  const glass = new THREE.MeshPhysicalMaterial({ color: '#ffffff', roughness: 0.03, transmission: 1, thickness: 0.35, ior: 1.5, clearcoat: 1, envMapIntensity: 2.0 });
  const liquid = new THREE.MeshStandardMaterial({ color: '#d9781a', roughness: 0.15, emissive: '#8a3a00', emissiveIntensity: 0.9 });
  const gold = new THREE.MeshStandardMaterial({ color: '#e2b65a', metalness: 1, roughness: 0.22, envMapIntensity: 1.8 });
  const body = new THREE.Mesh(new RoundedBoxGeometry(0.52, 0.6, 0.3, 6, 0.06), glass); body.position.y = 0.3;
  const liq = new THREE.Mesh(new RoundedBoxGeometry(0.44, 0.44, 0.22, 4, 0.04), liquid); liq.position.y = 0.26;
  const neck = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 0.08, 32), gold); neck.position.y = 0.64;
  const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.13, 0.13, 0.26, 48), gold); cap.position.y = 0.8;
  for (let i = 0; i < 6; i++) { const rib = new THREE.Mesh(new THREE.TorusGeometry(0.132, 0.006, 8, 48), gold); rib.rotation.x = Math.PI / 2; rib.position.y = 0.7 + i * 0.04; g.add(rib); }
  const lab = C.canvasTex(256, 128); lab.ctx.fillStyle = 'rgba(0,0,0,0)'; lab.ctx.clearRect(0, 0, 256, 128); lab.ctx.fillStyle = '#f3e3c0'; lab.ctx.font = '800 30px Inter'; lab.ctx.letterSpacing = '8px'; lab.ctx.textAlign = 'center'; lab.ctx.fillText('ÉLIXIR', 132, 60); lab.ctx.font = '600 16px Inter'; lab.ctx.fillText('EAU DE PARFUM', 132, 92);
  const label = new THREE.Mesh(new THREE.PlaneGeometry(0.36, 0.18), new THREE.MeshStandardMaterial({ map: lab.tex, transparent: true, roughness: 0.4, metalness: 0.6 })); label.position.set(0, 0.3, 0.152);
  g.add(body, liq, neck, cap, label); g.scale.setScalar(0.86); [body, cap].forEach(m => m.castShadow = true);
  S(slots[1]).add(g); slots[1].userData.prod = g;
}
// --- product 3: stack of folded clothes + cap
{
  const g = new THREE.Group();
  const fab = (() => { const { ctx, tex } = C.canvasTex(256, 256); ctx.fillStyle = '#808080'; ctx.fillRect(0, 0, 256, 256);
    for (let y = 0; y < 256; y += 2) { ctx.fillStyle = y % 4 ? '#8a8a8a' : '#767676'; ctx.fillRect(0, y, 256, 1); }
    const d = ctx.getImageData(0, 0, 256, 256); for (let i = 0; i < d.data.length; i += 4) { const n = (Math.random() - .5) * 40; d.data[i] += n; d.data[i + 1] += n; d.data[i + 2] += n; } ctx.putImageData(d, 0, 0);
    tex.wrapS = tex.wrapT = THREE.RepeatWrapping; tex.repeat.set(3, 3); tex.colorSpace = THREE.NoColorSpace; return tex; })();
  const cols = ['#141416', '#e9e6df', '#b9a888', '#2b3a55'];
  cols.forEach((c, i) => {
    const m = new THREE.Mesh(new RoundedBoxGeometry(0.86, 0.11, 0.66, 4, 0.045), new THREE.MeshStandardMaterial({ color: c, roughness: 0.92, bumpMap: fab, bumpScale: 2.5 }));
    m.position.y = 0.06 + i * 0.112; m.rotation.y = (i % 2 ? 1 : -1) * 0.05 * (i + 1); m.castShadow = m.receiveShadow = true; g.add(m);
    // folded collar hint
    const col = new THREE.Mesh(new THREE.TorusGeometry(0.1, 0.018, 8, 32, Math.PI), new THREE.MeshStandardMaterial({ color: c, roughness: 0.9 }));
    col.rotation.x = -Math.PI / 2; col.position.set(0, m.position.y + 0.055, -0.2); col.rotation.z = m.rotation.y; g.add(col);
  });
  // logo tag on top
  const tag = C.canvasTex(256, 128); tag.ctx.fillStyle = '#0d0d0e'; tag.ctx.fillRect(0, 0, 256, 128); tag.ctx.fillStyle = LIME; tag.ctx.font = '900 44px Inter'; tag.ctx.textAlign = 'center'; tag.ctx.fillText('FLIP', 128, 64); tag.ctx.fillStyle = '#ddd'; tag.ctx.font = '600 20px Inter'; tag.ctx.fillText('NOW', 128, 98);
  const t = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.008, 0.11), [0, 0, 0, 0, 0, 0].map((_, i) => i === 2 ? new THREE.MeshStandardMaterial({ map: tag.tex }) : new THREE.MeshStandardMaterial({ color: '#0d0d0e' })));
  t.position.set(0.2, 0.06 + 3 * 0.112 + 0.06, 0.18); t.rotation.y = 0.3; g.add(t);
  S(slots[2]).add(g); slots[2].userData.prod = g;
}

// chrome F hero
const { shapes } = await C.logoShapes();
const chrome = new THREE.MeshStandardMaterial({ color: '#ffffff', metalness: 1, roughness: 0.08, envMapIntensity: 2.2 });
const F = C.logoMesh(shapes, { depth: 0.22, bevel: 0.03, material: chrome }); F.castShadow = true;
const Fg = new THREE.Group(); Fg.add(F); scene.add(Fg);

// ---------------------------------------------------------------- overlay captions
const ov = C.canvasTex(C.W, C.H); ov.tex.colorSpace = THREE.NoColorSpace; ov.tex.anisotropy = 1;
fin.uniforms.ovl.value = ov.tex; fin.uniforms.useOvl.value = 1;
function word(x, txt, cx, cy, size, { color = '#fff', p = 1, weight = 900, font = 'Inter', box = null, tracking = -2, italic = false, alpha = 1 } = {}) {
  if (p <= 0) return;
  x.save(); const s = lerp(1.6, 1, easeOutExpo(p)); x.globalAlpha = clamp(p * 3) * alpha;
  x.translate(cx, cy); x.scale(s, s);
  x.font = `${italic ? 'italic ' : ''}${weight} ${size}px ${font === 'Inter' ? 'Inter' : '"' + font + '"'}`; x.letterSpacing = tracking + 'px'; x.textAlign = 'center'; x.textBaseline = 'middle';
  if (box) { const w = x.measureText(txt).width; x.fillStyle = box; C.rr(x, -w / 2 - size * 0.28, -size * 0.62, w + size * 0.56, size * 1.18, size * 0.16); x.fill(); }
  x.shadowColor = 'rgba(0,0,0,0.45)'; x.shadowBlur = box ? 0 : 30; x.fillStyle = color; x.fillText(txt, 0, size * 0.04);
  x.restore();
}
const pIn = (t, bt, d = 0.16) => prog(t, bt - 0.03, bt - 0.03 + d);
const pOut = (t, bt, d = 0.12) => 1 - prog(t, bt - 0.06, bt - 0.06 + d);
function counter(t, from, to, b0, b1) { return Math.round(lerp(from, to, easeOut(prog(t, beatT(b0), beatT(b1))))); }

function drawOverlay(t) {
  const x = ov.ctx; x.clearRect(0, 0, C.W, C.H);
  const cx = C.W / 2;
  // intro hook — one word per pre-drop beat
  if (t < C.DROP + 0.2) {
    const o = pOut(t, C.DROP + 0.04);
    word(x, 'PŁACISZ', cx, 470, 150, { p: pIn(t, 0.05), alpha: o });
    word(x, 'ZA', cx, 650, 150, { p: pIn(t, 0.54), alpha: o });
    word(x, 'DUŻO', cx - 40, 830, 150, { p: pIn(t, 1.03), alpha: o, color: LIME });
    word(x, '?', cx + 215, 830, 150, { p: pIn(t, 1.52), alpha: o, color: LIME });
  }
  const sec = (b0, b1, num, title, sub, price) => {
    const t0 = beatT(b0), o = pOut(t, beatT(b1));
    if (t < t0 - 0.05 || o <= 0) return;
    word(x, num, cx, 250, 34, { p: pIn(t, t0), alpha: o, weight: 600, tracking: 10, color: '#bdbdbd' });
    word(x, title, cx, 340, 104, { p: pIn(t, t0 + 0.05), alpha: o, tracking: -3 });
    if (sub) word(x, sub, cx, 430, 40, { p: pIn(t, beatT(b0 + 0.5)), alpha: o, weight: 600, color: '#d0d0d0', tracking: 0 });
    if (price) {
      const v = counter(t, price[0], price[1], b0 + 1, b0 + 2);
      word(x, `OD ${v} ZŁ`, cx, 1390, 150, { p: pIn(t, beatT(b0 + 1)), alpha: o, color: '#0a0a0a', box: LIME, tracking: -4 });
      word(x, `1 szt. = ${price[0]} zł  →  hurt od ${price[1]} zł`, cx, 1525, 36, { p: pIn(t, beatT(b0 + 2)), alpha: o * 0.9, weight: 600, color: '#cfcfcf', tracking: 0 });
    }
  };
  sec(0, 4, '01 / 03', 'AIRPODS PRO', 'Pro 2 · Pro 3', [200, 45]);
  sec(4, 8, '02 / 03', 'PERFUMY', 'premium, top zapachy', [130, 70]);
  sec(8, 12, '03 / 03', 'UBRANIA', 'pełny cennik u dostawcy', null);
  if (t >= beatT(9) - 0.05 && t < beatT(12)) { const o = pOut(t, beatT(12)); word(x, 'TANIEJ', cx, 1390, 170, { p: pIn(t, beatT(9)), alpha: o, color: '#0a0a0a', box: LIME, tracking: -5 }); }
  // all-in-one
  if (t >= beatT(12) - 0.05 && t < beatT(16)) {
    const o = pOut(t, beatT(16));
    word(x, 'WSZYSTKO', cx, 300, 120, { p: pIn(t, beatT(12)), alpha: o });
    word(x, 'OD JEDNEGO', cx, 425, 120, { p: pIn(t, beatT(13)), alpha: o });
    word(x, 'DOSTAWCY', cx, 550, 120, { p: pIn(t, beatT(14)), alpha: o, color: LIME });
  }
  // end card
  if (t >= beatT(16) - 0.05) {
    word(x, 'FLIPPING NOW', cx, 280, 112, { p: pIn(t, beatT(16)), tracking: 2 });
    word(x, 'prywatny dostawca', cx, 385, 72, { p: pIn(t, beatT(17)), weight: 400, font: 'Instrument Serif', italic: true, tracking: 0, color: '#e6e6e6' });
    const pulse = 1 + 0.06 * beatPulse(t, 18, 40, 0.16);
    x.save(); x.translate(cx, 1330); x.scale(pulse, pulse);
    word(x, 'LINK W BIO ↓', 0, 0, 120, { p: pIn(t, beatT(18)), color: '#0a0a0a', box: LIME, tracking: -3 });
    x.restore();
    word(x, '@flippingnow999', cx, 1475, 54, { p: pIn(t, beatT(19)), weight: 800, tracking: 1, color: '#ffffff' });
  }
  ov.tex.needsUpdate = true;
}

// ---------------------------------------------------------------- choreography
const V = (x, y, z) => new THREE.Vector3(x, y, z);
function tableAngle(t) {
  // product i faces camera when table.rotation.y = -i*120deg ; whip on bars
  const step = (b) => easeInOut(prog(t, beatT(b) - 0.3, beatT(b) + 0.05));
  return -(step(4) + step(8)) * Math.PI * 2 / 3 - step(12) * Math.PI / 3;
}
function frame(t) {
  drawOverlay(t);
  const on = t >= C.DROP - 0.02;
  const bp = beatPulse(t, 0, 40, 0.15);
  key.intensity = on ? 16 * (1 + 0.15 * bp) : 0;
  rimL.intensity = on ? 22 : 8 * smooth(prog(t, 0.2, 1.9));
  rimR.intensity = on ? 18 * (1 + 0.3 * bp) : 4 * smooth(prog(t, 0.8, 1.9));
  amb.intensity = on ? 0.12 : 0.02;
  ringMat.emissiveIntensity = on ? 2.5 * (1 + 0.8 * bp) : 0.0;
  table.rotation.y = tableAngle(t);
  table.position.y = -1.3 * easeInOut(prog(t, beatT(15.5), beatT(16.6)));
  { const k = lerp(1, 0.62, easeInOut(prog(t, beatT(11.8), beatT(12.8)))); slots.forEach((sl, i) => { const a = i * Math.PI * 2 / 3; sl.position.set(Math.sin(a) * R * k, 0, Math.cos(a) * R * k); }); }
  table.visible = on;
  slots.forEach((s, i) => { S(s).rotation.y = t * 0.6 + i; });
  const buds = slots[0].userData.prod.userData.buds; buds.forEach((b, i) => { b.position.y = 0.76 + Math.sin(t * 2.2 + i * 2) * 0.04; b.rotation.set(0.3 + Math.sin(t * 1.4 + i) * 0.2, t * 0.8 * (i ? -1 : 1), i ? -0.4 : 0.4); });
  // F: intro silhouette, then hidden, then rises in the centre for the finale
  const rise = easeOutBack(prog(t, beatT(12) - 0.1, beatT(13)), 1.3);
  if (!on) { Fg.visible = true; Fg.position.set(0, 1.25, 0.6); Fg.scale.setScalar(1.0); Fg.rotation.set(0, -0.6 + t * 0.35, 0); key.intensity = 0; }
  else if (t < beatT(12) - 0.1) { Fg.visible = false; }
  else { Fg.visible = true; Fg.position.set(0, lerp(-1.5, 1.62, rise) - 0.12 * easeInOut(prog(t, beatT(15.6), beatT(16.8))) + Math.sin(t * 1.5) * 0.04, 0.2 - 0.2 * easeInOut(prog(t, beatT(15.6), beatT(16.8)))); Fg.scale.setScalar(lerp(0.5, 1.08, rise)); const orb = (t - beatT(12)) * 0.18; Fg.rotation.set(Math.sin(t) * 0.05, orb + easeInOut(prog(t, beatT(12), beatT(14.5))) * Math.PI * 2 + Math.sin((t - beatT(14.5)) * 1.3) * 0.32 * smooth(prog(t, beatT(14.5), beatT(16))) + 0.12 * bp, 0); }
  key.target.position.copy(on && t < beatT(12) ? new THREE.Vector3(0, 0.4, R) : new THREE.Vector3(0, 1, 0));
  rimL.target.position.set(0, 0.6, R * 0.5); rimR.target.position.set(0, 0.6, R * 0.5);
  // camera
  let pos, look;
  if (!on) { pos = V(Math.sin(t * 0.4) * 0.3, 1.0, 6.4 - t * 0.35); look = V(0, 1.75, 0.6); }
  else if (t < beatT(12) - 0.1) {
    const lb = (C.beatOf(t) % 4) / 4; // push-in within each product bar
    const whip = beatPulse(t, 4, 12, 0.18, 4);
    pos = V(Math.sin(t * 0.7) * 0.25 + whip * 0.4, lerp(1.12, 1.02, lb), R + lerp(3.0, 2.55, easeOut(lb))); look = V(0, 0.7, R);
  } else {
    const p = easeInOut(prog(t, beatT(12) - 0.1, beatT(14)));
    const orbit = (t - beatT(12)) * 0.18;
    pos = V(Math.sin(orbit) * lerp(4.4, 6.4, p), lerp(1.2, 2.1, p), Math.cos(orbit) * lerp(4.4, 6.4, p)); look = V(0, lerp(0.8, 1.38, p), 0);
    if (t > beatT(16)) { const q = easeInOut(prog(t, beatT(16), beatT(18))); pos.lerp(V(Math.sin(orbit) * 4.9, 1.45, Math.cos(orbit) * 4.9), q); look.lerp(V(0, 1.3, 0), q); }
  }
  camera.position.copy(pos); camera.lookAt(look);
  camera.fov = 38 - 1.5 * bp; camera.updateProjectionMatrix();
  fin.uniforms.flash.value = 0.7 * punch(t, C.DROP, 0.14) + 0.35 * punch(t, beatT(12), 0.12) + 0.25 * punch(t, beatT(16), 0.12);
  fin.uniforms.vig.value = 0.5; fin.uniforms.grain.value = 0.05; fin.uniforms.ca.value = 0.002;
  fin.render(scene, camera, t);
}
window.__dbg = { scene, key };
C.run(frame);
