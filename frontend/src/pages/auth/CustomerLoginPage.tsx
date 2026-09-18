import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCustomerLogin } from '../../api/useAuth'
import { ErrorBanner } from '../../components/States'

export function CustomerLoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const login = useCustomerLogin()
  const navigate = useNavigate()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await login.mutateAsync({ username, password })
    navigate('/dashboard', { replace: true })
  }

  return (
    <div className="auth-shell">
      <div className="card auth-card">
        <div className="demo-banner" style={{ marginBottom: 16, borderRadius: 8 }}>
          Demo banking — fictional money
        </div>
        <h1>Log in</h1>
        {login.isError && <ErrorBanner error={login.error} />}
        <form onSubmit={onSubmit} noValidate>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input id="username" name="username" autoComplete="username" required value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
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
        <div className="stack" style={{ marginTop: 16 }}>
          <Link to="/recovery-login">Lost your password? Use a recovery code</Link>
          <Link to="/register">Create a new account</Link>
          <Link to="/admin/login" className="text-muted">
            Administrator login
          </Link>
        </div>
      </div>
    </div>
  )
}
