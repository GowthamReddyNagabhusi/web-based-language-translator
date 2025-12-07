import { debounce, uuidv4, saveToStorage, loadFromStorage, downloadText } from './utils.js';
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
const clearBtn = document.getElementById('clearBtn');
const extrasToggle = document.getElementById('extrasToggle');

let history = loadFromStorage(STORAGE_KEY) || [];

function showSpinner(on){
  if(on) spinner.classList.remove('hidden'); else spinner.classList.add('hidden');
}

async function doTranslate(nowText){
  const text = nowText || inputText.value.trim();
  if(!text) { outputBox.textContent = '...'; detectedLang.textContent = 'Source: —'; return; }
  showSpinner(true);
  try{
    const res = await apiTranslate(text, targetLang.value, 10000);
    const translated = res.translatedText || res.text || '';
    outputBox.textContent = translated;
    detectedLang.textContent = 'Source: ' + (res.sourceLang || 'auto');

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

// Events
inputText.addEventListener('input', (e)=>{ charCount.textContent = e.target.value.length; debouncedTranslate(); });
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
    // show temporary tooltip text for accessibility
    const prev = copyBtn.getAttribute('data-title') || '';
    copyBtn.textContent = 'Copied';
    setTimeout(()=>{ copyBtn.textContent = ''; if(prev) copyBtn.setAttribute('data-title', prev); },1200);
  }catch(e){alert('Copy failed');}
});

downloadBtn.addEventListener('click', ()=>{
  const text = outputBox.textContent || '';
  if(!text) return alert('Nothing to download');
  downloadText('translation.txt', text);
});

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

export const timeout = 10000;
