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
