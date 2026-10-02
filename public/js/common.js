'use strict';
// Shared helpers for every page.
window.BC = (() => {
  const csrf = () => document.querySelector('meta[name="csrf-token"]')?.content || '';

  async function api(url, { method = 'GET', body } = {}) {
    const res = await fetch(url, {
      method,
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf() },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    let data = {};
    try { data = await res.json(); } catch { /* empty body */ }
    if (res.status === 401 && !url.startsWith('/api/login')) {
      location.href = '/login?next=' + encodeURIComponent(location.pathname);
    }
    if (!res.ok) throw new Error(data.error || 'Coś poszło nie tak. Spróbuj ponownie.');
    return data;
  }

  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  // Only allow http(s) links in href attributes.
  const safeUrl = (u) => {
    if (!u) return '';
    const s = /^https?:\/\//i.test(u) ? u : 'https://' + u;
    try { const p = new URL(s); return ['http:', 'https:'].includes(p.protocol) ? p.href : ''; } catch { return ''; }
  };

  let toastEl; let toastT;
  function toast(msg, err = false) {
    if (!toastEl) { toastEl = document.createElement('div'); toastEl.className = 'toast'; document.body.appendChild(toastEl); }
    toastEl.textContent = msg;
    toastEl.classList.toggle('err', err);
    toastEl.classList.add('show');
    clearTimeout(toastT);
    toastT = setTimeout(() => toastEl.classList.remove('show'), 2600);
  }

  const CATS = {
    restauracje: 'Restauracja', kawiarnie: 'Kawiarnia', barberzy: 'Barber', fryzjerzy: 'Fryzjer',
    paznokcie: 'Salon paznokci', kosmetyczki: 'Kosmetyczka', mechanicy: 'Mechanik',
    fizjoterapeuci: 'Fizjoterapeuta', dentysci: 'Dentysta', sklepy: 'Sklep',
  };
  const SITE = { none: 'Brak strony', social: 'Tylko social', dead: 'Strona nie działa', pending: 'Sprawdzam…', ok: 'Ma stronę' };
  const STATUSES = { new: 'Nowy', called: 'Zadzwoniłem', interested: 'Zainteresowany', client: 'Klient', rejected: 'Odmowa' };

  function formData(form) {
    const o = {};
    for (const el of form.elements) {
      if (!el.name) continue;
      o[el.name] = el.type === 'checkbox' ? el.checked : el.value;
    }
    return o;
  }

  function showError(el, msg) {
    if (!el) return;
    el.textContent = msg || '';
    el.classList.toggle('hidden', !msg);
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelector('[data-burger]')?.addEventListener('click', () =>
      document.querySelector('[data-nav]').classList.toggle('open'));
    document.querySelectorAll('[data-logout]').forEach((b) => b.addEventListener('click', async () => {
      await api('/api/logout', { method: 'POST' }).catch(() => {});
      location.href = '/';
    }));
    document.querySelectorAll('.nav-link').forEach((a) => {
      if (a.getAttribute('href') === location.pathname) a.classList.add('active');
    });
  });

  return { api, esc, safeUrl, toast, CATS, SITE, STATUSES, formData, showError };
})();
