import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCustomerRegister } from '../../api/useAuth'
import { ErrorBanner } from '../../components/States'
import { FictionalFundsNote } from '../../components/FictionalFundsNote'

export function RegisterPage() {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const register = useCustomerRegister()
  const navigate = useNavigate()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await register.mutateAsync({ fullName, email, username, password })
  }

  if (register.isSuccess) {
    const codes = register.data.recoveryCodes
    return (
      <div className="auth-shell">
        <div className="card auth-card">
          <h1>Save your recovery codes</h1>
          <p>
            There is no email verification, so these one-time recovery codes are the only way to get back into your
            account if you forget your password. Each code works once. Store them somewhere safe now — they will never be shown
            again.
          </p>
          <div className="alert alert-info" style={{ fontFamily: 'monospace', fontSize: 15 }}>
            {codes.map((code) => (
              <div key={code}>{code}</div>
            ))}
          </div>
          <button type="button" className="btn btn-primary" style={{ width: '100%' }} onClick={() => navigate('/dashboard', { replace: true })}>
            I've saved my codes — continue
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-shell">
      <div className="card auth-card">
        <h1>Create your account</h1>
        {register.isError && <ErrorBanner error={register.error} />}
        <form onSubmit={onSubmit} noValidate>
          <div className="field">
            <label htmlFor="fullName">Full name</label>
            <input id="fullName" required value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input id="email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
            <span className="hint">Not verified.</span>
          </div>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input id="username" autoComplete="username" required minLength={3} maxLength={30} value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="new-password"
              required
              minLength={12}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <span className="hint">At least 12 characters.</span>
          </div>
          <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={register.isPending}>
            {register.isPending ? 'Creating account…' : 'Create account'}
          </button>
        </form>
        <div className="stack" style={{ marginTop: 16 }}>
          <Link to="/login">Already have an account? Log in</Link>
        </div>
        <FictionalFundsNote />
      </div>
    </div>
  )
}
