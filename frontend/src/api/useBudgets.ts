import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiPost } from './client'
import type { BudgetProgress, SpendingCategory } from './types'

export function useSpendingCategories() {
  return useQuery({ queryKey: ['spending-categories'], queryFn: () => apiGet<SpendingCategory[]>('/api/customer/budgets/categories') })
}

export function useBudgetProgress(month?: string) {
  return useQuery({
    queryKey: ['budgets', month],
    queryFn: () => apiGet<BudgetProgress[]>(`/api/customer/budgets${month ? `?month=${month}` : ''}`),
  })
}

export function useUpsertBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { categoryId: number; monthStart: string; limitAmount: string }) => apiPost<void>('/api/customer/budgets', input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
  })
}
