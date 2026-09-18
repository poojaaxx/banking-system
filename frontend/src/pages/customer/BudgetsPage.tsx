import { useState, type FormEvent } from 'react'
import { useBudgetProgress, useSpendingCategories, useUpsertBudget } from '../../api/useBudgets'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { formatInr } from '../../lib/format'

function currentMonthStart(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`
}

export function BudgetsPage() {
  const month = currentMonthStart()
  const { data: categories } = useSpendingCategories()
  const { data: progress, isLoading, isError, error, refetch } = useBudgetProgress(month)
  const upsertBudget = useUpsertBudget()

  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [limitAmount, setLimitAmount] = useState('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!categoryId) return
    await upsertBudget.mutateAsync({ categoryId, monthStart: month, limitAmount })
    setLimitAmount('')
  }

  return (
    <div className="stack">
      <h1>Budgets — {month.slice(0, 7)}</h1>
      <div className="card">
        <h2>Set a monthly limit</h2>
        {upsertBudget.isError && <ErrorBanner error={upsertBudget.error} />}
        <form onSubmit={onSubmit} noValidate className="row">
          <div className="field" style={{ flex: 1 }}>
            <label htmlFor="category">Category</label>
            <select id="category" value={categoryId ?? ''} onChange={(e) => setCategoryId(Number(e.target.value))}>
              <option value="">Select a category</option>
              {categories?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field" style={{ flex: 1 }}>
            <label htmlFor="limit">Monthly limit</label>
            <input id="limit" type="number" min="0.01" step="0.01" required value={limitAmount} onChange={(e) => setLimitAmount(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-primary" disabled={upsertBudget.isPending || !categoryId}>
            Save
          </button>
        </form>
      </div>

      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {progress && progress.length === 0 && <EmptyState>No budgets or spending recorded yet this month.</EmptyState>}
      {progress && progress.length > 0 && (
        <div className="grid grid-cols-2">
          {progress.map((p) => {
            const spent = Number(p.spentAmount)
            const limit = p.limitAmount ? Number(p.limitAmount) : null
            const pct = limit ? Math.min(100, Math.round((spent / limit) * 100)) : null
            return (
              <div key={p.categoryId} className="card" style={{ marginBottom: 0 }}>
                <div className="row-between">
                  <strong>{p.categoryName}</strong>
                  <span className={limit && spent > limit ? 'text-danger' : ''}>
                    {formatInr(p.spentAmount)} {limit ? `/ ${formatInr(p.limitAmount!)}` : ''}
                  </span>
                </div>
                {pct !== null && (
                  <div style={{ background: 'var(--color-surface-alt)', borderRadius: 8, height: 8, marginTop: 8 }}>
                    <div
                      style={{
                        width: `${pct}%`,
                        background: pct >= 100 ? 'var(--color-danger)' : 'var(--color-primary)',
                        height: 8,
                        borderRadius: 8,
                      }}
                    />
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
