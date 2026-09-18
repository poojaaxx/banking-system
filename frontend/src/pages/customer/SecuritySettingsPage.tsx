import { useState, type FormEvent } from 'react'
import { useChangePassword, useRecoveryStatus, useRegenerateRecoveryCodes } from '../../api/useAuth'
import { ErrorBanner } from '../../components/States'

export function SecuritySettingsPage() {
  const changePassword = useChangePassword()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordChanged, setPasswordChanged] = useState(false)

  const { data: recoveryStatus, refetch: refetchStatus } = useRecoveryStatus()
  const regenerate = useRegenerateRecoveryCodes()
  const [reauthPassword, setReauthPassword] = useState('')

  async function onChangePassword(e: FormEvent) {
    e.preventDefault()
    setPasswordChanged(false)
    await changePassword.mutateAsync({ currentPassword, newPassword })
    setCurrentPassword('')
    setNewPassword('')
    setPasswordChanged(true)
  }

  async function onRegenerate(e: FormEvent) {
    e.preventDefault()
    await regenerate.mutateAsync({ password: reauthPassword })
    setReauthPassword('')
    refetchStatus()
  }

  return (
    <div className="stack">
      <h1>Security</h1>

      <div className="card">
        <h2>Change password</h2>
        <p className="text-muted">Changing your password signs out all your other sessions.</p>
        {changePassword.isError && <ErrorBanner error={changePassword.error} />}
        {passwordChanged && <div className="alert alert-success">Password changed.</div>}
        <form onSubmit={onChangePassword} noValidate>
          <div className="field">
            <label htmlFor="currentPassword">Current password</label>
            <input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="newPassword">New password</label>
            <input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              required
              minLength={12}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={changePassword.isPending}>
            Change password
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Recovery codes</h2>
        <p className="text-muted">
          {recoveryStatus ? `You have ${recoveryStatus.activeCodeCount} unused recovery code(s).` : 'Loading…'}
        </p>
        <p>Regenerating replaces all existing codes — old codes stop working immediately.</p>
        {regenerate.isError && <ErrorBanner error={regenerate.error} />}
        {regenerate.isSuccess && (
          <div className="alert alert-info" style={{ fontFamily: 'monospace' }}>
            {regenerate.data.map((code) => (
              <div key={code}>{code}</div>
            ))}
          </div>
        )}
        <form onSubmit={onRegenerate} noValidate className="row">
          <div className="field" style={{ flex: 1 }}>
            <label htmlFor="reauthPassword">Confirm your password</label>
            <input id="reauthPassword" type="password" required value={reauthPassword} onChange={(e) => setReauthPassword(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-secondary" disabled={regenerate.isPending}>
            Generate new codes
          </button>
        </form>
      </div>
    </div>
  )
}
