import { Link } from 'react-router-dom'
import { useDashboard } from '../../api/useDashboard'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime, formatInr } from '../../lib/format'

export function DashboardPage() {
  const { data, isLoading, isError, error, refetch } = useDashboard()

  if (isLoading) return <LoadingState label="Loading your dashboard…" />
  if (isError) return <ErrorBanner error={error} onRetry={() => refetch()} />
  if (!data) return null

  return (
    <div className="stack">
      <div className="card">
        <h2>Total balance</h2>
        <div className="account-card__balance" style={{ fontSize: 32, fontWeight: 700 }}>
          {formatInr(data.totalBalance)}
        </div>
        <p className="text-muted">Across {data.accounts.length} account{data.accounts.length === 1 ? '' : 's'}</p>
      </div>

      <div className="row-between">
        <h2 style={{ margin: 0 }}>Your accounts</h2>
        <Link to="/accounts" className="btn btn-secondary btn-sm">
          Manage accounts
        </Link>
      </div>
      {data.accounts.length === 0 ? (
        <EmptyState>You have no accounts yet. Create one to get started.</EmptyState>
      ) : (
        <div className="grid grid-cols-3">
          {data.accounts.map((account) => (
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

      <div className="card">
        <h2>Recent activity</h2>
        {data.recentTransactions.length === 0 ? (
          <EmptyState>No transactions yet.</EmptyState>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Counterparty</th>
                  <th>Amount</th>
                </tr>
              </thead>
              <tbody>
                {data.recentTransactions.map((tx) => (
                  <tr key={tx.id}>
                    <td>{formatDateTime(tx.createdAt)}</td>
                    <td>{tx.type.replace('_', ' ')}</td>
                    <td>{tx.counterpartyDisplayName ?? tx.counterpartyAccountNumber ?? '—'}</td>
                    <td className={tx.direction === 'DEBIT' ? 'text-danger' : 'text-success'}>
                      {tx.direction === 'DEBIT' ? '-' : '+'}
                      {formatInr(tx.amount)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
