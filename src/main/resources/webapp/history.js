import { loadFromStorage, saveToStorage, formatTimestamp, escapeHtml, downloadText } from './utils.js';

const STORAGE_KEY = 'translationHistory';
const THEME_KEY = 'translatorTheme';
const grid = document.getElementById('historyGrid');
const searchInput = document.getElementById('searchInput');
const filterLang = document.getElementById('filterLang');
const clearAllBtn = document.getElementById('clearAllBtn');
const exportBtn = document.getElementById('exportBtn');
const importFile = document.getElementById('importFile');
const historyStats = document.getElementById('historyStats');
const themeToggle = document.getElementById('themeToggle');
const toastEl = document.getElementById('toast');

let history = loadFromStorage(STORAGE_KEY) || [];

// Theme
(function initTheme() {
  const saved = loadFromStorage(THEME_KEY);
  if (saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    document.body.classList.add('dark');
  }
})();
if (themeToggle) {
  themeToggle.addEventListener('click', () => {
    document.body.classList.toggle('dark');
    saveToStorage(THEME_KEY, document.body.classList.contains('dark') ? 'dark' : 'light');
  });
}

let toastTimer = null;
function showToast(message, type = '') {
  if (!toastEl) return;
  clearTimeout(toastTimer);
  toastEl.textContent = message;
  toastEl.className = 'toast show' + (type ? ' ' + type : '');
  toastTimer = setTimeout(() => { toastEl.className = 'toast'; }, 2500);
}

function updateStats(filtered) {
  if (!historyStats) return;
  const fav = history.filter(h => h.favorite).length;
  historyStats.textContent = `${history.length} translation${history.length !== 1 ? 's' : ''}` +
    (fav ? ` · ${fav} ★` : '') +
    (filtered !== undefined && filtered !== history.length ? ` · ${filtered} shown` : '');
}

function render() {
  grid.innerHTML = '';
  const q = (searchInput.value || '').toLowerCase();
  const lang = filterLang.value;
  const filtered = history.filter(h => {
    if (lang && h.targetLang !== lang) return false;
    if (!q) return true;
    return (h.inputText || '').toLowerCase().includes(q) || (h.outputText || '').toLowerCase().includes(q);
  }).slice().reverse();

  updateStats(filtered.length);

  if (filtered.length === 0) {
    grid.innerHTML = '<div class="placeholder-text">No history yet. Start translating!</div>';
    return;
  }

  const langNames = { fr:'French', es:'Spanish', de:'German', hi:'Hindi', ja:'Japanese', zh:'Chinese', it:'Italian', te:'Telugu' };

  filtered.forEach(item => {
    const card = document.createElement('article');
    card.className = 'history-card' + (item.favorite ? ' favorited' : '');
    const isFav = item.favorite;
    card.innerHTML = `
      <button class="favorite-btn ${isFav ? 'active' : ''}" title="Toggle favorite">${isFav ? '★' : '☆'}</button>
      <div class="panel-head">${escapeHtml(item.inputText.substring(0, 120))}</div>
      <div class="panel-body">${escapeHtml(item.outputText.substring(0, 300))}</div>
      <div class="panel-foot">
        <span class="lang-badge">${escapeHtml(item.sourceLang || 'auto')} → ${escapeHtml(item.targetLang)}</span>
        <small>${langNames[item.targetLang] || item.targetLang}</small>
        <small style="margin-left:auto">${formatTimestamp(item.timestamp)}</small>
      </div>
      <div class="card-actions">
        <button class="btn retranslate">Re-translate</button>
        <button class="btn copy">Copy</button>
        <button class="btn delete danger">Delete</button>
      </div>
    `;

    card.querySelector('.favorite-btn').addEventListener('click', () => {
      const idx = history.findIndex(h => h.id === item.id);
      if (idx !== -1) {
        history[idx].favorite = !history[idx].favorite;
        saveToStorage(STORAGE_KEY, history);
        render();
      }
    });
    card.querySelector('.retranslate').addEventListener('click', () => {
      localStorage.setItem('retranslate', JSON.stringify({ inputText: item.inputText, targetLang: item.targetLang }));
      window.location.href = 'index.html';
    });
    card.querySelector('.copy').addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(item.outputText);
        showToast('Copied!', 'success');
      } catch (e) {
        showToast('Copy failed', 'error');
      }
    });
    card.querySelector('.delete').addEventListener('click', () => {
      if (!confirm('Delete this item?')) return;
      history = history.filter(h => h.id !== item.id);
      saveToStorage(STORAGE_KEY, history);
      render();
      showToast('Deleted', 'success');
    });

    grid.appendChild(card);
  });
}

if (exportBtn) {
  exportBtn.addEventListener('click', () => {
    if (history.length === 0) return showToast('No history to export', 'error');
    const json = JSON.stringify(history, null, 2);
    downloadText('translation-history.json', json);
    showToast(`Exported ${history.length} items`, 'success');
  });
}

if (importFile) {
  importFile.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      try {
        const imported = JSON.parse(ev.target.result);
        if (!Array.isArray(imported)) throw new Error('Invalid format');
        const existingIds = new Set(history.map(h => h.id));
        let added = 0;
        for (const item of imported.slice(0, 5000)) {
          if (!item || typeof item !== 'object') continue;
          if (typeof item.id !== 'string' && typeof item.id !== 'number') continue;
          if (typeof item.inputText !== 'string' || typeof item.outputText !== 'string') continue;
          if (existingIds.has(item.id)) continue;
          history.push({
            id: String(item.id).slice(0, 100),
            inputText: String(item.inputText).slice(0, 10000),
            outputText: String(item.outputText).slice(0, 10000),
            targetLang: typeof item.targetLang === 'string' ? item.targetLang.slice(0, 10) : '',
            sourceLang: typeof item.sourceLang === 'string' ? item.sourceLang.slice(0, 10) : 'auto',
            timestamp: typeof item.timestamp === 'number' ? item.timestamp : Date.now(),
            favorite: !!item.favorite
          });
          added++;
        }
        saveToStorage(STORAGE_KEY, history);
        render();
        showToast(`Imported ${added} items`, 'success');
      } catch (err) {
        showToast('Invalid history file', 'error');
      }
      importFile.value = '';
    };
    reader.readAsText(file);
  });
}

searchInput.addEventListener('input', () => render());
filterLang.addEventListener('change', () => render());
clearAllBtn.addEventListener('click', () => {
  if (!confirm('Clear all history? This cannot be undone.')) return;
  history = [];
  saveToStorage(STORAGE_KEY, history);
  render();
  showToast('History cleared', 'success');
});

render();
