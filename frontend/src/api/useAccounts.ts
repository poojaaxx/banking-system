import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiPost } from './client'
import type { Account, RecipientLookupResult } from './types'

export function useAccounts() {
  return useQuery({
    queryKey: ['accounts'],
    queryFn: () => apiGet<Account[]>('/api/customer/accounts'),
  })
}

export function useAccount(id: number | undefined) {
  return useQuery({
    queryKey: ['accounts', id],
    queryFn: () => apiGet<Account>(`/api/customer/accounts/${id}`),
    enabled: id !== undefined,
  })
}

export function useCreateAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (nickname: string) => apiPost<Account>('/api/customer/accounts', { nickname }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  })
}

export function useCloseAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (accountId: number) => apiPost<void>(`/api/customer/accounts/${accountId}/close`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  })
}

export function useRecipientLookup() {
  return useMutation({
    mutationFn: (accountNumber: string) =>
      apiGet<RecipientLookupResult>(`/api/customer/accounts/lookup?accountNumber=${encodeURIComponent(accountNumber)}`),
  })
}
