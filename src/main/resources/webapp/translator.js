import { debounce, uuidv4, saveToStorage, loadFromStorage, downloadText, cleanText, speakText, escapeHtml } from './utils.js';
import { translate as apiTranslate } from './api.js';

const STORAGE_KEY = 'translationHistory';
const THEME_KEY = 'translatorTheme';
const MAX_CHARS = 5000;
const DEFAULT_TIMEOUT_MS = 10000;
const CHAR_RING_CIRCUMFERENCE = 87.96; // 2 * PI * 14

// DOM refs
const inputText          = document.getElementById('inputText');
const charCount          = document.getElementById('charCount');
const charRingFill       = document.getElementById('charRingFill');
const textStats          = document.getElementById('textStats');
const targetLang         = document.getElementById('targetLang');
const translateBtn       = document.getElementById('translateBtn');
const swapBtn            = document.getElementById('swapBtn');
const outputBox          = document.getElementById('outputBox');
const detectedLang       = document.getElementById('detectedLang');
const pronunciationBlock = document.getElementById('pronunciationBlock');
const pronunciationText  = document.getElementById('pronunciationText');
const copyBtn            = document.getElementById('copyBtn');
const downloadBtn        = document.getElementById('downloadBtn');
const saveBtn            = document.getElementById('saveBtn');
const speakBtn           = document.getElementById('speakBtn');
const shareBtn           = document.getElementById('shareBtn');
const spinner            = document.getElementById('spinner');
const liveToggle         = document.getElementById('liveToggle');
const clearBtn           = document.getElementById('clearBtn');
const suggestionBar      = document.getElementById('suggestionBar');
const targetSuggestions  = document.getElementById('targetSuggestions');
const themeToggle        = document.getElementById('themeToggle');
const toastEl            = document.getElementById('toast');
const progressBar        = document.getElementById('progressBar');
const progressFill       = document.getElementById('progressFill');

let history = loadFromStorage(STORAGE_KEY) || [];

// ---- Toast ----
let toastTimer = null;
function showToast(message, type = '') {
  if (!toastEl) return;
  clearTimeout(toastTimer);
  toastEl.textContent = message;
  toastEl.className = 'toast show' + (type ? ' ' + type : '');
  toastTimer = setTimeout(() => { toastEl.className = 'toast'; }, 2500);
}

// ---- Theme ----
function initTheme() {
  const saved = loadFromStorage(THEME_KEY);
  if (saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    document.body.classList.add('dark');
  }
}
if (themeToggle) {
  themeToggle.addEventListener('click', () => {
    document.body.classList.toggle('dark');
    const isDark = document.body.classList.contains('dark');
    saveToStorage(THEME_KEY, isDark ? 'dark' : 'light');
  });
}
initTheme();

// ---- Progress bar ----
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

// ---- Spinner (legacy compat) ----
function showSpinner(on) {
  if (!spinner) return;
  if (on) spinner.classList.remove('hidden');
  else spinner.classList.add('hidden');
}

// ---- Word count ----
function updateTextStats(text) {
  if (!textStats) return;
  const words = text.trim() ? text.trim().split(/\s+/).length : 0;
  textStats.textContent = `${words} word${words !== 1 ? 's' : ''}`;
}

// ---- Character count + SVG ring ----
function updateCharCount(len) {
  // Number display
  if (charCount) charCount.textContent = len;

  // SVG ring fill
  if (charRingFill) {
    const pct = Math.min(len / MAX_CHARS, 1);
    const offset = CHAR_RING_CIRCUMFERENCE * (1 - pct);
    charRingFill.style.strokeDashoffset = offset;
    charRingFill.classList.remove('warn', 'danger');
    if (pct >= 0.95) charRingFill.classList.add('danger');
    else if (pct >= 0.8) charRingFill.classList.add('warn');
  }
}

// ---- Pronunciation ----
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

// ---- Target suggestion chips ----
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
  const candidates = map[key] || ['es', 'fr', 'de'];
  targetSuggestions.innerHTML = '';
  for (const c of candidates) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'chip';
    btn.textContent = c.toUpperCase();
    btn.addEventListener('click', () => {
      targetLang.value = c;
      doTranslate();
    });
    targetSuggestions.appendChild(btn);
  }
}

// ---- Core translate ----
async function doTranslate(nowText) {
  const raw = nowText || inputText.value || '';
  const text = cleanText(raw).trim();
  if (!text) {
    outputBox.innerHTML = '<span class="out-placeholder">Translation will appear here\u2026</span>';
    if (detectedLang) detectedLang.textContent = '';
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
    if (detectedLang) detectedLang.textContent = src.toUpperCase();
    renderTargetSuggestions(src);
    renderPronunciation(res.pronunciation);

    // Auto-save
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
  } catch (err) {
    outputBox.innerHTML = '<span class="out-placeholder">Translation will appear here\u2026</span>';
    showToast('Translation failed: ' + (err.message || err), 'error');
  } finally {
    showSpinner(false);
    showProgress(false);
    translateBtn.disabled = false;
  }
}

const debouncedTranslate = debounce(() => doTranslate(), 500);

// ---- Events ----
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
    outputBox.innerHTML = '<span class="out-placeholder">Translation will appear here\u2026</span>';
    if (detectedLang) detectedLang.textContent = '';
    updateCharCount(0);
    updateTextStats('');
    renderPronunciation(null);
    if (suggestionBar) { suggestionBar.hidden = true; suggestionBar.innerHTML = ''; }
    inputText.focus();
  });
}

swapBtn.addEventListener('click', () => {
  const out = outputBox.textContent || '';
  if (!out || outputBox.querySelector('.out-placeholder')) return;
  inputText.value = out;
  updateCharCount(out.length);
  updateTextStats(out);
  outputBox.innerHTML = '<span class="out-placeholder">Translation will appear here\u2026</span>';
  if (detectedLang) detectedLang.textContent = '';
  inputText.focus();
  debouncedTranslate();
});

copyBtn.addEventListener('click', async () => {
  const text = outputBox.textContent || '';
  if (!text || outputBox.querySelector('.out-placeholder')) return showToast('Nothing to copy', 'error');
  try {
    await navigator.clipboard.writeText(text);
    showToast('Copied!', 'success');
  } catch (e) {
    showToast('Copy failed', 'error');
  }
});

if (speakBtn) {
  speakBtn.addEventListener('click', () => {
    const txt = outputBox.textContent || '';
    if (!txt || outputBox.querySelector('.out-placeholder')) return showToast('Nothing to speak', 'error');
    speakText(txt, { lang: targetLang.value });
    showToast('Playing audio\u2026');
  });
}

if (shareBtn) {
  shareBtn.addEventListener('click', async () => {
    const text = outputBox.textContent || '';
    const source = inputText.value.trim();
    if (!text || outputBox.querySelector('.out-placeholder')) return showToast('Nothing to share', 'error');
    const shareData = { title: 'Translation', text: `"${source}" \u2192 "${text}"` };
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
        showToast('Copied share text!', 'success');
      } catch (e) {
        showToast('Share not supported', 'error');
      }
    }
  });
}

downloadBtn.addEventListener('click', () => {
  const text = outputBox.textContent || '';
  if (!text || outputBox.querySelector('.out-placeholder')) return showToast('Nothing to download', 'error');
  downloadText('translation.txt', text);
  showToast('Downloaded!', 'success');
});

saveBtn.addEventListener('click', () => {
  const text = inputText.value.trim();
  const out = outputBox.textContent || '';
  if (!text || !out || outputBox.querySelector('.out-placeholder')) return showToast('Nothing to save', 'error');
  const record = {
    id: uuidv4(),
    inputText: text,
    outputText: out,
    sourceLang: (detectedLang ? detectedLang.textContent : '') || 'auto',
    targetLang: targetLang.value,
    timestamp: Date.now(),
    favorite: false
  };
  history.push(record);
  saveToStorage(STORAGE_KEY, history);
  showToast('Saved to history!', 'success');
});

// Quick phrases (works with both old .template-item and new .quick-card)
document.querySelectorAll('.quick-card, .template-item').forEach(item => {
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

// ---- Keyboard shortcuts ----
document.addEventListener('keydown', (e) => {
  if (e.ctrlKey && e.key === 'Enter') { e.preventDefault(); doTranslate(); }
  if (e.key === 'Escape') { e.preventDefault(); if (clearBtn) clearBtn.click(); }
  if (e.ctrlKey && e.shiftKey && e.key === 'C') { e.preventDefault(); copyBtn.click(); }
  if (e.ctrlKey && e.shiftKey && e.key === 'S') { e.preventDefault(); if (shareBtn) shareBtn.click(); }
});

// Re-translate from history page
const re = loadFromStorage('retranslate');
if (re) {
  inputText.value = re.inputText || '';
  targetLang.value = re.targetLang || targetLang.value;
  updateCharCount((re.inputText || '').length);
  updateTextStats(re.inputText || '');
  setTimeout(() => doTranslate(re.inputText), 200);
  localStorage.removeItem('retranslate');
}

// ---- Init ----
updateCharCount(inputText.value.length || 0);
updateTextStats(inputText.value || '');
showSpinner(false);
renderTargetSuggestions('auto');
renderPronunciation(null);
