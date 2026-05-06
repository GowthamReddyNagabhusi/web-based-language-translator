import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi, setTokens } from '../api/client'

export default function Login() {
  const navigate = useNavigate()
  const [tab, setTab]         = useState('login')
  const [email, setEmail]     = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState('')

  const handle = async e => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const fn = tab === 'login' ? authApi.login : authApi.register
      const { data } = await fn(email, password)
      setTokens(data)
      navigate('/translate')
    } catch (err) {
      setError(err.response?.data?.error || 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card card">
        <div className="auth-logo">
          <div className="auth-logo-icon">🌐</div>
          <h1>LinguaFlow</h1>
          <p>AI-powered translation for 75+ languages</p>
        </div>

        <div className="auth-tabs">
          <button className={`auth-tab ${tab === 'login' ? 'active' : ''}`}
                  onClick={() => { setTab('login'); setError('') }}>
            Sign In
          </button>
          <button className={`auth-tab ${tab === 'register' ? 'active' : ''}`}
                  onClick={() => { setTab('register'); setError('') }}>
            Create Account
          </button>
        </div>

        <form className="auth-form" onSubmit={handle}>
          <div className="form-group">
            <label className="form-label">Email address</label>
            <input id="auth-email" className="form-input" type="email" placeholder="you@example.com"
                   value={email} onChange={e => setEmail(e.target.value)} required autoFocus />
          </div>
          <div className="form-group">
            <label className="form-label">Password {tab === 'register' && '(min 8 chars)'}</label>
            <input id="auth-password" className="form-input" type="password"
                   placeholder={tab === 'register' ? 'At least 8 characters' : '••••••••'}
                   value={password} onChange={e => setPassword(e.target.value)}
                   required minLength={tab === 'register' ? 8 : 1} />
          </div>

          {error && <div className="form-error">⚠ {error}</div>}

          <button id="auth-submit" className="btn btn-primary btn-full" type="submit" disabled={loading}>
            {loading
              ? <span className="spinner" style={{width:18,height:18,borderWidth:2}} />
              : tab === 'login' ? 'Sign In' : 'Create Account'}
          </button>
        </form>

        <div style={{marginTop:24,textAlign:'center'}}>
          <p style={{fontSize:12,color:'var(--text-dim)'}}>
            🔒 RS256 JWT · BCrypt password hashing · Redis session blacklisting
          </p>
        </div>
      </div>
    </div>
  )
}
