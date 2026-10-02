'use strict';
// Database: libSQL / Turso. Locally it is a plain SQLite file (DATABASE_URL=file:...),
// on Vercel it is a free Turso database reached over HTTPS.
const { createClient } = require('@libsql/client');
const config = require('./config');

const client = createClient({ url: config.databaseUrl, authToken: config.databaseToken || undefined });

const SCHEMA = `
CREATE TABLE IF NOT EXISTS users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  email         TEXT NOT NULL UNIQUE COLLATE NOCASE,
  name          TEXT NOT NULL,
  password_hash TEXT,
  role          TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user','admin')),
  active        INTEGER NOT NULL DEFAULT 1,
  failed_logins INTEGER NOT NULL DEFAULT 0,
  locked_until  INTEGER,
  created_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  id_hash     TEXT PRIMARY KEY,
  user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  csrf        TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  expires_at  INTEGER NOT NULL,
  ip          TEXT,
  user_agent  TEXT
);
CREATE INDEX IF NOT EXISTS sessions_user ON sessions(user_id);

CREATE TABLE IF NOT EXISTS applications (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL,
  email       TEXT NOT NULL COLLATE NOCASE,
  answers     TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
  created_at  INTEGER NOT NULL,
  decided_at  INTEGER,
  ip          TEXT
);

CREATE TABLE IF NOT EXISTS tokens (
  token_hash  TEXT PRIMARY KEY,
  user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  purpose     TEXT NOT NULL CHECK (purpose IN ('activate','reset')),
  expires_at  INTEGER NOT NULL,
  used_at     INTEGER
);

CREATE TABLE IF NOT EXISTS leads (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  place_id    TEXT NOT NULL,
  data        TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'new'
              CHECK (status IN ('new','called','interested','client','rejected')),
  notes       TEXT NOT NULL DEFAULT '',
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  UNIQUE (user_id, place_id)
);

CREATE TABLE IF NOT EXISTS api_usage (
  month       TEXT PRIMARY KEY,
  google_calls INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS audit_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER,
  action      TEXT NOT NULL,
  detail      TEXT,
  ip          TEXT,
  created_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS rate_limits (
  key         TEXT PRIMARY KEY,
  count       INTEGER NOT NULL,
  reset_at    INTEGER NOT NULL
);

`;

let ready;
function init() {
  if (!ready) {
    ready = (async () => {
      await client.execute('PRAGMA foreign_keys = ON');
      await client.executeMultiple(SCHEMA);
    })().catch((e) => { ready = null; throw e; });
  }
  return ready;
}

const exec = async (sql, args = []) => { await init(); return client.execute({ sql, args }); };

const db = {
  init,
  async get(sql, ...args) { return (await exec(sql, args)).rows[0]; },
  async all(sql, ...args) { return (await exec(sql, args)).rows; },
  async run(sql, ...args) {
    const r = await exec(sql, args);
    return { changes: r.rowsAffected, lastInsertRowid: r.lastInsertRowid != null ? Number(r.lastInsertRowid) : null };
  },
};

async function audit(userId, action, detail, ip) {
  await db.run('INSERT INTO audit_log (user_id, action, detail, ip, created_at) VALUES (?,?,?,?,?)',
    userId || null, action, detail || null, ip || null, Date.now());
}

module.exports = { db, audit, client };
