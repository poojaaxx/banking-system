import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAdminAccounts, useFreezeAccount, useUnfreezeAccount } from '../../api/useAdmin'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatInr } from '../../lib/format'

export function AdminAccountsPage() {
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useAdminAccounts(status, page)
  const freeze = useFreezeAccount()
  const unfreeze = useUnfreezeAccount()

  function onFreeze(accountId: number) {
    const reason = window.prompt('Reason for freezing this account:')
    if (!reason) return
    freeze.mutate({ id: accountId, reason })
  }

  function onUnfreeze(accountId: number) {
    const reason = window.prompt('Reason for unfreezing this account:')
    if (!reason) return
    unfreeze.mutate({ id: accountId, reason })
  }

  return (
    <div className="stack">
      <div className="row-between">
        <h1 style={{ margin: 0 }}>Accounts</h1>
        <select
          aria-label="Filter by status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value)
            setPage(0)
          }}
        >
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="FROZEN">Frozen</option>
          <option value="CLOSED">Closed</option>
        </select>
      </div>
      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {data && data.content.length === 0 && <EmptyState>No accounts match this filter.</EmptyState>}
      {data && data.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Account number</th>
                  <th>Owner</th>
                  <th>Status</th>
                  <th>Balance</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id}>
                    <td className="account-number">{a.accountNumber}</td>
                    <td>
                      <Link to={`/admin/customers/${a.ownerCustomerId}`}>Customer #{a.ownerCustomerId}</Link>
                    </td>
                    <td>
                      <StatusBadge status={a.status} />
                    </td>
                    <td>{formatInr(a.balance)}</td>
                    <td>
                      {a.status === 'ACTIVE' && (
                        <button type="button" className="btn btn-secondary btn-sm" onClick={() => onFreeze(a.id)}>
                          Freeze
                        </button>
                      )}
                      {a.status === 'FROZEN' && (
                        <button type="button" className="btn btn-secondary btn-sm" onClick={() => onUnfreeze(a.id)}>
                          Unfreeze
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="row">
            <button type="button" className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </button>
            <span className="text-muted">
              Page {page + 1} of {Math.max(data.totalPages, 1)}
            </span>
            <button type="button" className="btn btn-secondary btn-sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </button>
          </div>
        </>
      )}
    </div>
  )
}
