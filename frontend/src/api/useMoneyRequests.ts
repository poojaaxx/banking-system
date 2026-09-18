import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch, apiGet, apiPost } from './client'
import type { MoneyMovementReceipt, MoneyRequest, Page } from './types'

export function useIncomingRequests(page = 0) {
  return useQuery({
    queryKey: ['money-requests', 'incoming', page],
    queryFn: () => apiGet<Page<MoneyRequest>>(`/api/customer/money-requests/incoming?page=${page}&size=20`),
  })
}

export function useOutgoingRequests(page = 0) {
  return useQuery({
    queryKey: ['money-requests', 'outgoing', page],
    queryFn: () => apiGet<Page<MoneyRequest>>(`/api/customer/money-requests/outgoing?page=${page}&size=20`),
  })
}

function invalidateRequests(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['money-requests'] })
  queryClient.invalidateQueries({ queryKey: ['accounts'] })
  queryClient.invalidateQueries({ queryKey: ['dashboard'] })
}

export function useCreateMoneyRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { requesterAccountId: number; payerAccountNumber: string; amount: string; note?: string }) =>
      apiPost<MoneyRequest>('/api/customer/money-requests', input),
    onSuccess: () => invalidateRequests(queryClient),
  })
}

export function useAcceptMoneyRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { id: number; payerAccountId?: number; idempotencyKey: string }) =>
      apiFetch<MoneyMovementReceipt>(`/api/customer/money-requests/${input.id}/accept`, {
        method: 'POST',
        body: input.payerAccountId ? { payerAccountId: input.payerAccountId } : {},
        idempotencyKey: input.idempotencyKey,
      }),
    onSuccess: () => invalidateRequests(queryClient),
  })
}

export function useRejectMoneyRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiPost<void>(`/api/customer/money-requests/${id}/reject`),
    onSuccess: () => invalidateRequests(queryClient),
  })
}

export function useCancelMoneyRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiPost<void>(`/api/customer/money-requests/${id}/cancel`),
    onSuccess: () => invalidateRequests(queryClient),
  })
}
