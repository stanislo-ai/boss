'use strict';
const path = require('path');
const fs = require('fs');

// Minimal .env loader (no dependency). Real environment variables win.
const envFile = path.join(__dirname, '..', '.env');
if (fs.existsSync(envFile)) {
  for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
}

const env = process.env;
const isProd = env.NODE_ENV === 'production';

module.exports = {
  isProd,
  port: Number(env.PORT || 3000),
  host: env.HOST || '127.0.0.1',
  baseUrl: (env.BASE_URL || (env.VERCEL_PROJECT_PRODUCTION_URL ? 'https://' + env.VERCEL_PROJECT_PRODUCTION_URL : 'http://localhost:3000')).replace(/\/$/, ''),
  // Turso: libsql://twoja-baza.turso.io + token. Locally falls back to a SQLite file.
  databaseUrl: env.DATABASE_URL || 'file:' + (env.DB_PATH || path.join(__dirname, '..', 'data', 'biznescreator.db')),
  databaseToken: env.DATABASE_AUTH_TOKEN || '',
  // Behind Vercel / nginx the client IP comes from X-Forwarded-For.
  trustProxy: env.VERCEL ? 1 : env.TRUST_PROXY ? Number(env.TRUST_PROXY) || env.TRUST_PROXY : false,
  sessionDays: Number(env.SESSION_DAYS || 7),
  // Optional Google Places (New) key. Empty = only free OpenStreetMap data is used.
  googleApiKey: env.GOOGLE_PLACES_API_KEY || '',
  // Hard monthly cap on Google requests so you never leave the free tier.
  googleMonthlyCap: Number(env.GOOGLE_MONTHLY_CAP || 900),
  overpassUrl: env.OVERPASS_URL || 'https://overpass-api.de/api/interpreter',
  contactEmail: env.CONTACT_EMAIL || 'kontakt@biznescreator.pl',
};
