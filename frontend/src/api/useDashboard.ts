import { useQuery } from '@tanstack/react-query'
import { apiGet } from './client'
import type { CustomerDashboard } from './types'

export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: () => apiGet<CustomerDashboard>('/api/customer/dashboard'),
  })
}
