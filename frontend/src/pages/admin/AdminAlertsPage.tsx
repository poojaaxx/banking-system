import { useState } from 'react'
import { useAcknowledgeAlert, useAdminAlerts } from '../../api/useAdmin'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { formatDateTime } from '../../lib/format'

export function AdminAlertsPage() {
  const [onlyUnacknowledged, setOnlyUnacknowledged] = useState(true)
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useAdminAlerts(onlyUnacknowledged, page)
  const acknowledge = useAcknowledgeAlert()

  return (
    <div className="stack">
      <div className="row-between">
        <h1 style={{ margin: 0 }}>Activity alerts</h1>
        <label className="row">
          <input
            type="checkbox"
            checked={onlyUnacknowledged}
            onChange={(e) => {
              setOnlyUnacknowledged(e.target.checked)
              setPage(0)
            }}
          />
          Only unacknowledged
        </label>
      </div>
      <p className="text-muted">
        Simple, explainable threshold rules (large transactions, rapid transfers, repeated failed logins) — not AI fraud detection.
      </p>
      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {data && data.content.length === 0 && <EmptyState>No alerts.</EmptyState>}
      {data && data.content.length > 0 && (
        <>
          <div className="stack">
            {data.content.map((a) => (
              <div key={a.id} className="card" style={{ marginBottom: 0 }}>
                <div className="row-between">
                  <strong>{a.ruleCode.replace(/_/g, ' ')}</strong>
                  <span className={`badge ${a.severity === 'HIGH' ? 'badge-danger' : a.severity === 'MEDIUM' ? 'badge-warning' : 'badge-closed'}`}>
                    {a.severity}
                  </span>
                </div>
                <p>{a.message}</p>
                <span className="text-muted">{formatDateTime(a.createdAt)}</span>
                {!a.acknowledgedAt ? (
                  <div style={{ marginTop: 8 }}>
                    <button type="button" className="btn btn-secondary btn-sm" onClick={() => acknowledge.mutate(a.id)}>
                      Acknowledge
                    </button>
                  </div>
                ) : (
                  <div className="text-muted" style={{ marginTop: 8 }}>
                    Acknowledged by admin #{a.acknowledgedByAdminId} on {formatDateTime(a.acknowledgedAt)}
                  </div>
                )}
              </div>
            ))}
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
