// ===================== Lectures + Attachments =====================
function openNewLecture(date) {
  editingLectureId = null;
  showLectureForm({
    title: '',
    date: date || toISODate(new Date()),
    subjectId: '',
    content: '',
    attachments: []
  });
}

function openLecture(id) {
  const l = db.lectures.find(x => x.id === id);
  if (!l) return;
  editingLectureId = id;
  showLectureViewer(normalizeLecture(l));
}

function showLectureViewer(l) {
  const subj = db.subjects.find(s => s.id === l.subjectId);
  const atts = l.attachments || [];
  const hasPdf = atts.some(a => a.type === 'pdf');
  document.getElementById('modalBox').classList.toggle('wide', hasPdf);
  document.getElementById('modalTitle').textContent = l.title || 'Лекция';

  let html = `
    <div class="meta" style="margin-bottom:16px; display:flex; gap:10px; flex-wrap:wrap;">
      <span class="badge">${l.date}</span>
      ${subj ? `<span class="badge subject">${escapeHtml(subj.name)}</span>` : ''}
    </div>
  `;
  if (l.content && l.content.trim()) {
    html += `<div class="viewer-content">${marked.parse(l.content)}</div>`;
  }

  // Images
  const images = atts.filter(a => a.type === 'image');
  if (images.length) {
    html += '<div style="margin-top:16px;">';
    images.forEach(a => {
      html += `<img src="${a.data}" alt="${escapeHtml(a.name)}" style="max-width:100%; border-radius:8px; margin-bottom:12px; border:1px solid var(--border);">`;
    });
    html += '</div>';
  }

  // PDFs
  const pdfs = atts.filter(a => a.type === 'pdf');
  pdfs.forEach(a => {
    html += `
      <div class="pdf-viewer">
        <div class="pdf-viewer-header">
          <span>📄 ${escapeHtml(a.name)}</span>
          <a class="btn sm secondary" href="${a.data}" download="${escapeHtml(a.name)}">Скачать</a>
        </div>
        <embed src="${a.data}" type="application/pdf" />
      </div>
    `;
  });

  if ((!l.content || !l.content.trim()) && atts.length === 0) {
    html += '<p style="color:var(--text-muted)">Пустая лекция</p>';
  }

  document.getElementById('modalBody').innerHTML = html;
  document.getElementById('modalFooter').innerHTML = `
    <button class="btn secondary" onclick="closeModal()">Закрыть</button>
    <button class="btn" onclick="editCurrentLecture()">Редактировать</button>
    <button class="btn danger" onclick="deleteCurrentLecture()">Удалить</button>
  `;
  document.getElementById('lectureModal').classList.remove('hidden');
}

function editCurrentLecture() {
  const l = db.lectures.find(x => x.id === editingLectureId);
  if (!l) return;
  showLectureForm(normalizeLecture(l));
}

function showLectureForm(l) {
  if (!editingLectureId) {
    const draft = loadDraft('lecture');
    if (draft) l = { ...l, ...draft };
  }
  document.getElementById('modalBox').classList.remove('wide');
  document.getElementById('modalTitle').textContent = editingLectureId ? 'Редактирование' : 'Новая лекция';
  document.getElementById('modalBody').innerHTML = `
    <div class="form-group">
      <label>Название</label>
      <input type="text" id="fTitle" value="${escapeHtml(l.title || '')}" placeholder="Название лекции">
    </div>
    <div class="form-group">
      <label>Дата</label>
      <input type="date" id="fDate" value="${l.date || ''}">
    </div>
    <div class="form-group">
      <label>Предмет</label>
      <select id="fSubject">
        <option value="">— без предмета —</option>
        ${db.subjects.map(s =>
          `<option value="${s.id}" ${s.id === l.subjectId ? 'selected' : ''}>${escapeHtml(s.name)}</option>`
        ).join('')}
      </select>
    </div>
    <div class="form-group">
      <label>Текст (Markdown)</label>
      <textarea id="fContent" placeholder="Текст лекции в Markdown...">${escapeHtml(l.content || '')}</textarea>
    </div>
    <div class="form-group">
      <label>Файлы (фото и PDF)</label>
      <div class="file-picker-row">
        <input class="file-input-hidden" type="file" id="fFiles" accept="image/*,application/pdf,.pdf" multiple>
        <label class="file-picker-btn" for="fFiles"><span aria-hidden="true">＋</span> Добавить файлы</label>
        <span class="file-picker-status" id="fFilesStatus">Файлы не выбраны</span>
      </div>
      <div class="files-preview" id="fFilesPreview"></div>
    </div>
  `;
  window._tempAttachments = JSON.parse(JSON.stringify(l.attachments || []));
  renderTempAttachments();

  document.getElementById('fFiles').addEventListener('change', async (e) => {
    const files = Array.from(e.target.files || []);
    for (const file of files) {
      const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
      const isImage = file.type.startsWith('image/');
      if (!isPdf && !isImage) {
        showToast('Можно только изображения и PDF');
        continue;
      }
      // Warn on large files
      if (file.size > 8 * 1024 * 1024) {
        if (!confirm(`Файл «${file.name}» больше 8 МБ. Он сильно увеличит JSON. Продолжить?`)) continue;
      }
      const dataUrl = await readFileAsDataURL(file);
      window._tempAttachments.push({
        id: uuid(),
        name: file.name,
        type: isPdf ? 'pdf' : 'image',
        data: dataUrl
      });
    }
    renderTempAttachments();
    e.target.value = '';
  });

  ['fTitle','fDate','fSubject','fContent'].forEach(id => {
    const el=document.getElementById(id);
    if(el) el.addEventListener('input', () => saveDraft('lecture', { title: document.getElementById('fTitle').value, date: document.getElementById('fDate').value, subjectId: document.getElementById('fSubject').value, content: document.getElementById('fContent').value, attachments: window._tempAttachments || [] }));
  });
  document.getElementById('modalFooter').innerHTML = `
    <button class="btn secondary" onclick="closeModal()">Отмена</button>
    <button class="btn danger" onclick="clearLectureEditor()">Очистить</button>
    <button class="btn" onclick="saveLecture()">Сохранить</button>
  `;
  document.getElementById('lectureModal').classList.remove('hidden');
}

function renderTempAttachments() {
  const prev = document.getElementById('fFilesPreview');
  if (!prev) return;
  const attachments = window._tempAttachments || [];
  const status = document.getElementById('fFilesStatus');
  if (status) {
    const n = attachments.length;
    status.textContent = n === 0 ? 'Файлы не выбраны' : n === 1 ? 'Выбран 1 файл' : `Выбрано файлов: ${n}`;
  }
  prev.innerHTML = attachments.map((a, i) => {
    if (a.type === 'image') {
      return `
        <div class="file-thumb">
          <img src="${a.data}" alt="">
          <button class="remove" onclick="removeTempAttachment(${i})">×</button>
        </div>
      `;
    }
    return `
      <div class="file-thumb">
        <div class="pdf-icon">📄</div>
        <div class="fname">${escapeHtml(a.name)}</div>
        <button class="remove" onclick="removeTempAttachment(${i})">×</button>
      </div>
    `;
  }).join('');
}

function removeTempAttachment(i) {
  window._tempAttachments.splice(i, 1);
  renderTempAttachments();
}

function readFileAsDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

function clearLectureEditor() {
  ['fTitle','fSubject','fContent'].forEach(id => { const e=document.getElementById(id); if(e) e.value=''; });
  // Дата лекции сохраняется при очистке редактора
  window._tempAttachments=[];
  renderTempAttachments();
  clearDraft('lecture');
}

async function saveLecture() {
  const title = document.getElementById('fTitle').value.trim();
  const date = document.getElementById('fDate').value;
  const subjectId = document.getElementById('fSubject').value || null;
  const content = document.getElementById('fContent').value;
  const attachments = window._tempAttachments || [];

  if (!date) {
    alert('Укажите дату');
    return;
  }

  if (editingLectureId) {
    const idx = db.lectures.findIndex(l => l.id === editingLectureId);
    if (idx >= 0) {
      db.lectures[idx] = {
        ...db.lectures[idx],
        title, date, subjectId, content, attachments,
        images: undefined, // clean old field
        updatedAt: Date.now()
      };
    }
  } else {
    db.lectures.push({
      id: uuid(),
      title, date, subjectId, content, attachments,
      createdAt: Date.now(),
      updatedAt: Date.now()
    });
  }
  await saveDB();
  clearDraft('lecture');
  closeModal();
  render();
  showToast('Сохранено');
}

async function deleteCurrentLecture() {
  if (!editingLectureId) return;
  if (!confirm('Удалить эту лекцию?')) return;
  db.lectures = db.lectures.filter(l => l.id !== editingLectureId);
  await saveDB();
  closeModal();
  render();
  showToast('Лекция удалена');
}

function closeModal() {
  document.getElementById('lectureModal').classList.add('hidden');
  document.getElementById('modalBox').classList.remove('wide');
  editingLectureId = null;
  editingHwId = null;
}
