import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { useAccounts } from '../../api/useAccounts'
import { useBillPaymentHistory, useBillers, usePayBill } from '../../api/useBills'
import { usePendingOperation } from '../../lib/pendingOperation'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { formatDateTime, formatInr } from '../../lib/format'

export function BillsPage() {
  const { data: accounts } = useAccounts()
  const { data: billers } = useBillers()
  const { data: history, isLoading, isError, error, refetch } = useBillPaymentHistory()
  const payBill = usePayBill()

  const [accountId, setAccountId] = useState<number | null>(null)
  const [billerId, setBillerId] = useState<number | null>(null)
  const [amount, setAmount] = useState('')
  const [referenceNote, setReferenceNote] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  const { ensureKey, clear } = usePendingOperation(`pay-bill:${accountId}:${billerId}:${amount}`)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!accountId || !billerId) return
    const idempotencyKey = ensureKey()
    setMessage(null)
    try {
      const receipt = await payBill.mutateAsync({ accountId, billerId, amount, referenceNote: referenceNote || undefined, idempotencyKey })
      clear()
      setMessage(`Paid ${formatInr(receipt.amount)}. Reference: ${receipt.reference}`)
      setAmount('')
      setReferenceNote('')
    } catch (err) {
      if (err instanceof ApiError && err.status >= 400 && err.status < 500 && err.status !== 409) {
        clear()
        setMessage(err.message)
      } else {
        setMessage("Couldn't confirm this payment — submit again to safely retry.")
      }
    }
  }

  return (
    <div className="stack">
      <h1>Bill pay</h1>
      <p className="alert alert-warning">Demo banking — these are fictional billers. No real bill is ever paid.</p>
      <div className="card">
        <h2>Pay a bill</h2>
        {message && <div className="alert alert-info">{message}</div>}
        <form onSubmit={onSubmit} noValidate>
          <div className="field">
            <label htmlFor="account">From account</label>
            <select id="account" value={accountId ?? ''} onChange={(e) => setAccountId(Number(e.target.value))}>
              <option value="">Select an account</option>
              {accounts?.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.accountNumber} — {a.nickname}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="biller">Biller</label>
            <select id="biller" value={billerId ?? ''} onChange={(e) => setBillerId(Number(e.target.value))}>
              <option value="">Select a biller</option>
              {billers?.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="amount">Amount</label>
            <input id="amount" type="number" min="0.01" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="referenceNote">Reference note</label>
            <input id="referenceNote" maxLength={150} value={referenceNote} onChange={(e) => setReferenceNote(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-primary" disabled={payBill.isPending || !accountId || !billerId}>
            {payBill.isPending ? 'Processing…' : 'Pay bill'}
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Payment history</h2>
        {isLoading && <LoadingState />}
        {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
        {history && history.content.length === 0 && <EmptyState>No bill payments yet.</EmptyState>}
        {history && history.content.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Amount</th>
                  <th>Note</th>
                </tr>
              </thead>
              <tbody>
                {history.content.map((p) => (
                  <tr key={p.id}>
                    <td>{formatDateTime(p.createdAt)}</td>
                    <td>{formatInr(p.amount)}</td>
                    <td>{p.referenceNote ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
