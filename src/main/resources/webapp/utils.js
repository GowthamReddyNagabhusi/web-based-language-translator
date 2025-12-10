// Utility helpers
export function uuidv4() {
  // simple uuid v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

export function debounce(fn, wait) {
  let t;
  return function(...args) {
    clearTimeout(t);
    t = setTimeout(() => fn.apply(this, args), wait);
  };
}

export function cleanText(s){
  if(!s) return '';
  // basic cleaning: normalize spaces, remove repeated punctuation, trim
  let t = String(s);
  // normalize Unicode white space
  t = t.replace(/\s+/g, ' ');
  // fix common smart quotes
  t = t.replace(/[“”]/g, '"').replace(/[‘’]/g, "'");
  // remove repeated punctuation like !!!! or ???
  t = t.replace(/([!?.,;:\-]){2,}/g, '$1');
  // trim
  t = t.trim();
  return t;
}

export function speakText(text, options = {}){
  try{
    const utter = new SpeechSynthesisUtterance(text);
    if(options.lang) utter.lang = options.lang;
    if(options.rate) utter.rate = options.rate;
    if(options.pitch) utter.pitch = options.pitch;
    if(options.voice) utter.voice = options.voice;
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utter);
    return true;
  }catch(e){
    return false;
  }
}

export function formatTimestamp(ts) {
  const d = new Date(ts);
  return d.toLocaleString();
}

export function saveToStorage(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch(e) {}
}

export function loadFromStorage(key) {
  try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : null; } catch(e) { return null; }
}

export function downloadText(filename, text) {
  const a = document.createElement('a');
  const blob = new Blob([text], {type: 'text/plain;charset=utf-8'});
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
}

export function escapeHtml(s){
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// Very small, client-side spelling suggestion helper (English-focused).
function levenshtein(a, b) {
  if (a === b) return 0;
  const al = a.length, bl = b.length;
  if (al === 0) return bl;
  if (bl === 0) return al;
  const v0 = new Array(bl + 1), v1 = new Array(bl + 1);
  for (let j = 0; j <= bl; j++) v0[j] = j;
  for (let i = 0; i < al; i++) {
    v1[0] = i + 1;
    for (let j = 0; j < bl; j++) {
      const cost = a[i] === b[j] ? 0 : 1;
      v1[j + 1] = Math.min(v1[j] + 1, v0[j + 1] + 1, v0[j] + cost);
    }
    for (let j = 0; j <= bl; j++) v0[j] = v1[j];
  }
  return v1[bl];
}

const COMMON_EN = [
  'the','be','to','of','and','a','in','that','have','i','it','for','not','on','with','he','as','you','do','at',
  'this','but','his','by','from','they','we','say','her','she','or','an','will','my','one','all','would','there','their',
  'what','so','up','out','if','about','who','get','which','go','me','when','make','can','like','time','no','just','him',
  'know','take','people','into','year','your','good','some','could','them','see','other','than','then','now','look','only',
  'come','its','over','think','also','back','after','use','two','how','our','work','first','well','way','even','new','want',
  'because','any','these','give','day','most','us'
];

export function suggestSpelling(text){
  if(!text || typeof text !== 'string') return null;
  const words = text.split(/(\s+)/); // keep separators
  let changed = false;
  const result = words.map(tok => {
    if (!tok || /\s+/.test(tok)) return tok; // preserve spaces
    const w = tok.replace(/[^\p{L}'’-]/gu, ''); // strip punctuation
    if (!w || w.length < 3) return tok;
    const lw = w.toLowerCase();
    if (COMMON_EN.includes(lw)) return tok;
    // find nearest common word
    let best = null, bestDist = 999;
    for(const c of COMMON_EN){
      const d = levenshtein(lw, c);
      if (d < bestDist){ bestDist = d; best = c; }
      if (bestDist === 0) break;
    }
    if (best && bestDist > 0 && bestDist <= 2 && best !== lw){
      changed = true;
      // preserve capitalization
      if (/[A-Z]/.test(tok[0])) return best[0].toUpperCase() + best.slice(1);
      return best;
    }
    return tok;
  });
  if(!changed) return null;
  return result.join('');
}
