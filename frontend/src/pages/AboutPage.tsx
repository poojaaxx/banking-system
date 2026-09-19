import { Link } from 'react-router-dom'

export function AboutPage() {
  return (
    <div className="auth-shell">
      <div className="card auth-card" style={{ maxWidth: 640 }}>
        <h1>About SecureBank</h1>
        <p>
          SecureBank is a banking simulator built for learning and portfolio purposes. All money in it is fictional: balances,
          deposits, transfers and bill payments are simulated inside this application and never touch a real bank, card network
          or payment system.
        </p>
        <ul>
          <li>It is not a licensed bank and does not offer real accounts, cards, UPI, loans or investments.</li>
          <li>Please do not enter real financial, identity or payment details.</li>
          <li>
            Insights, unusual-activity notes and forecasts are calculated with standard statistics on your own simulated
            transactions. They are estimates, not financial advice, and unusual-activity flags are not fraud findings.
          </li>
          <li>The optional assistant can use an external language model; its answers are checked against your records and labelled.</li>
        </ul>
        <p className="fine-print">Nothing here makes any claim of regulatory approval or compliance.</p>
        <Link to="/login">Back to log in</Link>
      </div>
    </div>
  )
}
