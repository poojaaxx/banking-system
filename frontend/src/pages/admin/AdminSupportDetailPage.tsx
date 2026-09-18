import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { useAdminRespondToTicket, useAdminTicketDetail } from '../../api/useAdmin'
import { ErrorBanner, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime } from '../../lib/format'
import type { TicketStatus } from '../../api/types'

export function AdminSupportDetailPage() {
  const { id } = useParams()
  const ticketId = id ? Number(id) : undefined
  const { data, isLoading, isError, error, refetch } = useAdminTicketDetail(ticketId)
  const respond = useAdminRespondToTicket(ticketId ?? 0)
  const [message, setMessage] = useState('')
  const [newStatus, setNewStatus] = useState<TicketStatus | ''>('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await respond.mutateAsync({ message, newStatus: newStatus || undefined })
    setMessage('')
    setNewStatus('')
    refetch()
  }

  if (isLoading) return <LoadingState />
  if (isError) return <ErrorBanner error={error} onRetry={() => refetch()} />
  if (!data) return null

  return (
    <div className="stack">
      <div className="row-between">
        <h1 style={{ margin: 0 }}>{data.ticket.subject}</h1>
        <StatusBadge status={data.ticket.status} />
      </div>
      <p className="text-muted">Customer #{data.ticket.customerId}</p>
      <div className="stack">
        {data.messages.map((m) => (
          <div key={m.id} className="card" style={{ marginBottom: 0, background: m.senderType === 'ADMIN' ? 'var(--color-info-bg)' : undefined }}>
            <div className="row-between">
              <strong>{m.senderType === 'ADMIN' ? 'Support team' : `Customer #${data.ticket.customerId}`}</strong>
              <span className="text-muted">{formatDateTime(m.createdAt)}</span>
            </div>
            <p style={{ margin: '8px 0 0' }}>{m.body}</p>
          </div>
        ))}
      </div>
      <div className="card">
        {respond.isError && <ErrorBanner error={respond.error} />}
        <form onSubmit={onSubmit} noValidate>
          <div className="field">
            <label htmlFor="reply">Reply</label>
            <textarea id="reply" required rows={3} maxLength={2000} value={message} onChange={(e) => setMessage(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="status">Update status (optional)</label>
            <select id="status" value={newStatus} onChange={(e) => setNewStatus(e.target.value as TicketStatus | '')}>
              <option value="">Keep current status</option>
              <option value="IN_PROGRESS">In progress</option>
              <option value="RESOLVED">Resolved</option>
              <option value="CLOSED">Closed</option>
            </select>
          </div>
          <button type="submit" className="btn btn-primary" disabled={respond.isPending}>
            Send response
          </button>
        </form>
      </div>
    </div>
  )
}
