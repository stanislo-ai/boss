'use strict';
const path = require('path');
const fs = require('fs');
const express = require('express');
const config = require('./config');
const { db, audit } = require('./db');
const auth = require('./auth');
const search = require('./search');
const { checkMany } = require('./sitecheck');

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
app.use('/static', express.static(path.join(__dirname, '..', 'public'), { maxAge: config.isProd ? '7d' : 0 }));
app.use('/static/leaflet', express.static(path.join(__dirname, '..', 'node_modules', 'leaflet', 'dist'), { maxAge: '30d' }));
app.get('/favicon.svg', (req, res) => res.sendFile(path.join(__dirname, '..', 'public', 'img', 'favicon.svg')));
app.get('/favicon.ico', (req, res) => res.redirect(301, '/favicon.svg'));
app.use(auth.loadSession);
app.use(auth.requireCsrf);

// ---------- views ----------
const VIEWS = path.join(__dirname, '..', 'views');
const layout = fs.readFileSync(path.join(VIEWS, 'layout.html'), 'utf8');
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
  const body = fs.readFileSync(path.join(VIEWS, view), 'utf8');
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

const LEAFLET = ['/static/leaflet/leaflet.css'];

app.get('/', (req, res) => render(res, 'index.html', { req, title: '', scripts: ['/static/js/common.js'] }));
app.get('/login', (req, res) => {
  if (req.user) return res.redirect('/app');
  render(res, 'login.html', { req, title: 'Logowanie', scripts: ['/static/js/common.js', '/static/js/login.js'] });
});
app.get(['/register', '/rejestracja', '/signup'], (req, res) => res.redirect(302, '/rekrutacja'));
app.get('/rekrutacja', (req, res) =>
  render(res, 'rekrutacja.html', { req, title: 'Rekrutacja', scripts: ['/static/js/common.js', '/static/js/rekrutacja.js'] }));
app.get('/aktywuj', (req, res) =>
  render(res, 'aktywuj.html', { req, title: 'Ustaw hasło', scripts: ['/static/js/common.js', '/static/js/aktywuj.js'] }));
app.get('/app', auth.requireUser, (req, res) =>
  render(res, 'app.html', {
    req, title: 'Mapa', bodyClass: 'app-page', styles: LEAFLET,
    scripts: ['/static/leaflet/leaflet.js', '/static/js/common.js', '/static/js/app.js'],
  }));
app.get('/app/leady', auth.requireUser, (req, res) =>
  render(res, 'leady.html', { req, title: 'Leady', scripts: ['/static/js/common.js', '/static/js/leady.js'] }));
app.get('/app/konto', auth.requireUser, (req, res) =>
  render(res, 'konto.html', { req, title: 'Konto', scripts: ['/static/js/common.js', '/static/js/konto.js'] }));
app.get('/admin', auth.requireUser, auth.requireAdmin, (req, res) =>
  render(res, 'admin.html', { req, title: 'Admin', scripts: ['/static/js/common.js', '/static/js/admin.js'] }));

// ---------- auth API ----------
const loginLimiter = auth.rateLimit({ windowMs: 15 * 60 * 1000, max: 10 });
app.post('/api/login', loginLimiter, (req, res) => {
  const { email, password } = req.body || {};
  const r = auth.attemptLogin(email, password, req.ip);
  if (r.error) return res.status(401).json({ error: r.error });
  auth.createSession(req, res, r.user.id);
  res.json({ ok: true });
});

app.post('/api/logout', (req, res) => {
  auth.destroySession(req, res);
  res.json({ ok: true });
});

const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,190}\.[a-z]{2,}$/i;
const str = (v, max) => (typeof v === 'string' ? v.trim().slice(0, max) : '');

const applyLimiter = auth.rateLimit({ windowMs: 60 * 60 * 1000, max: 5, message: 'Za dużo zgłoszeń z tego adresu. Spróbuj za godzinę.' });
app.post('/api/rekrutacja', applyLimiter, (req, res) => {
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

  const existingUser = db.prepare('SELECT 1 FROM users WHERE email = ?').get(a.email);
  const pending = db.prepare("SELECT 1 FROM applications WHERE email = ? AND status = 'pending'").get(a.email);
  // Same answer either way so the form cannot be used to check who has an account.
  if (!existingUser && !pending) {
    const { name, email, ...answers } = a;
    db.prepare('INSERT INTO applications (name, email, answers, created_at, ip) VALUES (?,?,?,?,?)')
      .run(name, email, JSON.stringify(answers), Date.now(), req.ip);
  }
  res.json({ ok: true });
});

app.get('/api/aktywuj', (req, res) => {
  const t = auth.findToken(String(req.query.token || ''));
  if (!t) return res.status(400).json({ error: 'Link jest nieprawidłowy lub wygasł.' });
  const u = db.prepare('SELECT name, email FROM users WHERE id = ?').get(t.user_id);
  res.json({ name: u.name, email: u.email, purpose: t.purpose });
});

const activateLimiter = auth.rateLimit({ windowMs: 15 * 60 * 1000, max: 20 });
app.post('/api/aktywuj', activateLimiter, (req, res) => {
  const { token, password } = req.body || {};
  const t = auth.findToken(String(token || ''));
  if (!t) return res.status(400).json({ error: 'Link jest nieprawidłowy lub wygasł.' });
  const pwErr = auth.validatePassword(password);
  if (pwErr) return res.status(400).json({ error: pwErr });
  db.prepare('UPDATE users SET password_hash = ?, failed_logins = 0, locked_until = NULL WHERE id = ?')
    .run(auth.hashPassword(password), t.user_id);
  auth.consumeToken(token);
  auth.destroyAllSessions(t.user_id);
  audit(t.user_id, 'password_set', t.purpose, req.ip);
  auth.createSession(req, res, t.user_id);
  res.json({ ok: true });
});

app.post('/api/account/password', auth.requireUser, loginLimiter, (req, res) => {
  const { current, password } = req.body || {};
  const row = db.prepare('SELECT password_hash FROM users WHERE id = ?').get(req.user.id);
  if (!auth.verifyPassword(String(current || ''), row.password_hash)) {
    return res.status(400).json({ error: 'Obecne hasło jest nieprawidłowe.' });
  }
  const pwErr = auth.validatePassword(password);
  if (pwErr) return res.status(400).json({ error: pwErr });
  db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(auth.hashPassword(password), req.user.id);
  auth.destroyAllSessions(req.user.id);
  auth.createSession(req, res, req.user.id);
  audit(req.user.id, 'password_changed', null, req.ip);
  res.json({ ok: true });
});

app.post('/api/account/logout-all', auth.requireUser, (req, res) => {
  auth.destroyAllSessions(req.user.id);
  auth.destroySession(req, res);
  res.json({ ok: true });
});

app.get('/api/me', auth.requireUser, (req, res) => res.json({ user: req.user }));

// ---------- search API ----------
app.get('/api/categories', auth.requireUser, (req, res) => {
  res.json({ categories: search.categoryList(), google: !!config.googleApiKey, googleUsage: search.googleUsage() });
});

const searchLimiter = auth.rateLimit({ windowMs: 60 * 1000, max: 8, key: (req) => 'u' + req.user.id, message: 'Za dużo wyszukiwań. Odczekaj minutę.' });
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

const checkLimiter = auth.rateLimit({ windowMs: 60 * 1000, max: 30, key: (req) => 'c' + req.user.id, message: 'Za dużo sprawdzeń stron. Odczekaj chwilę.' });
app.post('/api/check-sites', auth.requireUser, checkLimiter, async (req, res) => {
  const urls = Array.isArray(req.body?.urls) ? req.body.urls.slice(0, 25).map((u) => str(u, 500)) : [];
  const statuses = await checkMany(urls);
  res.json({ statuses });
});

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

app.get('/api/leads', auth.requireUser, (req, res) => {
  const rows = db.prepare('SELECT * FROM leads WHERE user_id = ? ORDER BY updated_at DESC').all(req.user.id);
  res.json({ leads: rows.map(leadRow) });
});

app.post('/api/leads', auth.requireUser, (req, res) => {
  const l = req.body || {};
  const placeId = str(l.id, 120);
  if (!/^(osm|google):/.test(placeId)) return res.status(400).json({ error: 'Nieprawidłowy lead.' });
  const count = db.prepare('SELECT COUNT(*) AS c FROM leads WHERE user_id = ?').get(req.user.id).c;
  if (count >= 5000) return res.status(400).json({ error: 'Osiągnięto limit 5000 leadów.' });
  const now = Date.now();
  db.prepare(`INSERT INTO leads (user_id, place_id, data, created_at, updated_at) VALUES (?,?,?,?,?)
              ON CONFLICT(user_id, place_id) DO NOTHING`)
    .run(req.user.id, placeId, JSON.stringify(sanitizeLead(l)), now, now);
  const row = db.prepare('SELECT * FROM leads WHERE user_id = ? AND place_id = ?').get(req.user.id, placeId);
  res.json({ lead: leadRow(row) });
});

app.patch('/api/leads/:id', auth.requireUser, (req, res) => {
  const row = db.prepare('SELECT * FROM leads WHERE id = ? AND user_id = ?').get(Number(req.params.id), req.user.id);
  if (!row) return res.status(404).json({ error: 'Nie znaleziono leada.' });
  const status = req.body?.status !== undefined ? req.body.status : row.status;
  if (!LEAD_STATUSES.includes(status)) return res.status(400).json({ error: 'Nieprawidłowy status.' });
  const notes = req.body?.notes !== undefined ? str(req.body.notes, 5000) : row.notes;
  db.prepare('UPDATE leads SET status = ?, notes = ?, updated_at = ? WHERE id = ?').run(status, notes, Date.now(), row.id);
  res.json({ lead: leadRow(db.prepare('SELECT * FROM leads WHERE id = ?').get(row.id)) });
});

app.delete('/api/leads/:id', auth.requireUser, (req, res) => {
  db.prepare('DELETE FROM leads WHERE id = ? AND user_id = ?').run(Number(req.params.id), req.user.id);
  res.json({ ok: true });
});

// ---------- admin ----------
const adminOnly = [auth.requireUser, auth.requireAdmin];

app.get('/api/admin/applications', adminOnly, (req, res) => {
  const rows = db.prepare('SELECT * FROM applications ORDER BY status = \'pending\' DESC, created_at DESC LIMIT 500').all();
  res.json({ applications: rows.map((r) => ({ ...r, answers: JSON.parse(r.answers) })) });
});

app.post('/api/admin/applications/:id/:decision', adminOnly, (req, res) => {
  const appRow = db.prepare('SELECT * FROM applications WHERE id = ?').get(Number(req.params.id));
  if (!appRow) return res.status(404).json({ error: 'Nie znaleziono zgłoszenia.' });
  if (appRow.status !== 'pending') return res.status(400).json({ error: 'Zgłoszenie zostało już rozpatrzone.' });
  const decision = req.params.decision;
  if (decision === 'reject') {
    db.prepare("UPDATE applications SET status = 'rejected', decided_at = ? WHERE id = ?").run(Date.now(), appRow.id);
    audit(req.user.id, 'application_rejected', appRow.email, req.ip);
    return res.json({ ok: true });
  }
  if (decision !== 'approve') return res.status(400).json({ error: 'Nieznana akcja.' });
  let user = db.prepare('SELECT * FROM users WHERE email = ?').get(appRow.email);
  if (!user) {
    const r = db.prepare('INSERT INTO users (email, name, created_at) VALUES (?,?,?)').run(appRow.email, appRow.name, Date.now());
    user = { id: r.lastInsertRowid };
  }
  const token = auth.createToken(user.id, 'activate', 72);
  db.prepare("UPDATE applications SET status = 'approved', decided_at = ? WHERE id = ?").run(Date.now(), appRow.id);
  audit(req.user.id, 'application_approved', appRow.email, req.ip);
  res.json({ ok: true, link: `${config.baseUrl}/aktywuj?token=${token}`, email: appRow.email, name: appRow.name });
});

app.get('/api/admin/users', adminOnly, (req, res) => {
  const rows = db.prepare(`SELECT u.id, u.email, u.name, u.role, u.active, u.created_at,
      (u.password_hash IS NOT NULL) AS activated,
      (SELECT COUNT(*) FROM leads l WHERE l.user_id = u.id) AS leads
    FROM users u ORDER BY u.created_at DESC`).all();
  res.json({ users: rows });
});

app.post('/api/admin/users/:id/toggle', adminOnly, (req, res) => {
  const id = Number(req.params.id);
  if (id === req.user.id) return res.status(400).json({ error: 'Nie możesz zablokować samego siebie.' });
  const u = db.prepare('SELECT active FROM users WHERE id = ?').get(id);
  if (!u) return res.status(404).json({ error: 'Nie znaleziono użytkownika.' });
  db.prepare('UPDATE users SET active = ? WHERE id = ?').run(u.active ? 0 : 1, id);
  if (u.active) auth.destroyAllSessions(id);
  audit(req.user.id, u.active ? 'user_blocked' : 'user_unblocked', String(id), req.ip);
  res.json({ ok: true, active: !u.active });
});

app.post('/api/admin/users/:id/reset', adminOnly, (req, res) => {
  const u = db.prepare('SELECT id, email, name FROM users WHERE id = ?').get(Number(req.params.id));
  if (!u) return res.status(404).json({ error: 'Nie znaleziono użytkownika.' });
  const token = auth.createToken(u.id, 'reset', 24);
  audit(req.user.id, 'reset_link_created', u.email, req.ip);
  res.json({ ok: true, link: `${config.baseUrl}/aktywuj?token=${token}`, email: u.email, name: u.name });
});

// ---------- errors ----------
app.use('/api', (req, res) => res.status(404).json({ error: 'Nie znaleziono.' }));
app.use((req, res) => res.status(404).type('html').send('<!doctype html><meta charset="utf-8"><title>404</title><p style="font-family:sans-serif">Nie znaleziono strony. <a href="/">Wróć</a></p>'));
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
