'use strict';
/* global BC */
document.addEventListener('DOMContentLoaded', async () => {
  const $ = (s) => document.querySelector(s);
  const listEl = $('[data-list]');
  const state = { leads: [], tab: 'all' };

  try {
    state.leads = (await BC.api('/api/leads')).leads;
  } catch (ex) { BC.toast(ex.message, true); }

  const catSel = $('[data-cat]');
  catSel.insertAdjacentHTML('beforeend', Object.entries(BC.CATS).map(([k, v]) => `<option value="${k}">${BC.esc(v)}</option>`).join(''));
  [$('[data-q]'), catSel, $('[data-sort]')].forEach((el) => el.addEventListener(el.type === 'search' ? 'input' : 'change', render));

  function renderTabs() {
    const count = (s) => state.leads.filter((l) => s === 'all' || l.status === s).length;
    const tabs = [['all', 'Wszystkie'], ...Object.entries(BC.STATUSES)];
    $('[data-tabs]').innerHTML = tabs.map(([k, v]) =>
      `<button class="tab" aria-selected="${state.tab === k}" data-tab="${k}">${BC.esc(v)}<span class="n">${count(k)}</span></button>`).join('');
  }
  $('[data-tabs]').addEventListener('click', (e) => {
    const t = e.target.closest('[data-tab]');
    if (!t) return;
    state.tab = t.dataset.tab;
    render();
  });

  function filtered() {
    const q = $('[data-q]').value.trim().toLowerCase();
    const cat = catSel.value;
    const sort = $('[data-sort]').value;
    return state.leads
      .filter((l) => state.tab === 'all' || l.status === state.tab)
      .filter((l) => !cat || l.category === cat)
      .filter((l) => !q || [l.name, l.address, l.notes, l.phone].some((x) => (x || '').toLowerCase().includes(q)))
      .sort((a, b) => sort === 'name' ? a.name.localeCompare(b.name, 'pl') : sort === 'created' ? b.createdAt - a.createdAt : b.updatedAt - a.updatedAt);
  }

  function leadHtml(l) {
    const opts = Object.entries(BC.STATUSES).map(([k, v]) => `<option value="${k}" ${l.status === k ? 'selected' : ''}>${BC.esc(v)}</option>`).join('');
    return `<div class="lead st-${l.status}" data-id="${l.id}">
      <div>
        <h3>${BC.esc(l.name)}</h3>
        <div class="meta">
          <span>${BC.esc(BC.CATS[l.category] || l.category)}</span>
          ${l.siteStatus ? `<span class="pill ${BC.esc(l.siteStatus)}">${BC.esc(BC.SITE[l.siteStatus] || '')}</span>` : ''}
          ${l.rating != null ? `<span>★ ${Number(l.rating).toFixed(1)} (${l.reviews ?? 0})</span>` : ''}
          ${l.address ? `<span>${BC.esc(l.address)}</span>` : ''}
          ${l.phone ? `<a href="tel:${BC.esc(l.phone.replace(/[^\d+]/g, ''))}">${BC.esc(l.phone)}</a>` : '<span>brak telefonu</span>'}
          ${l.website ? `<a href="${BC.esc(BC.safeUrl(l.website))}" target="_blank" rel="noopener nofollow">${BC.esc(l.website.replace(/^https?:\/\//, '').slice(0, 40))}</a>` : ''}
          ${l.mapsUrl ? `<a href="${BC.esc(l.mapsUrl)}" target="_blank" rel="noopener">Google Maps ↗</a>` : ''}
        </div>
        <textarea placeholder="Notatki: z kim rozmawiałeś, kiedy oddzwonić, ile chcą zapłacić…" data-notes maxlength="5000">${BC.esc(l.notes)}</textarea>
      </div>
      <div class="side">
        <label class="label" for="st-${l.id}">Status</label>
        <select id="st-${l.id}" data-status>${opts}</select>
        <span class="saved-note" data-note>Zmieniono ${new Date(l.updatedAt).toLocaleString('pl-PL', { dateStyle: 'short', timeStyle: 'short' })}</span>
        <button class="btn btn-xs btn-danger" data-del>Usuń</button>
      </div>
    </div>`;
  }

  function render() {
    renderTabs();
    const items = filtered();
    listEl.innerHTML = items.length ? items.map(leadHtml).join('')
      : `<div class="empty"><b>${state.leads.length ? 'Brak leadów w tym widoku' : 'Nie masz jeszcze leadów'}</b>${state.leads.length ? 'Zmień filtr.' : 'Przejdź do mapy i zapisz firmy bez strony.'}</div>`;
  }

  async function patch(id, body, card) {
    try {
      const { lead } = await BC.api(`/api/leads/${id}`, { method: 'PATCH', body });
      const i = state.leads.findIndex((l) => l.id === id);
      state.leads[i] = lead;
      card.querySelector('[data-note]').textContent = 'Zapisano ✓';
      card.className = `lead st-${lead.status}`;
      renderTabs();
    } catch (ex) { BC.toast(ex.message, true); }
  }

  listEl.addEventListener('change', (e) => {
    const card = e.target.closest('.lead');
    if (card && e.target.matches('[data-status]')) patch(Number(card.dataset.id), { status: e.target.value }, card);
  });

  const timers = new Map();
  listEl.addEventListener('input', (e) => {
    const card = e.target.closest('.lead');
    if (!card || !e.target.matches('[data-notes]')) return;
    const id = Number(card.dataset.id);
    card.querySelector('[data-note]').textContent = 'Zapisuję…';
    clearTimeout(timers.get(id));
    timers.set(id, setTimeout(() => patch(id, { notes: e.target.value }, card), 700));
  });

  listEl.addEventListener('click', async (e) => {
    const card = e.target.closest('.lead');
    if (!card || !e.target.matches('[data-del]')) return;
    if (!confirm('Usunąć tego leada?')) return;
    const id = Number(card.dataset.id);
    try {
      await BC.api(`/api/leads/${id}`, { method: 'DELETE' });
      state.leads = state.leads.filter((l) => l.id !== id);
      render();
    } catch (ex) { BC.toast(ex.message, true); }
  });

  render();
});
