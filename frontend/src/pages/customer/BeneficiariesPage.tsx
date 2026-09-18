import { useState, type FormEvent } from 'react'
import { useAddBeneficiary, useBeneficiaries, useRemoveBeneficiary } from '../../api/useBeneficiaries'
import { ErrorBanner, EmptyState, LoadingState } from '../../components/States'

export function BeneficiariesPage() {
  const { data, isLoading, isError, error, refetch } = useBeneficiaries()
  const addBeneficiary = useAddBeneficiary()
  const removeBeneficiary = useRemoveBeneficiary()
  const [accountNumber, setAccountNumber] = useState('')
  const [nickname, setNickname] = useState('')

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    await addBeneficiary.mutateAsync({ accountNumber, nickname })
    setAccountNumber('')
    setNickname('')
  }

  return (
    <div className="stack">
      <h1>Beneficiaries</h1>
      <div className="card">
        <h2>Add a beneficiary</h2>
        {addBeneficiary.isError && <ErrorBanner error={addBeneficiary.error} />}
        <form onSubmit={onSubmit} noValidate className="row">
          <div className="field" style={{ flex: 1 }}>
            <label htmlFor="accountNumber">Account number</label>
            <input id="accountNumber" required value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} />
          </div>
          <div className="field" style={{ flex: 1 }}>
            <label htmlFor="nickname">Nickname</label>
            <input id="nickname" required maxLength={60} value={nickname} onChange={(e) => setNickname(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-primary" disabled={addBeneficiary.isPending}>
            Add
          </button>
        </form>
      </div>

      {isLoading && <LoadingState />}
      {isError && <ErrorBanner error={error} onRetry={() => refetch()} />}
      {data && data.length === 0 && <EmptyState>No saved beneficiaries yet.</EmptyState>}
      {data && data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Nickname</th>
                <th>Account number</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.map((b) => (
                <tr key={b.id}>
                  <td>{b.nickname}</td>
                  <td className="account-number">{b.accountNumber}</td>
                  <td>
                    <button type="button" className="btn btn-secondary btn-sm" onClick={() => removeBeneficiary.mutate(b.id)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
