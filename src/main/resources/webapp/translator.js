import { debounce, uuidv4, saveToStorage, loadFromStorage, downloadText, cleanText, speakText, escapeHtml } from './utils.js';
import { translate as apiTranslate } from './api.js';

const STORAGE_KEY = 'translationHistory';
const THEME_KEY = 'translatorTheme';
const MAX_CHARS = 5000;
const DEFAULT_TIMEOUT_MS = 10000;

// DOM references
const inputText = document.getElementById('inputText');
const charCount = document.getElementById('charCount');
const textStats = document.getElementById('textStats');
const targetLang = document.getElementById('targetLang');
const translateBtn = document.getElementById('translateBtn');
const swapBtn = document.getElementById('swapBtn');
const outputBox = document.getElementById('outputBox');
const detectedLang = document.getElementById('detectedLang');
const pronunciationBlock = document.getElementById('pronunciationBlock');
const pronunciationText = document.getElementById('pronunciationText');
const copyBtn = document.getElementById('copyBtn');
const downloadBtn = document.getElementById('downloadBtn');
const saveBtn = document.getElementById('saveBtn');
const speakBtn = document.getElementById('speakBtn');
const shareBtn = document.getElementById('shareBtn');
const spinner = document.getElementById('spinner');
const liveToggle = document.getElementById('liveToggle');
const clearBtn = document.getElementById('clearBtn');
const extrasToggle = document.getElementById('extrasToggle');
const suggestionBar = document.getElementById('suggestionBar');
const targetSuggestions = document.getElementById('targetSuggestions');
const themeToggle = document.getElementById('themeToggle');
const historyPreview = document.getElementById('historyPreview');
const toastEl = document.getElementById('toast');
const progressBar = document.getElementById('progressBar');
const progressFill = document.getElementById('progressFill');

let history = loadFromStorage(STORAGE_KEY) || [];

// --- Toast notification system ---
let toastTimer = null;
function showToast(message, type = '') {
  if (!toastEl) return;
  clearTimeout(toastTimer);
  toastEl.textContent = message;
  toastEl.className = 'toast show' + (type ? ' ' + type : '');
  toastTimer = setTimeout(() => { toastEl.className = 'toast hidden'; }, 2500);
}

// --- Theme ---
function initTheme() {
  const saved = loadFromStorage(THEME_KEY);
  if (saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    document.body.classList.add('dark');
    if (themeToggle) themeToggle.textContent = '☀️';
  }
}
if (themeToggle) {
  themeToggle.addEventListener('click', () => {
    document.body.classList.toggle('dark');
    const isDark = document.body.classList.contains('dark');
    themeToggle.textContent = isDark ? '☀️' : '🌙';
    saveToStorage(THEME_KEY, isDark ? 'dark' : 'light');
  });
}
initTheme();

// --- Progress bar ---
function showProgress(on) {
  if (!progressBar || !progressFill) return;
  if (on) {
    progressBar.classList.add('active');
    progressFill.classList.add('indeterminate');
  } else {
    progressFill.classList.remove('indeterminate');
    progressFill.style.width = '100%';
    setTimeout(() => {
      progressBar.classList.remove('active');
      progressFill.style.width = '0%';
    }, 300);
  }
}

// --- Spinner ---
function showSpinner(on) {
  if (on) { spinner.classList.remove('hidden'); }
  else { spinner.classList.add('hidden'); }
}

// --- Word count & reading time ---
function updateTextStats(text) {
  if (!textStats) return;
  const words = text.trim() ? text.trim().split(/\s+/).length : 0;
  const readSec = Math.max(1, Math.ceil(words / 4)); // ~238 wpm ≈ 4 words/sec
  textStats.textContent = `${words} word${words !== 1 ? 's' : ''} · ~${readSec} sec read`;
}

// --- Character count with warning ---
function updateCharCount(len) {
  if (!charCount) return;
  charCount.textContent = len + ' / ' + MAX_CHARS;
  charCount.classList.remove('warning', 'danger');
  if (len >= MAX_CHARS * 0.95) charCount.classList.add('danger');
  else if (len >= MAX_CHARS * 0.8) charCount.classList.add('warning');
}

// --- Pronunciation ---
function renderPronunciation(pronunciation) {
  if (!pronunciationBlock || !pronunciationText) return;
  if (pronunciation && pronunciation.trim()) {
    pronunciationText.textContent = pronunciation;
    pronunciationBlock.classList.remove('hidden');
  } else {
    pronunciationText.textContent = '';
    pronunciationBlock.classList.add('hidden');
  }
}

// --- Target suggestions ---
function renderTargetSuggestions(sourceLang) {
  if (!targetSuggestions) return;
  const map = {
    en: ['es', 'fr', 'de', 'hi', 'ja', 'zh'],
    es: ['en', 'fr', 'it', 'de'],
    fr: ['en', 'es', 'it', 'de'],
    de: ['en', 'fr', 'es'],
    hi: ['en', 'te', 'ja'],
    zh: ['en', 'ja'],
    ja: ['en', 'zh'],
    it: ['en', 'fr', 'es'],
    te: ['en', 'hi']
  };
  const key = (sourceLang || 'auto').split('-')[0];
  const candidates = map[key] || ['en', 'es', 'fr', 'de'];
  targetSuggestions.innerHTML = '';
  for (const c of candidates) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'suggest-btn';
    button.textContent = c.toUpperCase();
    button.addEventListener('click', () => {
      targetLang.value = c;
      doTranslate();
    });
    targetSuggestions.appendChild(button);
  }
}

// --- History preview in sidebar ---
function renderHistoryPreview() {
  if (!historyPreview) return;
  const recent = history.slice(-3).reverse();
  if (recent.length === 0) {
    historyPreview.innerHTML = '<span style="color:var(--muted)">No recent items</span>';
    return;
  }
  historyPreview.innerHTML = recent.map(h =>
    `<div style="padding:4px 0;font-size:13px;border-bottom:1px solid var(--card-border)">
      <span style="color:var(--muted)">${escapeHtml(h.inputText.substring(0,30))}…</span>
      → <span>${escapeHtml(h.outputText.substring(0,30))}…</span>
    </div>`
  ).join('');
}

// --- Core translate ---
async function doTranslate(nowText) {
  const raw = nowText || inputText.value || '';
  const text = cleanText(raw).trim();
  if (!text) {
    outputBox.textContent = '';
    detectedLang.textContent = 'Source: —';
    renderPronunciation(null);
    return;
  }

  showSpinner(true);
  showProgress(true);
  translateBtn.disabled = true;
  try {
    const res = await apiTranslate(text, targetLang.value, DEFAULT_TIMEOUT_MS);
    const translated = res.translatedText || res.text || '';
    outputBox.textContent = translated;
    const src = res.sourceLang || res.detectedSourceLang || 'auto';
    detectedLang.textContent = 'Source: ' + src;
    renderTargetSuggestions(src);
    renderPronunciation(res.pronunciation);

    // Auto-save to history
    const record = {
      id: uuidv4(),
      inputText: text,
      outputText: translated,
      sourceLang: src,
      targetLang: targetLang.value,
      timestamp: Date.now(),
      favorite: false
    };
    history.push(record);
    if (history.length > 200) history.shift();
    saveToStorage(STORAGE_KEY, history);
    renderHistoryPreview();
  } catch (err) {
    outputBox.textContent = '';
    showToast('Translation failed: ' + (err.message || err), 'error');
  } finally {
    showSpinner(false);
    showProgress(false);
    translateBtn.disabled = false;
  }
}

const debouncedTranslate = debounce(() => doTranslate(), 500);

// --- Events ---
inputText.addEventListener('input', (e) => {
  const len = e.target.value.length;
  updateCharCount(len);
  updateTextStats(e.target.value);
  if (liveToggle && liveToggle.checked) debouncedTranslate();
});

translateBtn.addEventListener('click', () => doTranslate());

if (clearBtn) {
  clearBtn.addEventListener('click', () => {
    inputText.value = '';
    outputBox.textContent = '';
    detectedLang.textContent = 'Source: —';
    updateCharCount(0);
    updateTextStats('');
    renderPronunciation(null);
    if (suggestionBar) { suggestionBar.hidden = true; suggestionBar.innerHTML = ''; }
  });
}

if (extrasToggle) {
  extrasToggle.addEventListener('click', () => {
    const extras = document.getElementById('extras');
    const expanded = extrasToggle.getAttribute('aria-expanded') === 'true';
    extrasToggle.setAttribute('aria-expanded', String(!expanded));
    extrasToggle.textContent = !expanded ? 'Extras ▾' : 'Extras ▸';
    extras.classList.toggle('collapsed');
  });
}

swapBtn.addEventListener('click', () => {
  const out = outputBox.textContent || '';
  if (!out) return;
  inputText.value = out;
  updateCharCount(out.length);
  updateTextStats(out);
  outputBox.textContent = '';
  detectedLang.textContent = 'Source: —';
  inputText.focus();
  debouncedTranslate();
});

copyBtn.addEventListener('click', async () => {
  const text = outputBox.textContent || '';
  if (!text) return showToast('Nothing to copy', 'error');
  try {
    await navigator.clipboard.writeText(text);
    showToast('Copied to clipboard!', 'success');
  } catch (e) {
    showToast('Copy failed', 'error');
  }
});

if (speakBtn) {
  speakBtn.addEventListener('click', () => {
    const txt = outputBox.textContent || '';
    if (!txt) return showToast('Nothing to speak', 'error');
    speakText(txt, { lang: targetLang.value });
    showToast('Playing audio…');
  });
}

// --- Share (Web Share API with clipboard fallback) ---
if (shareBtn) {
  shareBtn.addEventListener('click', async () => {
    const text = outputBox.textContent || '';
    const source = inputText.value.trim();
    if (!text) return showToast('Nothing to share', 'error');
    const shareData = {
      title: 'Translation',
      text: `"${source}" → "${text}"`,
    };
    if (navigator.share) {
      try {
        await navigator.share(shareData);
        showToast('Shared!', 'success');
      } catch (e) {
        if (e.name !== 'AbortError') showToast('Share cancelled', 'error');
      }
    } else {
      try {
        await navigator.clipboard.writeText(shareData.text);
        showToast('Copied share text to clipboard!', 'success');
      } catch (e) {
        showToast('Share not supported', 'error');
      }
    }
  });
}

downloadBtn.addEventListener('click', () => {
  const text = outputBox.textContent || '';
  if (!text) return showToast('Nothing to download', 'error');
  downloadText('translation.txt', text);
  showToast('Downloaded!', 'success');
});

saveBtn.addEventListener('click', () => {
  const text = inputText.value.trim();
  const out = outputBox.textContent || '';
  if (!text || !out) return showToast('Nothing to save', 'error');
  const record = {
    id: uuidv4(),
    inputText: text,
    outputText: out,
    sourceLang: (detectedLang.textContent || '').replace('Source: ', '') || 'auto',
    targetLang: targetLang.value,
    timestamp: Date.now(),
    favorite: false
  };
  history.push(record);
  saveToStorage(STORAGE_KEY, history);
  renderHistoryPreview();
  showToast('Saved to history!', 'success');
});

// Quick phrase templates
document.querySelectorAll('.template-item').forEach(item => {
  item.addEventListener('click', () => {
    const text = item.getAttribute('data-text') || '';
    const lang = item.getAttribute('data-lang') || '';
    inputText.value = text;
    updateCharCount(text.length);
    updateTextStats(text);
    if (lang && targetLang) targetLang.value = lang;
    doTranslate(text);
  });
});

// --- Keyboard shortcuts ---
document.addEventListener('keydown', (e) => {
  // Ctrl+Enter → Translate
  if (e.ctrlKey && e.key === 'Enter') {
    e.preventDefault();
    doTranslate();
  }
  // Escape → Clear
  if (e.key === 'Escape') {
    e.preventDefault();
    if (clearBtn) clearBtn.click();
  }
  // Ctrl+Shift+C → Copy translation
  if (e.ctrlKey && e.shiftKey && e.key === 'C') {
    e.preventDefault();
    copyBtn.click();
  }
  // Ctrl+Shift+S → Share
  if (e.ctrlKey && e.shiftKey && e.key === 'S') {
    e.preventDefault();
    if (shareBtn) shareBtn.click();
  }
});

// Re-translate support from history page
const re = loadFromStorage('retranslate');
if (re) {
  inputText.value = re.inputText || '';
  targetLang.value = re.targetLang || targetLang.value;
  updateCharCount((re.inputText || '').length);
  updateTextStats(re.inputText || '');
  setTimeout(() => { doTranslate(re.inputText); }, 200);
  localStorage.removeItem('retranslate');
}

// --- Init ---
updateCharCount(inputText.value.length || 0);
updateTextStats(inputText.value || '');
showSpinner(false);
renderTargetSuggestions('auto');
renderPronunciation(null);
renderHistoryPreview();
