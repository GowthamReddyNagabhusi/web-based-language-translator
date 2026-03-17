export async function translate(text, targetLang, timeoutMs = 10000) {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch('/translate', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify({ text, lang: targetLang }),
      signal: controller.signal
    });

    const txt = await res.text();
    if (!res.ok) {
      let errorMsg = 'Translation failed';
      try {
        const err = JSON.parse(txt);
        errorMsg = err.error || err.message || errorMsg;
      } catch (_) {
        errorMsg = txt || errorMsg;
      }
      throw new Error(errorMsg);
    }

    try {
      const j = JSON.parse(txt);
      return {
        translatedText: j.translatedText || j.text || txt,
        sourceLang: j.detectedSourceLang || j.source || null,
        pronunciation: j.pronunciation || j.romanization || j.transliteration || null
      };
    } catch (_) {
      return { translatedText: txt, sourceLang: null, pronunciation: null };
    }
  } catch (err) {
    if (err.name === 'AbortError') throw new Error('Request timed out — please try again');
    throw err;
  } finally {
    clearTimeout(id);
  }
}
