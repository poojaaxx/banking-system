import { useState } from 'react'
import { useAiCategorySuggestion, useRecategorize } from '../api/useAi'
import { useSpendingCategories } from '../api/useBudgets'
import type { TransactionHistoryRow } from '../api/types'

interface Suggestion {
  code: string
  name: string
  source: 'RULE_BASED' | 'AI'
}

/**
 * Shows the current category (click to change), or, for an uncategorized
 * spending row, a suggestion the customer can accept or override. The
 * rule-based suggestion (if any) comes pre-computed with the transaction row;
 * "Ask AI" is a single on-demand call, never automatic, so a page of
 * transactions never triggers a burst of AI requests.
 */
export function CategorySuggestionCell({ tx, accountId }: { tx: TransactionHistoryRow; accountId: number }) {
  const { data: categories } = useSpendingCategories()
  const recategorize = useRecategorize(accountId)
  const aiSuggest = useAiCategorySuggestion()
  const [aiSuggestion, setAiSuggestion] = useState<Suggestion | null>(null)
  const [aiError, setAiError] = useState(false)
  const [editing, setEditing] = useState(false)

  if (tx.direction !== 'DEBIT') {
    return <span className="text-muted">—</span>
  }

  const currentCategoryName = tx.categoryId ? categories?.find((c) => c.id === tx.categoryId)?.name : null

  function accept(suggestion: Suggestion) {
    const category = categories?.find((c) => c.code === suggestion.code)
    if (category) {
      recategorize.mutate({ ledgerEntryId: tx.id, categoryId: category.id, acceptedFrom: suggestion.source })
    }
  }

  function pick(categoryId: number) {
    recategorize.mutate({ ledgerEntryId: tx.id, categoryId, acceptedFrom: 'MANUAL' })
    setEditing(false)
  }

  if (currentCategoryName && !editing) {
    return (
      <button type="button" className="badge badge-active" style={{ border: 'none', cursor: 'pointer' }} onClick={() => setEditing(true)}>
        {currentCategoryName}
      </button>
    )
  }

  const suggestion: Suggestion | null =
    aiSuggestion ?? (!editing && tx.suggestedCategoryCode && tx.suggestedCategoryName
      ? { code: tx.suggestedCategoryCode, name: tx.suggestedCategoryName, source: 'RULE_BASED' }
      : null)

  return (
    <div className="stack" style={{ gap: 4 }}>
      {suggestion && (
        <div className="row" style={{ gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
          <span className="badge badge-warning" title={suggestion.source === 'AI' ? 'Model-generated prediction' : 'Rule-based keyword match'}>
            {suggestion.source === 'AI' ? 'AI suggestion' : 'Suggested'}: {suggestion.name}
          </span>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => accept(suggestion)} disabled={recategorize.isPending}>
            Accept
          </button>
        </div>
      )}
      <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
        {!aiSuggestion && (
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            disabled={aiSuggest.isPending}
            onClick={async () => {
              setAiError(false)
              try {
                const result = await aiSuggest.mutateAsync(tx.id)
                setAiSuggestion({ code: result.categoryCode, name: result.categoryName, source: 'AI' })
              } catch {
                setAiError(true)
              }
            }}
          >
            {aiSuggest.isPending ? 'Asking AI…' : 'Ask AI'}
          </button>
        )}
        <select
          aria-label="Choose category"
          defaultValue=""
          onChange={(e) => {
            if (e.target.value) pick(Number(e.target.value))
          }}
        >
          <option value="" disabled>
            Pick category…
          </option>
          {categories?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>
      {aiError && <span className="text-muted" style={{ fontSize: 12 }}>AI suggestion unavailable right now.</span>}
    </div>
  )
}
