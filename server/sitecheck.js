'use strict';
// Checks whether a business website actually responds.
// SSRF-safe: only http/https, standard ports, and every DNS answer is checked
// against private/reserved ranges inside the socket's own lookup (no rebinding).
const http = require('http');
const https = require('https');
const dns = require('dns');
const net = require('net');

const TIMEOUT_MS = 7000;
const MAX_REDIRECTS = 4;
const cache = new Map(); // url -> { result, at }
const CACHE_MS = 24 * 3600 * 1000;

const SOCIAL_HOSTS = [
  'facebook.com', 'fb.com', 'fb.me', 'instagram.com', 'tiktok.com', 'linktr.ee',
  'booksy.com', 'twitter.com', 'x.com', 'youtube.com', 'google.com', 'goo.gl',
  'g.page', 'business.site', 'wa.me', 'whatsapp.com', 'pyszne.pl', 'glovoapp.com',
  'ubereats.com', 'wolt.com', 'znanylekarz.pl', 'moment.pl', 'versum.com',
];

function isPrivateIp(ip) {
  if (net.isIPv4(ip)) {
    const [a, b] = ip.split('.').map(Number);
    return a === 0 || a === 10 || a === 127 || (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) || (a === 192 && b === 0) || (a === 198 && (b === 18 || b === 19)) ||
      a >= 224;
  }
  const v = ip.toLowerCase();
  if (v.startsWith('::ffff:')) return isPrivateIp(v.slice(7));
  return v === '::' || v === '::1' || v.startsWith('fc') || v.startsWith('fd') ||
    v.startsWith('fe8') || v.startsWith('fe9') || v.startsWith('fea') || v.startsWith('feb') ||
    v.startsWith('ff');
}

function safeLookup(hostname, options, cb) {
  dns.lookup(hostname, { ...options, all: true }, (err, addrs) => {
    if (err) return cb(err);
    const list = Array.isArray(addrs) ? addrs : [{ address: addrs, family: options.family || 4 }];
    if (!list.length || list.some((a) => isPrivateIp(a.address))) {
      return cb(new Error('blocked address'));
    }
    if (options.all) return cb(null, list);
    cb(null, list[0].address, list[0].family);
  });
}

function normalizeUrl(raw) {
  if (!raw) return null;
  let s = String(raw).trim().split(/[;\s]/)[0];
  if (!/^https?:\/\//i.test(s)) s = 'http://' + s;
  try {
    const u = new URL(s);
    if (!['http:', 'https:'].includes(u.protocol)) return null;
    if (u.port && !['80', '443'].includes(u.port)) return null;
    if (u.username || u.password) return null;
    return u;
  } catch {
    return null;
  }
}

function isSocialOnly(u) {
  const host = u.hostname.replace(/^www\./, '').toLowerCase();
  return SOCIAL_HOSTS.some((h) => host === h || host.endsWith('.' + h));
}

function requestOnce(u) {
  return new Promise((resolve) => {
    if (net.isIP(u.hostname) && isPrivateIp(u.hostname)) return resolve({ ok: false });
    const lib = u.protocol === 'https:' ? https : http;
    const req = lib.request(u, {
      method: 'GET',
      lookup: safeLookup,
      timeout: TIMEOUT_MS,
      headers: { 'User-Agent': 'BiznesCreatorBot/1.0 (+https://biznescreator.pl)', Accept: 'text/html,*/*' },
    }, (res) => {
      res.resume();
      res.destroy();
      resolve({ ok: true, status: res.statusCode, location: res.headers.location });
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', () => resolve({ ok: false }));
    req.end();
  });
}

// Returns 'ok' | 'dead' | 'social'
async function checkWebsite(raw) {
  let u = normalizeUrl(raw);
  if (!u) return 'dead';
  if (isSocialOnly(u)) return 'social';
  const key = u.href;
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < CACHE_MS) return hit.result;

  let result = 'dead';
  for (let i = 0; i <= MAX_REDIRECTS; i++) {
    const r = await requestOnce(u);
    if (!r.ok) break;
    if (r.status >= 300 && r.status < 400 && r.location) {
      const next = normalizeUrl(new URL(r.location, u).href);
      if (!next) break;
      if (isSocialOnly(next)) { result = 'social'; break; }
      u = next;
      continue;
    }
    // 401/403 often means a WAF blocking bots – the site exists.
    result = r.status < 400 || r.status === 401 || r.status === 403 || r.status === 429 ? 'ok' : 'dead';
    break;
  }
  cache.set(key, { result, at: Date.now() });
  if (cache.size > 20000) cache.delete(cache.keys().next().value);
  return result;
}

// Runs checks with limited concurrency.
async function checkMany(urls, concurrency = 8) {
  const out = new Array(urls.length);
  let idx = 0;
  async function worker() {
    while (idx < urls.length) {
      const i = idx++;
      out[i] = await checkWebsite(urls[i]);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, urls.length) }, worker));
  return out;
}

module.exports = { checkWebsite, checkMany, normalizeUrl, isSocialOnly, isPrivateIp };
