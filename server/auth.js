'use strict';
const crypto = require('crypto');
const { db, audit } = require('./db');
const config = require('./config');

const SESSION_COOKIE = config.isProd ? '__Host-bc_sid' : 'bc_sid';
const SCRYPT = { N: 2 ** 15, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };
const MAX_FAILED = 5;
const LOCK_MS = 15 * 60 * 1000;

const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');
const randomToken = (bytes = 32) => crypto.randomBytes(bytes).toString('base64url');

// ---------- passwords (scrypt, built into Node) ----------
function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(password, salt, 64, SCRYPT);
  return `scrypt$${SCRYPT.N}$${SCRYPT.r}$${SCRYPT.p}$${salt.toString('base64')}$${hash.toString('base64')}`;
}

function verifyPassword(password, stored) {
  if (!stored) return false;
  const [algo, N, r, p, salt, hash] = stored.split('$');
  if (algo !== 'scrypt') return false;
  const expected = Buffer.from(hash, 'base64');
  const actual = crypto.scryptSync(password, Buffer.from(salt, 'base64'), expected.length, {
    N: Number(N), r: Number(r), p: Number(p), maxmem: SCRYPT.maxmem,
  });
  return crypto.timingSafeEqual(expected, actual);
}

// Used when the e-mail does not exist so response time does not reveal it.
const DUMMY_HASH = hashPassword(randomToken());

function validatePassword(pw) {
  if (typeof pw !== 'string' || pw.length < 12) return 'Hasło musi mieć co najmniej 12 znaków.';
  if (pw.length > 200) return 'Hasło jest za długie.';
  if (!/[a-zA-Z]/.test(pw) || !/[0-9]/.test(pw)) return 'Hasło musi zawierać litery i cyfry.';
  return null;
}

// ---------- sessions ----------
function parseCookies(header) {
  const out = {};
  if (!header) return out;
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

function setSessionCookie(res, value, maxAgeSec) {
  const attrs = [
    `${SESSION_COOKIE}=${value}`, 'Path=/', 'HttpOnly', 'SameSite=Lax', `Max-Age=${maxAgeSec}`,
  ];
  if (config.isProd) attrs.push('Secure');
  res.append('Set-Cookie', attrs.join('; '));
}

function createSession(req, res, userId) {
  const sid = randomToken();
  const now = Date.now();
  const expires = now + config.sessionDays * 86400000;
  db.prepare(`INSERT INTO sessions (id_hash, user_id, csrf, created_at, expires_at, ip, user_agent)
              VALUES (?,?,?,?,?,?,?)`)
    .run(sha256(sid), userId, randomToken(), now, expires, req.ip, String(req.get('user-agent') || '').slice(0, 300));
  setSessionCookie(res, sid, config.sessionDays * 86400);
}

function destroySession(req, res) {
  const sid = parseCookies(req.headers.cookie)[SESSION_COOKIE];
  if (sid) db.prepare('DELETE FROM sessions WHERE id_hash = ?').run(sha256(sid));
  setSessionCookie(res, '', 0);
}

function destroyAllSessions(userId) {
  db.prepare('DELETE FROM sessions WHERE user_id = ?').run(userId);
}

// Attaches req.user / req.session when a valid session cookie is present.
function loadSession(req, res, next) {
  const sid = parseCookies(req.headers.cookie)[SESSION_COOKIE];
  if (sid) {
    const row = db.prepare(`
      SELECT s.id_hash, s.csrf, s.expires_at, u.id, u.email, u.name, u.role, u.active
      FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.id_hash = ?`).get(sha256(sid));
    if (row && row.expires_at > Date.now() && row.active) {
      req.session = { idHash: row.id_hash, csrf: row.csrf };
      req.user = { id: row.id, email: row.email, name: row.name, role: row.role };
    } else if (row) {
      db.prepare('DELETE FROM sessions WHERE id_hash = ?').run(row.id_hash);
    }
  }
  next();
}

// ---------- guards ----------
function requireUser(req, res, next) {
  if (req.user) return next();
  if (req.path.startsWith('/api/')) return res.status(401).json({ error: 'Zaloguj się ponownie.' });
  return res.redirect('/login?next=' + encodeURIComponent(req.originalUrl));
}

function requireAdmin(req, res, next) {
  if (req.user && req.user.role === 'admin') return next();
  if (req.path.startsWith('/api/')) return res.status(403).json({ error: 'Brak uprawnień.' });
  return res.status(404).send('Nie znaleziono');
}

// CSRF: every state-changing request from a logged-in user must carry the
// per-session token in the X-CSRF-Token header (set by public/js/common.js).
function requireCsrf(req, res, next) {
  if (['GET', 'HEAD', 'OPTIONS'].includes(req.method)) return next();
  const origin = req.get('origin');
  if (origin && origin !== config.baseUrl) return res.status(403).json({ error: 'Nieprawidłowe źródło żądania.' });
  if (req.session) {
    const sent = String(req.get('x-csrf-token') || '');
    const a = Buffer.from(sent);
    const b = Buffer.from(req.session.csrf);
    if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
      return res.status(403).json({ error: 'Sesja wygasła, odśwież stronę.' });
    }
  }
  next();
}

// ---------- simple in-memory rate limiter ----------
function rateLimit({ windowMs, max, key = (req) => req.ip, message = 'Za dużo prób. Spróbuj później.' }) {
  const hits = new Map();
  setInterval(() => {
    const now = Date.now();
    for (const [k, v] of hits) if (v.reset < now) hits.delete(k);
  }, windowMs).unref();
  return (req, res, next) => {
    const k = key(req);
    const now = Date.now();
    let entry = hits.get(k);
    if (!entry || entry.reset < now) { entry = { count: 0, reset: now + windowMs }; hits.set(k, entry); }
    entry.count++;
    if (entry.count > max) {
      res.set('Retry-After', Math.ceil((entry.reset - now) / 1000));
      return res.status(429).json({ error: message });
    }
    next();
  };
}

// ---------- login with account lockout ----------
function attemptLogin(email, password, ip) {
  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(String(email || '').trim());
  if (!user || !user.password_hash) {
    verifyPassword(String(password || ''), DUMMY_HASH);
    return { error: 'Nieprawidłowy e-mail lub hasło.' };
  }
  if (user.locked_until && user.locked_until > Date.now()) {
    return { error: 'Konto tymczasowo zablokowane po zbyt wielu próbach. Spróbuj za 15 minut.' };
  }
  if (!verifyPassword(String(password || ''), user.password_hash) || !user.active) {
    const failed = user.failed_logins + 1;
    const lock = failed >= MAX_FAILED ? Date.now() + LOCK_MS : null;
    db.prepare('UPDATE users SET failed_logins = ?, locked_until = ? WHERE id = ?')
      .run(lock ? 0 : failed, lock, user.id);
    audit(user.id, 'login_failed', null, ip);
    return { error: 'Nieprawidłowy e-mail lub hasło.' };
  }
  db.prepare('UPDATE users SET failed_logins = 0, locked_until = NULL WHERE id = ?').run(user.id);
  audit(user.id, 'login', null, ip);
  return { user };
}

// ---------- one-time tokens (activation / password reset) ----------
function createToken(userId, purpose, hours = 72) {
  const token = randomToken();
  db.prepare('DELETE FROM tokens WHERE user_id = ? AND purpose = ?').run(userId, purpose);
  db.prepare('INSERT INTO tokens (token_hash, user_id, purpose, expires_at) VALUES (?,?,?,?)')
    .run(sha256(token), userId, purpose, Date.now() + hours * 3600000);
  return token;
}

function findToken(token) {
  if (typeof token !== 'string' || token.length < 20) return null;
  const row = db.prepare('SELECT * FROM tokens WHERE token_hash = ?').get(sha256(token));
  if (!row || row.used_at || row.expires_at < Date.now()) return null;
  return row;
}

function consumeToken(token) {
  db.prepare('UPDATE tokens SET used_at = ? WHERE token_hash = ?').run(Date.now(), sha256(token));
}

// Periodic cleanup of expired sessions/tokens.
setInterval(() => {
  const now = Date.now();
  db.prepare('DELETE FROM sessions WHERE expires_at < ?').run(now);
  db.prepare('DELETE FROM tokens WHERE expires_at < ?').run(now);
}, 3600000).unref();

module.exports = {
  hashPassword, verifyPassword, validatePassword,
  createSession, destroySession, destroyAllSessions, loadSession,
  requireUser, requireAdmin, requireCsrf, rateLimit,
  attemptLogin, createToken, findToken, consumeToken,
};
