import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { DepositWithdrawForm } from './DepositWithdrawForm'
import type { MoneyMovementReceipt } from '../api/types'

const RECEIPT: MoneyMovementReceipt = {
  financialTransactionId: 1,
  reference: 'TXN-abc',
  type: 'DEPOSIT',
  amount: '100.00',
  sourceAccountNumber: '900000000001',
  destinationAccountNumber: '123456789012',
  sourceBalanceAfter: null,
  destinationBalanceAfter: '100.00',
  description: null,
  createdAt: '2026-01-01T00:00:00Z',
}

function fillAndSubmit() {
  fireEvent.change(screen.getByLabelText(/Amount/i), { target: { value: '100' } })
  fireEvent.click(screen.getByRole('button', { name: /Confirm deposit|Retry/i }))
}

describe('DepositWithdrawForm', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('shows the receipt and clears the pending key on success', async () => {
    const mutateAsync = vi.fn().mockResolvedValue(RECEIPT)
    const onDone = vi.fn()
    render(<DepositWithdrawForm accountId={42} kind="deposit" mutateAsync={mutateAsync} onDone={onDone} />)

    fillAndSubmit()

    await waitFor(() => expect(screen.getByText(/Deposit of/)).toBeInTheDocument())
    expect(localStorage.getItem('pending-op:deposit:42')).toBeNull()
    expect(mutateAsync).toHaveBeenCalledTimes(1)
    expect(mutateAsync.mock.calls[0][0].idempotencyKey).toBeTruthy()
  })

  it('on an ambiguous (network-like) failure, keeps the SAME idempotency key for the retry', async () => {
    const mutateAsync = vi.fn().mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(RECEIPT)
    render(<DepositWithdrawForm accountId={42} kind="deposit" mutateAsync={mutateAsync} onDone={vi.fn()} />)

    fillAndSubmit()
    await waitFor(() => expect(screen.getByText(/couldn't confirm/i)).toBeInTheDocument())

    const firstKey = mutateAsync.mock.calls[0][0].idempotencyKey
    expect(localStorage.getItem('pending-op:deposit:42')).toBe(firstKey)

    // Retry -- must reuse the exact same key, never mint a new one.
    fireEvent.click(screen.getByRole('button', { name: /Retry/i }))
    await waitFor(() => expect(screen.getByText(/Deposit of/)).toBeInTheDocument())

    expect(mutateAsync).toHaveBeenCalledTimes(2)
    const secondKey = mutateAsync.mock.calls[1][0].idempotencyKey
    expect(secondKey).toBe(firstKey)
  })

  it('on a clean business rejection (4xx), clears the key so a future attempt can start fresh', async () => {
    const mutateAsync = vi.fn().mockRejectedValue(new ApiError(400, null, 'Insufficient funds'))
    render(<DepositWithdrawForm accountId={42} kind="withdrawal" mutateAsync={mutateAsync} onDone={vi.fn()} />)

    fireEvent.change(screen.getByLabelText(/Amount/i), { target: { value: '999999' } })
    fireEvent.click(screen.getByRole('button', { name: /Confirm withdrawal/i }))

    await waitFor(() => expect(screen.getByText('Insufficient funds')).toBeInTheDocument())
    expect(localStorage.getItem('pending-op:withdrawal:42')).toBeNull()
  })

  it('does not treat a 409 (already in progress) as a clean rejection -- keeps the key', async () => {
    const mutateAsync = vi.fn().mockRejectedValue(new ApiError(409, null, 'This operation is already being processed'))
    render(<DepositWithdrawForm accountId={7} kind="deposit" mutateAsync={mutateAsync} onDone={vi.fn()} />)

    fillAndSubmit()

    await waitFor(() => expect(screen.getByText(/couldn't confirm/i)).toBeInTheDocument())
    expect(localStorage.getItem('pending-op:deposit:7')).toBeTruthy()
  })
})
