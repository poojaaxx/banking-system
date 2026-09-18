import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAdminCustomers } from '../../api/useAdmin'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { formatDateTime } from '../../lib/format'

export function AdminCustomersPage() {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useAdminCustomers(page)

  return (
    <div className="stack">
      <h1>Customers</h1>
      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {data && data.content.length === 0 && <EmptyState>No customers yet.</EmptyState>}
      {data && data.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Status</th>
                  <th>Joined</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <Link to={`/admin/customers/${c.id}`}>{c.fullName}</Link>
                    </td>
                    <td>{c.username}</td>
                    <td>{c.email}</td>
                    <td>{c.status}</td>
                    <td>{formatDateTime(c.createdAt)}</td>
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
