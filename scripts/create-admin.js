'use strict';
// Usage: npm run create-admin -- email@domena.pl "Imię Nazwisko"
// Creates (or promotes) an admin and prints a one-time link to set the password.
// Uses DATABASE_URL / DATABASE_AUTH_TOKEN / BASE_URL from .env – so it works against Turso too.
// No password ever goes through the command line / shell history.
const { db } = require('../server/db');
const auth = require('../server/auth');
const config = require('../server/config');

(async () => {
  const [email, ...nameParts] = process.argv.slice(2);
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    console.error('Użycie: npm run create-admin -- email@domena.pl "Imię Nazwisko"');
    process.exit(1);
  }
  const name = nameParts.join(' ') || 'Administrator';
  let user = await db.get('SELECT * FROM users WHERE email = ?', email);
  if (user) {
    await db.run("UPDATE users SET role = 'admin', active = 1 WHERE id = ?", user.id);
  } else {
    const r = await db.run("INSERT INTO users (email, name, role, created_at) VALUES (?,?,'admin',?)", email, name, Date.now());
    user = { id: r.lastInsertRowid };
  }
  const token = await auth.createToken(user.id, 'activate', 24);
  console.log(`\nAdmin ${email} gotowy. Otwórz ten link (ważny 24h), żeby ustawić hasło:\n\n  ${config.baseUrl}/aktywuj?token=${token}\n`);
  process.exit(0);
})().catch((e) => { console.error('Błąd:', e.message); process.exit(1); });
