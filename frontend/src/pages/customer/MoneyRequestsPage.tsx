import { useState, type FormEvent } from 'react'
import { useAccounts } from '../../api/useAccounts'
import {
  useAcceptMoneyRequest,
  useCancelMoneyRequest,
  useCreateMoneyRequest,
  useIncomingRequests,
  useOutgoingRequests,
  useRejectMoneyRequest,
} from '../../api/useMoneyRequests'
import { usePendingOperation } from '../../lib/pendingOperation'
import { ApiError } from '../../api/client'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime, formatInr } from '../../lib/format'

function AcceptButton({ requestId }: { requestId: number }) {
  const accept = useAcceptMoneyRequest()
  const { ensureKey, clear } = usePendingOperation(`accept-money-request:${requestId}`)
  const [message, setMessage] = useState<string | null>(null)

  async function onAccept() {
    const idempotencyKey = ensureKey()
    try {
      await accept.mutateAsync({ id: requestId, idempotencyKey })
      clear()
      setMessage('Accepted and paid.')
    } catch (err) {
      if (err instanceof ApiError && err.status >= 400 && err.status < 500 && err.status !== 409) {
        clear()
        setMessage(err.message)
      } else {
        setMessage("Couldn't confirm — click Accept again to safely retry.")
      }
    }
  }

  return (
    <div className="stack" style={{ alignItems: 'flex-start' }}>
      <button type="button" className="btn btn-primary btn-sm" onClick={onAccept} disabled={accept.isPending}>
        {accept.isPending ? 'Processing…' : 'Accept & pay'}
      </button>
      {message && <span className="text-muted">{message}</span>}
    </div>
  )
}

export function MoneyRequestsPage() {
  const { data: accounts } = useAccounts()
  const [tab, setTab] = useState<'incoming' | 'outgoing'>('incoming')
  const incoming = useIncomingRequests()
  const outgoing = useOutgoingRequests()
  const reject = useRejectMoneyRequest()
  const cancel = useCancelMoneyRequest()
  const createRequest = useCreateMoneyRequest()

  const [showForm, setShowForm] = useState(false)
  const [requesterAccountId, setRequesterAccountId] = useState<number | null>(null)
  const [payerAccountNumber, setPayerAccountNumber] = useState('')
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!requesterAccountId) return
    await createRequest.mutateAsync({ requesterAccountId, payerAccountNumber, amount, note: note || undefined })
    setPayerAccountNumber('')
    setAmount('')
    setNote('')
    setShowForm(false)
  }

  const active = tab === 'incoming' ? incoming : outgoing

  return (
    <div className="stack">
      <div className="row-between">
        <h1 style={{ margin: 0 }}>Money requests</h1>
        <button type="button" className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : 'Request money'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          {createRequest.isError && <ErrorBanner error={createRequest.error} />}
          <form onSubmit={onSubmit} noValidate>
            <div className="field">
              <label htmlFor="requesterAccount">Pay into</label>
              <select id="requesterAccount" value={requesterAccountId ?? ''} onChange={(e) => setRequesterAccountId(Number(e.target.value))}>
                <option value="">Select an account</option>
                {accounts?.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.accountNumber} — {a.nickname}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="payerAccountNumber">Request from (account number)</label>
              <input id="payerAccountNumber" required value={payerAccountNumber} onChange={(e) => setPayerAccountNumber(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="amount">Amount</label>
              <input id="amount" type="number" min="0.01" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="note">Note</label>
              <input id="note" maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} />
            </div>
            <button type="submit" className="btn btn-primary" disabled={createRequest.isPending || !requesterAccountId}>
              Send request
            </button>
          </form>
        </div>
      )}

      <div className="row no-print">
        <button type="button" className={`btn btn-sm ${tab === 'incoming' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setTab('incoming')}>
          Incoming
        </button>
        <button type="button" className={`btn btn-sm ${tab === 'outgoing' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setTab('outgoing')}>
          Outgoing
        </button>
      </div>

      {active.isLoading && <LoadingState />}
      {active.isError && <ErrorBanner error={active.error} onRetry={() => active.refetch()} />}
      {active.data && active.data.content.length === 0 && <EmptyState>No {tab} requests.</EmptyState>}
      {active.data && active.data.content.length > 0 && (
        <div className="stack">
          {active.data.content.map((r) => (
            <div key={r.id} className="card" style={{ marginBottom: 0 }}>
              <div className="row-between">
                <strong>{formatInr(r.amount)}</strong>
                <StatusBadge status={r.status} />
              </div>
              {r.note && <p>{r.note}</p>}
              <span className="text-muted">{formatDateTime(r.createdAt)}</span>
              {r.status === 'PENDING' && tab === 'incoming' && (
                <div className="row" style={{ marginTop: 12 }}>
                  <AcceptButton requestId={r.id} />
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => reject.mutate(r.id)}>
                    Decline
                  </button>
                </div>
              )}
              {r.status === 'PENDING' && tab === 'outgoing' && (
                <div className="row" style={{ marginTop: 12 }}>
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => cancel.mutate(r.id)}>
                    Cancel request
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
