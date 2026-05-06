import { NavLink, useNavigate, Outlet } from 'react-router-dom'
import { Globe, Clock, Shield, LogOut } from 'lucide-react'
import { authApi, clearTokens, getEmail, getRole } from '../api/client'

export default function Navbar() {
  const navigate = useNavigate()
  const role = getRole()

  const handleLogout = async () => {
    try {
      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) await authApi.logout(refreshToken)
    } catch (_) {}
    clearTokens()
    navigate('/login')
  }

  return (
    <>
      <nav className="navbar">
        <div className="navbar-inner">
          <NavLink to="/translate" className="navbar-logo">
            <div className="navbar-logo-icon">🌐</div>
            <span className="navbar-logo-text">LinguaFlow</span>
          </NavLink>

          <div className="navbar-links">
            <NavLink to="/translate" className={({isActive}) => `navbar-link ${isActive ? 'active' : ''}`}>
              <Globe size={15} style={{display:'inline',marginRight:5}} />
              <span>Translate</span>
            </NavLink>
            <NavLink to="/history" className={({isActive}) => `navbar-link ${isActive ? 'active' : ''}`}>
              <Clock size={15} style={{display:'inline',marginRight:5}} />
              <span>History</span>
            </NavLink>
            {role === 'ADMIN' && (
              <NavLink to="/admin" className={({isActive}) => `navbar-link ${isActive ? 'active' : ''}`}>
                <Shield size={15} style={{display:'inline',marginRight:5}} />
                <span>Admin</span>
              </NavLink>
            )}
            <span style={{fontSize:13,color:'var(--text-dim)',padding:'0 8px'}}>
              {getEmail()}
            </span>
            <button className="navbar-link logout" onClick={handleLogout}>
              <LogOut size={15} style={{display:'inline',marginRight:4}} />
              <span>Logout</span>
            </button>
          </div>
        </div>
      </nav>
      <Outlet />
    </>
  )
}
