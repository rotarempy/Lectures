// ===================== Init =====================
(async function init() {
  updateCurrentDateTitle();
  // 1. Primary: IndexedDB
  let loaded = await loadDataFromIDB();

  // 2. Migrate from old localStorage cache if IDB empty
  if (!loaded) {
    loaded = loadFromLegacyLocalStorage();
    if (loaded) {
      try { await saveDataToIDB(); } catch (e) {}
    }
  }


  if (!db.homeworks) db.homeworks = [];
  db = normalizeDB(db);


  render();

  if (loaded) {
    const n = db.lectures.length + (db.homeworks || []).length;
    if (n > 0) showToast('Загружено: ' + db.lectures.length + ' лекций, ' + (db.homeworks || []).length + ' ДЗ', 2000);
  }
})();
