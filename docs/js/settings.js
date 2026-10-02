// ===================== Appearance settings =====================
const APPEARANCE_KEY = 'lecture_app_appearance_v1';
function loadAppearance() {
  let prefs = { theme: 'gray', scale: 100 };
  try { prefs = { ...prefs, ...JSON.parse(localStorage.getItem(APPEARANCE_KEY) || '{}') }; } catch (_) {}
  if (!['gray','light','paper','coffee','uunit','uunit-dark'].includes(prefs.theme)) prefs.theme = 'gray';
  prefs.scale = Math.min(130, Math.max(80, Number(prefs.scale) || 100));
  applyAppearance(prefs, false);
  return prefs;
}
function applyAppearance(prefs, save = true) {
  document.documentElement.dataset.theme = prefs.theme;
  document.documentElement.style.setProperty('--app-scale', String(prefs.scale / 100));
  document.documentElement.style.setProperty('--app-unscaled-height', `${window.innerHeight / (prefs.scale / 100)}px`);
  const themeCards = document.querySelectorAll('[data-theme-choice]');
  const scaleRange = document.getElementById('scaleRange');
  const scaleValue = document.getElementById('scaleValue');
  themeCards.forEach(card => {
    const active = card.dataset.themeChoice === prefs.theme;
    card.classList.toggle('active', active);
    card.setAttribute('aria-pressed', active ? 'true' : 'false');
  });
  if (scaleRange) scaleRange.value = prefs.scale;
  if (scaleValue) scaleValue.textContent = prefs.scale + '%';
  if (save) localStorage.setItem(APPEARANCE_KEY, JSON.stringify(prefs));
}
let appearance = loadAppearance();
window.addEventListener('resize', () => applyAppearance(appearance, false));
function openSettingsModal() { document.getElementById('settingsModal').classList.remove('hidden'); }
function closeSettingsModal() { document.getElementById('settingsModal').classList.add('hidden'); }
