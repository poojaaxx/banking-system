import { useQuery } from '@tanstack/react-query'
import { apiGet } from './client'
import type { Insights, UnusualActivity } from './types'

export function useInsights() {
  return useQuery({ queryKey: ['insights'], queryFn: () => apiGet<Insights>('/api/customer/insights') })
}

export function useUnusualActivity() {
  return useQuery({
    queryKey: ['insights', 'unusual-activity'],
    queryFn: () => apiGet<UnusualActivity[]>('/api/customer/insights/alerts?limit=20'),
  })
}
