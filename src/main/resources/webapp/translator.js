import { debounce, uuidv4, saveToStorage, loadFromStorage, downloadText, cleanText, speakText, suggestSpelling } from './utils.js';
import { translate as apiTranslate } from './api.js';

const STORAGE_KEY = 'translationHistory';

// DOM
const inputText = document.getElementById('inputText');
const charCount = document.getElementById('charCount');
const targetLang = document.getElementById('targetLang');
const translateBtn = document.getElementById('translateBtn');
const swapBtn = document.getElementById('swapBtn');
const outputBox = document.getElementById('outputBox');
const detectedLang = document.getElementById('detectedLang');
const copyBtn = document.getElementById('copyBtn');
const downloadBtn = document.getElementById('downloadBtn');
const saveBtn = document.getElementById('saveBtn');
const spinner = document.getElementById('spinner');
const liveToggle = document.getElementById('liveToggle');
const toneSelect = document.getElementById('toneSelect');
const speakBtn = document.getElementById('speakBtn');
const clearBtn = document.getElementById('clearBtn');
const extrasToggle = document.getElementById('extrasToggle');
const suggestionBar = document.getElementById('suggestionBar');
const targetLangCode = document.getElementById('targetLangCode');
const targetSuggestions = document.getElementById('targetSuggestions');

let history = loadFromStorage(STORAGE_KEY) || [];

function showSpinner(on){
  if(on) spinner.classList.remove('hidden'); else spinner.classList.add('hidden');
}

async function doTranslate(nowText){
  const raw = nowText || inputText.value || '';
  const text = cleanText(raw).trim();
  if(!text) { outputBox.textContent = '...'; detectedLang.textContent = 'Source: —'; return; }
  showSpinner(true);
  try{
    const style = (toneSelect && toneSelect.value) || 'default';
    // allow explicit language code override (e.g. fr-CA, zh-Hans)
    const explicitTarget = (targetLangCode && targetLangCode.value && targetLangCode.value.trim()) ? targetLangCode.value.trim() : targetLang.value;
    const res = await apiTranslate(text, explicitTarget, 10000, { style });
    const translated = res.translatedText || res.text || '';
    outputBox.textContent = translated;
    const src = (res.sourceLang || res.detectedSourceLang || 'auto');
    detectedLang.textContent = 'Source: ' + src;
    renderTargetSuggestions(src);

    // save to history automatically
    const record = {
      id: uuidv4(),
      inputText: text,
      outputText: translated,
      sourceLang: res.sourceLang || 'auto',
      targetLang: targetLang.value,
      timestamp: Date.now()
    };
    history.push(record);
    if(history.length > 200) history.shift();
    saveToStorage(STORAGE_KEY, history);
  }catch(err){
    outputBox.textContent = 'Error: ' + (err.message || err);
  }finally{ showSpinner(false); }
}

const debouncedTranslate = debounce(() => doTranslate(), 400);

// Simple mapping of useful target suggestions per detected source
function renderTargetSuggestions(sourceLang){
  if(!targetSuggestions) return;
  const map = {
    en: ['es','fr','de','hi','zh'],
    es: ['en','pt','fr'],
    fr: ['en','es','it'],
    zh: ['en','ja','ko'],
    ja: ['en','zh','ko']
  };
  const key = (sourceLang || 'auto').split('-')[0];
  const candidates = map[key] || ['en','es','fr'];
  targetSuggestions.innerHTML = '';
  for(const c of candidates){
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'suggest-btn';
    b.textContent = c;
    b.addEventListener('click', ()=>{
      // set both select (if available) and explicit code
      if(targetLang) targetLang.value = c; if(targetLangCode) targetLangCode.value = c;
      doTranslate();
    });
    targetSuggestions.appendChild(b);
  }
}

// Events
inputText.addEventListener('input', (e)=>{
  charCount.textContent = e.target.value.length;
  // spelling suggestion (lightweight, English-focused)
  if(suggestionBar){
    const suggestion = suggestSpelling(e.target.value || '');
    if(suggestion){
      suggestionBar.hidden = false;
      suggestionBar.innerHTML = `<span>Did you mean: <strong>${suggestion}</strong>?</span> <button id=applySuggestion class=btn>Apply</button>`;
      const btn = document.getElementById('applySuggestion');
      if(btn) btn.addEventListener('click', ()=>{
        inputText.value = suggestion;
        charCount.textContent = suggestion.length;
        suggestionBar.hidden = true;
        if(liveToggle && liveToggle.checked) debouncedTranslate();
      });
    }else{ suggestionBar.hidden = true; suggestionBar.innerHTML = ''; }
  }
  if(liveToggle && liveToggle.checked) debouncedTranslate();
});
translateBtn.addEventListener('click', ()=> doTranslate());

// clear input and output
if(clearBtn) clearBtn.addEventListener('click', ()=>{
  inputText.value = '';
  outputBox.textContent = '';
  detectedLang.textContent = 'Source: —';
  charCount.textContent = '0';
});

// extras toggle (collapsible sidebar)
if(extrasToggle){
  extrasToggle.addEventListener('click', ()=>{
    const ex = document.getElementById('extras');
    const expanded = extrasToggle.getAttribute('aria-expanded') === 'true';
    extrasToggle.setAttribute('aria-expanded', String(!expanded));
    ex.classList.toggle('collapsed');
  });
}

swapBtn.addEventListener('click', ()=>{
  // swap output to input and set focus
  const out = outputBox.textContent || '';
  if(!out) return;
  inputText.value = out;
  charCount.textContent = out.length;
  outputBox.textContent = '';
  detectedLang.textContent = 'Source: —';
  inputText.focus();
  debouncedTranslate();
});

copyBtn.addEventListener('click', async ()=>{
  try{ await navigator.clipboard.writeText(outputBox.textContent || '');
    copyBtn.textContent = 'Copied';
    setTimeout(()=>{ copyBtn.textContent = ''; },1200);
  }catch(e){alert('Copy failed');}
});

downloadBtn.addEventListener('click', ()=>{
  const text = outputBox.textContent || '';
  if(!text) return alert('Nothing to download');
  downloadText('translation.txt', text);
});

// speak output
if(speakBtn){
  speakBtn.addEventListener('click', ()=>{
    const txt = outputBox.textContent || '';
    if(!txt) return;
    speakText(txt, { lang: targetLang.value });
  });
}

saveBtn.addEventListener('click', ()=>{
  const text = inputText.value.trim();
  const out = outputBox.textContent || '';
  if(!text || !out) return alert('Nothing to save');
  const record = { id: uuidv4(), inputText: text, outputText: out, sourceLang: (detectedLang.textContent||'').replace('Source: ','') || 'auto', targetLang: targetLang.value, timestamp: Date.now() };
  history.push(record); saveToStorage(STORAGE_KEY, history); alert('Saved to history');
});

// Template quick-insert (if templates exist in the DOM)
document.querySelectorAll('.template-item').forEach(item => {
  item.addEventListener('click', ()=>{
    const text = item.getAttribute('data-text') || '';
    const lang = item.getAttribute('data-lang') || '';
    inputText.value = text;
    charCount.textContent = text.length;
    if(lang && targetLang) targetLang.value = lang;
    doTranslate(text);
  });
});

// re-translate support: if history wants to send data back, check localStorage 'retranslate'
const re = loadFromStorage('retranslate');
if(re){ inputText.value = re.inputText || ''; targetLang.value = re.targetLang || targetLang.value; setTimeout(()=>{ doTranslate(re.inputText); }, 200); localStorage.removeItem('retranslate'); }

// init
charCount.textContent = inputText.value.length || 0;
showSpinner(false);
renderTargetSuggestions('auto');

export const timeout = 10000;
