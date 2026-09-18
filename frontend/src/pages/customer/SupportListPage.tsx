import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useCreateTicket, useMyTickets } from '../../api/useSupport'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime } from '../../lib/format'

export function SupportListPage() {
  const { data, isLoading, isError, error, refetch } = useMyTickets()
  const createTicket = useCreateTicket()
  const [showForm, setShowForm] = useState(false)
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await createTicket.mutateAsync({ subject, message })
    setSubject('')
    setMessage('')
    setShowForm(false)
  }

  return (
    <div className="stack">
      <div className="row-between">
        <h1 style={{ margin: 0 }}>Support</h1>
        <button type="button" className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : 'New ticket'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          {createTicket.isError && <ErrorBanner error={createTicket.error} />}
          <form onSubmit={onSubmit} noValidate>
            <div className="field">
              <label htmlFor="subject">Subject</label>
              <input id="subject" required maxLength={150} value={subject} onChange={(e) => setSubject(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="message">Message</label>
              <textarea id="message" required rows={4} maxLength={2000} value={message} onChange={(e) => setMessage(e.target.value)} />
            </div>
            <button type="submit" className="btn btn-primary" disabled={createTicket.isPending}>
              Submit
            </button>
          </form>
        </div>
      )}

      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {data && data.content.length === 0 && <EmptyState>No support tickets yet.</EmptyState>}
      {data && data.content.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Subject</th>
                <th>Status</th>
                <th>Updated</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((t) => (
                <tr key={t.id}>
                  <td>
                    <Link to={`/support/${t.id}`}>{t.subject}</Link>
                  </td>
                  <td>
                    <StatusBadge status={t.status} />
                  </td>
                  <td>{formatDateTime(t.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
