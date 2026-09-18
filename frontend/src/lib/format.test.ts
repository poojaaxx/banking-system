import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, formatInr } from './format'

describe('formatInr', () => {
  it('formats a whole rupee amount with the currency symbol and two decimals', () => {
    expect(formatInr('10000')).toBe('₹10,000.00')
  })

  it('formats a decimal-string amount using Indian digit grouping', () => {
    expect(formatInr('123456.7')).toBe('₹1,23,456.70')
  })

  it('formats a numeric amount', () => {
    expect(formatInr(0)).toBe('₹0.00')
  })

  it('falls back to the raw string for a non-numeric value instead of throwing', () => {
    expect(formatInr('not-a-number')).toBe('not-a-number')
  })
})

describe('formatDateTime / formatDate', () => {
  it('renders a valid date and time for an ISO timestamp', () => {
    const result = formatDateTime('2026-01-15T10:30:00Z')
    expect(result).toMatch(/2026/)
  })

  it('renders a valid date for an ISO timestamp', () => {
    const result = formatDate('2026-01-15T10:30:00Z')
    expect(result).toMatch(/2026/)
  })
})
