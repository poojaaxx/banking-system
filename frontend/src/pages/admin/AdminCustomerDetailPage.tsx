import { useParams } from 'react-router-dom'
import { useAdminCustomerDetail, useFreezeAccount, useUnfreezeAccount } from '../../api/useAdmin'
import { ErrorBanner, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatInr } from '../../lib/format'

export function AdminCustomerDetailPage() {
  const { id } = useParams()
  const customerId = id ? Number(id) : undefined
  const { data, isLoading, isError, error, refetch } = useAdminCustomerDetail(customerId)
  const freeze = useFreezeAccount()
  const unfreeze = useUnfreezeAccount()

  if (isLoading) return <LoadingState />
  if (isError) return <ErrorBanner error={error} onRetry={() => refetch()} />
  if (!data) return null

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
      <h1>{data.customer.fullName}</h1>
      <div className="card">
        <p>
          <strong>Username:</strong> {data.customer.username}
        </p>
        <p>
          <strong>Email:</strong> {data.customer.email}
        </p>
        <p>
          <strong>Status:</strong> {data.customer.status}
        </p>
      </div>

      <h2>Accounts</h2>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Account number</th>
              <th>Nickname</th>
              <th>Status</th>
              <th>Balance</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.accounts.map((a) => (
              <tr key={a.id}>
                <td className="account-number">{a.accountNumber}</td>
                <td>{a.nickname ?? '—'}</td>
                <td>
                  <StatusBadge status={a.status} />
                  {a.frozenReason && <div className="text-muted">{a.frozenReason}</div>}
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
    </div>
  )
}
