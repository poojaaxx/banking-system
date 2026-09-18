import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAccounts, useCreateAccount } from '../../api/useAccounts'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatInr } from '../../lib/format'

export function AccountsPage() {
  const { data: accounts, isLoading, isError, error, refetch } = useAccounts()
  const createAccount = useCreateAccount()
  const [nickname, setNickname] = useState('')
  const [showForm, setShowForm] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await createAccount.mutateAsync(nickname)
    setNickname('')
    setShowForm(false)
  }

  return (
    <div className="stack">
      <div className="row-between">
        <h1 style={{ margin: 0 }}>Accounts</h1>
        <button type="button" className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : 'Open new account'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          <h2>Open a new savings account</h2>
          <p className="text-muted">New accounts always start at ₹0.00.</p>
          {createAccount.isError && <ErrorBanner error={createAccount.error} />}
          <form onSubmit={onSubmit} noValidate>
            <div className="field">
              <label htmlFor="nickname">Nickname</label>
              <input id="nickname" required maxLength={60} value={nickname} onChange={(e) => setNickname(e.target.value)} placeholder="e.g. Everyday spending" />
            </div>
            <button type="submit" className="btn btn-primary" disabled={createAccount.isPending}>
              {createAccount.isPending ? 'Creating…' : 'Create account'}
            </button>
          </form>
        </div>
      )}

      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {accounts && accounts.length === 0 && <EmptyState>You have no accounts yet.</EmptyState>}
      {accounts && accounts.length > 0 && (
        <div className="grid grid-cols-3">
          {accounts.map((account) => (
            <Link key={account.id} to={`/accounts/${account.id}`} className="account-card">
              <div className="row-between">
                <span className="account-number">{account.accountNumber}</span>
                <StatusBadge status={account.status} />
              </div>
              <div className="balance">{formatInr(account.balance)}</div>
              <div className="text-muted">{account.nickname ?? 'Account'}</div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
