import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useRecoveryLogin } from '../../api/useAuth'
import { ErrorBanner } from '../../components/States'

export function RecoveryLoginPage() {
  const [username, setUsername] = useState('')
  const [code, setCode] = useState('')
  const recoveryLogin = useRecoveryLogin()
  const navigate = useNavigate()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await recoveryLogin.mutateAsync({ username, code })
    navigate('/settings/security', { replace: true })
  }

  return (
    <div className="auth-shell">
      <div className="card auth-card">
        <h1>Recover your account</h1>
        <p className="text-muted">Enter one of your unused recovery codes. It will be consumed once you log in.</p>
        {recoveryLogin.isError && <ErrorBanner error={recoveryLogin.error} />}
        <form onSubmit={onSubmit} noValidate>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input id="username" required value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="code">Recovery code</label>
            <input id="code" required placeholder="XXXXX-XXXXX" value={code} onChange={(e) => setCode(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={recoveryLogin.isPending}>
            {recoveryLogin.isPending ? 'Verifying…' : 'Log in with recovery code'}
          </button>
        </form>
        <div className="stack" style={{ marginTop: 16 }}>
          <Link to="/login">Back to login</Link>
        </div>
      </div>
    </div>
  )
}
