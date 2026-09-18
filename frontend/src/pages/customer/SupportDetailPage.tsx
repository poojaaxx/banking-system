import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { useAddTicketMessage, useTicketDetail } from '../../api/useSupport'
import { ErrorBanner, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime } from '../../lib/format'

export function SupportDetailPage() {
  const { id } = useParams()
  const ticketId = id ? Number(id) : undefined
  const { data, isLoading, isError, error, refetch } = useTicketDetail(ticketId)
  const addMessage = useAddTicketMessage(ticketId ?? 0)
  const [message, setMessage] = useState('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await addMessage.mutateAsync(message)
    setMessage('')
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
      <div className="stack">
        {data.messages.map((m) => (
          <div key={m.id} className="card" style={{ marginBottom: 0, background: m.senderType === 'ADMIN' ? 'var(--color-info-bg)' : undefined }}>
            <div className="row-between">
              <strong>{m.senderType === 'ADMIN' ? 'Support team' : 'You'}</strong>
              <span className="text-muted">{formatDateTime(m.createdAt)}</span>
            </div>
            <p style={{ margin: '8px 0 0' }}>{m.body}</p>
          </div>
        ))}
      </div>
      <div className="card">
        {addMessage.isError && <ErrorBanner error={addMessage.error} />}
        <form onSubmit={onSubmit} noValidate>
          <div className="field">
            <label htmlFor="reply">Reply</label>
            <textarea id="reply" required rows={3} maxLength={2000} value={message} onChange={(e) => setMessage(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-primary" disabled={addMessage.isPending}>
            Send
          </button>
        </form>
      </div>
    </div>
  )
}
