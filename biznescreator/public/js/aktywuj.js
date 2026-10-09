'use strict';
document.addEventListener('DOMContentLoaded', async () => {
  const token = new URLSearchParams(location.search).get('token') || '';
  const form = document.querySelector('[data-form]');
  const err = document.querySelector('[data-error]');
  const lead = document.querySelector('[data-lead]');
  // Remove the token from the address bar / history right away.
  history.replaceState(null, '', '/aktywuj');
  try {
    const info = await BC.api('/api/aktywuj?token=' + encodeURIComponent(token));
    document.querySelector('[data-title]').textContent = info.purpose === 'reset' ? 'Nowe hasło' : 'Witaj w BiznesCreator!';
    lead.textContent = `${info.name} (${info.email}) – ustaw hasło do swojego konta.`;
    form.classList.remove('hidden');
  } catch (ex) {
    lead.textContent = 'Poproś administratora o nowy link.';
    BC.showError(err, ex.message);
    return;
  }
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const { password, password2 } = BC.formData(form);
    if (password !== password2) return BC.showError(err, 'Hasła nie są takie same.');
    BC.showError(err, '');
    try {
      await BC.api('/api/aktywuj', { method: 'POST', body: { token, password } });
      location.href = '/app';
    } catch (ex) {
      BC.showError(err, ex.message);
    }
  });
});
