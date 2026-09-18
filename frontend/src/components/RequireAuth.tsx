import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useSession } from '../api/useAuth'
import { LoadingState } from './States'

export function RequireCustomer({ children }: { children: ReactNode }) {
  const { data, isLoading } = useSession()
  if (isLoading) return <LoadingState label="Checking your session…" />
  if (!data?.authenticated || data.role !== 'CUSTOMER') {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { data, isLoading } = useSession()
  if (isLoading) return <LoadingState label="Checking your session…" />
  if (!data?.authenticated || data.role !== 'ADMIN') {
    return <Navigate to="/admin/login" replace />
  }
  return <>{children}</>
}
