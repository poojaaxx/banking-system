import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { usePendingOperation } from '../lib/pendingOperation'
import type { MoneyMovementReceipt } from '../api/types'
import { formatInr } from '../lib/format'

interface Props {
  accountId: number
  kind: 'deposit' | 'withdrawal'
  mutateAsync: (input: { accountId: number; amount: string; description?: string; idempotencyKey: string }) => Promise<MoneyMovementReceipt>
  onDone: () => void
}

type Outcome = { kind: 'success'; receipt: MoneyMovementReceipt } | { kind: 'unknown' } | { kind: 'error'; message: string } | null

export function DepositWithdrawForm({ accountId, kind, mutateAsync, onDone }: Props) {
  const { ensureKey, clear } = usePendingOperation(`${kind}:${accountId}`)
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [outcome, setOutcome] = useState<Outcome>(null)

  async function attempt() {
    const idempotencyKey = ensureKey()
    setSubmitting(true)
    try {
      const receipt = await mutateAsync({ accountId, amount, description: description || undefined, idempotencyKey })
      clear()
      setOutcome({ kind: 'success', receipt })
    } catch (err) {
      if (err instanceof ApiError && err.status >= 400 && err.status < 500 && err.status !== 409) {
        // A clean, definite business rejection (validation, insufficient funds, frozen account, ...).
        // Safe to let the customer try a fresh operation next time.
        clear()
        setOutcome({ kind: 'error', message: err.message })
      } else if (err instanceof ApiError && err.status === 409) {
        setOutcome({ kind: 'unknown' })
      } else {
        // Network error or server error: we genuinely don't know what happened.
        // Keep the same key so a retry safely reconciles instead of risking a duplicate.
        setOutcome({ kind: 'unknown' })
      }
    } finally {
      setSubmitting(false)
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    void attempt()
  }

  if (outcome?.kind === 'success') {
    return (
      <div className="alert alert-success">
        <p>
          {kind === 'deposit' ? 'Deposit' : 'Withdrawal'} of {formatInr(outcome.receipt.amount)} completed. Reference:{' '}
          {outcome.receipt.reference}
        </p>
        <button type="button" className="btn btn-secondary btn-sm" onClick={onDone}>
          Close
        </button>
      </div>
    )
  }

  return (
    <form onSubmit={onSubmit} noValidate>
      {outcome?.kind === 'unknown' && (
        <div className="alert alert-warning">
          We couldn't confirm this {kind}. It may or may not have gone through. Click "Retry" below — it's safe, this will never
          create a duplicate {kind}.
        </div>
      )}
      {outcome?.kind === 'error' && <div className="alert alert-danger">{outcome.message}</div>}
      <div className="field">
        <label htmlFor={`${kind}-amount`}>Amount (INR)</label>
        <input
          id={`${kind}-amount`}
          type="number"
          min="0.01"
          step="0.01"
          required
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`${kind}-description`}>Description (optional)</label>
        <input id={`${kind}-description`} maxLength={255} value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <button type="submit" className="btn btn-primary" disabled={submitting}>
        {submitting ? 'Processing…' : outcome?.kind === 'unknown' ? 'Retry' : `Confirm ${kind}`}
      </button>
    </form>
  )
}
