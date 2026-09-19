const inrFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatInr(amount: string | number): string {
  const value = typeof amount === 'string' ? Number(amount) : amount
  if (Number.isNaN(value)) return amount.toString()
  return inrFormatter.format(value)
}

const dateTimeFormatter = new Intl.DateTimeFormat('en-IN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const dateFormatter = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' })

/** Backend timestamps are always UTC ISO-8601; formatted here in the viewer's local time zone. */
export function formatDateTime(iso: string): string {
  return dateTimeFormatter.format(new Date(iso))
}

export function formatDate(iso: string): string {
  return dateFormatter.format(new Date(iso))
}

const monthFormatter = new Intl.DateTimeFormat('en-IN', { month: 'short', year: 'numeric', timeZone: 'UTC' })
const isoDateFormatter = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeZone: 'UTC' })

/** "2027-02" -> "Feb 2027". Calendar months from the backend are UTC and must not shift with the viewer's time zone. */
export function formatMonth(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number)
  return monthFormatter.format(new Date(Date.UTC(year, month - 1, 1)))
}

/** "2026-09-19" -> "19 Sept 2026" (a calendar date, not an instant, so no time-zone shifting). */
export function formatIsoDate(isoDate: string): string {
  return isoDateFormatter.format(new Date(isoDate.slice(0, 10) + 'T00:00:00Z'))
}
