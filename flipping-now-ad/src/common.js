import * as THREE from 'three';
import { SVGLoader } from 'three/addons/loaders/SVGLoader.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';

export const W = 1080, H = 1920;
// Beat grid of the track (123 BPM). Audio is cut so the drop lands at DROP seconds.
export const DROP = 1.96, BEAT = 0.4868, BAR = BEAT * 4;
export const beatT = (b) => DROP + b * BEAT;          // time of beat b (b=0 is the drop)
export const beatOf = (t) => (t - DROP) / BEAT;

export const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
export const lerp = (a, b, t) => a + (b - a) * t;
export const smooth = (t) => { t = clamp(t); return t * t * (3 - 2 * t); };
export const easeOut = (t) => 1 - Math.pow(1 - clamp(t), 3);
export const easeIn = (t) => Math.pow(clamp(t), 3);
export const easeInOut = (t) => { t = clamp(t); return t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; };
export const easeOutBack = (t, s = 1.7) => { t = clamp(t) - 1; return 1 + t * t * ((s + 1) * t + s); };
export const easeOutExpo = (t) => t >= 1 ? 1 : 1 - Math.pow(2, -10 * clamp(t));
export const prog = (t, a, b) => clamp((t - a) / (b - a));
// punch envelope: 1 at the beat, decays after
export const punch = (t, bt, d = 0.25) => (t < bt ? 0 : Math.exp(-(t - bt) / d));
// sum of punches on every beat in [b0,b1)
export const beatPulse = (t, b0, b1, d = 0.18, every = 1) => {
  const b = beatOf(t); if (b < b0 || b >= b1 + 1) return 0;
  const k = Math.floor((b - b0) / every) * every + b0; if (k >= b1) return 0;
  return punch(t, beatT(k), d);
};

export function makeRenderer({ toneExposure = 1.0 } = {}) {
  const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(1);
  renderer.setSize(W, H);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = toneExposure;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  document.body.appendChild(renderer.domElement);
  return renderer;
}

export function envMap(renderer) {
  const pm = new THREE.PMREMGenerator(renderer);
  return pm.fromScene(new RoomEnvironment(), 0.04).texture;
}

export async function loadFonts(list) {
  await Promise.all(list.map((f) => document.fonts.load(f)));
  await document.fonts.ready;
}

export function canvasTex(w, h) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace; tex.anisotropy = 8;
  return { c, ctx: c.getContext('2d'), tex };
}

export function loadImage(src) {
  return new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });
}

// The F logo as THREE.Shapes, centred, height = 1 unit
export async function logoShapes() {
  const txt = await (await fetch('/assets/logo.svg')).text();
  const data = new SVGLoader().parse(txt);
  const shapes = [];
  for (const p of data.paths) shapes.push(...SVGLoader.createShapes(p));
  // bbox
  const box = new THREE.Box2();
  shapes.forEach(s => s.getPoints(8).forEach(pt => box.expandByPoint(pt)));
  const c = box.getCenter(new THREE.Vector2()), sz = box.getSize(new THREE.Vector2());
  const k = 1 / sz.y;
  const out = shapes.map(s => {
    const tr = (v) => new THREE.Vector2((v.x - c.x) * k, -(v.y - c.y) * k);
    const ns = new THREE.Shape(s.getPoints(24).map(tr));
    ns.holes = s.holes.map(h => new THREE.Path(h.getPoints(24).map(tr)));
    return ns;
  });
  return { shapes: out, aspect: sz.x / sz.y };
}

export function logoMesh(shapes, { depth = 0.12, bevel = 0.02, material } = {}) {
  const geo = new THREE.ExtrudeGeometry(shapes, { depth, bevelEnabled: true, bevelThickness: bevel, bevelSize: bevel * 0.8, bevelSegments: 4, curveSegments: 12 });
  geo.translate(0, 0, -depth / 2);
  geo.computeVertexNormals();
  return new THREE.Mesh(geo, material);
}

// Film look: grain + vignette + chromatic fringe, drawn as a fullscreen pass
export function makeFinisher(renderer) {
  const rt = new THREE.WebGLRenderTarget(W, H, { samples: 4, type: THREE.HalfFloatType });
  rt.texture.colorSpace = THREE.LinearSRGBColorSpace;
  const scene = new THREE.Scene(); const cam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  const mat = new THREE.ShaderMaterial({
    uniforms: { ovl: { value: null }, useOvl: { value: 0 }, tex: { value: rt.texture }, time: { value: 0 }, vig: { value: 0.35 }, grain: { value: 0.045 }, flash: { value: 0 }, ca: { value: 0.0015 }, blur: { value: 0 } },
    vertexShader: `varying vec2 vUv; void main(){ vUv=uv; gl_Position=vec4(position.xy,0.,1.); }`,
    fragmentShader: `
      uniform sampler2D tex, ovl; uniform float useOvl; uniform float time, vig, grain, flash, ca, blur; varying vec2 vUv;
      float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233)) + time*17.0)*43758.5453); }
      vec3 samp(vec2 uv){
        vec2 d = (uv-0.5); float r = length(d);
        vec3 c; c.r = texture2D(tex, uv + d*ca*r*8.0).r; c.g = texture2D(tex, uv).g; c.b = texture2D(tex, uv - d*ca*r*8.0).b;
        return c;
      }
      void main(){
        vec3 c = samp(vUv);
        if (blur > 0.0) { vec3 acc = c; float n = 1.0;
          for (int i=1;i<=6;i++){ float o = float(i)*blur; acc += samp(vUv+vec2(0.,o)) + samp(vUv-vec2(0.,o)); n+=2.0; }
          c = acc/n; }
        // tone map (input is linear HDR)
        c = c * 0.9; c = (c*(2.51*c+0.03))/(c*(2.43*c+0.59)+0.14);
        c = pow(clamp(c,0.,1.), vec3(1.0/2.2));
        vec2 q = vUv - 0.5; q.x *= 0.5625;
        c *= 1.0 - vig * smoothstep(0.25, 0.75, length(q) * 1.4);
        c += (h(vUv*vec2(1080.,1920.)) - 0.5) * grain;
        if (useOvl > 0.5) { vec4 o = texture2D(ovl, vUv); c = mix(c, o.rgb, o.a); }
        c = mix(c, vec3(1.0), flash);
        gl_FragColor = vec4(c, 1.0);
      }`,
    depthTest: false, depthWrite: false,
  });
  scene.add(new THREE.Mesh(new THREE.PlaneGeometry(2, 2), mat));
  return {
    uniforms: mat.uniforms,
    render(sc, camera, t) {
      renderer.toneMapping = THREE.NoToneMapping;
      renderer.setRenderTarget(rt); renderer.render(sc, camera);
      renderer.setRenderTarget(null); mat.uniforms.time.value = t;
      renderer.render(scene, cam);
    },
  };
}

// Hook the scene into the offline renderer / live preview
export function run(renderFn) {
  const canvas = document.querySelector('canvas');
  window.__frame = (t) => { renderFn(t); return canvas.toDataURL('image/jpeg', 0.94).split(',')[1]; };
  window.__ready = true;
  const params = new URLSearchParams(location.search);
  if (!params.has('render')) {
    const t0 = performance.now(); const fixed = params.get('t');
    const loop = () => { renderFn(fixed ? +fixed : ((performance.now() - t0) / 1000) % 15.2); requestAnimationFrame(loop); };
    loop();
  }
}

// rounded rect path
export function rr(ctx, x, y, w, h, r) {
  ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
}
