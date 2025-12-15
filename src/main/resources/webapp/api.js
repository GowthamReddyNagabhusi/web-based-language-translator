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
      // prefer JSON error messages
      try {
        const err = JSON.parse(txt);
        throw new Error(err.error || err.message || txt || 'Translation failed');
      } catch (e) {
        throw new Error(txt || 'Translation failed');
      }
    }
    // try parse JSON if backend returned structured response
    try {
      const j = JSON.parse(txt);
      // expect { translatedText, detectedSourceLang, pronunciation? }
      const pronunciation =
        j.pronunciation ||
        j.pronouncedText ||
        j.pronunciationText ||
        j.readable ||
        j.romanization ||
        j.transliteration ||
        null;
      return {
        translatedText: j.translatedText || j.text || txt,
        sourceLang: j.detectedSourceLang || j.source || null,
        pronunciation
      };
    } catch (e) {
      return { translatedText: txt, sourceLang: null, pronunciation: null };
    }
  } catch (err) {
    if (err.name === 'AbortError') throw new Error('Request timed out');
    throw err;
  } finally {
    clearTimeout(id);
  }
}
