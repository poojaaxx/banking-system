import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch, apiGet, apiPost } from './client'
import type { MoneyMovementReceipt, SavingsGoal } from './types'

export function useSavingsGoals() {
  return useQuery({ queryKey: ['savings-goals'], queryFn: () => apiGet<SavingsGoal[]>('/api/customer/savings-goals') })
}

function invalidate(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['savings-goals'] })
  queryClient.invalidateQueries({ queryKey: ['accounts'] })
  queryClient.invalidateQueries({ queryKey: ['dashboard'] })
}

export function useCreateSavingsGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { name: string; targetAmount: string; targetDate?: string }) => apiPost<SavingsGoal>('/api/customer/savings-goals', input),
    onSuccess: () => invalidate(queryClient),
  })
}

export function useContributeSavingsGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { goalId: number; sourceAccountId: number; amount: string; idempotencyKey: string }) =>
      apiFetch<MoneyMovementReceipt>(`/api/customer/savings-goals/${input.goalId}/contribute`, {
        method: 'POST',
        body: { sourceAccountId: input.sourceAccountId, amount: input.amount },
        idempotencyKey: input.idempotencyKey,
      }),
    onSuccess: () => invalidate(queryClient),
  })
}

export function useCloseSavingsGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (goalId: number) => apiPost<void>(`/api/customer/savings-goals/${goalId}/close`),
    onSuccess: () => invalidate(queryClient),
  })
}
