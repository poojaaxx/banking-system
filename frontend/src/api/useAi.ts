import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch, apiGet, apiPost } from './client'
import type { AiStatus, AssistantAskResponse, CategorySuggestion } from './types'

export function useAiStatus() {
  return useQuery({
    queryKey: ['ai-status'],
    queryFn: () => apiGet<AiStatus>('/api/customer/ai/status'),
    staleTime: 30_000,
  })
}

export function useAskAssistant() {
  return useMutation({
    mutationFn: (question: string) => apiPost<AssistantAskResponse>('/api/customer/ai/assistant', { question }),
  })
}

export function useAiCategorySuggestion() {
  return useMutation({
    mutationFn: (ledgerEntryId: number) =>
      apiPost<CategorySuggestion>(`/api/customer/ledger-entries/${ledgerEntryId}/category-suggestion/ai`),
  })
}

export function useRecategorize(accountId: number | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { ledgerEntryId: number; categoryId: number; acceptedFrom?: 'MANUAL' | 'RULE_BASED' | 'AI' }) =>
      apiFetch<void>(`/api/customer/ledger-entries/${input.ledgerEntryId}/category`, {
        method: 'PATCH',
        body: { categoryId: input.categoryId, acceptedFrom: input.acceptedFrom ?? 'MANUAL' },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['transactions', accountId] }),
  })
}
