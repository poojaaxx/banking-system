import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch, apiGet } from './client'
import type { Biller, BillPayment, MoneyMovementReceipt, Page } from './types'

export function useBillers() {
  return useQuery({ queryKey: ['billers'], queryFn: () => apiGet<Biller[]>('/api/customer/bills/billers') })
}

export function useBillPaymentHistory(page = 0) {
  return useQuery({
    queryKey: ['bill-payments', page],
    queryFn: () => apiGet<Page<BillPayment>>(`/api/customer/bills/payments?page=${page}&size=20`),
  })
}

export function usePayBill() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { accountId: number; billerId: number; amount: string; referenceNote?: string; idempotencyKey: string }) =>
      apiFetch<MoneyMovementReceipt>('/api/customer/bills/pay', {
        method: 'POST',
        body: { accountId: input.accountId, billerId: input.billerId, amount: input.amount, referenceNote: input.referenceNote },
        idempotencyKey: input.idempotencyKey,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bill-payments'] })
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}
