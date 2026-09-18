import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiDelete, apiGet, apiPost } from './client'
import type { Beneficiary } from './types'

export function useBeneficiaries() {
  return useQuery({
    queryKey: ['beneficiaries'],
    queryFn: () => apiGet<Beneficiary[]>('/api/customer/beneficiaries'),
  })
}

export function useAddBeneficiary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { accountNumber: string; nickname: string }) => apiPost<Beneficiary>('/api/customer/beneficiaries', input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['beneficiaries'] }),
  })
}

export function useRemoveBeneficiary() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiDelete<void>(`/api/customer/beneficiaries/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['beneficiaries'] }),
  })
}
