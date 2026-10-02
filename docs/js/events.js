// ===================== Events =====================
document.querySelectorAll('.tab').forEach(tab => {
  tab.addEventListener('click', () => {
    currentView = tab.dataset.view;
    render();
  });
});

document.getElementById('addHwBtn').addEventListener('click', () => openNewHomework());
document.getElementById('closeModal').addEventListener('click', closeModal);
function shiftWeek(deltaWeeks) {
  currentWeekStart.setDate(currentWeekStart.getDate() + deltaWeeks * 7);
  if (currentView !== 'days') currentView = 'days';
  render();
}
function goToToday() {
  currentWeekStart = startOfWeek(new Date());
  if (currentView !== 'days') currentView = 'days';
  render();
}
document.getElementById('prevWeek').addEventListener('click', () => shiftWeek(-1));
document.getElementById('nextWeek').addEventListener('click', () => shiftWeek(1));
document.getElementById('todayBtn').addEventListener('click', goToToday);
document.getElementById('addSubjectBtn').addEventListener('click', addSubject);
document.getElementById('newSubjectName').addEventListener('keydown', e => {
  if (e.key === 'Enter') addSubject();
});

// Arrow keys: ← previous week, → next week (ignore when typing in inputs)
document.addEventListener('keydown', (e) => {
  const tag = (e.target && e.target.tagName) ? e.target.tagName.toLowerCase() : '';
  if (tag === 'input' || tag === 'textarea' || tag === 'select' || e.target.isContentEditable) return;
  if (e.key === 'ArrowLeft') {
    e.preventDefault();
    shiftWeek(-1);
  } else if (e.key === 'ArrowRight') {
    e.preventDefault();
    shiftWeek(1);
  }
});

document.getElementById('syncBtn').addEventListener('click', () => {
  alert('Веб-версия не подключается напрямую к устройствам. Для переноса откройте Настройки → Экспорт в JSON, а затем импортируйте файл на другом устройстве.');
});
document.getElementById('settingsBtn').addEventListener('click', openSettingsModal);
document.getElementById('closeSettings').addEventListener('click', closeSettingsModal);
document.querySelectorAll('[data-theme-choice]').forEach(card => card.addEventListener('click', () => { appearance.theme = card.dataset.themeChoice; applyAppearance(appearance); }));
document.getElementById('scaleRange').addEventListener('input', e => { appearance.scale = Number(e.target.value); applyAppearance(appearance); });
document.getElementById('exportDataBtn').addEventListener('click', exportDataToJson);
document.getElementById('importDataBtn').addEventListener('click', () => {
  document.getElementById('importDataFile').value = '';
  document.getElementById('importDataFile').click();
});
document.getElementById('importDataFile').addEventListener('change', (e) => {
  const file = e.target.files && e.target.files[0];
  if (file) importDataFromJson(file);
});
