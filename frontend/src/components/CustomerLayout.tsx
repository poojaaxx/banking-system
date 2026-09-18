import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useLogout, useSession } from '../api/useAuth'
import { useUnreadCount } from '../api/useNotifications'
import { useEventStream } from '../api/useEventStream'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/transfer', label: 'Transfer' },
  { to: '/money-requests', label: 'Money requests' },
  { to: '/bills', label: 'Bill pay' },
  { to: '/savings-goals', label: 'Savings goals' },
  { to: '/budgets', label: 'Budgets' },
  { to: '/beneficiaries', label: 'Beneficiaries' },
  { to: '/notifications', label: 'Notifications' },
  { to: '/support', label: 'Support' },
  { to: '/settings/security', label: 'Security' },
]

export function CustomerLayout() {
  const { data: session } = useSession()
  const logout = useLogout()
  const navigate = useNavigate()
  const { data: unreadCount } = useUnreadCount()
  const [navOpen, setNavOpen] = useState(false)
  useEventStream(Boolean(session?.authenticated))

  async function handleLogout() {
    await logout.mutateAsync()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <a href="#main-content" className="skip-link">
        Skip to content
      </a>
      <div className="demo-banner">Demo banking — fictional money. No real funds are involved.</div>
      <header className="app-header">
        <div className="row">
          <button
            type="button"
            className="mobile-nav-toggle"
            aria-expanded={navOpen}
            aria-controls="customer-nav"
            onClick={() => setNavOpen((v) => !v)}
          >
            Menu
          </button>
          <span className="brand">🏦 Demo Bank</span>
        </div>
        <div className="row">
          {session?.customer && (
            <span className="text-muted">
              Hi, {session.customer.fullName}
              {unreadCount ? ` · ${unreadCount} unread` : ''}
            </span>
          )}
          <button type="button" className="btn btn-secondary btn-sm" onClick={handleLogout} disabled={logout.isPending}>
            Log out
          </button>
        </div>
      </header>
      <div className="app-body">
        <nav id="customer-nav" className={`app-nav ${navOpen ? 'open' : ''}`} aria-label="Customer navigation">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.to} to={item.to} onClick={() => setNavOpen(false)} className={({ isActive }) => (isActive ? 'active' : '')}>
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
