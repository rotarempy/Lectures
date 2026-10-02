// ===================== Render =====================
function render() {
  document.querySelectorAll('.tab').forEach(t => {
    t.classList.toggle('active', t.dataset.view === currentView);
  });
  document.getElementById('daysView').classList.toggle('hidden', currentView !== 'days');
  document.getElementById('subjectsView').classList.toggle('hidden', currentView !== 'subjects');
  document.getElementById('homeworkView').classList.toggle('hidden', currentView !== 'homework');
  document.getElementById('manageView').classList.toggle('hidden', currentView !== 'manage');
  document.getElementById('weekNav').style.display = currentView === 'days' ? 'flex' : 'none';

  const addH = document.getElementById('addHwBtn');
  addH.classList.toggle('hidden', currentView !== 'homework');
  addH.style.display = currentView === 'homework' ? 'inline-flex' : 'none';


  if (currentView === 'days') renderDays();
  else if (currentView === 'subjects') renderSubjectsView();
  else if (currentView === 'homework') renderHomework();
  else renderManage();
}

function attachmentBadges(atts) {
  if (!atts || !atts.length) return '';
  const imgs = atts.filter(a => a.type === 'image').length;
  const pdfs = atts.filter(a => a.type === 'pdf').length;
  let s = '';
  if (imgs) s += `<span class="badge">🖼 ${imgs}</span>`;
  if (pdfs) s += `<span class="badge">📄 ${pdfs}</span>`;
  return s;
}

function renderDays() {
  const container = document.getElementById('daysView');
  const days = getWeekDates(currentWeekStart);
  document.getElementById('weekLabel').textContent =
    `${formatDate(days[0])} — ${formatDate(days[6])}`;

  container.innerHTML = days.map(d => {
    const iso = toISODate(d);
    const lectures = db.lectures
      .filter(l => l.date === iso)
      .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
    const isToday = iso === toISODate(new Date());
    return `
      <div class="column">
        <div class="column-header" style="${isToday ? 'border-bottom-color: var(--accent);' : ''}">
          <div>
            <div>${formatDate(d)}</div>
            <div class="date">${iso}</div>
          </div>
          <button class="btn sm add-lecture-btn" onclick="openNewLecture('${iso}')" title="Добавить лекцию" aria-label="Добавить лекцию">+</button>
        </div>
        <div class="column-body">
          ${lectures.length === 0
            ? '<div class="empty-col">Нет лекций</div>'
            : lectures.map(l => lectureCard(l)).join('')}
        </div>
      </div>
    `;
  }).join('');
}

function lectureCard(l) {
  const subj = db.subjects.find(s => s.id === l.subjectId);
  const atts = l.attachments || [];
  return `
    <div class="lecture-card" onclick="openLecture('${l.id}')">
      <h3>${escapeHtml(l.title || 'Без названия')}</h3>
      <div class="meta">
        ${subj ? `<span class="badge subject">${escapeHtml(subj.name)}</span>` : ''}
        ${attachmentBadges(atts)}
        ${l.content && l.content.trim() ? `<span class="badge">MD</span>` : ''}
      </div>
    </div>
  `;
}

function renderSubjectsView() {
  const container = document.getElementById('subjectsView');
  if (db.subjects.length === 0) {
    container.innerHTML = '<div style="color:var(--text-muted);padding:40px;">Добавьте предметы во вкладке «Предметы»</div>';
    return;
  }
  container.innerHTML = db.subjects.map(subj => {
    const lectures = db.lectures
      .filter(l => l.subjectId === subj.id)
      .sort((a, b) => a.date.localeCompare(b.date) || (a.createdAt || 0) - (b.createdAt || 0));
    return `
      <div class="column">
        <div class="column-header">
          <div>${escapeHtml(subj.name)}</div>
          <span class="badge">${lectures.length}</span>
        </div>
        <div class="column-body">
          ${lectures.length === 0
            ? '<div class="empty-col">Нет лекций</div>'
            : lectures.map(l => `
                <div class="lecture-card" onclick="openLecture('${l.id}')">
                  <h3>${escapeHtml(l.title || 'Без названия')}</h3>
                  <div class="meta">
                    <span class="badge">${l.date}</span>
                    ${attachmentBadges(l.attachments)}
                  </div>
                </div>
              `).join('')}
        </div>
      </div>
    `;
  }).join('');
}
