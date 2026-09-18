import { useMarkAllNotificationsRead, useMarkNotificationRead, useNotifications } from '../../api/useNotifications'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { formatDateTime } from '../../lib/format'

export function NotificationsPage() {
  const { data, isLoading, isError, error, refetch } = useNotifications()
  const markRead = useMarkNotificationRead()
  const markAllRead = useMarkAllNotificationsRead()

  return (
    <div className="stack">
      <div className="row-between">
        <h1 style={{ margin: 0 }}>Notifications</h1>
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => markAllRead.mutate()}>
          Mark all read
        </button>
      </div>
      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {data && data.content.length === 0 && <EmptyState>No notifications yet.</EmptyState>}
      {data && data.content.length > 0 && (
        <div className="stack">
          {data.content.map((n) => (
            <div key={n.id} className="card" style={{ marginBottom: 0, opacity: n.read ? 0.7 : 1 }}>
              <div className="row-between">
                <strong>{n.title}</strong>
                {!n.read && (
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => markRead.mutate(n.id)}>
                    Mark read
                  </button>
                )}
              </div>
              <p style={{ margin: '8px 0' }}>{n.body}</p>
              <span className="text-muted">{formatDateTime(n.createdAt)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
