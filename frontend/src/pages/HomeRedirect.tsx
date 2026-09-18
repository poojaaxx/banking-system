import { Navigate } from 'react-router-dom'
import { useSession } from '../api/useAuth'
import { LoadingState } from '../components/States'

export function HomeRedirect() {
  const { data, isLoading } = useSession()
  if (isLoading) return <LoadingState />
  if (data?.role === 'ADMIN') return <Navigate to="/admin" replace />
  if (data?.role === 'CUSTOMER') return <Navigate to="/dashboard" replace />
  return <Navigate to="/login" replace />
}
