import { loadFromStorage, saveToStorage, formatTimestamp, escapeHtml } from './utils.js';

const STORAGE_KEY = 'translationHistory';
const grid = document.getElementById('historyGrid');
const searchInput = document.getElementById('searchInput');
const filterLang = document.getElementById('filterLang');
const clearAllBtn = document.getElementById('clearAllBtn');

let history = loadFromStorage(STORAGE_KEY) || [];

function render() {
  grid.innerHTML = '';
  const q = (searchInput.value || '').toLowerCase();
  const lang = filterLang.value;
  const filtered = history.filter(h => {
    if(lang && h.targetLang !== lang) return false;
    if(!q) return true;
    return (h.inputText||'').toLowerCase().includes(q) || (h.outputText||'').toLowerCase().includes(q);
  }).slice().reverse();

  if(filtered.length === 0){ grid.innerHTML = '<div class="placeholder-text">No history to show</div>'; return; }

  filtered.forEach(item => {
    const card = document.createElement('article');
    card.className = 'panel history-card';
    card.innerHTML = `
      <div class="panel-head"><strong>${escapeHtml(item.inputText.substring(0,120))}</strong></div>
      <div class="panel-body"><p>${escapeHtml(item.outputText.substring(0,300))}</p></div>
      <div class="panel-foot row">
        <small>${escapeHtml((item.sourceLang||'auto'))} → ${escapeHtml(item.targetLang)}</small>
        <small style="margin-left:auto">${formatTimestamp(item.timestamp)}</small>
      </div>
      <div class="card-actions">
        <button class="btn retranslate">Re-translate</button>
        <button class="btn copy">Copy</button>
        <button class="btn delete">Delete</button>
      </div>
    `;

    // actions
    card.querySelector('.retranslate').addEventListener('click', ()=>{
      // store retranslate payload and navigate back
      localStorage.setItem('retranslate', JSON.stringify({ inputText: item.inputText, targetLang: item.targetLang }));
      window.location.href = 'index.html';
    });
    card.querySelector('.copy').addEventListener('click', async ()=>{
      try{ await navigator.clipboard.writeText(item.outputText); alert('Copied!'); }catch(e){ alert('Copy failed'); }
    });
    card.querySelector('.delete').addEventListener('click', ()=>{
      if(!confirm('Delete this item?')) return;
      history = history.filter(h => h.id !== item.id);
      saveToStorage(STORAGE_KEY, history);
      render();
    });

    grid.appendChild(card);
  });
}

searchInput.addEventListener('input', ()=> render());
filterLang.addEventListener('change', ()=> render());
clearAllBtn.addEventListener('click', ()=>{
  if(!confirm('Clear all history?')) return;
  history = []; saveToStorage(STORAGE_KEY, history); render();
});

render();
