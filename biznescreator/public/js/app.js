'use strict';
/* global L, BC */
document.addEventListener('DOMContentLoaded', async () => {
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];

  // ---------- map ----------
  const saved = (() => { try { return JSON.parse(localStorage.getItem('bc_view')); } catch { return null; } })();
  const map = L.map('map', { zoomControl: true, attributionControl: true })
    .setView(saved?.c || [52.2297, 21.0122], saved?.z || 13);
  // Free OpenStreetMap tiles, darkened with a CSS filter (see style.css).
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }).addTo(map);
  map.on('moveend', () => {
    try { localStorage.setItem('bc_view', JSON.stringify({ c: map.getCenter(), z: map.getZoom() })); } catch { /* ignore */ }
  });
  const markers = L.layerGroup().addTo(map);

  // ---------- state ----------
  const state = {
    cats: new Set(['restauracje', 'fryzjerzy', 'kosmetyczki']),
    bounds: null,
    rect: null,
    results: [],
    source: 'osm',
    savedIds: new Map(), // placeId -> lead id
    activeId: null,
    markerById: new Map(),
  };
  try {
    const c = JSON.parse(localStorage.getItem('bc_cats'));
    if (Array.isArray(c) && c.length) state.cats = new Set(c);
  } catch { /* ignore */ }

  // ---------- categories + google availability ----------
  const meta = await BC.api('/api/categories');
  const catBox = $('[data-cats]');
  catBox.innerHTML = meta.categories.map((c) =>
    `<button class="chip" data-cat="${c.id}" aria-pressed="${state.cats.has(c.id)}">${BC.esc(c.label)}</button>`).join('');
  catBox.addEventListener('click', (e) => {
    const b = e.target.closest('[data-cat]');
    if (!b) return;
    const id = b.dataset.cat;
    state.cats.has(id) ? state.cats.delete(id) : state.cats.add(id);
    b.setAttribute('aria-pressed', state.cats.has(id));
    try { localStorage.setItem('bc_cats', JSON.stringify([...state.cats])); } catch { /* ignore */ }
    updateSearchBtn();
  });
  if (!meta.google) $('[data-source-row]').classList.add('hidden');
  else $('[data-google-usage]').textContent = `Google: wykorzystano ${meta.googleUsage.used}/${meta.googleUsage.cap} zapytań w tym miesiącu (darmowy limit).`;
  const srcSel = $('[data-source]');
  srcSel.addEventListener('change', () => { state.source = srcSel.value; toggleGoogleFilters(); });
  function toggleGoogleFilters() {
    $('[data-google-only]').classList.toggle('hidden', state.source !== 'google');
  }
  toggleGoogleFilters();

  // ---------- saved leads ----------
  try {
    const { leads } = await BC.api('/api/leads');
    for (const l of leads) state.savedIds.set(l.placeId, l.id);
  } catch { /* ignore */ }

  // ---------- rectangle drawing ----------
  let drawing = false; let start = null; let tmpRect = null;
  const container = map.getContainer();
  const hint = $('[data-hint]');

  function setDrawing(on) {
    drawing = on;
    document.body.classList.toggle('drawing', on);
    $$('[data-draw]').forEach((b) => b.setAttribute('aria-pressed', on));
    hint.classList.toggle('hidden', !on);
    if (on) { map.dragging.disable(); map.boxZoom.disable(); } else { map.dragging.enable(); map.boxZoom.enable(); }
  }
  $$('[data-draw]').forEach((b) => b.addEventListener('click', () => setDrawing(!drawing)));

  const toLatLng = (e) => {
    const r = container.getBoundingClientRect();
    return map.containerPointToLatLng([e.clientX - r.left, e.clientY - r.top]);
  };
  container.addEventListener('pointerdown', (e) => {
    if (!drawing || e.target.closest('.leaflet-control')) return;
    e.preventDefault();
    container.setPointerCapture(e.pointerId);
    start = toLatLng(e);
    if (tmpRect) tmpRect.remove();
    tmpRect = L.rectangle([start, start], { color: '#fff', weight: 1.5, fillOpacity: 0.06, dashArray: '6 4' }).addTo(map);
  });
  container.addEventListener('pointermove', (e) => {
    if (!drawing || !start || !tmpRect) return;
    const b = L.latLngBounds(start, toLatLng(e));
    tmpRect.setBounds(b);
    hint.textContent = `Obszar: ${areaKm2(b).toFixed(1)} km² (max 60 km²)`;
  });
  container.addEventListener('pointerup', (e) => {
    if (!drawing || !start) return;
    const b = L.latLngBounds(start, toLatLng(e));
    start = null;
    if (map.latLngToContainerPoint(b.getNorthWest()).distanceTo(map.latLngToContainerPoint(b.getSouthEast())) < 12) {
      tmpRect.remove(); tmpRect = null; return;
    }
    if (state.rect) state.rect.remove();
    state.rect = tmpRect; tmpRect = null;
    state.rect.setStyle({ dashArray: null });
    state.bounds = b;
    setDrawing(false);
    hint.textContent = 'Przytrzymaj i przeciągnij, żeby zaznaczyć prostokąt';
    const a = areaKm2(b);
    setStatus(a > 60 ? `Obszar ${a.toFixed(0)} km² jest za duży – zaznacz mniejszy.` : `Zaznaczono ${a.toFixed(1)} km². Kliknij „Szukaj”.`);
    updateSearchBtn();
  });

  function areaKm2(b) {
    const s = b.getSouth(); const n = b.getNorth();
    const lat = (n - s) * 111.32;
    const lon = (b.getEast() - b.getWest()) * 111.32 * Math.cos(((s + n) / 2) * Math.PI / 180);
    return Math.abs(lat * lon);
  }

  $('[data-locate]').addEventListener('click', () => {
    if (!navigator.geolocation) return BC.toast('Przeglądarka nie obsługuje lokalizacji', true);
    navigator.geolocation.getCurrentPosition(
      (p) => map.setView([p.coords.latitude, p.coords.longitude], 15),
      () => BC.toast('Nie udało się pobrać lokalizacji', true),
    );
  });

  // ---------- search ----------
  const searchBtn = $('[data-search]');
  function updateSearchBtn() {
    searchBtn.disabled = !state.bounds || !state.cats.size || areaKm2(state.bounds) > 60;
  }
  const setStatus = (t) => { $('[data-status]').textContent = t; };
  const progress = $('[data-progress]');
  const setProgress = (p) => {
    progress.classList.toggle('hidden', p === null);
    if (p !== null) progress.firstElementChild.style.width = `${Math.round(p * 100)}%`;
  };

  searchBtn.addEventListener('click', runSearch);

  async function runSearch() {
    if (searchBtn.disabled) return;
    searchBtn.disabled = true;
    setStatus('Szukam firm w zaznaczonym obszarze…');
    setProgress(0.05);
    markers.clearLayers();
    state.markerById.clear();
    $('[data-results]').innerHTML = '<div class="empty"><b>Szukam…</b>To może potrwać do 30 sekund.</div>';
    try {
      const b = state.bounds;
      const r = await BC.api('/api/search', {
        method: 'POST',
        body: {
          bounds: { south: b.getSouth(), west: b.getWest(), north: b.getNorth(), east: b.getEast() },
          categories: [...state.cats],
          source: state.source,
        },
      });
      state.results = r.results;
      if (r.capHit) BC.toast('Osiągnięto miesięczny limit Google – część wyników pominięta.', true);
      render();
      await checkSites();
    } catch (ex) {
      setStatus(ex.message);
      $('[data-results]').innerHTML = `<div class="empty"><b>Błąd</b>${BC.esc(ex.message)}</div>`;
    } finally {
      setProgress(null);
      updateSearchBtn();
    }
  }

  async function checkSites() {
    const pending = state.results.filter((r) => r.siteStatus === 'pending');
    if (!pending.length) { setStatus(summary()); return; }
    const BATCH = 20;
    for (let i = 0; i < pending.length; i += BATCH) {
      const batch = pending.slice(i, i + BATCH);
      setStatus(`Sprawdzam strony internetowe… ${Math.min(i + BATCH, pending.length)}/${pending.length}`);
      setProgress(0.1 + 0.9 * (i / pending.length));
      try {
        const { statuses } = await BC.api('/api/check-sites', { method: 'POST', body: { urls: batch.map((r) => r.website) } });
        batch.forEach((r, j) => { r.siteStatus = statuses[j] || 'dead'; });
      } catch (ex) {
        // If checking fails (e.g. rate limit) treat as "has a site" so we never show false leads.
        batch.forEach((r) => { r.siteStatus = 'ok'; });
        BC.toast(ex.message, true);
      }
      render();
    }
    setStatus(summary());
  }

  function summary() {
    const v = visible();
    const total = state.results.length;
    const withSite = state.results.filter((r) => r.siteStatus === 'ok').length;
    return `${v.length} firm bez działającej strony (z ${total} znalezionych, ${withSite} ma stronę).`;
  }

  // ---------- filters ----------
  const f = (k) => $(`[data-f="${k}"]`);
  $$('[data-f]').forEach((el) => el.addEventListener(el.type === 'search' ? 'input' : 'change', () => { render(); setStatus(summary()); }));

  function visible() {
    const q = f('q').value.trim().toLowerCase();
    const minRating = Number(f('minRating').value);
    const minReviews = Number(f('minReviews').value);
    return state.results.filter((r) => {
      if (r.siteStatus === 'ok') return false;
      if (r.siteStatus !== 'pending' && !f(r.siteStatus)?.checked) return false;
      if (f('phone').checked && !r.phone) return false;
      if (f('hideSaved').checked && state.savedIds.has(r.id)) return false;
      if (q && !r.name.toLowerCase().includes(q) && !(r.address || '').toLowerCase().includes(q)) return false;
      if (r.source === 'google') {
        if (minRating && (r.rating ?? 0) < minRating) return false;
        if (minReviews && (r.reviews ?? 0) < minReviews) return false;
      }
      return true;
    }).sort((a, b) => (b.reviews ?? 0) - (a.reviews ?? 0) || (b.phone ? 1 : 0) - (a.phone ? 1 : 0) || a.name.localeCompare(b.name, 'pl'));
  }

  // ---------- rendering ----------
  const list = $('[data-results]');

  function resultHtml(r) {
    const site = r.website && r.siteStatus !== 'none'
      ? `<a href="${BC.esc(BC.safeUrl(r.website))}" target="_blank" rel="noopener nofollow">${BC.esc(r.website.replace(/^https?:\/\//, '').slice(0, 40))}</a>` : '';
    const rating = r.rating != null ? `<span>★ ${r.rating.toFixed(1)} (${r.reviews ?? 0})</span>` : '';
    const isSaved = state.savedIds.has(r.id);
    return `<div class="result${state.activeId === r.id ? ' active' : ''}" data-id="${BC.esc(r.id)}">
      <h4><span>${BC.esc(r.name)}</span><span class="pill ${r.siteStatus}">${BC.SITE[r.siteStatus]}</span></h4>
      <div class="meta">
        <span>${BC.esc(BC.CATS[r.category] || r.category)}</span>${rating}
        ${r.address ? `<span>${BC.esc(r.address)}</span>` : ''}
        ${r.phone ? `<a href="tel:${BC.esc(r.phone.replace(/[^\d+]/g, ''))}">${BC.esc(r.phone)}</a>` : ''}
        ${site}
        ${r.openingHours ? `<span title="${BC.esc(r.openingHours)}">🕑 ${BC.esc(r.openingHours.slice(0, 40))}${r.openingHours.length > 40 ? '…' : ''}</span>` : ''}
      </div>
      <div class="acts">
        <button class="btn btn-xs ${isSaved ? 'btn-ghost' : ''}" data-save ${isSaved ? 'disabled' : ''}>${isSaved ? '✓ W leadach' : '+ Zapisz lead'}</button>
        <a class="btn btn-xs btn-ghost" href="${BC.esc(r.mapsUrl)}" target="_blank" rel="noopener">Google Maps ↗</a>
      </div>
    </div>`;
  }

  function markerIcon(r) {
    const cls = state.savedIds.has(r.id) ? 'saved' : r.siteStatus;
    return L.divIcon({ className: '', html: `<div class="marker-dot ${cls}${state.activeId === r.id ? ' active' : ''}"></div>`, iconSize: [14, 14], iconAnchor: [7, 7] });
  }

  function render() {
    const v = visible();
    $('[data-save-all]').classList.toggle('hidden', !v.some((r) => !state.savedIds.has(r.id) && r.siteStatus !== 'pending'));
    if (!v.length) {
      list.innerHTML = state.results.length
        ? '<div class="empty"><b>Brak firm spełniających filtry</b>Spróbuj innych branż lub większego obszaru.</div>'
        : '<div class="empty"><b>Nic nie znaleziono</b>W tym obszarze nie ma firm z wybranych branż w bazie map.</div>';
    } else {
      list.innerHTML = v.map(resultHtml).join('');
    }
    markers.clearLayers();
    state.markerById.clear();
    for (const r of v) {
      if (r.lat == null || r.lon == null) continue;
      const m = L.marker([r.lat, r.lon], { icon: markerIcon(r) })
        .bindPopup(`<b>${BC.esc(r.name)}</b><br>${BC.esc(BC.CATS[r.category] || '')}${r.phone ? '<br>' + BC.esc(r.phone) : ''}`)
        .on('click', () => focusResult(r.id, false));
      m.addTo(markers);
      state.markerById.set(r.id, m);
    }
  }

  function focusResult(id, pan) {
    state.activeId = id;
    $$('.result.active').forEach((el) => el.classList.remove('active'));
    const el = list.querySelector(`[data-id="${CSS.escape(id)}"]`);
    if (el) { el.classList.add('active'); if (!pan) el.scrollIntoView({ block: 'nearest', behavior: 'smooth' }); }
    const m = state.markerById.get(id);
    if (m && pan) { map.panTo(m.getLatLng()); m.openPopup(); }
  }

  list.addEventListener('click', async (e) => {
    const card = e.target.closest('.result');
    if (!card) return;
    const r = state.results.find((x) => x.id === card.dataset.id);
    if (!r) return;
    if (e.target.closest('[data-save]')) {
      e.stopPropagation();
      await saveLead(r);
      render();
      return;
    }
    if (e.target.closest('a')) return;
    focusResult(r.id, true);
  });

  async function saveLead(r) {
    try {
      const { lead } = await BC.api('/api/leads', { method: 'POST', body: r });
      state.savedIds.set(r.id, lead.id);
      BC.toast(`Zapisano: ${r.name}`);
    } catch (ex) { BC.toast(ex.message, true); }
  }

  $('[data-save-all]').addEventListener('click', async (e) => {
    const toSave = visible().filter((r) => !state.savedIds.has(r.id) && r.siteStatus !== 'pending');
    if (!toSave.length || !confirm(`Zapisać ${toSave.length} firm do leadów?`)) return;
    e.target.disabled = true;
    for (const r of toSave) {
      try {
        const { lead } = await BC.api('/api/leads', { method: 'POST', body: r });
        state.savedIds.set(r.id, lead.id);
      } catch (ex) { BC.toast(ex.message, true); break; }
    }
    e.target.disabled = false;
    BC.toast(`Zapisano ${toSave.length} leadów`);
    render();
  });

  setTimeout(() => map.invalidateSize(), 100);
});
