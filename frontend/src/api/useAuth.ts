import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiPost } from './client'
import type { RegisterResponse, SessionResponse } from './types'

export const sessionQueryKey = ['session'] as const

export function useSession() {
  return useQuery({
    queryKey: sessionQueryKey,
    queryFn: () => apiGet<SessionResponse>('/api/auth/session'),
    staleTime: 30_000,
  })
}

export function useCustomerLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { username: string; password: string }) =>
      apiPost<SessionResponse>('/api/auth/customer/login', input),
    onSuccess: (data) => queryClient.setQueryData(sessionQueryKey, data),
  })
}

export function useCustomerRegister() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { fullName: string; email: string; username: string; password: string }) =>
      apiPost<RegisterResponse>('/api/auth/customer/register', input),
    onSuccess: (data) =>
      queryClient.setQueryData(sessionQueryKey, {
        authenticated: true,
        role: 'CUSTOMER',
        customer: data.customer,
        admin: null,
      } satisfies SessionResponse),
  })
}

export function useRecoveryLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { username: string; code: string }) =>
      apiPost<SessionResponse>('/api/auth/customer/recovery/login', input),
    onSuccess: (data) => queryClient.setQueryData(sessionQueryKey, data),
  })
}

export function useAdminLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { username: string; password: string }) =>
      apiPost<SessionResponse>('/api/auth/admin/login', input),
    onSuccess: (data) => queryClient.setQueryData(sessionQueryKey, data),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiPost<void>('/api/auth/logout'),
    onSuccess: () => {
      queryClient.setQueryData(sessionQueryKey, {
        authenticated: false,
        role: null,
        customer: null,
        admin: null,
      } satisfies SessionResponse)
      queryClient.clear()
    },
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (input: { currentPassword: string; newPassword: string }) =>
      apiPost<void>('/api/auth/customer/change-password', input),
  })
}

export function useRegenerateRecoveryCodes() {
  return useMutation({
    mutationFn: (input: { password: string }) =>
      apiPost<string[]>('/api/auth/customer/recovery/regenerate', input),
  })
}

export function useRecoveryStatus() {
  return useQuery({
    queryKey: ['recovery-status'],
    queryFn: () => apiGet<{ activeCodeCount: number }>('/api/auth/customer/recovery/status'),
  })
}
