import { useState } from 'react'
import { useAccounts } from '../../api/useAccounts'
import { apiGet, ApiError } from '../../api/client'
import { useTransfer } from '../../api/useTransfers'
import { usePendingOperation } from '../../lib/pendingOperation'
import { LoadingState } from '../../components/States'
import { formatInr } from '../../lib/format'
import type { MoneyMovementReceipt, RecipientLookupResult } from '../../api/types'

type Step = 'form' | 'confirm' | 'result'
type Outcome = { kind: 'success'; receipt: MoneyMovementReceipt } | { kind: 'unknown' } | { kind: 'error'; message: string }

export function TransferPage() {
  const { data: accounts, isLoading } = useAccounts()
  const transfer = useTransfer()

  const [step, setStep] = useState<Step>('form')
  const [sourceAccountId, setSourceAccountId] = useState<number | null>(null)
  const [destinationAccountNumber, setDestinationAccountNumber] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [recipient, setRecipient] = useState<RecipientLookupResult | null>(null)
  const [lookupError, setLookupError] = useState<string | null>(null)
  const [lookingUp, setLookingUp] = useState(false)
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const activeSourceAccountId = sourceAccountId ?? accounts?.[0]?.id ?? null
  const { ensureKey, clear } = usePendingOperation(
    activeSourceAccountId ? `transfer:${activeSourceAccountId}:${destinationAccountNumber}:${amount}` : 'transfer:unset',
  )

  async function lookupRecipient() {
    setLookupError(null)
    setLookingUp(true)
    try {
      const result = await apiGet<RecipientLookupResult>(`/api/customer/accounts/lookup?accountNumber=${encodeURIComponent(destinationAccountNumber)}`)
      setRecipient(result)
      setStep('confirm')
    } catch (err) {
      setLookupError(err instanceof ApiError ? err.message : 'Could not look up that account.')
    } finally {
      setLookingUp(false)
    }
  }

  async function submitTransfer() {
    if (!activeSourceAccountId) return
    const idempotencyKey = ensureKey()
    setSubmitting(true)
    try {
      const receipt = await transfer.mutateAsync({
        sourceAccountId: activeSourceAccountId,
        destinationAccountNumber,
        amount,
        description: description || undefined,
        idempotencyKey,
      })
      clear()
      setOutcome({ kind: 'success', receipt })
      setStep('result')
    } catch (err) {
      if (err instanceof ApiError && err.status >= 400 && err.status < 500 && err.status !== 409) {
        clear()
        setOutcome({ kind: 'error', message: err.message })
      } else {
        setOutcome({ kind: 'unknown' })
      }
      setStep('result')
    } finally {
      setSubmitting(false)
    }
  }

  function startOver() {
    setStep('form')
    setDestinationAccountNumber('')
    setAmount('')
    setDescription('')
    setRecipient(null)
    setOutcome(null)
  }

  if (isLoading) return <LoadingState label="Loading your accounts…" />

  if (step === 'result' && outcome) {
    return (
      <div className="card">
        {outcome.kind === 'success' && (
          <div className="no-print-wrap">
            <div className="alert alert-success">Transfer completed.</div>
            <div className="stack" id="receipt">
              <h2>Receipt</h2>
              <p>
                <strong>Reference:</strong> {outcome.receipt.reference}
              </p>
              <p>
                <strong>From:</strong> {outcome.receipt.sourceAccountNumber}
              </p>
              <p>
                <strong>To:</strong> {outcome.receipt.destinationAccountNumber}
              </p>
              <p>
                <strong>Amount:</strong> {formatInr(outcome.receipt.amount)}
              </p>
              {outcome.receipt.sourceBalanceAfter && (
                <p>
                  <strong>Your new balance:</strong> {formatInr(outcome.receipt.sourceBalanceAfter)}
                </p>
              )}
            </div>
            <div className="row no-print" style={{ marginTop: 16 }}>
              <button type="button" className="btn btn-secondary" onClick={() => window.print()}>
                Print receipt
              </button>
              <button type="button" className="btn btn-primary" onClick={startOver}>
                Send another transfer
              </button>
            </div>
          </div>
        )}
        {outcome.kind === 'unknown' && (
          <>
            <div className="alert alert-warning">
              We couldn't confirm whether this transfer went through. Do not submit a new transfer with different details until
              this is resolved — click retry below, which is always safe and will never create a duplicate.
            </div>
            <button type="button" className="btn btn-primary" disabled={submitting} onClick={submitTransfer}>
              {submitting ? 'Retrying…' : 'Retry'}
            </button>
          </>
        )}
        {outcome.kind === 'error' && (
          <>
            <div className="alert alert-danger">{outcome.message}</div>
            <button type="button" className="btn btn-secondary" onClick={startOver}>
              Start over
            </button>
          </>
        )}
      </div>
    )
  }

  if (step === 'confirm' && recipient) {
    const sourceAccount = accounts?.find((a) => a.id === activeSourceAccountId)
    return (
      <div className="card">
        <h1>Confirm transfer</h1>
        <div className="stack">
          <p>
            <strong>From:</strong> {sourceAccount?.accountNumber} ({sourceAccount?.nickname})
          </p>
          <p>
            <strong>To:</strong> {recipient.accountNumber} — {recipient.displayName}
          </p>
          <p>
            <strong>Amount:</strong> {formatInr(amount)}
          </p>
          {description && (
            <p>
              <strong>Description:</strong> {description}
            </p>
          )}
        </div>
        <div className="row" style={{ marginTop: 16 }}>
          <button type="button" className="btn btn-secondary" onClick={() => setStep('form')} disabled={submitting}>
            Back
          </button>
          <button type="button" className="btn btn-primary" onClick={submitTransfer} disabled={submitting}>
            {submitting ? 'Sending…' : 'Confirm & send'}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="card">
      <h1>Transfer money</h1>
      <div className="field">
        <label htmlFor="source">From account</label>
        <select id="source" value={activeSourceAccountId ?? ''} onChange={(e) => setSourceAccountId(Number(e.target.value))}>
          {accounts?.map((a) => (
            <option key={a.id} value={a.id} disabled={a.status !== 'ACTIVE'}>
              {a.accountNumber} — {a.nickname} ({formatInr(a.balance)}){a.status !== 'ACTIVE' ? ` [${a.status}]` : ''}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label htmlFor="destination">Recipient account number</label>
        <input id="destination" required value={destinationAccountNumber} onChange={(e) => setDestinationAccountNumber(e.target.value)} />
      </div>
      <div className="field">
        <label htmlFor="amount">Amount (INR)</label>
        <input id="amount" type="number" min="0.01" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} />
      </div>
      <div className="field">
        <label htmlFor="description">Description (optional)</label>
        <input id="description" maxLength={255} value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      {lookupError && <div className="alert alert-danger">{lookupError}</div>}
      <button
        type="button"
        className="btn btn-primary"
        disabled={!activeSourceAccountId || !destinationAccountNumber || !amount || lookingUp}
        onClick={lookupRecipient}
      >
        {lookingUp ? 'Looking up recipient…' : 'Review transfer'}
      </button>
    </div>
  )
}
