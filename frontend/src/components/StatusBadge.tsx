interface StatusBadgeProps {
  status: string
}

const CLASS_BY_STATUS: Record<string, string> = {
  ACTIVE: 'badge-active',
  FROZEN: 'badge-frozen',
  CLOSED: 'badge-closed',
  OPEN: 'badge-warning',
  IN_PROGRESS: 'badge-frozen',
  RESOLVED: 'badge-active',
  PENDING: 'badge-warning',
  ACCEPTED: 'badge-active',
  REJECTED: 'badge-danger',
  CANCELLED: 'badge-closed',
  COMPLETED: 'badge-active',
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const className = CLASS_BY_STATUS[status] ?? 'badge-closed'
  return <span className={`badge ${className}`}>{status.replace('_', ' ')}</span>
}
