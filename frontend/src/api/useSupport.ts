import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiPost } from './client'
import type { Page, SupportTicket, SupportTicketDetail } from './types'

export function useMyTickets(page = 0) {
  return useQuery({
    queryKey: ['support-tickets', page],
    queryFn: () => apiGet<Page<SupportTicket>>(`/api/customer/support/tickets?page=${page}&size=20`),
  })
}

export function useTicketDetail(id: number | undefined) {
  return useQuery({
    queryKey: ['support-tickets', 'detail', id],
    queryFn: () => apiGet<SupportTicketDetail>(`/api/customer/support/tickets/${id}`),
    enabled: id !== undefined,
  })
}

export function useCreateTicket() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { subject: string; message: string; relatedTransactionId?: number }) =>
      apiPost<SupportTicket>('/api/customer/support/tickets', input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['support-tickets'] }),
  })
}

export function useAddTicketMessage(ticketId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (message: string) => apiPost<void>(`/api/customer/support/tickets/${ticketId}/messages`, { message }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['support-tickets'] }),
  })
}
