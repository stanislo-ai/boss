'use strict';
const { test, before, after } = require('node:test');
const assert = require('node:assert');
const os = require('os');
const path = require('path');
const fs = require('fs');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'bc-'));
process.env.DATABASE_URL = 'file:' + path.join(tmp, 'test.db');
process.env.BASE_URL = 'http://localhost';

const app = require('../server/index');
const { db } = require('../server/db');
const auth = require('../server/auth');
const { checkWebsite, isPrivateIp } = require('../server/sitecheck');
const search = require('../server/search');

let server; let base;
before(async () => {
  server = app.listen(0);
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => server.close());

function client() {
  let cookie = '';
  let csrf = '';
  async function req(method, url, body) {
    const res = await fetch(base + url, {
      method, redirect: 'manual',
      headers: { 'Content-Type': 'application/json', Cookie: cookie, 'X-CSRF-Token': csrf },
      body: body ? JSON.stringify(body) : undefined,
    });
    const sc = res.headers.get('set-cookie');
    if (sc) cookie = sc.split(';')[0];
    const text = await res.text();
    let json; try { json = JSON.parse(text); } catch { json = null; }
    const m = text.match(/name="csrf-token" content="([^"]*)"/);
    if (m && m[1]) csrf = m[1];
    return { status: res.status, json, text, headers: res.headers };
  }
  return { req, get cookie() { return cookie; } };
}

const application = {
  name: 'Jan Kowalski', email: 'jan@example.com', age: 16, city: 'Kraków',
  why: 'Chcę zarabiać na tworzeniu stron dla lokalnych firm.', experience: 'Żadne', hours: '3–10 godzin',
  goal: '1–2 klientów', source: 'TikTok', social: '', consent: true,
};

test('register redirects to recruitment', async () => {
  const c = client();
  const r = await c.req('GET', '/register');
  assert.equal(r.status, 302);
  assert.equal(r.headers.get('location'), '/rekrutacja');
});

test('app requires login', async () => {
  const c = client();
  assert.equal((await c.req('GET', '/app')).status, 302);
  assert.equal((await c.req('POST', '/api/search', {})).status, 401);
});

test('recruitment validation + honeypot', async () => {
  const c = client();
  assert.equal((await c.req('POST', '/api/rekrutacja', { ...application, email: 'zly' })).status, 400);
  assert.equal((await c.req('POST', '/api/rekrutacja', { ...application, consent: false })).status, 400);
  assert.equal((await c.req('POST', '/api/rekrutacja', { ...application, website: 'spam' })).status, 200);
  assert.equal((await db.get('SELECT COUNT(*) c FROM applications')).c, 0);
});

test('full flow: apply -> admin approves -> activate -> login -> leads', async () => {
  // admin
  const adminId = (await db.run("INSERT INTO users (email,name,role,password_hash,created_at) VALUES ('admin@x.pl','Admin','admin',?,?)",
    auth.hashPassword('AdminHaslo1234'), Date.now())).lastInsertRowid;
  assert.ok(adminId);

  const visitor = client();
  assert.equal((await visitor.req('POST', '/api/rekrutacja', application)).status, 200);
  assert.equal((await visitor.req('POST', '/api/rekrutacja', application)).status, 200); // duplicate ignored
  assert.equal((await db.get('SELECT COUNT(*) c FROM applications')).c, 1);

  const admin = client();
  assert.equal((await admin.req('POST', '/api/login', { email: 'admin@x.pl', password: 'zle' })).status, 401);
  assert.equal((await admin.req('POST', '/api/login', { email: 'admin@x.pl', password: 'AdminHaslo1234' })).status, 200);
  await admin.req('GET', '/admin'); // picks up CSRF token
  const apps = (await admin.req('GET', '/api/admin/applications')).json.applications;
  assert.equal(apps.length, 1);

  // CSRF required
  const noCsrf = await fetch(`${base}/api/admin/applications/${apps[0].id}/approve`, { method: 'POST', headers: { Cookie: admin.cookie } });
  assert.equal(noCsrf.status, 403);

  const appr = await admin.req('POST', `/api/admin/applications/${apps[0].id}/approve`);
  assert.equal(appr.status, 200);
  const token = new URL(appr.json.link).searchParams.get('token');

  const user = client();
  assert.equal((await user.req('POST', '/api/aktywuj', { token, password: 'krotkie' })).status, 400);
  assert.equal((await user.req('POST', '/api/aktywuj', { token, password: 'DobreHaslo2024xyz' })).status, 200);
  assert.equal((await client().req('POST', '/api/aktywuj', { token, password: 'DobreHaslo2024xyz' })).status, 400); // one-time

  assert.equal((await user.req('GET', '/app')).status, 200);
  // non-admin cannot see admin api
  assert.equal((await user.req('GET', '/api/admin/users')).status, 403);

  const lead = { id: 'osm:node/1', name: 'Salon Ania', category: 'fryzjerzy', phone: '123', siteStatus: 'none', mapsUrl: 'javascript:alert(1)' };
  const saved = await user.req('POST', '/api/leads', lead);
  assert.equal(saved.status, 200);
  assert.equal(saved.json.lead.mapsUrl, ''); // unsafe url dropped
  const id = saved.json.lead.id;
  assert.equal((await user.req('PATCH', `/api/leads/${id}`, { status: 'called', notes: 'oddzwonić' })).json.lead.status, 'called');
  assert.equal((await user.req('PATCH', `/api/leads/${id}`, { status: 'hacked' })).status, 400);
  // another user cannot touch it
  assert.equal((await admin.req('PATCH', `/api/leads/${id}`, { status: 'client' })).status, 404);
  assert.equal((await user.req('GET', '/api/leads')).json.leads.length, 1);
});

test('account lockout after 5 failed logins', async () => {
  await db.run("INSERT INTO users (email,name,password_hash,created_at) VALUES ('lock@x.pl','L',?,?)", auth.hashPassword('Poprawne12345'), Date.now());
  for (let i = 0; i < 5; i++) await auth.attemptLogin('lock@x.pl', 'zle', '1.1.1.1');
  const r = await auth.attemptLogin('lock@x.pl', 'Poprawne12345', '1.1.1.1');
  assert.match(r.error, /zablokowane/);
});

test('login rate limit stored in DB', async () => {
  const c = client();
  let last;
  for (let i = 0; i < 11; i++) last = await c.req('POST', '/api/login', { email: 'nikt@x.pl', password: 'x' });
  assert.equal(last.status, 429);
  await db.run('DELETE FROM rate_limits');
});

test('cross-site Origin is rejected', async () => {
  const r = await fetch(base + '/api/rekrutacja', { method: 'POST', headers: { 'Content-Type': 'application/json', Origin: 'https://evil.example' }, body: '{}' });
  assert.equal(r.status, 403);
});

test('static files served at root', async () => {
  for (const p of ['/css/style.css', '/js/app.js', '/favicon.svg', '/vendor/leaflet/leaflet.js']) {
    assert.equal((await fetch(base + p)).status, 200, p);
  }
});

test('security headers present', async () => {
  const r = await client().req('GET', '/');
  assert.match(r.headers.get('content-security-policy'), /script-src 'self'/);
  assert.equal(r.headers.get('x-frame-options'), 'DENY');
});

test('site checker blocks private addresses (SSRF)', async () => {
  assert.ok(isPrivateIp('127.0.0.1'));
  assert.ok(isPrivateIp('10.1.2.3'));
  assert.ok(isPrivateIp('::1'));
  assert.ok(isPrivateIp('169.254.169.254'));
  assert.ok(!isPrivateIp('8.8.8.8'));
  assert.equal(await checkWebsite('http://127.0.0.1/'), 'dead');
  assert.equal(await checkWebsite('http://localhost:3000/'), 'dead');
  assert.equal(await checkWebsite('https://www.facebook.com/salon'), 'social');
});

test('OSM classification and area limits', () => {
  const all = Object.keys(search.CATEGORIES);
  assert.equal(search.classifyOsm({ shop: 'hairdresser', hairdresser: 'barber' }, all), 'barberzy');
  assert.equal(search.classifyOsm({ shop: 'hairdresser', hairdresser: 'female' }, all), 'fryzjerzy');
  assert.equal(search.classifyOsm({ shop: 'beauty', beauty: 'nails' }, all), 'paznokcie');
  assert.equal(search.classifyOsm({ amenity: 'fast_food' }, all), 'restauracje');
  assert.equal(search.classifyOsm({ amenity: 'cafe' }, ['restauracje']), null);
  assert.ok(search.validateBounds({ south: 50, west: 19, north: 51, east: 20 }).error);
  assert.ok(search.validateBounds({ south: 50.05, west: 19.9, north: 50.07, east: 19.95 }).bounds);
});

test('OSM search parses Overpass response', async () => {
  const realFetch = global.fetch;
  global.fetch = async () => ({
    ok: true,
    json: async () => ({ elements: [
      { type: 'node', id: 1, lat: 50.06, lon: 19.93, tags: { name: 'Pizzeria Roma', amenity: 'restaurant', phone: '+48 123', 'addr:street': 'Długa', 'addr:housenumber': '5', 'addr:city': 'Kraków' } },
      { type: 'way', id: 2, center: { lat: 50.061, lon: 19.931 }, tags: { name: 'Studio Fryz', shop: 'hairdresser', website: 'https://example.com' } },
    ] }),
  });
  try {
    const r = await search.search({ bounds: { south: 50.05, west: 19.9, north: 50.07, east: 19.95 }, categories: ['restauracje', 'fryzjerzy'] });
    assert.equal(r.results.length, 2);
    const pizza = r.results.find((x) => x.name === 'Pizzeria Roma');
    assert.equal(pizza.siteStatus, 'none');
    assert.equal(pizza.address, 'Długa 5, Kraków');
    assert.equal(r.results.find((x) => x.name === 'Studio Fryz').siteStatus, 'pending');
  } finally {
    global.fetch = realFetch;
  }
});
