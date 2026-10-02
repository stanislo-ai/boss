'use strict';
document.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('[data-form]');
  const err = document.querySelector('[data-error]');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    BC.showError(err, '');
    const btn = form.querySelector('button');
    btn.disabled = true;
    try {
      await BC.api('/api/login', { method: 'POST', body: BC.formData(form) });
      const next = new URLSearchParams(location.search).get('next') || '';
      // Only same-site relative paths, never "//evil.com".
      location.href = /^\/(?!\/)/.test(next) ? next : '/app';
    } catch (ex) {
      BC.showError(err, ex.message);
      btn.disabled = false;
    }
  });
});
