import { useState, useEffect, useCallback } from 'react'
import { Users, BarChart2, UserX, ChevronLeft, ChevronRight } from 'lucide-react'
import { adminApi, getRole } from '../api/client'
import { useNavigate } from 'react-router-dom'

function Toast({ msg, type }) {
  if (!msg) return null
  return <div className={`toast toast-${type}`}>{msg}</div>
}

export default function Admin() {
  const navigate = useNavigate()
  const [users, setUsers]       = useState([])
  const [sysStats, setSysStats] = useState(null)
  const [page, setPage]         = useState(0)
  const [totalPages, setTotal]  = useState(0)
  const [loading, setLoading]   = useState(false)
  const [toast, setToast]       = useState({ msg:'', type:'success' })

  useEffect(() => {
    if (getRole() !== 'ADMIN') { navigate('/translate'); return }
  }, [navigate])

  const showToast = (msg, type='success') => {
    setToast({ msg, type })
    setTimeout(() => setToast({ msg:'', type:'success' }), 2200)
  }

  const loadStats = useCallback(async () => {
    try { const { data } = await adminApi.getStats(); setSysStats(data) } catch (_) {}
  }, [])

  const loadUsers = useCallback(async () => {
    setLoading(true)
    try {
      const { data } = await adminApi.getUsers({ page, size: 15 })
      setUsers(data.content ?? [])
      setTotal(data.totalPages ?? 0)
    } catch (_) {}
    finally { setLoading(false) }
  }, [page])

  useEffect(() => { loadUsers(); loadStats() }, [loadUsers, loadStats])

  const deactivate = async (userId, email) => {
    if (!confirm(`Deactivate ${email}?`)) return
    try {
      await adminApi.deactivate(userId)
      setUsers(prev => prev.map(u => u.id === userId ? {...u, active: false} : u))
      showToast(`${email} deactivated`)
    } catch { showToast('Failed', 'error') }
  }

  const fmt = (iso) => iso ? new Date(iso).toLocaleDateString() : '—'

  return (
    <div className="page">
      <div className="container">
        <div style={{display:'flex',alignItems:'center',gap:12,marginBottom:4}}>
          <span style={{fontSize:28}}>🛡️</span>
          <div>
            <h1 className="page-heading">Admin Console</h1>
            <p className="page-sub">System-wide user and translation management</p>
          </div>
        </div>

        {/* System stats */}
        {sysStats && (
          <div className="admin-grid" style={{marginTop:28}}>
            {[
              { icon:'👥', val: sysStats.totalUsers,            label:'Total users' },
              { icon:'📊', val: sysStats.totalTranslationsToday, label:'Translations today' },
              { icon:'🌐', val: sysStats.awsTranslateCount,      label:'AWS Translate calls' },
              { icon:'⚡', val: `${(sysStats.cacheHitRate * 100).toFixed(1)}%`, label:'Cache hit rate' },
            ].map(s => (
              <div key={s.label} className="stat-card">
                <div style={{fontSize:24,marginBottom:6}}>{s.icon}</div>
                <div className="stat-value" style={{fontSize:28}}>{s.val}</div>
                <div className="stat-label">{s.label}</div>
              </div>
            ))}
          </div>
        )}

        <div className="divider" />

        {/* Users table */}
        <div style={{display:'flex',alignItems:'center',gap:8,marginBottom:16}}>
          <Users size={18} style={{color:'var(--purple-1)'}} />
          <h2 style={{fontSize:18,fontWeight:700}}>All Users</h2>
        </div>

        {loading && <div className="center" style={{padding:60}}><div className="spinner" /></div>}

        {!loading && (
          <div className="card" style={{padding:0,overflow:'hidden'}}>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th>Translations</th>
                    <th>Joined</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.length === 0 && (
                    <tr><td colSpan={6} style={{textAlign:'center',padding:40,color:'var(--text-dim)'}}>No users found</td></tr>
                  )}
                  {users.map(u => (
                    <tr key={u.id}>
                      <td style={{fontWeight:500}}>{u.email}</td>
                      <td>
                        <span className={`badge ${u.role === 'ADMIN' ? 'badge-purple' : 'badge-blue'}`}>
                          {u.role}
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${u.active ? 'badge-green' : 'badge-red'}`}>
                          {u.active ? '● Active' : '○ Inactive'}
                        </span>
                      </td>
                      <td>{u.translationCount}</td>
                      <td>{fmt(u.createdAt)}</td>
                      <td>
                        {u.active && u.role !== 'ADMIN' && (
                          <button className="btn btn-danger btn-sm"
                                  onClick={() => deactivate(u.id, u.email)}>
                            <UserX size={13} /> Deactivate
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
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
