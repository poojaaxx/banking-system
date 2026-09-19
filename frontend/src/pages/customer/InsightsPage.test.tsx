import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InsightsPage } from './InsightsPage'
import type { Insights, UnusualActivity } from '../../api/types'

const insightsHook = vi.fn()
const unusualHook = vi.fn()

vi.mock('../../api/useInsights', () => ({
  useInsights: () => insightsHook(),
  useUnusualActivity: () => unusualHook(),
}))

const BASE: Insights = {
  generatedAt: '2026-09-19T12:00:00Z',
  asOfDate: '2026-09-19',
  currency: 'INR',
  observed: {
    monthStart: '2026-09-01',
    daysElapsed: 19,
    daysInMonth: 30,
    paymentCount: 4,
    grossSpent: '2000.00',
    refundsNetted: '500.00',
    netSpent: '1500.00',
    byCategory: [{ categoryId: 5, name: 'Transport', amount: '1200.00' }],
    uncategorized: '800.00',
  },
  projection: {
    status: 'INSUFFICIENT_HISTORY',
    reason: 'Only 3 day(s) of history so far; at least 14 are needed.',
    projectedMonthEnd: null,
    rangeLow: null,
    rangeHigh: null,
    basis: null,
    assumptions: [],
  },
  budgets: [],
  goals: [],
  methodology: ['Observed figures are what has already happened this UTC calendar month.'],
}

function withInsights(data: Insights, unusual: UnusualActivity[] = []) {
  insightsHook.mockReturnValue({ isLoading: false, isError: false, data, refetch: vi.fn() })
  unusualHook.mockReturnValue({ isLoading: false, isError: false, data: unusual, refetch: vi.fn() })
}

function renderPage() {
  return render(
    <MemoryRouter>
      <InsightsPage />
    </MemoryRouter>,
  )
}

describe('InsightsPage', () => {
  beforeEach(() => {
    insightsHook.mockReset()
    unusualHook.mockReset()
  })

  it('shows "Insufficient history" and no projected figure when the history does not support a projection', () => {
    withInsights(BASE)
    renderPage()

    const projection = screen.getByRole('region', { name: /Projected month-end spending/i })
    expect(within(projection).getByText('Insufficient history')).toBeInTheDocument()
    expect(within(projection).getByText(/at least 14 are needed/)).toBeInTheDocument()
    expect(projection.querySelector('.kpi')).toBeNull()
    expect(within(projection).queryByText(/₹/)).toBeNull()
  })

  it('keeps observed figures and projections in separate, clearly labelled sections', () => {
    withInsights({
      ...BASE,
      projection: {
        status: 'OK',
        reason: null,
        projectedMonthEnd: '6100.00',
        rangeLow: '5900.00',
        rangeHigh: '6300.00',
        basis: {
          windowStart: '2026-06-01',
          windowDays: 75,
          paymentsInWindow: 75,
          dailyRate: '200.00',
          weeklyMedianDailyRate: '195.00',
          largestPaymentCap: null,
          cappedOneOffs: false,
          remainingDaysInMonth: '11.50',
        },
        assumptions: ['Your spending for the rest of the month is similar to your typical day over the last 90 days.'],
      },
    })
    renderPage()

    const observed = screen.getByRole('region', { name: /This month so far/i })
    expect(within(observed).getByText('Observed')).toBeInTheDocument()
    expect(within(observed).getByLabelText('Net spent this month')).toHaveTextContent(/1,500\.00/)
    expect(within(observed).queryByText(/Projection/)).not.toBeInTheDocument()

    const projection = screen.getByRole('region', { name: /Projected month-end spending/i })
    expect(within(projection).getByText('Projection (estimate)')).toBeInTheDocument()
    expect(within(projection).getByLabelText('Projected month-end spending')).toHaveTextContent(/6,100\.00/)
    expect(within(projection).getByText(/not a statistical confidence interval/i)).toBeInTheDocument()
    expect(within(projection).getByText(/not guarantees/i)).toBeInTheDocument()
    expect(within(projection).getByText(/similar to your typical day/)).toBeInTheDocument()
  })

  it('never displays a confidence percentage anywhere', () => {
    withInsights(BASE)
    const { container } = renderPage()
    expect(container.textContent).not.toMatch(/\d+\s*%\s*(confidence|likely|chance|probab)/i)
    expect(container.textContent).not.toMatch(/(confidence|likelihood|probability)\s*(of|:)?\s*\d+/i)
  })

  it('labels unusual activity as a statistical check or rule and never as fraud', () => {
    withInsights(BASE, [
      {
        id: 1,
        ruleCode: 'UNUSUAL_LARGE_SPEND',
        label: 'Unusually large payment (statistical check)',
        explanation: 'Unusual activity (statistical check): a payment of ₹9000.00 is well above your usual range. Nothing was blocked.',
        transactionReference: 'TXN-1',
        createdAt: '2026-09-19T10:00:00Z',
      },
    ])
    renderPage()

    const section = screen.getByRole('region', { name: /Unusual activity/i })
    expect(within(section).getByText(/Unusual activity · Unusually large payment/)).toBeInTheDocument()
    expect(within(section).getByText(/not fraud\s+detection/i)).toBeInTheDocument()
    expect(within(section).getByText(/nothing is ever blocked or frozen automatically/i)).toBeInTheDocument()
    expect(within(section).queryByText(/fraud alert|confirmed fraud|fraudulent/i)).not.toBeInTheDocument()
  })

  it('shows an empty state when nothing unusual has been noticed', () => {
    withInsights(BASE, [])
    renderPage()
    expect(screen.getByText('Nothing unusual noticed.')).toBeInTheDocument()
  })

  it('shows budget estimates with honest statuses', () => {
    withInsights({
      ...BASE,
      budgets: [
        { categoryId: 1, category: 'Dining & Food', limit: '3000.00', spent: '1400.00', status: 'PROJECTED_OVER', projectedMonthEnd: '3050.00', estimatedOverrun: '50.00', reason: null },
        { categoryId: 2, category: 'Utilities', limit: '400.00', spent: '50.00', status: 'INSUFFICIENT_HISTORY', projectedMonthEnd: null, estimatedOverrun: null, reason: 'Only 2 payment(s) in the window; at least 3 are needed.' },
      ],
    })
    renderPage()

    expect(screen.getByText('Projected to exceed')).toBeInTheDocument()
    expect(screen.getByText(/Estimated overrun/)).toBeInTheDocument()
    const utilitiesRow = screen.getByText('Utilities').closest('tr') as HTMLElement
    expect(within(utilitiesRow).getByText('Insufficient history')).toBeInTheDocument()
    expect(within(utilitiesRow).queryByText(/Projected month-end/)).not.toBeInTheDocument()
  })

  it('projects a goal only when supported, and says so plainly when there is no positive trend', () => {
    withInsights({
      ...BASE,
      goals: [
        {
          goalId: 1, name: 'Laptop', goalStatus: 'ACTIVE', target: '10000.00', saved: '4000.00', remaining: '6000.00', percentComplete: '40.0',
          targetDate: '2027-01-15', requiredMonthlyForTargetDate: '1300.00',
          projection: {
            status: 'OK', reason: null, averageMonthlyContribution: '1000.00', completeMonthsUsed: 3, estimatedCompletionMonth: '2027-02',
            onPaceForTargetDate: false, assumptions: ['This is an estimate, not a guaranteed date.'],
          },
        },
        {
          goalId: 2, name: 'Trip', goalStatus: 'ACTIVE', target: '5000.00', saved: '0.00', remaining: '5000.00', percentComplete: '0.0',
          targetDate: null, requiredMonthlyForTargetDate: null,
          projection: {
            status: 'NO_POSITIVE_TREND', reason: 'Net contributions over the last 3 complete months were not positive, so no completion date is estimated.',
            averageMonthlyContribution: '0.00', completeMonthsUsed: 3, estimatedCompletionMonth: null, onPaceForTargetDate: null, assumptions: [],
          },
        },
      ],
    })
    renderPage()

    const goals = screen.getByRole('region', { name: /Savings goals/i })
    expect(within(goals).getByRole('progressbar', { name: /Laptop progress/ })).toHaveAttribute('aria-valuenow', '40')
    expect(within(goals).getByText(/could be reached around/)).toHaveTextContent(/Feb 2027/)
    expect(within(goals).getByText(/behind the pace needed/)).toBeInTheDocument()
    expect(within(goals).getByText(/not a guaranteed date/)).toBeInTheDocument()

    expect(within(goals).getByText('No positive contribution trend')).toBeInTheDocument()
    expect(within(goals).getByText(/no completion date is estimated/)).toBeInTheDocument()
    expect(within(goals).getAllByText(/could be reached around/)).toHaveLength(1)
  })
})
