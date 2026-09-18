import { useQuery } from '@tanstack/react-query'
import { apiGet } from './client'
import type { Page, TransactionHistoryRow, TransactionType } from './types'

export interface TransactionFilters {
  type?: TransactionType | ''
  from?: string
  to?: string
  minAmount?: string
  maxAmount?: string
  search?: string
  page?: number
  size?: number
}

export function useTransactionHistory(accountId: number | undefined, filters: TransactionFilters) {
  const params = new URLSearchParams()
  if (filters.type) params.set('type', filters.type)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.minAmount) params.set('minAmount', filters.minAmount)
  if (filters.maxAmount) params.set('maxAmount', filters.maxAmount)
  if (filters.search) params.set('search', filters.search)
  params.set('page', String(filters.page ?? 0))
  params.set('size', String(filters.size ?? 20))

  return useQuery({
    queryKey: ['transactions', accountId, filters],
    queryFn: () => apiGet<Page<TransactionHistoryRow>>(`/api/customer/accounts/${accountId}/transactions?${params}`),
    enabled: accountId !== undefined,
  })
}

export function statementCsvUrl(accountId: number): string {
  return `/api/customer/accounts/${accountId}/statement.csv`
}
