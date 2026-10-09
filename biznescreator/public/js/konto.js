'use strict';
document.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('[data-form]');
  const msg = document.querySelector('[data-msg]');
  const show = (text, ok) => { msg.textContent = text; msg.className = 'alert ' + (ok ? 'alert-ok' : 'alert-error'); };
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await BC.api('/api/account/password', { method: 'POST', body: BC.formData(form) });
      form.reset();
      show('Hasło zmienione. Inne urządzenia zostały wylogowane.', true);
      // Session cookie was rotated – reload to pick up the new CSRF token.
      setTimeout(() => location.reload(), 1200);
    } catch (ex) { show(ex.message, false); }
  });
  document.querySelector('[data-logout-all]').addEventListener('click', async () => {
    await BC.api('/api/account/logout-all', { method: 'POST' }).catch(() => {});
    location.href = '/login';
  });
});
