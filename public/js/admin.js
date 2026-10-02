'use strict';
/* global BC */
document.addEventListener('DOMContentLoaded', () => {
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  let apps = [];
  let appFilter = 'pending';

  const LABELS = { age: 'Wiek', city: 'Miasto', why: 'Dlaczego', experience: 'Doświadczenie', hours: 'Czas / tydz.', goal: 'Cel', source: 'Źródło', social: 'Portfolio / social' };

  $$('[data-tab]').forEach((t) => t.addEventListener('click', () => {
    $$('[data-tab]').forEach((x) => x.setAttribute('aria-selected', x === t));
    $$('[data-pane]').forEach((p) => p.classList.toggle('hidden', p.dataset.pane !== t.dataset.tab));
    if (t.dataset.tab === 'users') loadUsers();
  }));
  $('[data-app-filter]').addEventListener('click', (e) => {
    const b = e.target.closest('[data-st]');
    if (!b) return;
    appFilter = b.dataset.st;
    $$('[data-st]').forEach((x) => x.setAttribute('aria-selected', x === b));
    renderApps();
  });

  function linkBox(link, email, name) {
    const subject = encodeURIComponent('BiznesCreator – dostęp do konta');
    const body = encodeURIComponent(`Cześć ${name}!\n\nOto Twój link do ustawienia hasła w BiznesCreator (jednorazowy, ważny ograniczony czas):\n\n${link}\n\nPowodzenia!\nZespół BiznesCreator`);
    return `<div class="linkbox"><input readonly value="${BC.esc(link)}" data-link>
      <button class="btn btn-xs" data-copy>Kopiuj</button>
      <a class="btn btn-xs btn-ghost" href="mailto:${BC.esc(email)}?subject=${subject}&body=${body}">Wyślij e-mail</a></div>
      <p class="muted small">Link jest jednorazowy i pokazuje się tylko teraz. Wyślij go na ${BC.esc(email)}.</p>`;
  }

  document.addEventListener('click', async (e) => {
    if (e.target.matches('[data-copy]')) {
      const input = e.target.parentElement.querySelector('[data-link]');
      await navigator.clipboard.writeText(input.value).catch(() => { input.select(); document.execCommand('copy'); });
      BC.toast('Skopiowano link');
    }
  });

  async function loadApps() {
    try { apps = (await BC.api('/api/admin/applications')).applications; } catch (ex) { BC.toast(ex.message, true); }
    renderApps();
  }

  function renderApps() {
    const items = apps.filter((a) => a.status === appFilter);
    $('[data-apps]').innerHTML = items.length ? items.map((a) => `
      <div class="lead" data-id="${a.id}">
        <div>
          <h3>${BC.esc(a.name)}</h3>
          <div class="meta"><a href="mailto:${BC.esc(a.email)}">${BC.esc(a.email)}</a><span>${new Date(a.created_at).toLocaleString('pl-PL')}</span></div>
          <dl class="answers">${Object.entries(LABELS).map(([k, v]) => `<dt>${v}</dt><dd>${BC.esc(a.answers[k] ?? '–') || '–'}</dd>`).join('')}</dl>
          <div data-result></div>
        </div>
        <div class="side">
          ${a.status === 'pending' ? '<button class="btn btn-sm" data-approve>Przyjmij</button><button class="btn btn-sm btn-danger" data-reject>Odrzuć</button>' : `<span class="muted small">${a.status === 'approved' ? 'Przyjęte' : 'Odrzucone'} ${a.decided_at ? new Date(a.decided_at).toLocaleDateString('pl-PL') : ''}</span>`}
        </div>
      </div>`).join('') : '<div class="empty"><b>Brak zgłoszeń</b>Nic tu nie ma.</div>';
  }

  $('[data-apps]').addEventListener('click', async (e) => {
    const card = e.target.closest('.lead');
    if (!card) return;
    const id = Number(card.dataset.id);
    const decision = e.target.matches('[data-approve]') ? 'approve' : e.target.matches('[data-reject]') ? 'reject' : null;
    if (!decision) return;
    if (decision === 'reject' && !confirm('Odrzucić zgłoszenie?')) return;
    try {
      const r = await BC.api(`/api/admin/applications/${id}/${decision}`, { method: 'POST' });
      const a = apps.find((x) => x.id === id);
      a.status = decision === 'approve' ? 'approved' : 'rejected';
      a.decided_at = Date.now();
      if (r.link) {
        card.querySelector('[data-result]').innerHTML = linkBox(r.link, r.email, r.name);
        card.querySelector('.side').innerHTML = '<span class="muted small">Przyjęte ✓</span>';
      } else {
        renderApps();
      }
    } catch (ex) { BC.toast(ex.message, true); }
  });

  async function loadUsers() {
    try {
      const { users } = await BC.api('/api/admin/users');
      $('[data-users]').innerHTML = users.map((u) => `
        <tr data-id="${u.id}">
          <td><b>${BC.esc(u.name)}</b><br><span class="muted small">${BC.esc(u.email)}</span><div data-result></div></td>
          <td>${u.role === 'admin' ? 'Admin' : 'Użytkownik'}</td>
          <td>${!u.active ? 'Zablokowany' : u.activated ? 'Aktywny' : 'Czeka na aktywację'}</td>
          <td>${u.leads}</td>
          <td><button class="btn btn-xs btn-ghost" data-reset>Link do hasła</button>
              ${u.role !== 'admin' ? `<button class="btn btn-xs btn-danger" data-toggle>${u.active ? 'Zablokuj' : 'Odblokuj'}</button>` : ''}</td>
        </tr>`).join('');
    } catch (ex) { BC.toast(ex.message, true); }
  }

  $('[data-users]').addEventListener('click', async (e) => {
    const row = e.target.closest('tr');
    if (!row) return;
    const id = row.dataset.id;
    try {
      if (e.target.matches('[data-reset]')) {
        const r = await BC.api(`/api/admin/users/${id}/reset`, { method: 'POST' });
        row.querySelector('[data-result]').innerHTML = linkBox(r.link, r.email, r.name);
      } else if (e.target.matches('[data-toggle]')) {
        await BC.api(`/api/admin/users/${id}/toggle`, { method: 'POST' });
        loadUsers();
      }
    } catch (ex) { BC.toast(ex.message, true); }
  });

  loadApps();
});
