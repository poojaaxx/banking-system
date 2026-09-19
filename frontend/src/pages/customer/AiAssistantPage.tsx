import { useState, type FormEvent } from 'react'
import { useAiStatus, useAskAssistant } from '../../api/useAi'
import { ErrorBanner } from '../../components/States'
import { formatInr, formatIsoDate } from '../../lib/format'
import type { AssistantAskResponse, AssistantFallbackReason } from '../../api/types'

interface ConversationEntry {
  question: string
  response: AssistantAskResponse
}

const EXAMPLE_QUESTIONS = ['How much did I spend this month?', 'Show my largest payments.', 'What are my account balances?']

const FALLBACK_EXPLANATION: Record<AssistantFallbackReason, string> = {
  NONE: '',
  AI_NOT_AVAILABLE: 'AI is not available right now, so this answer was calculated directly from your records.',
  AI_ANSWER_REJECTED: 'The AI reply did not match your records, so it was discarded and this calculated answer is shown instead.',
  AI_ERROR: 'The AI reply could not be used, so this answer was calculated directly from your records.',
}

export function AiAssistantPage() {
  const { data: status } = useAiStatus()
  const ask = useAskAssistant()
  const [question, setQuestion] = useState('')
  const [conversation, setConversation] = useState<ConversationEntry[]>([])

  async function submit(q: string) {
    const trimmed = q.trim()
    if (!trimmed) return
    const response = await ask.mutateAsync(trimmed)
    setConversation((prev) => [...prev, { question: trimmed, response }])
    setQuestion('')
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    void submit(question)
  }

  return (
    <div className="stack">
      <h1>Assistant</h1>
      <p className="text-muted">
        Read-only: it can look at your own records but cannot move money, change settings, or see anyone else's data. Every amount
        shown under an answer comes straight from your records, not from the AI.
      </p>
      {status && !status.aiAvailable && (
        <div className="alert alert-info">
          AI-written answers are currently unavailable. You can still ask about your spending, largest payments or balances; those
          are calculated directly and labelled "Calculated answer".
        </div>
      )}

      <div className="card">
        <div className="row" style={{ flexWrap: 'wrap', marginBottom: 12 }}>
          {EXAMPLE_QUESTIONS.map((q) => (
            <button key={q} type="button" className="btn btn-secondary btn-sm" onClick={() => submit(q)} disabled={ask.isPending}>
              {q}
            </button>
          ))}
        </div>

        <div className="stack" style={{ gap: 16, marginBottom: 16 }}>
          {conversation.map((entry, i) => (
            <div key={i} className="stack" style={{ gap: 6 }}>
              <div style={{ fontWeight: 600 }}>{entry.question}</div>
              <div className="alert alert-info" style={{ margin: 0 }}>
                <div style={{ marginBottom: 6 }}>
                  <span className={`badge ${entry.response.aiGenerated ? 'badge-active' : 'badge-warning'}`}>
                    {entry.response.aiGenerated ? 'AI answer · checked against your records' : 'Calculated answer'}
                  </span>
                </div>
                <div>{entry.response.answer}</div>
                {!entry.response.aiGenerated && FALLBACK_EXPLANATION[entry.response.fallbackReason] && (
                  <div className="fine-print" style={{ marginTop: 6 }}>{FALLBACK_EXPLANATION[entry.response.fallbackReason]}</div>
                )}

                {entry.response.verifiedFigures && (
                  <dl className="fine-print" style={{ margin: '10px 0 0' }} aria-label="Figures from your records">
                    <div>
                      <dt style={{ display: 'inline' }}>From your records, spent this month: </dt>
                      <dd style={{ display: 'inline', margin: 0 }}>{formatInr(entry.response.verifiedFigures.spentThisMonth)}</dd>
                    </div>
                    <div>
                      <dt style={{ display: 'inline' }}>Spent last month: </dt>
                      <dd style={{ display: 'inline', margin: 0 }}>{formatInr(entry.response.verifiedFigures.spentLastMonth)}</dd>
                    </div>
                  </dl>
                )}

                {entry.response.relatedTransactions.length > 0 && (
                  <div className="table-wrap" style={{ marginTop: 8 }}>
                    <table>
                      <thead>
                        <tr>
                          <th>Date</th>
                          <th>Reference</th>
                          <th>Description</th>
                          <th>Amount</th>
                        </tr>
                      </thead>
                      <tbody>
                        {entry.response.relatedTransactions.map((t) => (
                          <tr key={t.reference}>
                            <td>{formatIsoDate(t.date)}</td>
                            <td>{t.reference}</td>
                            <td>{t.description || '—'}</td>
                            <td>{formatInr(t.amount)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>

        {ask.isError && <ErrorBanner error={ask.error} />}

        <form onSubmit={onSubmit} className="row">
          <input
            aria-label="Ask a question about your spending"
            placeholder="Ask about your spending…"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            maxLength={500}
            style={{ flex: 1 }}
          />
          <button type="submit" className="btn btn-primary" disabled={ask.isPending || !question.trim()}>
            {ask.isPending ? 'Asking…' : 'Ask'}
          </button>
        </form>
      </div>
    </div>
  )
}
