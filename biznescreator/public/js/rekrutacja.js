'use strict';
document.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('[data-form]');
  const err = document.querySelector('[data-error]');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    BC.showError(err, '');
    const missing = [...form.querySelectorAll('[required]')].find((el) => (el.type === 'checkbox' ? !el.checked : !el.value.trim()));
    if (missing) {
      BC.showError(err, 'Uzupełnij wszystkie wymagane pola.');
      missing.focus();
      return;
    }
    const btn = form.querySelector('button[type=submit]');
    btn.disabled = true;
    try {
      await BC.api('/api/rekrutacja', { method: 'POST', body: BC.formData(form) });
      document.querySelector('[data-panel]').classList.add('hidden');
      document.querySelector('[data-done]').classList.remove('hidden');
      window.scrollTo(0, 0);
    } catch (ex) {
      BC.showError(err, ex.message);
      btn.disabled = false;
      window.scrollTo(0, 0);
    }
  });
});
