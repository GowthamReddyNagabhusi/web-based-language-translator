import { useState, useEffect, useCallback } from 'react'
import { Search, Star, Trash2, ChevronLeft, ChevronRight, ArrowRight, BarChart2 } from 'lucide-react'
import { historyApi } from '../api/client'

function StatCard({ value, label, color }) {
  return (
    <div className="stat-card">
      <div className="stat-value" style={color ? {background:color,WebkitBackgroundClip:'text'} : {}}>
        {value ?? '—'}
      </div>
      <div className="stat-label">{label}</div>
    </div>
  )
}

function Toast({ msg, type }) {
  if (!msg) return null
  return <div className={`toast toast-${type}`}>{msg}</div>
}

export default function History() {
  const [items, setItems]         = useState([])
  const [stats, setStats]         = useState(null)
  const [search, setSearch]       = useState('')
  const [langFilter, setLang]     = useState('')
  const [favOnly, setFavOnly]     = useState(false)
  const [page, setPage]           = useState(0)
  const [totalPages, setTotal]    = useState(0)
  const [loading, setLoading]     = useState(false)
  const [toast, setToast]         = useState({ msg:'', type:'success' })

  const showToast = (msg, type='success') => {
    setToast({ msg, type })
    setTimeout(() => setToast({ msg:'', type:'success' }), 2200)
  }

  const loadStats = useCallback(async () => {
    try { const { data } = await historyApi.getStats(); setStats(data) } catch (_) {}
  }, [])

  const loadHistory = useCallback(async () => {
    setLoading(true)
    try {
      const params = { page, size: 10 }
      if (search)    params.search         = search
      if (langFilter) params.targetLanguage = langFilter
      if (favOnly)   params.favoritesOnly  = true
      const { data } = await historyApi.getHistory(params)
      setItems(data.content ?? [])
      setTotal(data.totalPages ?? 0)
    } catch (_) {}
    finally { setLoading(false) }
  }, [page, search, langFilter, favOnly])

  useEffect(() => { loadHistory(); loadStats() }, [loadHistory, loadStats])

  const toggleFav = async (id) => {
    try {
      await historyApi.toggleFav(id)
      setItems(prev => prev.map(i => i.id === id ? {...i, favorite: !i.favorite} : i))
      showToast('Favourite updated')
    } catch { showToast('Failed', 'error') }
  }

  const deleteOne = async (id) => {
    if (!confirm('Delete this translation?')) return
    try {
      await historyApi.deleteOne(id)
      setItems(prev => prev.filter(i => i.id !== id))
      showToast('Deleted')
      loadStats()
    } catch { showToast('Failed', 'error') }
  }

  const deleteAll = async () => {
    if (!confirm('Delete ALL translation history? This cannot be undone.')) return
    try {
      await historyApi.deleteAll()
      setItems([]); setTotal(0)
      loadStats()
      showToast('All history deleted')
    } catch { showToast('Failed', 'error') }
  }

  const fmt = (iso) => iso ? new Date(iso).toLocaleString() : ''

  return (
    <div className="page">
      <div className="container">
        <div style={{display:'flex',alignItems:'flex-end',justifyContent:'space-between',flexWrap:'wrap',gap:12}}>
          <div>
            <h1 className="page-heading">Translation History</h1>
            <p className="page-sub">Browse, search, and manage your past translations</p>
          </div>
          {items.length > 0 && (
            <button className="btn btn-danger btn-sm" onClick={deleteAll} id="delete-all-btn">
              <Trash2 size={14} /> Clear All
            </button>
          )}
        </div>

        {/* Stats */}
        {stats && (
          <div className="stats-grid" style={{marginTop:28}}>
            <StatCard value={stats.totalTranslations} label="Total translations" />
            <StatCard value={stats.favoriteCount}    label="Favourites" />
            <StatCard value={stats.translationsThisWeek} label="This week" />
            <StatCard value={stats.mostUsedLanguage?.toUpperCase() ?? '—'} label="Top language" />
          </div>
        )}

        {/* Filters */}
        <div className="history-filters">
          <div style={{position:'relative'}}>
            <Search size={15} style={{position:'absolute',left:12,top:'50%',transform:'translateY(-50%)',color:'var(--text-dim)'}} />
            <input id="history-search" className="form-input" placeholder="Search source text…"
                   style={{paddingLeft:36}}
                   value={search}
                   onChange={e => { setSearch(e.target.value); setPage(0) }} />
          </div>
          <input id="lang-filter" className="form-input" placeholder="Filter by language (e.g. fr)"
                 value={langFilter}
                 onChange={e => { setLang(e.target.value); setPage(0) }}
                 style={{maxWidth:180}} />
          <button id="fav-filter-btn"
                  className={`btn ${favOnly ? 'btn-primary' : 'btn-ghost'} btn-sm`}
                  onClick={() => { setFavOnly(!favOnly); setPage(0) }}>
            <Star size={14} /> Favourites
          </button>
        </div>

        {/* List */}
        {loading && (
          <div className="center" style={{padding:60}}><div className="spinner" /></div>
        )}
        {!loading && items.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon">📭</div>
            <p>No translations found</p>
            <p style={{fontSize:13,marginTop:6}}>Translate something to see it here</p>
          </div>
        )}
        {!loading && items.length > 0 && (
          <div className="history-list">
            {items.map(item => (
              <div key={item.id} className="history-item">
                <div className="history-langs">
                  <span>{(item.sourceLanguage || 'auto').toUpperCase()}</span>
                  <ArrowRight size={12} className="history-arrow" />
                  <span>{item.targetLanguage?.toUpperCase()}</span>
                  {item.isCached && <span className="badge badge-green" style={{marginLeft:4}}>⚡ cached</span>}
                  {item.favorite && <span className="badge badge-purple" style={{marginLeft:4}}>⭐ fav</span>}
                </div>
                <div className="history-source">"{item.sourceText}"</div>
                <div className="history-translated">{item.translatedText}</div>
                <div className="history-footer">
                  <div className="history-meta">
                    <span>{fmt(item.createdAt)}</span>
                    {item.providerUsed && <span>via {item.providerUsed}</span>}
                  </div>
                  <div className="history-actions">
                    <button className={`btn btn-ghost btn-icon ${item.favorite ? 'btn-primary' : ''}`}
                            onClick={() => toggleFav(item.id)} title="Toggle favourite">
                      <Star size={14} fill={item.favorite ? 'currentColor' : 'none'} />
                    </button>
                    <button className="btn btn-danger btn-icon"
                            onClick={() => deleteOne(item.id)} title="Delete">
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="pagination">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(p => p-1)}>
              <ChevronLeft size={16} />
            </button>
            <span style={{fontSize:14,color:'var(--text-muted)'}}>Page {page+1} of {totalPages}</span>
            <button className="btn btn-ghost btn-sm" disabled={page >= totalPages-1} onClick={() => setPage(p => p+1)}>
              <ChevronRight size={16} />
            </button>
          </div>
        )}
      </div>
      <Toast {...toast} />
    </div>
  )
}
