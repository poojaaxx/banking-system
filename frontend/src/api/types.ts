export interface CustomerSessionInfo {
  id: number
  username: string
  fullName: string
  email: string
}

export interface AdminSessionInfo {
  id: number
  username: string
}

export interface SessionResponse {
  authenticated: boolean
  role: 'CUSTOMER' | 'ADMIN' | null
  customer: CustomerSessionInfo | null
  admin: AdminSessionInfo | null
}

export interface RegisterResponse {
  customer: CustomerSessionInfo
  recoveryCodes: string[]
}

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED'

export interface Account {
  id: number
  accountNumber: string
  nickname: string | null
  status: AccountStatus
  balance: string
  currency: string
  createdAt: string
  closedAt: string | null
}

export interface RecipientLookupResult {
  accountNumber: string
  displayName: string
  nickname: string | null
}

export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER' | 'BILL_PAYMENT'
export type LedgerDirection = 'DEBIT' | 'CREDIT'

export interface MoneyMovementReceipt {
  financialTransactionId: number
  reference: string
  type: TransactionType
  amount: string
  sourceAccountNumber: string
  destinationAccountNumber: string
  sourceBalanceAfter: string | null
  destinationBalanceAfter: string | null
  description: string | null
  createdAt: string
}

export interface TransactionHistoryRow {
  id: number
  reference: string
  type: TransactionType
  direction: LedgerDirection
  amount: string
  balanceAfter: string
  description: string | null
  categoryId: number | null
  createdAt: string
  counterpartyAccountNumber: string | null
  counterpartyDisplayName: string | null
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface CustomerDashboard {
  totalBalance: string
  accounts: Account[]
  recentTransactions: TransactionHistoryRow[]
  unreadNotifications: number
}

export interface Beneficiary {
  id: number
  accountNumber: string
  nickname: string
}

export interface AppNotification {
  id: number
  type: string
  title: string
  body: string
  relatedEntityType: string | null
  relatedEntityId: number | null
  read: boolean
  createdAt: string
}

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

export interface SupportTicket {
  id: number
  customerId: number
  subject: string
  status: TicketStatus
  relatedTransactionId: number | null
  createdAt: string
  updatedAt: string
}

export interface SupportMessage {
  id: number
  senderType: 'CUSTOMER' | 'ADMIN'
  senderId: number
  body: string
  createdAt: string
}

export interface SupportTicketDetail {
  ticket: SupportTicket
  messages: SupportMessage[]
}

export type MoneyRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED'

export interface MoneyRequest {
  id: number
  requesterCustomerId: number
  requesterAccountId: number
  payerCustomerId: number
  amount: string
  note: string | null
  status: MoneyRequestStatus
  financialTransactionId: number | null
  createdAt: string
  respondedAt: string | null
}

export interface Biller {
  id: number
  code: string
  name: string
}

export interface BillPayment {
  id: number
  financialTransactionId: number
  billerId: number
  accountId: number
  amount: string
  referenceNote: string | null
  createdAt: string
}

export interface SpendingCategory {
  id: number
  code: string
  name: string
}

export interface BudgetProgress {
  categoryId: number
  categoryName: string
  limitAmount: string | null
  spentAmount: string
}

export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'CLOSED'

export interface SavingsGoal {
  id: number
  name: string
  targetAmount: string
  targetDate: string | null
  linkedAccountId: number
  linkedAccountNumber: string
  currentBalance: string
  status: GoalStatus
}

// --- Admin ---

export interface AdminCustomerSummary {
  id: number
  fullName: string
  email: string
  username: string
  status: string
  createdAt: string
}

export interface AdminAccountSummary {
  id: number
  accountNumber: string
  ownerCustomerId: number
  nickname: string | null
  status: AccountStatus
  balance: string
  frozenReason: string | null
  createdAt: string
}

export interface AdminCustomerDetail {
  customer: AdminCustomerSummary
  accounts: AdminAccountSummary[]
}

export interface AdminTransactionSummary {
  id: number
  reference: string
  type: TransactionType
  amount: string
  initiatedByCustomerId: number
  sourceAccountId: number
  destinationAccountId: number
  description: string | null
  createdAt: string
}

export interface AdminLedgerEntrySummary {
  accountId: number
  direction: LedgerDirection
  amount: string
  balanceAfter: string
}

export interface AdminTransactionDetail {
  transaction: AdminTransactionSummary
  entries: AdminLedgerEntrySummary[]
}

export interface AdminAlertSummary {
  id: number
  ruleCode: string
  accountId: number | null
  customerId: number | null
  financialTransactionId: number | null
  severity: 'LOW' | 'MEDIUM' | 'HIGH'
  message: string
  createdAt: string
  acknowledgedAt: string | null
  acknowledgedByAdminId: number | null
}

export interface AdminAuditLogSummary {
  id: number
  adminId: number
  action: string
  targetType: string
  targetId: number
  reason: string | null
  createdAt: string
}

export interface AdminDashboardSummary {
  totalCustomers: number
  totalCustomerAccounts: number
  totalCustomerBalance: string
  totalTransactions: number
  openSupportTickets: number
  unacknowledgedAlerts: number
}
