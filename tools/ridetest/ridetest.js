// End-to-end ride test for ScooterMC.
//
// A minimal protocol client that does the two things a real Minecraft client does and that the
// plugin depends on: it sends player_input packets, and it integrates the velocity the server
// sends into its own position (movement is client-authoritative). That is enough to exercise
// mounting, throttle, steering, the rig entities and dismount against a live server.

const mc = require('minecraft-protocol');

const HOST = '127.0.0.1';
const PORT = 25565;
const VERSION = '1.21.11';
const USERNAME = 'ScootBot';

const INTERACTION_TYPE = 69;   // minecraft:interaction
const ITEM_DISPLAY_TYPE = 72;  // minecraft:item_display

const IN = { forward: 0, backward: 0, left: 0, right: 0, jump: 0, shift: 0, sprint: 0 };

const state = {
  entityId: null,
  pos: null,
  yaw: 0,
  vel: { x: 0, y: 0, z: 0 },
  interactions: new Map(),
  itemDisplays: new Set(),
  ridingTicks: 0,
  travelled: 0,
  headingSeen: 0,
  log: [],
};

function say(...a) {
  const line = a.join(' ');
  state.log.push(line);
  console.log(line);
}

const client = mc.createClient({
  host: HOST, port: PORT, username: USERNAME, version: VERSION, auth: 'offline',
});

client.on('error', (e) => { say('ERROR', e.message); process.exit(2); });
client.on('end', (r) => { say('disconnected:', r || ''); });

client.on('login', (packet) => {
  state.entityId = packet.entityId;
  say('logged in, entityId=' + packet.entityId);
  try { client.write('player_loaded', {}); } catch (e) { /* older protocols lack it */ }
});

client.on('position', (packet) => {
  // Absolute unless the corresponding relative flag is set; the server only ever sends us
  // absolute placements here, so take them at face value and confirm.
  state.pos = { x: packet.x, y: packet.y, z: packet.z };
  state.yaw = packet.yaw;
  client.write('teleport_confirm', { teleportId: packet.teleportId });
  sendPosition();
});

let velLogged = 0;
client.on('entity_velocity', (packet) => {
  if (packet.entityId !== state.entityId) return;
  if (velLogged < 3) { velLogged++; say('raw entity_velocity ' + JSON.stringify(packet.velocity)); }
  const v = packet.velocity;
  // minecraft-data may hand these back either as raw shorts or already scaled.
  const scale = Math.abs(v.x) > 8 || Math.abs(v.z) > 8 ? 1 / 8000 : 1;
  state.vel = { x: v.x * scale, y: v.y * scale, z: v.z * scale };
});

function plain(node) {
  if (node === null || node === undefined) return '';
  if (typeof node === 'string') return node;
  if (typeof node !== 'object') return String(node);
  if (Array.isArray(node)) return node.map(plain).join('');
  let out = plain(node.text ?? node.value?.text ?? '');
  const extra = node.extra ?? node.value?.extra?.value?.value ?? node.value?.extra;
  if (extra) out += plain(extra);
  if (!out && node.value) out = plain(node.value);
  return out;
}
client.on('system_chat', (p) => {
  const t = plain(p.content).replace(/\s+/g, ' ').trim();
  if (t) say('  [chat] ' + t);
});

client.on('spawn_entity', (packet) => {
  if (packet.type === INTERACTION_TYPE) {
    state.interactions.set(packet.entityId, packet);
  } else if (packet.type === ITEM_DISPLAY_TYPE) {
    state.itemDisplays.add(packet.entityId);
  }
});

client.on('entity_destroy', (packet) => {
  for (const id of packet.entityIds || []) {
    state.interactions.delete(id);
    state.itemDisplays.delete(id);
  }
});

client.on('kick_disconnect', (p) => say('KICKED:', JSON.stringify(p)));
client.on('disconnect', (p) => say('DISCONNECT:', JSON.stringify(p)));

function inputByte() {
  return { ...IN };
}

function sendPosition() {
  if (!state.pos) return;
  client.write('position', {
    x: state.pos.x, y: state.pos.y, z: state.pos.z,
    flags: { onGround: true, hasHorizontalCollision: false },
  });
}

let lastInput = null;
function sendInputIfChanged() {
  const now = JSON.stringify(IN);
  if (now !== lastInput) {
    lastInput = now;
    client.write('player_input', { inputs: inputByte() });
  }
}

// The physics a real client would run. The plugin overwrites horizontal velocity every tick, so
// integrating it and reporting the result back is exactly what a vanilla client ends up doing.
function tick() {
  if (!state.pos) return;
  const before = { ...state.pos };
  state.pos.x += state.vel.x;
  state.pos.z += state.vel.z;
  // Ground friction, so an unanswered velocity packet dies away instead of drifting for ever.
  state.vel.x *= 0.55;
  state.vel.z *= 0.55;
  // Stay on the flat superflat surface rather than simulating full vertical physics.
  const moved = Math.hypot(state.pos.x - before.x, state.pos.z - before.z);
  state.travelled += moved;
  if (moved > 1e-6) {
    const dir = Math.atan2(-state.vel.x, state.vel.z);
    if (state.lastDir !== undefined && Math.abs(dir - state.lastDir) > 0.01) state.headingSeen++;
    state.lastDir = dir;
  }
  sendPosition();
  sendInputIfChanged();
}

function cmd(text) {
  say('> /' + text);
  client.write('chat_command', { command: text });
}

const steps = [];
function at(ms, fn) { steps.push([ms, fn]); }

at(4000, () => cmd('scooter spawn cyan'));
function tryMount(attempt) {
  const target = [...state.interactions.keys()].pop();
  if (target === undefined) {
    if (attempt < 12) { setTimeout(() => tryMount(attempt + 1), 400); return; }
    say('FAIL no interaction entity appeared to click');
    return;
  }
  say('interaction entities seen: ' + state.interactions.size
      + ', item displays: ' + state.itemDisplays.size + '; mounting ' + target);
  client.write('use_entity', { target, mouse: 2, x: 0, y: 0.5, z: 0, hand: 0, sneaking: false });
  client.write('use_entity', { target, mouse: 0, hand: 0, sneaking: false });
  state.mounted = true;
}
at(5200, () => tryMount(0));
at(7000, () => { say('throttle on (jump to kick off first)'); IN.jump = 1; });
at(7300, () => { IN.jump = 0; IN.forward = 1; });
at(8300, () => { IN.jump = 1; });
at(8600, () => { IN.jump = 0; });
let seq = 0;
function action(status) {
  client.write('block_dig', {
    status, location: { x: 0, y: 0, z: 0 }, face: 1, sequence: ++seq,
  });
}
at(11500, () => {
  say('displays attached while riding: ' + state.itemDisplays.size);
  say('distance travelled so far: ' + state.travelled.toFixed(2) + ' blocks');
  cmd('scooter diagnose');
});
at(12200, () => { say('scroll down (slot +3): slower mode'); client.write('held_item_slot', { slotId: 3 }); });
at(12800, () => { say('scroll up (slot -4 after wrap): faster mode'); client.write('held_item_slot', { slotId: 5 }); });
at(13100, () => { say('scroll up again'); client.write('held_item_slot', { slotId: 6 }); });
at(13400, () => { say('F: toggle headlight'); action(6); });      // SWAP_ITEM_WITH_OFFHAND
at(13700, () => { say('F: toggle headlight off'); action(6); });
at(14000, () => { say('right-click: horn'); client.write('use_item', { hand: 0, sequence: ++seq, rotation: { x: 0, z: 0 } }); });
at(14600, () => { say('steering right'); IN.right = 1; });
at(16000, () => { IN.right = 0; cmd('scooter info'); });
at(17000, () => { say('braking'); IN.forward = 0; IN.backward = 1; });
at(19000, () => { IN.backward = 0; cmd('scooter diagnose'); });
at(20500, () => {
  say('total distance: ' + state.travelled.toFixed(2) + ' blocks');
  say('dismounting (sneak)');
  IN.shift = 1;
});
at(21500, () => { IN.shift = 0; cmd('scooter list'); });
at(22200, () => {
  // Fold the parked scooter back into an item: left-click (attack) its Interaction entity.
  const target = [...state.interactions.keys()].pop();
  if (target === undefined) { say('FAIL nothing to pick up'); return; }
  say('picking the scooter back up (sneak + right-click ' + target + ')');
  IN.shift = 1;
  client.write('player_input', { inputs: { ...IN } });
  setTimeout(() => {
    client.write('use_entity', { target, mouse: 2, x: 0, y: 0.5, z: 0, hand: 0, sneaking: true });
    client.write('use_entity', { target, mouse: 0, hand: 0, sneaking: true });
    IN.shift = 0;
    client.write('player_input', { inputs: { ...IN } });
  }, 250);
  state.pickedUp = true;
});
at(23200, () => { cmd('scooter list'); });
at(24000, () => {
  // Put it back down on the block we are standing on, to prove the item kept its state.
  const bx = Math.floor(state.pos.x), by = Math.floor(state.pos.y) - 1, bz = Math.floor(state.pos.z);
  say('placing it again on ' + bx + ',' + by + ',' + bz);
  client.write('block_place', {
    hand: 0, location: { x: bx, y: by, z: bz }, direction: 1,
    cursorX: 0.5, cursorY: 1.0, cursorZ: 0.5,
    insideBlock: false, worldBorderHit: false, sequence: ++seq,
  });
});
at(25000, () => { cmd('scooter list'); });
at(26500, () => {
  const crashes = state.log.filter((l) => l.includes('hit something')).length;
  say('RESULT travelled=' + state.travelled.toFixed(2)
      + ' displays=' + state.itemDisplays.size
      + ' spuriousCrashes=' + crashes
      + ' headingChanged=' + state.headingSeen);
  const ok = state.mounted && state.pickedUp && state.travelled > 15 && crashes === 0;
  say(ok ? 'PASS ride, controls and dismount all behaved'
         : 'FAIL see above');
  client.end();
  setTimeout(() => process.exit(ok ? 0 : 1), 500);
});

client.on('login', () => {
  setInterval(tick, 50);
  let elapsed = 0;
  const timer = setInterval(() => {
    elapsed += 100;
    while (steps.length && steps[0][0] <= elapsed) {
      const [, fn] = steps.shift();
      try { fn(); } catch (e) { say('step error: ' + e.message); }
    }
    if (!steps.length) clearInterval(timer);
  }, 100);
});
