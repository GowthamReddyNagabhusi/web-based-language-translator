import { useState, useCallback } from 'react'
import { ArrowLeftRight, Copy, Star, Loader2, Zap } from 'lucide-react'
import { translateApi } from '../api/client'

const LANGUAGES = [
  { code:'auto', name:'Auto Detect' },
  { code:'en',   name:'English' },
  { code:'es',   name:'Spanish' },
  { code:'fr',   name:'French' },
  { code:'de',   name:'German' },
  { code:'it',   name:'Italian' },
  { code:'pt',   name:'Portuguese' },
  { code:'nl',   name:'Dutch' },
  { code:'ru',   name:'Russian' },
  { code:'ja',   name:'Japanese' },
  { code:'ko',   name:'Korean' },
  { code:'zh',   name:'Chinese (Simplified)' },
  { code:'ar',   name:'Arabic' },
  { code:'hi',   name:'Hindi' },
  { code:'bn',   name:'Bengali' },
  { code:'tr',   name:'Turkish' },
  { code:'vi',   name:'Vietnamese' },
  { code:'pl',   name:'Polish' },
  { code:'uk',   name:'Ukrainian' },
  { code:'sv',   name:'Swedish' },
]

function Toast({ msg, type }) {
  if (!msg) return null
  return <div className={`toast toast-${type}`}>{msg}</div>
}

export default function Translate() {
  const [sourceText, setSourceText]     = useState('')
  const [targetLang, setTargetLang]     = useState('es')
  const [sourceLang, setSourceLang]     = useState('auto')
  const [result, setResult]             = useState(null)
  const [loading, setLoading]           = useState(false)
  const [error, setError]               = useState('')
  const [toast, setToast]               = useState({ msg:'', type:'success' })

  const showToast = (msg, type='success') => {
    setToast({ msg, type })
    setTimeout(() => setToast({ msg:'', type:'success' }), 2500)
  }

  const handleTranslate = useCallback(async () => {
    if (!sourceText.trim()) return
    setLoading(true); setError('')
    try {
      const { data } = await translateApi.translate(sourceText, targetLang, sourceLang)
      setResult(data)
    } catch (err) {
      setError(err.response?.data?.error || 'Translation failed')
    } finally {
      setLoading(false)
    }
  }, [sourceText, targetLang, sourceLang])

  const handleSwap = () => {
    if (sourceLang === 'auto') return
    const tmp = sourceLang
    setSourceLang(targetLang)
    setTargetLang(tmp)
    setSourceText(result?.translatedText || '')
    setResult(null)
  }

  const copyResult = () => {
    if (result?.translatedText) {
      navigator.clipboard.writeText(result.translatedText)
      showToast('Copied to clipboard!')
    }
  }

  const handleKey = e => { if (e.ctrlKey && e.key === 'Enter') handleTranslate() }

  return (
    <div className="page">
      <div className="container">
        <h1 className="page-heading">Translate</h1>
        <p className="page-sub">Powered by AWS Translate · Caffeine L1 + Redis L2 caching</p>

        {/* Language selectors */}
        <div className="lang-row" style={{marginTop:24}}>
          <select id="source-lang" className="form-select" value={sourceLang}
                  onChange={e => setSourceLang(e.target.value)}
                  style={{maxWidth:220}}>
            {LANGUAGES.map(l => <option key={l.code} value={l.code}>{l.name}</option>)}
          </select>

          <button className="lang-swap-btn" onClick={handleSwap} title="Swap languages" id="swap-btn">
            <ArrowLeftRight size={18} />
          </button>

          <select id="target-lang" className="form-select" value={targetLang}
                  onChange={e => setTargetLang(e.target.value)}
                  style={{maxWidth:220}}>
            {LANGUAGES.filter(l => l.code !== 'auto').map(l =>
              <option key={l.code} value={l.code}>{l.name}</option>
            )}
          </select>

          <button id="translate-btn" className="btn btn-primary"
                  onClick={handleTranslate} disabled={loading || !sourceText.trim()}>
            {loading ? <Loader2 size={16} style={{animation:'spin 0.75s linear infinite'}} /> : <Zap size={16} />}
            {loading ? 'Translating…' : 'Translate'}
          </button>
        </div>

        {/* Text areas */}
        <div className="translate-grid">
          {/* Source */}
          <div>
            <div className="translate-header">
              <span className="translate-header-label">Source text</span>
              <span className="char-count">{sourceText.length} / 5000</span>
            </div>
            <textarea id="source-text" className="form-textarea" rows={8}
                      placeholder="Type or paste text here… (Ctrl+Enter to translate)"
                      maxLength={5000}
                      value={sourceText}
                      onChange={e => setSourceText(e.target.value)}
                      onKeyDown={handleKey}
                      style={{width:'100%',minHeight:200}} />
          </div>

          {/* Result */}
          <div>
            <div className="translate-header">
              <span className="translate-header-label">Translation</span>
              {result && (
                <div className="provider-pill">
                  <Zap size={10} />
                  {result.servedFromCache ? '⚡ Cached' : result.providerUsed}
                </div>
              )}
            </div>
            <div className="result-box" style={{minHeight:200}}>
              {loading && (
                <div style={{display:'flex',alignItems:'center',gap:10,color:'var(--text-muted)'}}>
                  <div className="spinner" />
                  Translating…
                </div>
              )}
              {!loading && !result && !error && (
                <span className="result-empty">Translation will appear here</span>
              )}
              {!loading && error && (
                <span style={{color:'var(--red-1)'}}>⚠ {error}</span>
              )}
              {!loading && result && (
                <>
                  <div style={{fontSize:17,lineHeight:1.7}}>{result.translatedText}</div>
                  <div className="stats-row" style={{marginTop:16}}>
                    <span className="stat-chip">📝 {result.wordCount} words</span>
                    <span className="stat-chip">🔤 {result.characterCount} chars</span>
                    <span className="stat-chip">🔤 Detected: {result.sourceLanguageDetected}</span>
                  </div>
                </>
              )}
            </div>

            {result && (
              <div className="result-actions">
                <button className="btn btn-ghost btn-sm" onClick={copyResult} id="copy-btn">
                  <Copy size={14} /> Copy
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Tips */}
        <div style={{marginTop:32,display:'flex',gap:12,flexWrap:'wrap'}}>
          {[
            { icon:'⚡', label:'L1 Caffeine cache (10 min)' },
            { icon:'🗄️', label:'L2 Redis cache (24 h)' },
            { icon:'🔄', label:'Circuit breaker + retry' },
            { icon:'📊', label:'History auto-saved' },
          ].map(t => (
            <span key={t.label} className="badge badge-purple">
              {t.icon} {t.label}
            </span>
          ))}
        </div>
      </div>
      <Toast {...toast} />
    </div>
  )
}
