import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAccount, useCloseAccount } from '../../api/useAccounts'
import { useDeposit, useWithdraw } from '../../api/useTransfers'
import { statementCsvUrl, useTransactionHistory, type TransactionFilters } from '../../api/useTransactions'
import { DepositWithdrawForm } from '../../components/DepositWithdrawForm'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime, formatInr } from '../../lib/format'
import type { TransactionType } from '../../api/types'

export function AccountDetailPage() {
  const { id } = useParams()
  const accountId = id ? Number(id) : undefined
  const navigate = useNavigate()
  const { data: account, isLoading, isError, error, refetch } = useAccount(accountId)
  const closeAccount = useCloseAccount()
  const deposit = useDeposit()
  const withdraw = useWithdraw()

  const [activeForm, setActiveForm] = useState<'deposit' | 'withdrawal' | null>(null)
  const [filters, setFilters] = useState<TransactionFilters>({ page: 0 })
  const { data: page } = useTransactionHistory(accountId, filters)

  if (isLoading) return <LoadingState label="Loading account…" />
  if (isError) return <ErrorBanner error={error} onRetry={() => refetch()} />
  if (!account) return null

  async function handleClose() {
    if (!accountId) return
    if (!window.confirm('Close this account? This can only be done for a zero-balance active account.')) return
    await closeAccount.mutateAsync(accountId)
    navigate('/accounts')
  }

  return (
    <div className="stack">
      <div className="card">
        <div className="row-between">
          <div>
            <h1 style={{ margin: 0 }}>{account.nickname ?? 'Account'}</h1>
            <span className="account-number">{account.accountNumber}</span>
          </div>
          <StatusBadge status={account.status} />
        </div>
        <div className="balance" style={{ fontSize: 32, marginTop: 12 }}>
          {formatInr(account.balance)}
        </div>
        {account.status === 'ACTIVE' && (
          <div className="row no-print" style={{ marginTop: 16 }}>
            <button type="button" className="btn btn-secondary" onClick={() => setActiveForm(activeForm === 'deposit' ? null : 'deposit')}>
              Simulated deposit
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => setActiveForm(activeForm === 'withdrawal' ? null : 'withdrawal')}>
              Simulated withdrawal
            </button>
            <a className="btn btn-secondary" href={statementCsvUrl(account.id)}>
              Download CSV statement
            </a>
            <button type="button" className="btn btn-secondary" onClick={() => window.print()}>
              Print statement
            </button>
            {account.balance === '0.00' && (
              <button type="button" className="btn btn-danger" onClick={handleClose} disabled={closeAccount.isPending}>
                Close account
              </button>
            )}
          </div>
        )}
        {account.status === 'FROZEN' && <div className="alert alert-info">This account is frozen and cannot send or receive money.</div>}
      </div>

      {activeForm && (
        <div className="card no-print">
          <h2>{activeForm === 'deposit' ? 'Deposit' : 'Withdrawal'}</h2>
          <DepositWithdrawForm
            accountId={account.id}
            kind={activeForm}
            mutateAsync={(activeForm === 'deposit' ? deposit : withdraw).mutateAsync}
            onDone={() => setActiveForm(null)}
          />
        </div>
      )}

      <div className="card">
        <h2>Transaction history</h2>
        <div className="row no-print" style={{ marginBottom: 16 }}>
          <select
            aria-label="Filter by type"
            value={filters.type ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, type: e.target.value as TransactionType | '', page: 0 }))}
          >
            <option value="">All types</option>
            <option value="DEPOSIT">Deposit</option>
            <option value="WITHDRAWAL">Withdrawal</option>
            <option value="TRANSFER">Transfer</option>
            <option value="BILL_PAYMENT">Bill payment</option>
          </select>
          <input
            aria-label="Search description or reference"
            placeholder="Search description or reference"
            value={filters.search ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, search: e.target.value, page: 0 }))}
          />
          <input aria-label="Min amount" type="number" placeholder="Min ₹" value={filters.minAmount ?? ''} onChange={(e) => setFilters((f) => ({ ...f, minAmount: e.target.value, page: 0 }))} />
          <input aria-label="Max amount" type="number" placeholder="Max ₹" value={filters.maxAmount ?? ''} onChange={(e) => setFilters((f) => ({ ...f, maxAmount: e.target.value, page: 0 }))} />
        </div>
        {!page || page.content.length === 0 ? (
          <EmptyState>No transactions match these filters.</EmptyState>
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Reference</th>
                    <th>Type</th>
                    <th>Counterparty</th>
                    <th>Description</th>
                    <th>Amount</th>
                    <th>Balance after</th>
                  </tr>
                </thead>
                <tbody>
                  {page.content.map((tx) => (
                    <tr key={tx.id}>
                      <td>{formatDateTime(tx.createdAt)}</td>
                      <td>{tx.reference}</td>
                      <td>{tx.type.replace('_', ' ')}</td>
                      <td>{tx.counterpartyDisplayName ?? tx.counterpartyAccountNumber ?? '—'}</td>
                      <td>{tx.description ?? '—'}</td>
                      <td className={tx.direction === 'DEBIT' ? 'text-danger' : 'text-success'}>
                        {tx.direction === 'DEBIT' ? '-' : '+'}
                        {formatInr(tx.amount)}
                      </td>
                      <td>{formatInr(tx.balanceAfter)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="row no-print" style={{ marginTop: 12 }}>
              <button type="button" className="btn btn-secondary btn-sm" disabled={page.number === 0} onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) - 1 }))}>
                Previous
              </button>
              <span className="text-muted">
                Page {page.number + 1} of {Math.max(page.totalPages, 1)}
              </span>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page.number + 1 >= page.totalPages}
                onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) + 1 }))}
              >
                Next
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
