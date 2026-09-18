import { useState } from 'react'
import { useAdminTransactionDetail, useAdminTransactions } from '../../api/useAdmin'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { formatDateTime, formatInr } from '../../lib/format'

export function AdminTransactionsPage() {
  const [reference, setReference] = useState('')
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<number | undefined>(undefined)
  const { data, isLoading, isError, error, refetch } = useAdminTransactions(reference, page)
  const { data: detail } = useAdminTransactionDetail(selectedId)

  return (
    <div className="stack">
      <h1>Transactions</h1>
      <input
        aria-label="Search by reference"
        placeholder="Search by reference"
        value={reference}
        onChange={(e) => {
          setReference(e.target.value)
          setPage(0)
        }}
      />
      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {data && data.content.length === 0 && <EmptyState>No transactions found.</EmptyState>}
      {data && data.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Reference</th>
                  <th>Type</th>
                  <th>Amount</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((t) => (
                  <tr key={t.id}>
                    <td>{formatDateTime(t.createdAt)}</td>
                    <td>{t.reference}</td>
                    <td>{t.type}</td>
                    <td>{formatInr(t.amount)}</td>
                    <td>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setSelectedId(t.id)}>
                        Details
                      </button>
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

      {detail && (
        <div className="card">
          <h2>Transaction {detail.transaction.reference}</h2>
          <p>
            <strong>Type:</strong> {detail.transaction.type}
          </p>
          <p>
            <strong>Amount:</strong> {formatInr(detail.transaction.amount)}
          </p>
          <p>
            <strong>Description:</strong> {detail.transaction.description ?? '—'}
          </p>
          <h3>Ledger entries</h3>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Direction</th>
                  <th>Amount</th>
                  <th>Balance after</th>
                </tr>
              </thead>
              <tbody>
                {detail.entries.map((e, idx) => (
                  <tr key={idx}>
                    <td>#{e.accountId}</td>
                    <td>{e.direction}</td>
                    <td>{formatInr(e.amount)}</td>
                    <td>{formatInr(e.balanceAfter)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
