import { Link } from 'react-router-dom'

/**
 * A quiet, single-line disclosure, deliberately not a banner. SecureBank is a
 * fictional-money simulator; this keeps that clear without dominating any page.
 */
export function FictionalFundsNote() {
  return (
    <p className="fine-print" style={{ textAlign: 'center', marginTop: 16, marginBottom: 0 }}>
      SecureBank uses fictional funds only. <Link to="/about">About</Link>
    </p>
  )
}
