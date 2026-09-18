import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { useAccounts } from '../../api/useAccounts'
import { useCloseSavingsGoal, useContributeSavingsGoal, useCreateSavingsGoal, useSavingsGoals } from '../../api/useSavingsGoals'
import { usePendingOperation } from '../../lib/pendingOperation'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatInr } from '../../lib/format'

function ContributeForm({ goalId, defaultAccountId }: { goalId: number; defaultAccountId: number | undefined }) {
  const { data: accounts } = useAccounts()
  const contribute = useContributeSavingsGoal()
  const [sourceAccountId, setSourceAccountId] = useState<number | undefined>(defaultAccountId)
  const [amount, setAmount] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const { ensureKey, clear } = usePendingOperation(`contribute:${goalId}:${sourceAccountId}:${amount}`)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!sourceAccountId) return
    const idempotencyKey = ensureKey()
    try {
      await contribute.mutateAsync({ goalId, sourceAccountId, amount, idempotencyKey })
      clear()
      setMessage('Contribution added.')
      setAmount('')
    } catch (err) {
      if (err instanceof ApiError && err.status >= 400 && err.status < 500 && err.status !== 409) {
        clear()
        setMessage(err.message)
      } else {
        setMessage("Couldn't confirm — submit again to safely retry.")
      }
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="row" style={{ marginTop: 12 }}>
      <select aria-label="From account" value={sourceAccountId ?? ''} onChange={(e) => setSourceAccountId(Number(e.target.value))}>
        {accounts?.filter((a) => a.id !== defaultAccountId || true).map((a) => (
          <option key={a.id} value={a.id}>
            {a.accountNumber}
          </option>
        ))}
      </select>
      <input aria-label="Amount" type="number" min="0.01" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} style={{ width: 120 }} />
      <button type="submit" className="btn btn-secondary btn-sm" disabled={contribute.isPending}>
        Contribute
      </button>
      {message && <span className="text-muted">{message}</span>}
    </form>
  )
}

export function SavingsGoalsPage() {
  const { data: goals, isLoading, isError, error, refetch } = useSavingsGoals()
  const createGoal = useCreateSavingsGoal()
  const closeGoal = useCloseSavingsGoal()
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [targetAmount, setTargetAmount] = useState('')
  const [targetDate, setTargetDate] = useState('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await createGoal.mutateAsync({ name, targetAmount, targetDate: targetDate || undefined })
    setName('')
    setTargetAmount('')
    setTargetDate('')
    setShowForm(false)
  }

  return (
    <div className="stack">
      <div className="row-between">
        <h1 style={{ margin: 0 }}>Savings goals</h1>
        <button type="button" className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : 'New goal'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          {createGoal.isError && <ErrorBanner error={createGoal.error} />}
          <form onSubmit={onSubmit} noValidate>
            <div className="field">
              <label htmlFor="name">Goal name</label>
              <input id="name" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="targetAmount">Target amount</label>
              <input id="targetAmount" type="number" min="0.01" step="0.01" required value={targetAmount} onChange={(e) => setTargetAmount(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="targetDate">Target date (optional)</label>
              <input id="targetDate" type="date" value={targetDate} onChange={(e) => setTargetDate(e.target.value)} />
            </div>
            <button type="submit" className="btn btn-primary" disabled={createGoal.isPending}>
              Create goal
            </button>
          </form>
        </div>
      )}

      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {goals && goals.length === 0 && <EmptyState>No savings goals yet.</EmptyState>}
      {goals && goals.length > 0 && (
        <div className="grid grid-cols-2">
          {goals.map((g) => {
            const pct = Math.min(100, Math.round((Number(g.currentBalance) / Number(g.targetAmount)) * 100))
            return (
              <div key={g.id} className="card" style={{ marginBottom: 0 }}>
                <div className="row-between">
                  <strong>{g.name}</strong>
                  <StatusBadge status={g.status} />
                </div>
                <p>
                  {formatInr(g.currentBalance)} of {formatInr(g.targetAmount)}
                </p>
                <div style={{ background: 'var(--color-surface-alt)', borderRadius: 8, height: 8 }}>
                  <div style={{ width: `${pct}%`, background: 'var(--color-success)', height: 8, borderRadius: 8 }} />
                </div>
                {g.status === 'ACTIVE' && (
                  <>
                    <ContributeForm goalId={g.id} defaultAccountId={undefined} />
                    <button type="button" className="btn btn-secondary btn-sm" style={{ marginTop: 8 }} onClick={() => closeGoal.mutate(g.id)}>
                      Close goal
                    </button>
                  </>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
