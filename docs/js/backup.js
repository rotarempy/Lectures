// ===================== Export / Import JSON =====================
async function exportDataToJson() {
  try {
    const payload = {
      subjects: db.subjects || [],
      lectures: db.lectures || [],
      homeworks: db.homeworks || [],
      version: 5,
      exportedAt: new Date().toISOString()
    };
    const json = JSON.stringify(payload, null, 2);
    const stamp = toISODate(new Date());
    const defaultName = `lectures-backup-${stamp}.json`;

    // The browser downloads the backup as a regular JSON file.
    const blob = new Blob([json], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = defaultName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);

    showToast('Экспорт сохранён');
  } catch (e) {
    console.error(e);
    alert('Не удалось экспортировать данные: ' + (e.message || e));
  }
}

async function importDataFromJson(file) {
  if (!file) return;
  try {
    const text = await file.text();
    let incoming;
    try {
      incoming = JSON.parse(text);
    } catch (_) {
      throw new Error('Файл не является корректным JSON');
    }
    if (!incoming || !Array.isArray(incoming.lectures)) {
      throw new Error('В файле нет поля lectures — это не резервная копия приложения');
    }
    const nL = (incoming.lectures || []).length;
    const nH = (incoming.homeworks || []).length;
    const nS = (incoming.subjects || []).length;
    if (!confirm(
      `Импорт заменит ВСЕ текущие данные.\n\n` +
      `В файле: ${nS} предметов, ${nL} лекций, ${nH} ДЗ.\n\n` +
      `Продолжить?`
    )) return;

    // Backup current DB before overwrite
    try {
      await idbPut('data', 'pre_import_backup_' + Date.now(), db);
    } catch (_) {}

    db = normalizeDB(incoming);
    if (!db.homeworks) db.homeworks = [];
    await saveDB();
    render();
    closeSettingsModal();
    showToast(`Импортировано: ${nL} лекций, ${nH} ДЗ, ${nS} предметов`);
  } catch (e) {
    console.error(e);
    alert('Ошибка импорта: ' + (e.message || e));
  }
}
