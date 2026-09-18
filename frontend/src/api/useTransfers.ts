import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { MoneyMovementReceipt } from './types'

function invalidateAfterMovement(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['accounts'] })
  queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  queryClient.invalidateQueries({ queryKey: ['transactions'] })
}

export function useDeposit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { accountId: number; amount: string; description?: string; idempotencyKey: string }) =>
      apiFetch<MoneyMovementReceipt>('/api/customer/deposits', {
        method: 'POST',
        body: { accountId: input.accountId, amount: input.amount, description: input.description },
        idempotencyKey: input.idempotencyKey,
      }),
    onSuccess: () => invalidateAfterMovement(queryClient),
  })
}

export function useWithdraw() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { accountId: number; amount: string; description?: string; idempotencyKey: string }) =>
      apiFetch<MoneyMovementReceipt>('/api/customer/withdrawals', {
        method: 'POST',
        body: { accountId: input.accountId, amount: input.amount, description: input.description },
        idempotencyKey: input.idempotencyKey,
      }),
    onSuccess: () => invalidateAfterMovement(queryClient),
  })
}

export function useTransfer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      sourceAccountId: number
      destinationAccountNumber: string
      amount: string
      description?: string
      categoryId?: number
      idempotencyKey: string
    }) =>
      apiFetch<MoneyMovementReceipt>('/api/customer/transfers', {
        method: 'POST',
        body: {
          sourceAccountId: input.sourceAccountId,
          destinationAccountNumber: input.destinationAccountNumber,
          amount: input.amount,
          description: input.description,
          categoryId: input.categoryId,
        },
        idempotencyKey: input.idempotencyKey,
      }),
    onSuccess: () => invalidateAfterMovement(queryClient),
  })
}
