import { useAdminDashboard } from '../../api/useAdmin'
import { ErrorBanner, LoadingState } from '../../components/States'
import { formatInr } from '../../lib/format'

export function AdminDashboardPage() {
  const { data, isLoading, isError, error, refetch } = useAdminDashboard()
  if (isLoading) return <LoadingState />
  if (isError) return <ErrorBanner error={error} onRetry={() => refetch()} />
  if (!data) return null

  const tiles = [
    { label: 'Customers', value: data.totalCustomers },
    { label: 'Customer accounts', value: data.totalCustomerAccounts },
    { label: 'Total customer balance', value: formatInr(data.totalCustomerBalance) },
    { label: 'Transactions', value: data.totalTransactions },
    { label: 'Open support tickets', value: data.openSupportTickets },
    { label: 'Unacknowledged alerts', value: data.unacknowledgedAlerts },
  ]

  return (
    <div className="stack">
      <h1>Admin dashboard</h1>
      <div className="grid grid-cols-3">
        {tiles.map((t) => (
          <div key={t.label} className="card" style={{ marginBottom: 0 }}>
            <div className="text-muted">{t.label}</div>
            <div style={{ fontSize: 28, fontWeight: 700 }}>{t.value}</div>
          </div>
        ))}
      </div>
    </div>
  )
}
