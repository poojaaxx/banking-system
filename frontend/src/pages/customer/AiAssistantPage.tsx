import { useState, type FormEvent } from 'react'
import { useAiStatus, useAskAssistant } from '../../api/useAi'
import { ErrorBanner } from '../../components/States'
import type { AssistantAskResponse } from '../../api/types'

interface ConversationEntry {
  question: string
  response: AssistantAskResponse
}

const EXAMPLE_QUESTIONS = ['How much did I spend this month?', 'Show my largest payments.', 'What are my account balances?']

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
        Answers are calculated directly from your own transaction records. This assistant is read-only: it cannot move
        money, change settings, or access any other customer's data.
      </p>
      {status && !status.aiAvailable && (
        <div className="alert alert-info">
          AI-generated natural-language answers are currently unavailable. You can still ask about your total spending,
          largest payments, or account balances — those are always answered directly by calculation.
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
                <div className="row-between" style={{ marginBottom: 6 }}>
                  <span className={`badge ${entry.response.aiGenerated ? 'badge-active' : 'badge-warning'}`}>
                    {entry.response.aiGenerated ? 'AI answer' : 'Calculated answer'}
                  </span>
                </div>
                <div>{entry.response.answer}</div>
                {entry.response.relatedTransactionReferences.length > 0 && (
                  <div className="row" style={{ flexWrap: 'wrap', marginTop: 8 }}>
                    {entry.response.relatedTransactionReferences.map((ref) => (
                      <span key={ref} className="badge badge-closed" title="Referenced transaction">
                        {ref}
                      </span>
                    ))}
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
