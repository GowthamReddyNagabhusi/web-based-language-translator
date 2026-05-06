import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Navbar from './components/Navbar'
import ProtectedRoute from './components/ProtectedRoute'
import Login from './pages/Login'
import Translate from './pages/Translate'
import History from './pages/History'
import Admin from './pages/Admin'
import { isLoggedIn } from './api/client'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public */}
        <Route path="/login" element={
          isLoggedIn() ? <Navigate to="/translate" replace /> : <Login />
        } />

        {/* Protected — Navbar is the layout shell */}
        <Route element={<ProtectedRoute />}>
          <Route element={<Navbar />}>
            <Route path="/translate" element={<Translate />} />
            <Route path="/history"   element={<History />} />
            <Route path="/admin"     element={<Admin />} />
          </Route>
        </Route>

        {/* Catch-all */}
        <Route path="*" element={
          <Navigate to={isLoggedIn() ? '/translate' : '/login'} replace />
        } />
      </Routes>
    </BrowserRouter>
  )
}

