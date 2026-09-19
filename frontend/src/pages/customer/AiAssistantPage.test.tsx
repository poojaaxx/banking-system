import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AiAssistantPage } from './AiAssistantPage'
import type { AssistantAskResponse } from '../../api/types'

const mutateAsync = vi.fn()

vi.mock('../../api/useAi', () => ({
  useAiStatus: () => ({ data: { aiAvailable: false } }),
  useAskAssistant: () => ({ mutateAsync, isPending: false, isError: false, error: null }),
}))

const FIGURES = { asOfDate: '2026-09-19', spentThisMonth: '1500.00', spentLastMonth: '820.50', currency: 'INR' }

function respond(response: Partial<AssistantAskResponse>) {
  mutateAsync.mockResolvedValue({
    answer: 'answer text',
    relatedTransactionReferences: [],
    aiGenerated: false,
    fallbackReason: 'AI_NOT_AVAILABLE',
    verifiedFigures: FIGURES,
    relatedTransactions: [],
    ...response,
  })
}

async function ask(question: string) {
  fireEvent.change(screen.getByLabelText(/Ask a question/i), { target: { value: question } })
  fireEvent.click(screen.getByRole('button', { name: /^Ask$/ }))
  await waitFor(() => expect(screen.getByText(question)).toBeInTheDocument())
}

describe('AiAssistantPage', () => {
  beforeEach(() => mutateAsync.mockReset())

  it('says plainly that AI-written answers are unavailable and that calculated answers still work', () => {
    render(<AiAssistantPage />)
    expect(screen.getByText(/AI-written answers are currently unavailable/)).toBeInTheDocument()
  })

  it('labels a deterministic answer "Calculated answer", never as AI, and explains why', async () => {
    respond({ answer: 'You spent ₹1500.00 so far this month.' })
    render(<AiAssistantPage />)
    await ask('How much did I spend?')

    expect(await screen.findByText('Calculated answer')).toBeInTheDocument()
    expect(screen.queryByText(/AI answer/)).not.toBeInTheDocument()
    expect(screen.getByText(/AI is not available right now/)).toBeInTheDocument()
  })

  it('tells the customer when an AI reply was thrown away for not matching their records', async () => {
    respond({ fallbackReason: 'AI_ANSWER_REJECTED', answer: 'You spent ₹1500.00 so far this month.' })
    render(<AiAssistantPage />)
    await ask('How much did I spend?')

    expect(await screen.findByText('Calculated answer')).toBeInTheDocument()
    expect(screen.getByText(/did not match your records, so it was discarded/)).toBeInTheDocument()
  })

  it('labels a verified model answer as an AI answer and still renders figures from backend fields', async () => {
    respond({
      aiGenerated: true,
      fallbackReason: 'NONE',
      answer: 'So far this month you spent ₹1500.00.',
      relatedTransactionReferences: ['TXN-1'],
      relatedTransactions: [{ reference: 'TXN-1', amount: '1250.00', date: '2026-09-12', description: 'Laptop bag', category: 'Shopping' }],
    })
    render(<AiAssistantPage />)
    await ask('What did I spend?')

    expect(await screen.findByText(/AI answer · checked against your records/)).toBeInTheDocument()
    expect(screen.getByLabelText('Figures from your records')).toHaveTextContent(/1,500\.00/)
    expect(screen.getByText('TXN-1')).toBeInTheDocument()
    expect(screen.getByText('Laptop bag')).toBeInTheDocument()
    expect(screen.getByText(/1,250\.00/)).toBeInTheDocument()
  })
})
