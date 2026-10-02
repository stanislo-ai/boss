'use strict';
// Business search inside a rectangle.
//  - "osm": OpenStreetMap via the Overpass API – free, no key, no limits on cost.
//  - "google": Google Places API (New) Text Search – optional, needs a key,
//              guarded by a hard monthly cap so it stays inside the free tier.
const config = require('./config');
const { db } = require('./db');

const CATEGORIES = {
  restauracje:    { label: 'Restauracje',      google: 'restauracja',      osm: ['["amenity"~"^(restaurant|fast_food)$"]'] },
  kawiarnie:      { label: 'Kawiarnie',        google: 'kawiarnia',        osm: ['["amenity"="cafe"]'] },
  barberzy:       { label: 'Barberzy',         google: 'barber',           osm: ['["shop"="hairdresser"]["hairdresser"~"^(barber|male)$"]', '["shop"="barber"]'] },
  fryzjerzy:      { label: 'Fryzjerzy',        google: 'fryzjer',          osm: ['["shop"="hairdresser"]'] },
  paznokcie:      { label: 'Salony paznokci',  google: 'salon paznokci',   osm: ['["shop"="beauty"]["beauty"~"nails"]', '["shop"="nails"]'] },
  kosmetyczki:    { label: 'Kosmetyczki',      google: 'salon kosmetyczny', osm: ['["shop"="beauty"]'] },
  mechanicy:      { label: 'Mechanicy',        google: 'mechanik samochodowy', osm: ['["shop"~"^(car_repair|tyres)$"]', '["craft"="car_repair"]'] },
  fizjoterapeuci: { label: 'Fizjoterapeuci',   google: 'fizjoterapeuta',   osm: ['["healthcare"="physiotherapist"]', '["amenity"="physiotherapist"]'] },
  dentysci:       { label: 'Dentyści',         google: 'dentysta',         osm: ['["amenity"="dentist"]', '["healthcare"="dentist"]'] },
  sklepy:         { label: 'Sklepy',           google: 'sklep',            osm: ['["shop"~"^(convenience|bakery|butcher|florist|clothes|shoes|gift|greengrocer|confectionery|deli|jewelry|optician|pet|toys|books|furniture|hardware|alcohol|beverages|cosmetics|bicycle|sports)$"]'] },
};
// Order matters: more specific categories first.
const CLASSIFY_ORDER = ['barberzy', 'paznokcie', 'fryzjerzy', 'kosmetyczki', 'restauracje', 'kawiarnie',
  'mechanicy', 'fizjoterapeuci', 'dentysci', 'sklepy'];

const MAX_AREA_KM2 = 60;
const OVERPASS_MIRRORS = [...new Set([config.overpassUrl,
  'https://overpass-api.de/api/interpreter',
  'https://overpass.private.coffee/api/interpreter'])];

function validateBounds(b) {
  const n = ['south', 'west', 'north', 'east'].map((k) => Number(b && b[k]));
  if (n.some((x) => !Number.isFinite(x))) return { error: 'Nieprawidłowy obszar.' };
  const [s, w, no, e] = n;
  if (s < -90 || no > 90 || w < -180 || e > 180 || s >= no || w >= e) return { error: 'Nieprawidłowy obszar.' };
  const latKm = (no - s) * 111.32;
  const lonKm = (e - w) * 111.32 * Math.cos(((s + no) / 2) * Math.PI / 180);
  const area = latKm * lonKm;
  if (area > MAX_AREA_KM2) {
    return { error: `Obszar jest za duży (${area.toFixed(0)} km²). Maksymalnie ${MAX_AREA_KM2} km² – zaznacz mniejszy fragment.` };
  }
  return { bounds: { south: s, west: w, north: no, east: e }, area };
}

function mapsSearchUrl(name, address, lat, lon) {
  const q = encodeURIComponent([name, address].filter(Boolean).join(', ') || `${lat},${lon}`);
  return `https://www.google.com/maps/search/?api=1&query=${q}`;
}

// ---------- OpenStreetMap ----------
function matchesSelector(tags, selector) {
  const re = /\["([^"]+)"(=|~)"([^"]+)"\]/g;
  let m;
  while ((m = re.exec(selector))) {
    const v = tags[m[1]];
    if (v === undefined) return false;
    if (m[2] === '=' && v !== m[3]) return false;
    if (m[2] === '~' && !new RegExp(m[3]).test(v)) return false;
  }
  return true;
}

function classifyOsm(tags, wanted) {
  for (const key of CLASSIFY_ORDER) {
    if (CATEGORIES[key].osm.some((sel) => matchesSelector(tags, sel))) {
      return wanted.includes(key) ? key : null;
    }
  }
  return null;
}

function osmAddress(t) {
  const street = [t['addr:street'] || t['addr:place'], t['addr:housenumber']].filter(Boolean).join(' ');
  const city = [t['addr:postcode'], t['addr:city']].filter(Boolean).join(' ');
  return [street, city].filter(Boolean).join(', ');
}

async function fetchOverpass(query) {
  let lastErr;
  for (const url of OVERPASS_MIRRORS) {
    try {
      const ctrl = new AbortController();
      const t = setTimeout(() => ctrl.abort(), 40000);
      const res = await fetch(url, {
        method: 'POST',
        body: new URLSearchParams({ data: query }),
        headers: { 'User-Agent': 'BiznesCreator/1.0 (+https://biznescreator.pl)' },
        signal: ctrl.signal,
      });
      clearTimeout(t);
      if (!res.ok) { lastErr = new Error('Overpass HTTP ' + res.status); continue; }
      return await res.json();
    } catch (e) {
      lastErr = e;
    }
  }
  throw lastErr || new Error('Overpass niedostępny');
}

async function searchOsm(bounds, cats) {
  const bbox = `${bounds.south},${bounds.west},${bounds.north},${bounds.east}`;
  const parts = [];
  for (const c of cats) for (const sel of CATEGORIES[c].osm) parts.push(`nwr${sel}["name"](${bbox});`);
  const query = `[out:json][timeout:30];(${parts.join('')});out center tags 3000;`;
  const json = await fetchOverpass(query);

  const results = [];
  for (const el of json.elements || []) {
    const t = el.tags || {};
    const category = classifyOsm(t, cats);
    if (!category) continue;
    const lat = el.lat ?? el.center?.lat;
    const lon = el.lon ?? el.center?.lon;
    const address = osmAddress(t);
    results.push({
      id: `osm:${el.type}/${el.id}`,
      source: 'osm',
      name: t.name,
      category,
      address,
      phone: t.phone || t['contact:phone'] || t['contact:mobile'] || '',
      website: t.website || t['contact:website'] || t.url || '',
      social: t['contact:facebook'] || t['contact:instagram'] || t.facebook || t.instagram || '',
      openingHours: t.opening_hours || '',
      rating: null,
      reviews: null,
      lat, lon,
      mapsUrl: mapsSearchUrl(t.name, address, lat, lon),
      sourceUrl: `https://www.openstreetmap.org/${el.type}/${el.id}`,
    });
  }
  return results;
}

// ---------- Google Places (optional) ----------
function currentMonth() {
  return new Date().toISOString().slice(0, 7);
}

function googleUsage() {
  const row = db.prepare('SELECT google_calls FROM api_usage WHERE month = ?').get(currentMonth());
  return { used: row ? row.google_calls : 0, cap: config.googleMonthlyCap };
}

// Atomically reserve one call; returns false when the cap is reached.
const reserveGoogleCall = db.transaction(() => {
  const m = currentMonth();
  db.prepare('INSERT OR IGNORE INTO api_usage (month, google_calls) VALUES (?, 0)').run(m);
  const r = db.prepare('UPDATE api_usage SET google_calls = google_calls + 1 WHERE month = ? AND google_calls < ?')
    .run(m, config.googleMonthlyCap);
  return r.changes === 1;
});

const GOOGLE_FIELDS = [
  'places.id', 'places.displayName', 'places.formattedAddress', 'places.nationalPhoneNumber',
  'places.websiteUri', 'places.rating', 'places.userRatingCount', 'places.location',
  'places.googleMapsUri', 'places.regularOpeningHours.weekdayDescriptions', 'places.businessStatus',
  'nextPageToken',
].join(',');

async function searchGoogle(bounds, cats) {
  if (!config.googleApiKey) throw new Error('Google nie jest skonfigurowane.');
  const results = [];
  let capHit = false;
  for (const cat of cats) {
    let pageToken;
    for (let page = 0; page < 3; page++) {
      if (!reserveGoogleCall()) { capHit = true; break; }
      const body = {
        textQuery: CATEGORIES[cat].google,
        languageCode: 'pl',
        regionCode: 'PL',
        pageSize: 20,
        locationRestriction: {
          rectangle: {
            low: { latitude: bounds.south, longitude: bounds.west },
            high: { latitude: bounds.north, longitude: bounds.east },
          },
        },
      };
      if (pageToken) body.pageToken = pageToken;
      const res = await fetch('https://places.googleapis.com/v1/places:searchText', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Goog-Api-Key': config.googleApiKey,
          'X-Goog-FieldMask': GOOGLE_FIELDS,
        },
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error('Google Places HTTP ' + res.status);
      const json = await res.json();
      for (const p of json.places || []) {
        if (p.businessStatus && p.businessStatus !== 'OPERATIONAL') continue;
        results.push({
          id: `google:${p.id}`,
          source: 'google',
          name: p.displayName?.text || '',
          category: cat,
          address: p.formattedAddress || '',
          phone: p.nationalPhoneNumber || '',
          website: p.websiteUri || '',
          social: '',
          openingHours: (p.regularOpeningHours?.weekdayDescriptions || []).join('; '),
          rating: p.rating ?? null,
          reviews: p.userRatingCount ?? null,
          lat: p.location?.latitude,
          lon: p.location?.longitude,
          mapsUrl: p.googleMapsUri || mapsSearchUrl(p.displayName?.text, p.formattedAddress),
          sourceUrl: p.googleMapsUri || '',
        });
      }
      pageToken = json.nextPageToken;
      if (!pageToken) break;
    }
    if (capHit) break;
  }
  return { results, capHit };
}

// ---------- public entry ----------
const cache = new Map();
const CACHE_MS = 15 * 60 * 1000;

async function search({ bounds, categories, source }) {
  const cats = (Array.isArray(categories) ? categories : []).filter((c) => CATEGORIES[c]);
  if (!cats.length) return { error: 'Wybierz przynajmniej jedną branżę.' };
  const v = validateBounds(bounds);
  if (v.error) return v;
  const src = source === 'google' && config.googleApiKey ? 'google' : 'osm';

  const key = JSON.stringify([src, v.bounds, cats.sort()]);
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < CACHE_MS) return hit.value;

  let results; let capHit = false;
  if (src === 'google') ({ results, capHit } = await searchGoogle(v.bounds, cats));
  else results = await searchOsm(v.bounds, cats);

  // De-duplicate and mark website state; sites that need an HTTP check are "pending".
  const seen = new Set();
  const out = [];
  for (const r of results) {
    if (seen.has(r.id)) continue;
    seen.add(r.id);
    r.siteStatus = r.website ? 'pending' : 'none';
    out.push(r);
  }
  const value = { source: src, area: v.area, capHit, results: out };
  cache.set(key, { at: Date.now(), value });
  if (cache.size > 300) cache.delete(cache.keys().next().value);
  return value;
}

function categoryList() {
  return Object.entries(CATEGORIES).map(([id, c]) => ({ id, label: c.label }));
}

module.exports = { search, categoryList, googleUsage, validateBounds, classifyOsm, CATEGORIES };
