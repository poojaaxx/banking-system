import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAdminLogin } from '../../api/useAuth'
import { ErrorBanner } from '../../components/States'

export function AdminLoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const login = useAdminLogin()
  const navigate = useNavigate()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await login.mutateAsync({ username, password })
    navigate('/admin', { replace: true })
  }

  return (
    <div className="auth-shell">
      <div className="card auth-card">
        <h1>Administrator login</h1>
        <p className="text-muted">There is no public admin registration. Accounts are bootstrapped by the operator.</p>
        {login.isError && <ErrorBanner error={login.error} />}
        <form onSubmit={onSubmit} noValidate>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input id="username" autoComplete="username" required value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={login.isPending}>
            {login.isPending ? 'Logging in…' : 'Log in'}
          </button>
        </form>
      </div>
    </div>
  )
}
