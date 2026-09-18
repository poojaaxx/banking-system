import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAdminTickets } from '../../api/useAdmin'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime } from '../../lib/format'
import type { TicketStatus } from '../../api/types'

export function AdminSupportListPage() {
  const [status, setStatus] = useState<TicketStatus | ''>('')
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useAdminTickets(status, page)

  return (
    <div className="stack">
      <div className="row-between">
        <h1 style={{ margin: 0 }}>Support tickets</h1>
        <select
          aria-label="Filter by status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as TicketStatus | '')
            setPage(0)
          }}
        >
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="RESOLVED">Resolved</option>
          <option value="CLOSED">Closed</option>
        </select>
      </div>
      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {data && data.content.length === 0 && <EmptyState>No tickets match this filter.</EmptyState>}
      {data && data.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Customer</th>
                  <th>Status</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((t) => (
                  <tr key={t.id}>
                    <td>
                      <Link to={`/admin/support/${t.id}`}>{t.subject}</Link>
                    </td>
                    <td>#{t.customerId}</td>
                    <td>
                      <StatusBadge status={t.status} />
                    </td>
                    <td>{formatDateTime(t.updatedAt)}</td>
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
