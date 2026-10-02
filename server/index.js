'use strict';
const path = require('path');
const fs = require('fs');
const express = require('express');
const config = require('./config');
const { db, audit } = require('./db');
const auth = require('./auth');
const search = require('./search');
const { checkMany } = require('./sitecheck');

const { h } = auth;

const app = express();
app.disable('x-powered-by');
app.set('trust proxy', config.trustProxy);

// ---------- security headers ----------
app.use((req, res, next) => {
  res.set({
    'Content-Security-Policy': [
      "default-src 'self'",
      "script-src 'self'",
      "style-src 'self' https://fonts.googleapis.com",
      "font-src https://fonts.gstatic.com",
      "img-src 'self' data: https://tile.openstreetmap.org",
      "connect-src 'self'",
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "object-src 'none'",
    ].join('; '),
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=(self), payment=()',
    'Cross-Origin-Opener-Policy': 'same-origin',
  });
  if (config.isProd) res.set('Strict-Transport-Security', 'max-age=63072000; includeSubDomains; preload');
  next();
});

app.use(express.json({ limit: '200kb' }));
// On Vercel files in public/ are served by the CDN; locally Express serves them.
app.use(express.static(path.join(__dirname, '..', 'public'), { maxAge: config.isProd ? '1d' : 0, index: false }));
app.get('/favicon.ico', (req, res) => res.redirect(301, '/favicon.svg'));
app.use(auth.loadSession);
app.use(auth.requireCsrf);

// ---------- views ----------
const VIEWS = path.join(__dirname, '..', 'views');
const views = Object.fromEntries(fs.readdirSync(VIEWS).map((f) => [f, fs.readFileSync(path.join(VIEWS, f), 'utf8')]));
const layout = views['layout.html'];
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function navFor(user) {
  if (!user) {
    return `<a href="/rekrutacja" class="nav-link">Dołącz</a><a href="/login" class="btn btn-sm">Zaloguj się</a>`;
  }
  const admin = user.role === 'admin' ? '<a href="/admin" class="nav-link">Admin</a>' : '';
  return `<a href="/app" class="nav-link">Mapa</a><a href="/app/leady" class="nav-link">Leady</a>${admin}` +
    `<a href="/app/konto" class="nav-link">Konto</a><button class="btn btn-sm btn-ghost" data-logout>Wyloguj</button>`;
}

function render(res, view, { title, req, scripts = [], styles = [], bodyClass = '' }) {
  const body = views[view];
  const html = layout
    .replace('{{title}}', esc(title ? `${title} · BiznesCreator` : 'BiznesCreator'))
    .replace('{{csrf}}', esc(req.session ? req.session.csrf : ''))
    .replace('{{nav}}', navFor(req.user))
    .replace('{{bodyClass}}', esc(bodyClass))
    .replace('{{styles}}', styles.map((s) => `<link rel="stylesheet" href="${s}">`).join(''))
    .replace('{{scripts}}', scripts.map((s) => `<script src="${s}" defer></script>`).join(''))
    .replace('{{year}}', String(new Date().getFullYear()))
    .replace('{{body}}', () => body);
  res.set('Cache-Control', 'no-store').type('html').send(html);
}

const LEAFLET = ['/vendor/leaflet/leaflet.css'];

app.get('/', (req, res) => render(res, 'index.html', { req, title: '', scripts: ['/js/common.js'] }));
app.get('/login', (req, res) => {
  if (req.user) return res.redirect('/app');
  render(res, 'login.html', { req, title: 'Logowanie', scripts: ['/js/common.js', '/js/login.js'] });
});
app.get(['/register', '/rejestracja', '/signup'], (req, res) => res.redirect(302, '/rekrutacja'));
app.get('/rekrutacja', (req, res) =>
  render(res, 'rekrutacja.html', { req, title: 'Rekrutacja', scripts: ['/js/common.js', '/js/rekrutacja.js'] }));
app.get('/aktywuj', (req, res) =>
  render(res, 'aktywuj.html', { req, title: 'Ustaw hasło', scripts: ['/js/common.js', '/js/aktywuj.js'] }));
app.get('/app', auth.requireUser, (req, res) =>
  render(res, 'app.html', {
    req, title: 'Mapa', bodyClass: 'app-page', styles: LEAFLET,
    scripts: ['/vendor/leaflet/leaflet.js', '/js/common.js', '/js/app.js'],
  }));
app.get('/app/leady', auth.requireUser, (req, res) =>
  render(res, 'leady.html', { req, title: 'Leady', scripts: ['/js/common.js', '/js/leady.js'] }));
app.get('/app/konto', auth.requireUser, (req, res) =>
  render(res, 'konto.html', { req, title: 'Konto', scripts: ['/js/common.js', '/js/konto.js'] }));
app.get('/admin', auth.requireUser, auth.requireAdmin, (req, res) =>
  render(res, 'admin.html', { req, title: 'Admin', scripts: ['/js/common.js', '/js/admin.js'] }));

// ---------- auth API ----------
const loginLimiter = auth.rateLimit({ name: 'login', windowMs: 15 * 60 * 1000, max: 10 });
app.post('/api/login', loginLimiter, h(async (req, res) => {
  const { email, password } = req.body || {};
  const r = await auth.attemptLogin(email, password, req.ip);
  if (r.error) return res.status(401).json({ error: r.error });
  await auth.createSession(req, res, r.user.id);
  res.json({ ok: true });
}));

app.post('/api/logout', h(async (req, res) => {
  await auth.destroySession(req, res);
  res.json({ ok: true });
}));

const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,190}\.[a-z]{2,}$/i;
const str = (v, max) => (typeof v === 'string' ? v.trim().slice(0, max) : '');

const applyLimiter = auth.rateLimit({ name: 'apply', windowMs: 60 * 60 * 1000, max: 5, message: 'Za dużo zgłoszeń z tego adresu. Spróbuj za godzinę.' });
app.post('/api/rekrutacja', applyLimiter, h(async (req, res) => {
  const b = req.body || {};
  if (b.website) return res.json({ ok: true }); // honeypot – bots fill hidden fields
  const a = {
    name: str(b.name, 100),
    email: str(b.email, 254).toLowerCase(),
    age: Number(b.age),
    city: str(b.city, 100),
    why: str(b.why, 2000),
    experience: str(b.experience, 50),
    hours: str(b.hours, 50),
    source: str(b.source, 50),
    goal: str(b.goal, 50),
    social: str(b.social, 300),
    consent: b.consent === true,
  };
  if (a.name.length < 2) return res.status(400).json({ error: 'Podaj imię i nazwisko.' });
  if (!EMAIL_RE.test(a.email)) return res.status(400).json({ error: 'Podaj poprawny adres e-mail.' });
  if (!Number.isInteger(a.age) || a.age < 13 || a.age > 99) return res.status(400).json({ error: 'Podaj poprawny wiek (min. 13 lat).' });
  if (a.why.length < 20) return res.status(400).json({ error: 'Napisz kilka zdań, dlaczego chcesz dołączyć (min. 20 znaków).' });
  if (!a.consent) return res.status(400).json({ error: 'Zaznacz zgodę na przetwarzanie danych.' });

  const existingUser = await db.get('SELECT 1 AS x FROM users WHERE email = ?', a.email);
  const pending = await db.get("SELECT 1 AS x FROM applications WHERE email = ? AND status = 'pending'", a.email);
  // Same answer either way so the form cannot be used to check who has an account.
  if (!existingUser && !pending) {
    const { name, email, ...answers } = a;
    await db.run('INSERT INTO applications (name, email, answers, created_at, ip) VALUES (?,?,?,?,?)',
      name, email, JSON.stringify(answers), Date.now(), req.ip || null);
  }
  res.json({ ok: true });
}));

app.get('/api/aktywuj', h(async (req, res) => {
  const t = await auth.findToken(String(req.query.token || ''));
  if (!t) return res.status(400).json({ error: 'Link jest nieprawidłowy lub wygasł.' });
  const u = await db.get('SELECT name, email FROM users WHERE id = ?', t.user_id);
  res.json({ name: u.name, email: u.email, purpose: t.purpose });
}));

const activateLimiter = auth.rateLimit({ name: 'activate', windowMs: 15 * 60 * 1000, max: 20 });
app.post('/api/aktywuj', activateLimiter, h(async (req, res) => {
  const { token, password } = req.body || {};
  const t = await auth.findToken(String(token || ''));
  if (!t) return res.status(400).json({ error: 'Link jest nieprawidłowy lub wygasł.' });
  const pwErr = auth.validatePassword(password);
  if (pwErr) return res.status(400).json({ error: pwErr });
  if (!(await auth.consumeToken(token))) return res.status(400).json({ error: 'Link jest nieprawidłowy lub wygasł.' });
  await db.run('UPDATE users SET password_hash = ?, failed_logins = 0, locked_until = NULL WHERE id = ?',
    auth.hashPassword(password), t.user_id);
  await auth.destroyAllSessions(t.user_id);
  await audit(t.user_id, 'password_set', t.purpose, req.ip);
  await auth.createSession(req, res, t.user_id);
  res.json({ ok: true });
}));

app.post('/api/account/password', auth.requireUser, loginLimiter, h(async (req, res) => {
  const { current, password } = req.body || {};
  const row = await db.get('SELECT password_hash FROM users WHERE id = ?', req.user.id);
  if (!auth.verifyPassword(String(current || ''), row.password_hash)) {
    return res.status(400).json({ error: 'Obecne hasło jest nieprawidłowe.' });
  }
  const pwErr = auth.validatePassword(password);
  if (pwErr) return res.status(400).json({ error: pwErr });
  await db.run('UPDATE users SET password_hash = ? WHERE id = ?', auth.hashPassword(password), req.user.id);
  await auth.destroyAllSessions(req.user.id);
  await auth.createSession(req, res, req.user.id);
  await audit(req.user.id, 'password_changed', null, req.ip);
  res.json({ ok: true });
}));

app.post('/api/account/logout-all', auth.requireUser, h(async (req, res) => {
  await auth.destroyAllSessions(req.user.id);
  await auth.destroySession(req, res);
  res.json({ ok: true });
}));

app.get('/api/me', auth.requireUser, (req, res) => res.json({ user: req.user }));

// ---------- search API ----------
app.get('/api/categories', auth.requireUser, h(async (req, res) => {
  res.json({ categories: search.categoryList(), google: !!config.googleApiKey, googleUsage: await search.googleUsage() });
}));

const searchLimiter = auth.rateLimit({ name: 'search', windowMs: 60 * 1000, max: 8, key: (req) => req.user.id, message: 'Za dużo wyszukiwań. Odczekaj minutę.' });
app.post('/api/search', auth.requireUser, searchLimiter, async (req, res) => {
  try {
    const r = await search.search(req.body || {});
    if (r.error) return res.status(400).json(r);
    res.json(r);
  } catch (e) {
    console.error('search failed:', e.message);
    res.status(502).json({ error: 'Serwer map nie odpowiada. Spróbuj ponownie za chwilę albo zaznacz mniejszy obszar.' });
  }
});

const checkLimiter = auth.rateLimit({ name: 'check', windowMs: 60 * 1000, max: 30, key: (req) => req.user.id, message: 'Za dużo sprawdzeń stron. Odczekaj chwilę.' });
app.post('/api/check-sites', auth.requireUser, checkLimiter, h(async (req, res) => {
  const urls = Array.isArray(req.body?.urls) ? req.body.urls.slice(0, 25).map((u) => str(u, 500)) : [];
  const statuses = await checkMany(urls);
  res.json({ statuses });
}));

// ---------- leads (CRM) ----------
const LEAD_STATUSES = ['new', 'called', 'interested', 'client', 'rejected'];

function sanitizeLead(l) {
  return {
    name: str(l.name, 200), category: str(l.category, 40), address: str(l.address, 300),
    phone: str(l.phone, 60), website: str(l.website, 500), social: str(l.social, 500),
    openingHours: str(l.openingHours, 600), siteStatus: str(l.siteStatus, 20),
    rating: Number.isFinite(l.rating) ? l.rating : null,
    reviews: Number.isFinite(l.reviews) ? l.reviews : null,
    lat: Number.isFinite(l.lat) ? l.lat : null, lon: Number.isFinite(l.lon) ? l.lon : null,
    mapsUrl: /^https:\/\/(www\.)?google\.[a-z.]+\/maps|^https:\/\/maps\.google\.com/.test(l.mapsUrl || '') ? str(l.mapsUrl, 600) : '',
    source: l.source === 'google' ? 'google' : 'osm',
  };
}

const leadRow = (r) => ({ id: r.id, placeId: r.place_id, status: r.status, notes: r.notes, createdAt: r.created_at, updatedAt: r.updated_at, ...JSON.parse(r.data) });

app.get('/api/leads', auth.requireUser, h(async (req, res) => {
  const rows = await db.all('SELECT * FROM leads WHERE user_id = ? ORDER BY updated_at DESC', req.user.id);
  res.json({ leads: rows.map(leadRow) });
}));

app.post('/api/leads', auth.requireUser, h(async (req, res) => {
  const l = req.body || {};
  const placeId = str(l.id, 120);
  if (!/^(osm|google):/.test(placeId)) return res.status(400).json({ error: 'Nieprawidłowy lead.' });
  const { c } = await db.get('SELECT COUNT(*) AS c FROM leads WHERE user_id = ?', req.user.id);
  if (c >= 5000) return res.status(400).json({ error: 'Osiągnięto limit 5000 leadów.' });
  const now = Date.now();
  await db.run(`INSERT INTO leads (user_id, place_id, data, created_at, updated_at) VALUES (?,?,?,?,?)
                ON CONFLICT(user_id, place_id) DO NOTHING`,
  req.user.id, placeId, JSON.stringify(sanitizeLead(l)), now, now);
  const row = await db.get('SELECT * FROM leads WHERE user_id = ? AND place_id = ?', req.user.id, placeId);
  res.json({ lead: leadRow(row) });
}));

app.patch('/api/leads/:id', auth.requireUser, h(async (req, res) => {
  const row = await db.get('SELECT * FROM leads WHERE id = ? AND user_id = ?', Number(req.params.id), req.user.id);
  if (!row) return res.status(404).json({ error: 'Nie znaleziono leada.' });
  const status = req.body?.status !== undefined ? req.body.status : row.status;
  if (!LEAD_STATUSES.includes(status)) return res.status(400).json({ error: 'Nieprawidłowy status.' });
  const notes = req.body?.notes !== undefined ? str(req.body.notes, 5000) : row.notes;
  await db.run('UPDATE leads SET status = ?, notes = ?, updated_at = ? WHERE id = ?', status, notes, Date.now(), row.id);
  res.json({ lead: leadRow(await db.get('SELECT * FROM leads WHERE id = ?', row.id)) });
}));

app.delete('/api/leads/:id', auth.requireUser, h(async (req, res) => {
  await db.run('DELETE FROM leads WHERE id = ? AND user_id = ?', Number(req.params.id), req.user.id);
  res.json({ ok: true });
}));

// ---------- admin ----------
const adminOnly = [auth.requireUser, auth.requireAdmin];

app.get('/api/admin/applications', adminOnly, h(async (req, res) => {
  const rows = await db.all("SELECT * FROM applications ORDER BY status = 'pending' DESC, created_at DESC LIMIT 500");
  res.json({ applications: rows.map((r) => ({ ...r, answers: JSON.parse(r.answers) })) });
}));

app.post('/api/admin/applications/:id/:decision', adminOnly, h(async (req, res) => {
  const appRow = await db.get('SELECT * FROM applications WHERE id = ?', Number(req.params.id));
  if (!appRow) return res.status(404).json({ error: 'Nie znaleziono zgłoszenia.' });
  if (appRow.status !== 'pending') return res.status(400).json({ error: 'Zgłoszenie zostało już rozpatrzone.' });
  const decision = req.params.decision;
  if (decision === 'reject') {
    await db.run("UPDATE applications SET status = 'rejected', decided_at = ? WHERE id = ?", Date.now(), appRow.id);
    await audit(req.user.id, 'application_rejected', appRow.email, req.ip);
    return res.json({ ok: true });
  }
  if (decision !== 'approve') return res.status(400).json({ error: 'Nieznana akcja.' });
  let user = await db.get('SELECT * FROM users WHERE email = ?', appRow.email);
  if (!user) {
    const r = await db.run('INSERT INTO users (email, name, created_at) VALUES (?,?,?)', appRow.email, appRow.name, Date.now());
    user = { id: r.lastInsertRowid };
  }
  const token = await auth.createToken(user.id, 'activate', 72);
  await db.run("UPDATE applications SET status = 'approved', decided_at = ? WHERE id = ?", Date.now(), appRow.id);
  await audit(req.user.id, 'application_approved', appRow.email, req.ip);
  res.json({ ok: true, link: `${config.baseUrl}/aktywuj?token=${token}`, email: appRow.email, name: appRow.name });
}));

app.get('/api/admin/users', adminOnly, h(async (req, res) => {
  const rows = await db.all(`SELECT u.id, u.email, u.name, u.role, u.active, u.created_at,
      (u.password_hash IS NOT NULL) AS activated,
      (SELECT COUNT(*) FROM leads l WHERE l.user_id = u.id) AS leads
    FROM users u ORDER BY u.created_at DESC`);
  res.json({ users: rows });
}));

app.post('/api/admin/users/:id/toggle', adminOnly, h(async (req, res) => {
  const id = Number(req.params.id);
  if (id === req.user.id) return res.status(400).json({ error: 'Nie możesz zablokować samego siebie.' });
  const u = await db.get('SELECT active FROM users WHERE id = ?', id);
  if (!u) return res.status(404).json({ error: 'Nie znaleziono użytkownika.' });
  await db.run('UPDATE users SET active = ? WHERE id = ?', u.active ? 0 : 1, id);
  if (u.active) await auth.destroyAllSessions(id);
  await audit(req.user.id, u.active ? 'user_blocked' : 'user_unblocked', String(id), req.ip);
  res.json({ ok: true, active: !u.active });
}));

app.post('/api/admin/users/:id/reset', adminOnly, h(async (req, res) => {
  const u = await db.get('SELECT id, email, name FROM users WHERE id = ?', Number(req.params.id));
  if (!u) return res.status(404).json({ error: 'Nie znaleziono użytkownika.' });
  const token = await auth.createToken(u.id, 'reset', 24);
  await audit(req.user.id, 'reset_link_created', u.email, req.ip);
  res.json({ ok: true, link: `${config.baseUrl}/aktywuj?token=${token}`, email: u.email, name: u.name });
}));

// ---------- errors ----------
app.use('/api', (req, res) => res.status(404).json({ error: 'Nie znaleziono.' }));
app.use((req, res) => res.status(404).type('html').send('<!doctype html><meta charset="utf-8"><title>404</title><p>Nie znaleziono strony. <a href="/">Wróć</a></p>'));
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  console.error(err);
  if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'Nieprawidłowe dane.' });
  res.status(500).json({ error: 'Błąd serwera.' });
});

if (require.main === module) {
  app.listen(config.port, config.host, () => {
    console.log(`BiznesCreator działa na http://${config.host}:${config.port}`);
  });
}

module.exports = app;
