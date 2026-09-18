import { useState } from 'react'
import { useAdminAuditLog } from '../../api/useAdmin'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { formatDateTime } from '../../lib/format'

export function AdminAuditPage() {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useAdminAuditLog('', undefined, page)

  return (
    <div className="stack">
      <h1>Audit log</h1>
      <p className="text-muted">Security-sensitive administrative actions: account freeze/unfreeze and alert acknowledgement.</p>
      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {data && data.content.length === 0 && <EmptyState>No audit entries yet.</EmptyState>}
      {data && data.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Admin</th>
                  <th>Action</th>
                  <th>Target</th>
                  <th>Reason</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id}>
                    <td>{formatDateTime(a.createdAt)}</td>
                    <td>#{a.adminId}</td>
                    <td>{a.action}</td>
                    <td>
                      {a.targetType} #{a.targetId}
                    </td>
                    <td>{a.reason ?? '—'}</td>
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
