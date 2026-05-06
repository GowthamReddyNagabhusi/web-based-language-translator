import axios from 'axios'

const api = axios.create({ baseURL: '/api/v1' })

// Attach JWT to every request
api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('accessToken')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

// Auto-refresh on 401
api.interceptors.response.use(
  r => r,
  async err => {
    const orig = err.config
    if (err.response?.status === 401 && !orig._retry) {
      orig._retry = true
      const refresh = localStorage.getItem('refreshToken')
      if (refresh) {
        try {
          const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken: refresh })
          localStorage.setItem('accessToken', data.accessToken)
          orig.headers.Authorization = `Bearer ${data.accessToken}`
          return api(orig)
        } catch {
          localStorage.clear()
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(err)
  }
)

export default api

// ── Auth ──────────────────────────────────────────────
export const authApi = {
  register: (email, password) => api.post('/auth/register', { email, password }),
  login:    (email, password) => api.post('/auth/login',    { email, password }),
  logout:   (refreshToken)    => api.post('/auth/logout',   { refreshToken }),
}

// ── Translation ───────────────────────────────────────
export const translateApi = {
  translate: (sourceText, targetLanguage, sourceLanguage = 'auto') =>
    api.post('/translations', { sourceText, targetLanguage, sourceLanguage }),
}

// ── History ───────────────────────────────────────────
export const historyApi = {
  getHistory: (params) => api.get('/history', { params }),
  getStats:   ()       => api.get('/history/stats'),
  toggleFav:  (id)     => api.patch(`/history/${id}/favorite`),
  deleteOne:  (id)     => api.delete(`/history/${id}`),
  deleteAll:  ()       => api.delete('/history'),
}

// ── Admin ─────────────────────────────────────────────
export const adminApi = {
  getUsers:      (params) => api.get('/admin/users', { params }),
  getStats:      ()       => api.get('/admin/stats'),
  deactivate:    (userId) => api.patch(`/admin/users/${userId}/deactivate`),
}

// ── Helpers ───────────────────────────────────────────
export const setTokens = ({ accessToken, refreshToken, email, role }) => {
  localStorage.setItem('accessToken', accessToken)
  localStorage.setItem('refreshToken', refreshToken)
  localStorage.setItem('email', email)
  localStorage.setItem('role', role)
}

export const clearTokens = () => localStorage.clear()
export const getEmail    = () => localStorage.getItem('email')
export const getRole     = () => localStorage.getItem('role')
export const isLoggedIn  = () => !!localStorage.getItem('accessToken')
