import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiPost } from './client'
import type {
  AdminAccountSummary,
  AdminAlertSummary,
  AdminAuditLogSummary,
  AdminCustomerDetail,
  AdminCustomerSummary,
  AdminDashboardSummary,
  AdminTransactionDetail,
  AdminTransactionSummary,
  Page,
  SupportTicket,
  SupportTicketDetail,
  TicketStatus,
} from './types'

export function useAdminDashboard() {
  return useQuery({ queryKey: ['admin', 'dashboard'], queryFn: () => apiGet<AdminDashboardSummary>('/api/admin/dashboard') })
}

export function useAdminCustomers(page = 0) {
  return useQuery({
    queryKey: ['admin', 'customers', page],
    queryFn: () => apiGet<Page<AdminCustomerSummary>>(`/api/admin/customers?page=${page}&size=20`),
  })
}

export function useAdminCustomerDetail(id: number | undefined) {
  return useQuery({
    queryKey: ['admin', 'customers', 'detail', id],
    queryFn: () => apiGet<AdminCustomerDetail>(`/api/admin/customers/${id}`),
    enabled: id !== undefined,
  })
}

export function useAdminAccounts(status: string, page = 0) {
  return useQuery({
    queryKey: ['admin', 'accounts', status, page],
    queryFn: () => apiGet<Page<AdminAccountSummary>>(`/api/admin/accounts?${status ? `status=${status}&` : ''}page=${page}&size=20`),
  })
}

function invalidateAccounts(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['admin', 'accounts'] })
  queryClient.invalidateQueries({ queryKey: ['admin', 'customers'] })
}

export function useFreezeAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { id: number; reason: string }) => apiPost<AdminAccountSummary>(`/api/admin/accounts/${input.id}/freeze`, { reason: input.reason }),
    onSuccess: () => invalidateAccounts(queryClient),
  })
}

export function useUnfreezeAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { id: number; reason: string }) => apiPost<AdminAccountSummary>(`/api/admin/accounts/${input.id}/unfreeze`, { reason: input.reason }),
    onSuccess: () => invalidateAccounts(queryClient),
  })
}

export function useAdminTransactions(reference: string, page = 0) {
  return useQuery({
    queryKey: ['admin', 'transactions', reference, page],
    queryFn: () => apiGet<Page<AdminTransactionSummary>>(`/api/admin/transactions?${reference ? `reference=${encodeURIComponent(reference)}&` : ''}page=${page}&size=20`),
  })
}

export function useAdminTransactionDetail(id: number | undefined) {
  return useQuery({
    queryKey: ['admin', 'transactions', 'detail', id],
    queryFn: () => apiGet<AdminTransactionDetail>(`/api/admin/transactions/${id}`),
    enabled: id !== undefined,
  })
}

export function useAdminTickets(status: TicketStatus | '', page = 0) {
  return useQuery({
    queryKey: ['admin', 'support', status, page],
    queryFn: () => apiGet<Page<SupportTicket>>(`/api/admin/support/tickets?${status ? `status=${status}&` : ''}page=${page}&size=20`),
  })
}

export function useAdminTicketDetail(id: number | undefined) {
  return useQuery({
    queryKey: ['admin', 'support', 'detail', id],
    queryFn: () => apiGet<SupportTicketDetail>(`/api/admin/support/tickets/${id}`),
    enabled: id !== undefined,
  })
}

export function useAdminRespondToTicket(ticketId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { message: string; newStatus?: TicketStatus }) =>
      apiPost<void>(`/api/admin/support/tickets/${ticketId}/respond`, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'support'] }),
  })
}

export function useAdminAlerts(onlyUnacknowledged: boolean, page = 0) {
  return useQuery({
    queryKey: ['admin', 'alerts', onlyUnacknowledged, page],
    queryFn: () => apiGet<Page<AdminAlertSummary>>(`/api/admin/alerts?onlyUnacknowledged=${onlyUnacknowledged}&page=${page}&size=20`),
  })
}

export function useAcknowledgeAlert() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiPost<AdminAlertSummary>(`/api/admin/alerts/${id}/acknowledge`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'alerts'] }),
  })
}

export function useAdminAuditLog(targetType: string, targetId: number | undefined, page = 0) {
  return useQuery({
    queryKey: ['admin', 'audit', targetType, targetId, page],
    queryFn: () =>
      apiGet<Page<AdminAuditLogSummary>>(
        `/api/admin/audit?${targetType && targetId ? `targetType=${targetType}&targetId=${targetId}&` : ''}page=${page}&size=20`,
      ),
  })
}
