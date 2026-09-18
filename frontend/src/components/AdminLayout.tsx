import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useLogout, useSession } from '../api/useAuth'
import { useEventStream } from '../api/useEventStream'

const NAV_ITEMS = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/customers', label: 'Customers' },
  { to: '/admin/accounts', label: 'Accounts' },
  { to: '/admin/transactions', label: 'Transactions' },
  { to: '/admin/support', label: 'Support' },
  { to: '/admin/alerts', label: 'Alerts' },
  { to: '/admin/audit', label: 'Audit log' },
]

export function AdminLayout() {
  const { data: session } = useSession()
  const logout = useLogout()
  const navigate = useNavigate()
  const [navOpen, setNavOpen] = useState(false)
  useEventStream(Boolean(session?.authenticated))

  async function handleLogout() {
    await logout.mutateAsync()
    navigate('/admin/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="row">
          <button
            type="button"
            className="mobile-nav-toggle"
            aria-expanded={navOpen}
            aria-controls="admin-nav"
            onClick={() => setNavOpen((v) => !v)}
          >
            Menu
          </button>
          <span className="brand">🏦 SecureBank Admin</span>
        </div>
        <div className="row">
          {session?.admin && <span className="text-muted">Signed in as {session.admin.username}</span>}
          <button type="button" className="btn btn-secondary btn-sm" onClick={handleLogout} disabled={logout.isPending}>
            Log out
          </button>
        </div>
      </header>
      <div className="app-body">
        <nav id="admin-nav" className={`app-nav ${navOpen ? 'open' : ''}`} aria-label="Admin navigation">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => setNavOpen(false)}
              className={({ isActive }) => (isActive ? 'active' : '')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <main className="app-main" id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
